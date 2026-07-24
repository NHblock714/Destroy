package petrolpark.mc.destroy.content.processing.extrusion;

import java.util.ArrayList;
import java.util.List;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyRecipeTypes;

/**
 * Extrusion "recipe" type — JEI-display-only; matches always returns false (the actual extrusion
 * substitution happens via {@link ExtrudableMovementBehaviour.visitNewPosition}). Purpose is to
 * register a RecipeType so JEI can enumerate {@link BlockExtrusion#EXTRUSIONS} entries as
 * "push X through die → Y" recipes.
*/
public class ExtrusionRecipe extends AdvancedProcessingRecipe<RecipeInput> {

    /** Counter for unique recipe-id generation in {@link #create(BlockExtrusion)}.*/
    private static int recipeId = 0;

    /** Consumed by DestroyJEI's
 * {@code addRecipes(() -> ExtrusionRecipe.RECIPES)} builder call.
*/
    public static final List<RecipeHolder<ExtrusionRecipe>> RECIPES = new ArrayList<>();

    
    private BlockExtrusion extrusion;

    public ExtrusionRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.EXTRUSION, params);
    }

    /** Builds a JEI-display-only Extrusion recipe
 * for one {@link BlockExtrusion} entry.
*/
    public static RecipeHolder<ExtrusionRecipe> create(BlockExtrusion extrusion) {
        Block ingredientBlock = BlockExtrusion.EXTRUSIONS.inverse().get(extrusion);
        ResourceLocation id = Destroy.asResource("extrusion_" + recipeId++);
        ExtrusionRecipe recipe = new AdvancedProcessingRecipe.Builder<ExtrusionRecipe>(ExtrusionRecipe::new, id)
            .require(Ingredient.of(new ItemStack(ingredientBlock.asItem(), 1)))
            .output(new ItemStack(extrusion.getExtruded(
                ingredientBlock.defaultBlockState(), Direction.NORTH).getBlock().asItem(), 1))
            .build();
        recipe.extrusion = extrusion;
        return new RecipeHolder<>(id, recipe);
    }

    public BlockExtrusion getExtrusion() {
        return extrusion;
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
        return 1;
    }
}
