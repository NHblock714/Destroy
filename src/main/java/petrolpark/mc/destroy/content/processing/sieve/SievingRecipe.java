package petrolpark.mc.destroy.content.processing.sieve;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.DestroyRecipeTypes;

/**
 * Recipe for the Mechanical Sieve — takes a single item input, rolls a list of outputs with
 * optional first-time-lucky semantics (guarantee best result on first craft per player, driven
 * by the recipe JSON's {@code firstTimeLuckyKey} field on
 * {@link AdvancedProcessingRecipeParams}).
*/
public class SievingRecipe extends AdvancedProcessingRecipe<RecipeInput> {

    public SievingRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.SIEVING, params);
    }

    @Override
    public boolean matches(RecipeInput inv, Level level) {
        if (inv.isEmpty()) return false;
        return ingredients.get(0).test(inv.getItem(0));
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 16;
    }

    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }
}
