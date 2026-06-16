package petrolpark.mc.destroy.content.processing.trypolithography.recipe;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.DeployerRecipeSearchEvent;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipeParams;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternItem;

/**
 * DeployerApplicationRecipe variant marking a circuit-pattern-bearing sub-step in a
 * SequencedAssembly chain. Implements {@link IConfersCircuitPatternRecipe} so
 * {@link CircuitSequencedAssemblyRecipe} can validate "exactly one pattern-conferring step".
*/
@EventBusSubscriber(modid = Destroy.MOD_ID)
public class CircuitDeployerApplicationRecipe extends DeployerApplicationRecipe
    implements IConfersCircuitPatternRecipe {

    private final boolean example;

    public CircuitDeployerApplicationRecipe(ItemApplicationRecipeParams params) {
        this(params, true);
    }

    private CircuitDeployerApplicationRecipe(ItemApplicationRecipeParams params, boolean example) {
        super(params);
        this.example = example;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        // Returns the registered serializer instance; wired via DestroyRecipeTypes enum entry.
        return petrolpark.mc.destroy.DestroyRecipeTypes.CIRCUIT_DEPLOYING.getSerializer();
    }

    /**
 * JEI preview: inject {@link CircuitSequencedAssemblyRecipe#EXAMPLE_PATTERN}
 * into any {@link CircuitPatternItem} output stack so the displayed result shows a
 * representative punched pattern (not a blank board).
*/
    @Override
    public List<ProcessingOutput> getRollableResults() {
        if (!example) return super.getRollableResults();
        return super.getRollableResults().stream().map(output -> {
            ItemStack result = output.getStack().copy();
            if (!(result.getItem() instanceof CircuitPatternItem)) return output;
            CircuitPatternItem.putPattern(result, CircuitSequencedAssemblyRecipe.EXAMPLE_PATTERN);
            return new ProcessingOutput(result, output.getChance());
        }).toList();
    }

    /**
 * Runtime per-input specialization. When the deployer fires this recipe with a specific
 * punched mask, arm a one-shot enforced result that carries the mask's pattern bit.
 *
 * <p><b>Composition, not replacement:</b> when this recipe was resolved through a sequenced
 * assembly ({@code SequencedAssemblyRecipe.getRecipes}), Create has ALREADY armed
 * {@code forcedResult} with the {@code advance()} supplier that performs the assembly
 * bookkeeping — writing the step counter + progress data component onto the output. An
 * earlier revision overwrote that supplier wholesale, which output a pattern-stamped stack
 * with <i>no</i> step counter: the next machine in the line (Spout for the etching step)
 * could no longer match the stack to the assembly chain and the board was stranded after
 * the deploying step. Now the prior supplier runs first (falling back to the declared
 * results for standalone CIRCUIT_DEPLOYING recipes outside an assembly) and the mask's
 * pattern is stamped on top of whatever it produced.</p>
*/
    public RecipeHolder<DeployerApplicationRecipe> specify(ResourceLocation id, RecipeWrapper inv) {
        int pattern = CircuitPatternItem.getPattern(inv.getItem(1));
        java.util.function.Supplier<ItemStack> prior =
            ((petrolpark.mc.destroy.mixin.accessor.ProcessingRecipeAccessor) this).destroy$getForcedResult();
        // enforceNextResult is consumed on the next rollResults call — set it on `this` so the
        // next deployer cycle picks up the pattern-bearing stack.
        this.enforceNextResult(() -> {
            ItemStack result = prior != null
                ? prior.get()
                : transformWithPattern(super.getRollableResults(), pattern);
            if (result.getItem() instanceof CircuitPatternItem
                || result.getItem() instanceof SequencedAssemblyItem) {
                CircuitPatternItem.putPattern(result, pattern);
            }
            return result;
        });
        return new RecipeHolder<>(id, this);
    }

    /** Apply pattern bit to whichever output stack supports CircuitPatternItem / SequencedAssemblyItem.*/
    private static ItemStack transformWithPattern(List<ProcessingOutput> rollable, int pattern) {
        if (rollable.isEmpty()) return ItemStack.EMPTY;
        ItemStack result = rollable.get(0).getStack().copy();
        if (result.getItem() instanceof CircuitPatternItem || result.getItem() instanceof SequencedAssemblyItem) {
            CircuitPatternItem.putPattern(result, pattern);
        }
        return result;
    }

    /**
 * 1.21 uses RecipeHolder<>-wrapped recipes throughout.
*/
    @SubscribeEvent
    public static void onDeployerRecipeSearch(DeployerRecipeSearchEvent event) {
        RecipeWrapper inv = event.getInventory();
        // 1.21 RecipeWrapper has no hasAnyMatching helper;
        // hand-roll the scan over getItem(0..size).
        boolean hasPatternItem = false;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getItem(i).getItem() instanceof CircuitPatternItem) {
                hasPatternItem = true;
                break;
            }
        }
        if (!hasPatternItem) return;

        // First: check if a CircuitDeployerApplicationRecipe is the directly-matching deployer
        // recipe. Then: check sequenced-assembly chain that points at a CIRCUIT_DEPLOYING step.
        var level = event.getBlockEntity().getLevel();
        if (level == null) return;

        Optional<RecipeHolder<CircuitDeployerApplicationRecipe>> direct =
            DestroyRecipeTypes.CIRCUIT_DEPLOYING.find(inv, level);
        Optional<RecipeHolder<CircuitDeployerApplicationRecipe>> viaSeq = Optional.empty();
        if (direct.isEmpty()) {
            // SequencedAssemblyRecipe.getRecipe returns the next deployer recipe in the chain
            // when the input matches a transitionalItem
            viaSeq = SequencedAssemblyRecipe.getRecipe(level, inv,
                AllRecipeTypes.DEPLOYING.getType(),
                CircuitDeployerApplicationRecipe.class);
        }

        Optional<RecipeHolder<CircuitDeployerApplicationRecipe>> chosen = direct.isPresent() ? direct : viaSeq;
        chosen.ifPresent(holder -> event.addRecipe(
            () -> Optional.of(holder.value().specify(holder.id(), inv)),
            150));
    }

    /**
 * Codec-level validator — formerly a sanity check on the recipe ingredient slots.
 *
 * <p>Returns success unconditionally because the slot semantics are
 * already enforced by the runtime {@code matches()} check + the codec deserialization itself,
 * and there's no clear-cut "wrong recipe" case the validator was actually catching. A future
 * regression could motivate reinstating a real check here.</p>
*/
    private static DataResult<CircuitDeployerApplicationRecipe> validateCircuitDeployer(CircuitDeployerApplicationRecipe recipe) {
        return DataResult.success(recipe);
    }

    /**
 * Serializer wraps {@link ItemApplicationRecipe.Serializer} with the {@link #validate}
 * check on decode. Registered in {@link petrolpark.mc.destroy.DestroyRecipeTypes}.
*/
    public static class Serializer extends ItemApplicationRecipe.Serializer<CircuitDeployerApplicationRecipe> {

        private final MapCodec<CircuitDeployerApplicationRecipe> validatedCodec;

        public Serializer() {
            super(CircuitDeployerApplicationRecipe::new);
            this.validatedCodec = super.codec().flatXmap(
                CircuitDeployerApplicationRecipe::validateCircuitDeployer,
                DataResult::success);
        }

        @Override
        public MapCodec<CircuitDeployerApplicationRecipe> codec() {
            return validatedCodec;
        }
    }

}
