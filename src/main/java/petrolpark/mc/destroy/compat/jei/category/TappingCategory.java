package petrolpark.mc.destroy.compat.jei.category;

import java.util.ArrayList;
import java.util.List;

import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.crafting.RecipeHolder;

import petrolpark.mc.destroy.compat.jei.animation.AnimatedTreeTap;
import petrolpark.mc.destroy.content.processing.treetap.BlockTapping;
import petrolpark.mc.destroy.content.processing.treetap.TappingRecipe;

/**
 * JEI category for {@link TappingRecipe} · displays log-item input → fluid result with an
 * animated TreeTap in the centre.
 *
 * <p>Recipes are statically generated from {@link BlockTapping#ALL_TAPPINGS} at class-load — one
 * TappingRecipe per registered tap-able block. The {@link #RECIPES} list is consumed by
 * DestroyJEI's {@code addRecipes(() -> TappingCategory.RECIPES)} builder call.</p>
*/
public class TappingCategory extends PetrolparkRecipeCategory<TappingRecipe> {

    /**
 * Consumed by DestroyJEI as the source of programmatic JEI display recipes.
*/
    public static final List<RecipeHolder<TappingRecipe>> RECIPES = new ArrayList<>(BlockTapping.ALL_TAPPINGS.size());

    static {
        for (BlockTapping tapping : BlockTapping.ALL_TAPPINGS) {
            RECIPES.add(TappingRecipe.create(tapping));
        }
    }

    private final AnimatedTreeTap tap = new AnimatedTreeTap();

    public TappingCategory(Info<TappingRecipe> info, IJeiHelpers helpers) {
        super(info, helpers);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, TappingRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 51)
            .setBackground(getRenderedSlot(), -1, -1)
            .addIngredients(recipe.getIngredients().get(0));

        addFluidSlot(builder, 131, 50, recipe.getFluidResults().get(0));
    }

    @Override
    public void draw(TappingRecipe recipe, IRecipeSlotsView iRecipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 41);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 54);

        tap.draw(graphics, 80, 50);
    }
}
