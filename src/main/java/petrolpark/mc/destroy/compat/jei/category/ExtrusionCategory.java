package petrolpark.mc.destroy.compat.jei.category;

import com.mojang.blaze3d.vertex.PoseStack;
import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.foundation.gui.AllGuiTextures;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.compat.jei.animation.GUIBlockRenderer;
import petrolpark.mc.destroy.content.processing.extrusion.ExtrusionRecipe;

/**
 * JEI category for {@link ExtrusionRecipe} · displays item input → extruded item output with
 * an EXTRUSION_DIE block shown in the centre (rotated Z-axis, no animation).
*/
public class ExtrusionCategory extends PetrolparkRecipeCategory<ExtrusionRecipe> {

    private static final GUIBlockRenderer blockRenderer = new GUIBlockRenderer();

    public ExtrusionCategory(Info<ExtrusionRecipe> info, IJeiHelpers helpers) {
        super(info, helpers);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, ExtrusionRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 27, 36)
            .setBackground(getRenderedSlot(), -1, -1)
            .addIngredients(recipe.getIngredients().get(0));

        ProcessingOutput output = recipe.getRollableResults().get(0);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 131, 36)
            .setBackground(getRenderedSlot(output), -1, -1)
            .addItemStack(output.getStack())
            .addRichTooltipCallback(addStochasticTooltip(output));
    }

    @Override
    public void draw(ExtrusionRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_SHADOW.render(graphics, 61, 26);
        AllGuiTextures.JEI_LONG_ARROW.render(graphics, 52, 39);
        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(72, 27, 0);
        blockRenderer.renderBlock(
            DestroyBlocks.EXTRUSION_DIE.getDefaultState().setValue(BlockStateProperties.AXIS, Axis.Z),
            graphics, 24);
        ms.popPose();
    }
}
