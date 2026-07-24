package petrolpark.mc.destroy.content.processing.phytomining;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.DestroyRecipeTypes;

/**
 * Crop-mutation "recipe" type — JEI-display-only; matches always returns false. Actual
 * crop-mutation happens via {@link HyperaccumulatingFertilizerItem} / {@link MagicBeetrootShootsBlock}
 * runtime logic (referencing {@link CropMutation}). Purpose is to register a RecipeType so JEI
 * can list all {@link CropMutation} entries as "grow X beetroot from Y crop" recipes.
*/
public class PhytominingRecipe extends AdvancedProcessingRecipe<RecipeInput> {

    /** Monotonic counter for synthesizing unique recipe IDs per {@link CropMutation}.*/
    private static int counter = 0;

    /** The backing {@link CropMutation} this synthetic recipe displays (used by MutationCategory).*/
    private CropMutation mutation;

    public PhytominingRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.MUTATION, params);
    }

    @Override
    public boolean matches(RecipeInput container, Level level) {
        return false;
    }

    @Override
    protected int getMaxInputCount() {
        return 2;
    }

    @Override
    protected int getMaxOutputCount() {
        return 2;
    }

    /**
 * Synthesize a JEI-display-only PhytominingRecipe for the given {@link CropMutation}. The
 * recipe's ingredients + outputs are populated from the mutation's start/end crop suppliers;
 * ore-specific mutations overwrite these with the ore→resultant-block pair.
*/
    public static PhytominingRecipe create(CropMutation mutation) {
        AdvancedProcessingRecipe.Builder<PhytominingRecipe> recipeBuilder =
            new AdvancedProcessingRecipe.Builder<>(PhytominingRecipe::new, Destroy.asResource("mutation_" + counter++))
                .withItemIngredients(
                    Ingredient.of(new ItemStack(mutation.getStartCropSupplier().get().asItem(), 1)),
                    Ingredient.of(new ItemStack(DestroyItems.HYPERACCUMULATING_FERTILIZER.get(), 1)))
                .withItemOutputs(new ProcessingOutput(
                    new ItemStack(mutation.getResultantCropSupplier().get().getBlock().asItem(), 1), 1.0f));
        if (mutation.isOreSpecific()) {
            Block oreBlock = mutation.getOreSupplier().get();
            recipeBuilder
                .withItemIngredients(Ingredient.of(new ItemStack(oreBlock.asItem(), 1)))
                .withItemOutputs(new ProcessingOutput(
                    new ItemStack(mutation.getResultantBlockUnder(oreBlock.defaultBlockState()).getBlock().asItem(), 1), 1.0f));
        }
        PhytominingRecipe recipe = recipeBuilder.build();
        recipe.mutation = mutation;
        return recipe;
    }

    public CropMutation getMutation() {
        return this.mutation;
    }
}
