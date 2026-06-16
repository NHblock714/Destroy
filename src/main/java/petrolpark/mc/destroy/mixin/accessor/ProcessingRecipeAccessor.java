package petrolpark.mc.destroy.mixin.accessor;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.world.item.ItemStack;

/**
 * Exposes Create's private {@code ProcessingRecipe.forcedResult} one-shot supplier.
 *
 * <p>Create 6's {@code SequencedAssemblyRecipe.getRecipes} arms this field with the
 * {@code advance()} supplier that performs assembly bookkeeping (step counter + progress
 * component) when it hands a sub-recipe to a machine. Destroy's
 * {@code CircuitDeployerApplicationRecipe.specify} needs to read the already-armed supplier
 * so it can compose with it (run advance, then stamp the mask's circuit pattern on top)
 * instead of clobbering it — clobbering loses the step counter and strands the assembly
 * at the deploying step. Mirrors the upstream 1.20.1 accessor of the same name.</p>
 */
@Mixin(ProcessingRecipe.class)
public interface ProcessingRecipeAccessor {

    @Accessor(value = "forcedResult", remap = false)
    Supplier<ItemStack> destroy$getForcedResult();
}
