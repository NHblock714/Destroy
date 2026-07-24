package petrolpark.mc.destroy.compat.jei.category;

import java.util.Collections;
import java.util.List;

import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.item.TooltipHelper;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;

import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe;

/**
 * JEI category for {@link MixtureConversionRecipe} · displays a single-fluid → single-fluid
 * conversion (e.g. converting a raw water/milk/etc. fluid into a Mixture-typed fluid before
 * feeding it into a Vat).
 *
 * <p>This category will be registered in {@link petrolpark.mc.destroy.compat.jei.DestroyJEI
 * DestroyJEI} via {@code registerCategories} in a follow-up session (requires
 * {@code DestroyRecipeTypes.MIXTURE_CONVERSION} recipe type wire to JEI RecipeType).</p>
*/
public class MixtureConversionCategory extends PetrolparkRecipeCategory<MixtureConversionRecipe> {

    public MixtureConversionCategory(Info<MixtureConversionRecipe> info, IJeiHelpers helpers) {
        super(info, helpers);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MixtureConversionRecipe recipe, IFocusGroup focuses) {
        addFluidSlot(builder, 2, 2, recipe.getFluidIngredients().get(0));
        addFluidSlot(builder, 107, 2, recipe.getFluidResults().get(0));
    }

    @Override
    public List<Component> getTooltipStrings(MixtureConversionRecipe recipe, IRecipeSlotsView recipeSlotsView,
                                              double mouseX, double mouseY) {
        if (mouseX > 20 && mouseX < 105) {
            return TooltipHelper.cutStringTextComponent(
                DestroyLang.translate("recipe.mixture_conversion.description").string(),
                Palette.GRAY_AND_WHITE);
        }
        return Collections.emptyList();
    }

    @Override
    public void draw(MixtureConversionRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        AllGuiTextures.JEI_LONG_ARROW.render(guiGraphics, 27, 6);
    }
}
