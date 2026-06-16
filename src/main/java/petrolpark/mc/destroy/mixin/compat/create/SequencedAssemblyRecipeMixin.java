package petrolpark.mc.destroy.mixin.compat.create;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipeSerializer;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternItem;
import petrolpark.mc.destroy.content.processing.trypolithography.recipe.CircuitSequencedAssemblyRecipe;

/**
 * Port of upstream 1.20.1 {@code SequencedAssemblyRecipeMixin} — the half of the
 * trypolithography pattern-carry mechanism that lives inside Create's sequenced-assembly
 * step advancement. Without it, Create's stock {@code advance}:
 * <ul>
 *   <li>mid-sequence: returns a <b>fresh copy of the transitional item template</b>, discarding
 *       every component the in-flight stack carried — including the circuit pattern stamped by
 *       the {@code destroy:circuit_deploying} step;</li>
 *   <li>final step: rolls a fresh stack from the result pool — same loss.</li>
 * </ul>
 * Net effect: circuit boards always came out of the assembly line blank (pattern = 0), so
 * pattern-gated recipes (Colourimeter / Pollutometer / Redstone Programmer / any modpack
 * {@code circuit_pattern_item} ingredient) could never match a player-made board.
 *
 * <p>This overwrite mirrors Create's logic exactly, with two deviations (both from upstream):
 * <ol>
 *   <li>Mid-sequence, from step 1 onward the result is copied from the <b>input stack</b>
 *       rather than the transitional template, conserving accumulated components.</li>
 *   <li>When the recipe belongs to a {@link CircuitSequencedAssemblyRecipe} (checked via its
 *       serializer), the circuit pattern is re-stamped from input to result on <b>every</b>
 *       advance, including the final roll.</li>
 * </ol>
 *
 * <p>1.21 changes vs upstream: {@code advance} gained {@code (ResourceLocation, RandomSource)}
 * params; progress NBT became the {@code AllDataComponents.SEQUENCED_ASSEMBLY} data component
 * carrying Create's {@code SequencedAssembly(id, step, progress)} record.</p>
 */
@Mixin(SequencedAssemblyRecipe.class)
public abstract class SequencedAssemblyRecipeMixin {

    @Shadow protected SequencedAssemblyRecipeSerializer serializer;
    @Shadow protected List<SequencedRecipe<?>> sequence;
    @Shadow protected int loops;

    @Shadow public abstract ItemStack getTransitionalItem();

    // Private shadow targets — mixin requires a dummy body (abstract private is illegal Java).
    @Shadow private int getStep(ItemStack stack) { throw new AssertionError(); }
    @Shadow private ItemStack rollResult(RandomSource random) { throw new AssertionError(); }

    /**
     * @author NHblock714 (port of Petrolpark's upstream mixin)
     * @reason Conserve the in-flight stack's components across assembly steps and carry the
     * circuit pattern onto the final result — Create's stock implementation resets to the
     * transitional template each step and rolls a component-less stack at the end, which
     * silently strips the trypolithography circuit pattern.
     */
    @Overwrite(remap = false)
    private ItemStack advance(ResourceLocation id, ItemStack input, RandomSource random) {
        int step = getStep(input);
        ItemStack result;
        if ((step + 1) / sequence.size() >= loops) {
            result = rollResult(random);
        } else {
            result = step == 0 ? getTransitionalItem().copyWithCount(1) : input.copyWithCount(1);
            result.set(AllDataComponents.SEQUENCED_ASSEMBLY,
                new SequencedAssemblyRecipe.SequencedAssembly(
                    id, step + 1, (step + 1f) / (sequence.size() * loops)));
        }

        if (serializer instanceof CircuitSequencedAssemblyRecipe.Serializer) {
            CircuitPatternItem.putPattern(result, CircuitPatternItem.getPattern(input));
        }

        return result;
    }
}
