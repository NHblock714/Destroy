package petrolpark.mc.destroy.content.product.fireretardant;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.core.recipe.SingleFluidRecipe;

/**
 * Recipe type for "apply flame retardant coating to an item". Consumes a fluid ingredient of
 * a pre-configured amount; the applied item gets the {@code destroy:fireproof} Contaminant
 * (which makes it fire-resistant). Matching is done manually via
 * {@link FireproofingHelper} (no RecipeManager {@code matches} lookup per-slot), so
 * {@link #matches} always returns {@code true} — the recipe is applicable whenever its fluid
 * ingredient matches the available fluid.
*/
public class FlameRetardantApplicationRecipe extends SingleFluidRecipe {

    public FlameRetardantApplicationRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.FLAME_RETARDANT_APPLICATION, params);
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return true;
    }

    @Override
    protected int getMaxInputCount() {
        return 0;
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
        return "flame retardant application";
    }
}
