package petrolpark.mc.destroy.core.chemistry.recipe;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.core.recipe.SingleFluidRecipe;

/**
 * A recipe that converts a non-mixture fluid (e.g. vanilla water, lava, or a Destroy Potion Fluid) into
 * its equivalent Mixture — allowing chemistry Basin/Vat machinery to accept inputs from the wider fluid
 * ecosystem. Strict 1→1 amount mapping: any X mB of input fluid produces X mB of output Mixture.
*/
public class MixtureConversionRecipe extends SingleFluidRecipe {

    public MixtureConversionRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.MIXTURE_CONVERSION, params);
        if (getFluidIngredients().isEmpty()
            || getFluidResults().isEmpty()
            || !DestroyFluids.isMixture(getFluidResults().get(0))) {
            Destroy.LOGGER.warn("Mixture conversion recipes must define a single input and a Mixture output");
        }
    }

    /**
 * Pre-requisite: caller has verified the input stack matches this recipe's fluid ingredient.
 * Scales the output Mixture's amount to match the input stack.
 *
 * @param nonMixtureStack the non-mixture input being converted
 * @return a Mixture FluidStack with the Mixture payload from this recipe + the input's amount
*/
    public FluidStack apply(FluidStack nonMixtureStack) {
        FluidStack result = getFluidResults().get(0).copy();
        result.setAmount(nonMixtureStack.getAmount());
        return result;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 1;
    }

    @Override
    public String getRecipeTypeName() {
        return "Mixture Conversion";
    }
}
