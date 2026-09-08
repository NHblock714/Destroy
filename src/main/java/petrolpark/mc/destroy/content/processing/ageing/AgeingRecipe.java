package petrolpark.mc.destroy.content.processing.ageing;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.resources.ResourceLocation;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.core.recipe.SingleFluidRecipe;

/**
 * Aging Barrel recipe: one fluid ingredient plus up to two item ingredients, yielding one fluid.
 * Matched by hand in {@link AgeingBarrelBlockEntity#checkRecipe()} rather than through
 * {@code matches}, which always returns {@code false}.
 */
public class AgeingRecipe extends SingleFluidRecipe {

    public AgeingRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.AGING, params);
    }

    @Override
    protected int getMaxInputCount() {
        return 2;
    }

    @Override
    protected int getMaxOutputCount() {
        return 0;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 1;
    }

    @Override
    public String getRecipeTypeName() {
        return "aging";
    }

    public static petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe.Builder<AgeingRecipe> builder(ResourceLocation id) {
        return new petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe.Builder<>(AgeingRecipe::new, id);
    }
}
