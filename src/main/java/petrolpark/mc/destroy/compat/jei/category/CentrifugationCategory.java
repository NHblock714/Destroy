package petrolpark.mc.destroy.compat.jei.category;

import petrolpark.mc.library.core.client.rendering.PetrolparkGuiTexture;
import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import petrolpark.mc.destroy.compat.jei.animation.AnimatedCentrifuge;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugationRecipe;

/**
 * JEI category for {@link CentrifugationRecipe} · displays a single-fluid → 2-fluid separation
 * (dense + light outputs), with an animated 3D Centrifuge block in the centre.
*/
public class CentrifugationCategory extends PetrolparkRecipeCategory<CentrifugationRecipe> {

    private static final AnimatedCentrifuge centrifuge = new AnimatedCentrifuge();

    private static final int CENTRIFUGE_X = 35;
    private static final int CENTRIFUGE_Y = 60;

    public CentrifugationCategory(Info<CentrifugationRecipe> info, IJeiHelpers helpers) {
        super(info, helpers);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CentrifugationRecipe recipe, IFocusGroup focuses) {
        SizedFluidIngredient inputFluid = recipe.getFluidIngredients().iterator().next();
        FluidStack denseOutputFluid = recipe.getDenseOutputFluid();
        FluidStack lightOutputFluid = recipe.getLightOutputFluid();

        addFluidSlot(builder, 3, 3, inputFluid);
        addOptionalRequiredBiomeSlot(builder, recipe, 3, 19);

        addFluidSlot(builder, 99, 38, denseOutputFluid);
        addFluidSlot(builder, 33, 96, lightOutputFluid);
    }

    @Override
    public void draw(CentrifugationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        // Note: 1.21 CreateRecipeCategory#draw(T, ...) is abstract protected · no super to call.
        AllGuiTextures.JEI_SHADOW.render(graphics, CENTRIFUGE_X - 19, CENTRIFUGE_Y - 5);
        centrifuge.draw(graphics, CENTRIFUGE_X, CENTRIFUGE_Y);

        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 29, 9);
        PetrolparkGuiTexture.JEI_SHORT_DOWN_ARROW.render(graphics, 33, 70);
        PetrolparkGuiTexture.JEI_SHORT_RIGHT_ARROW.render(graphics, 72, 38);
    }
}
