package petrolpark.mc.destroy.core.explosion;

import com.petrolpark.compat.create.core.recipe.AdvancedProcessingRecipe;
import com.petrolpark.compat.create.core.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.DestroyRecipeTypes;

/**
 * Display-only recipe type for Obliteration: a block-loot replacement triggered by a custom
 * explosive mixture's detonation. These recipes are JEI-display-only — they never match at
 * runtime (loot-table-driven), but the JEI {@code ObliterationCategory} shows them so
 * players can discover which blocks give which loot when obliterated.
*/
public class ObliterationRecipe extends AdvancedProcessingRecipe<RecipeInput> {

    public ObliterationRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.OBLITERATION, params);
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false; // Display-only · never matches at runtime.
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }
}
