package petrolpark.mc.destroy.content.processing.treetap;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyRecipeTypes;

/**
 * Tree tap "recipe" type — not used for RecipeManager matching; matches always returns false.
 * Its purpose is to register a RecipeType so that JEI can list all {@link BlockTapping} entries
 * as "tapping X → Y fluid" recipes in the ingredient browser.
*/
public class TappingRecipe extends AdvancedProcessingRecipe<RecipeInput> {

    /** Counter used to generate unique recipe ids for programmatic JEI display.*/
    public static int recipeId = 0;

    public TappingRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.TAPPING, params);
    }

    /**
 * Build a programmatic JEI-display TappingRecipe from a {@link BlockTapping} declaration.
 * Returned as a {@link RecipeHolder} so it slots directly into JEI's
 * {@code addRecipes(Supplier<Collection<? extends RecipeHolder<R>>>)} builder.
*/
    public static RecipeHolder<TappingRecipe> create(BlockTapping tapping) {
        net.minecraft.resources.ResourceLocation id = Destroy.asResource("tapping_" + recipeId++);
        TappingRecipe recipe = new AdvancedProcessingRecipe.Builder<TappingRecipe>(TappingRecipe::new, id)
            .require(Ingredient.of(tapping.displayItems.toArray(new ItemStack[0])))
            .output(tapping.result.copy())
            .build();
        return new RecipeHolder<>(id, recipe);
    }

    @Override
    public boolean matches(RecipeInput container, Level level) {
        return false;
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 0;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 1;
    }
}
