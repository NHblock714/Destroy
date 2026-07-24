package petrolpark.mc.destroy.content.processing.distillation;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.core.recipe.SingleFluidRecipe;

/**
 * Distillation recipe — fractional distillation on a Distillation Tower: 1 fluid input →
 * multiple fluid outputs (the "fractions"). Requires heat. Duration is implicit from number of
 * fractions (not specifiable). Biome-specific recipes supported via inherited
 * {@link AdvancedProcessingRecipeParams#allowedBiomes}.
*/
public class DistillationRecipe extends SingleFluidRecipe {

    public DistillationRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.DISTILLATION, params);
    }

    /** The number of fractions this recipe produces (= fluid output count).*/
    public int getFractions() {
        return getFluidResults().size();
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 7;
    }

    @Override
    protected boolean canRequireHeat() {
        return true;
    }

    @Override
    protected boolean canSpecifyDuration() {
        return false;
    }

    @Override
    public String getRecipeTypeName() {
        return "distillation";
    }
}
