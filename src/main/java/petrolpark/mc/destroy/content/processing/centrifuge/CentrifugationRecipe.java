package petrolpark.mc.destroy.content.processing.centrifuge;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.core.recipe.SingleFluidRecipe;

/**
 * Centrifugation recipe — a single fluid input is separated into exactly 2 fluid outputs (index 0
 * dense, index 1 light).
*/
public class CentrifugationRecipe extends SingleFluidRecipe {

    public CentrifugationRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.CENTRIFUGATION, params);
    }

    public FluidStack getDenseOutputFluid() {
        checkForValidOutputs();
        return fluidResults.get(0);
    }

    public FluidStack getLightOutputFluid() {
        checkForValidOutputs();
        return fluidResults.get(1);
    }

    private void checkForValidOutputs() {
        // 1.21 note: ProcessingRecipe no longer exposes an id field directly (recipe ID lives on
        // the containing RecipeHolder); error message omits the specific recipe ID.
        if (fluidResults.isEmpty() || fluidResults.size() != 2) {
            throw new IllegalStateException("Centrifugation Recipe contains the wrong number of output fluids.");
        }
    }

    @Override
    public String getRecipeTypeName() {
        return "Centrifugation";
    }
}
