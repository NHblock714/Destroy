package petrolpark.mc.destroy.compat.jei.category;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableInt;

import com.mojang.blaze3d.vertex.PoseStack;
import petrolpark.mc.library.core.client.rendering.PetrolparkGuiTexture;
import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.item.ItemHelper;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;

import net.createmod.catnip.data.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.compat.jei.animation.GUIBlockRenderer;
import petrolpark.mc.destroy.content.processing.ageing.AgeingRecipe;
import petrolpark.mc.destroy.content.processing.ageing.AgingBarrelBlock;

/**
 * JEI category for {@link AgeingRecipe} · displays fluid + item inputs aging inside an Aging
 * Barrel over time into a fluid result. Shows the recipe's processing duration (converted to
 * minutes:seconds) in a text box below.
*/
public class AgingCategory extends PetrolparkRecipeCategory<AgeingRecipe> {

    private static final GUIBlockRenderer blockRenderer = new GUIBlockRenderer();

    public AgingCategory(Info<AgeingRecipe> info, IJeiHelpers helpers) {
        super(info, helpers);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AgeingRecipe recipe, IFocusGroup focuses) {
        List<Pair<Ingredient, MutableInt>> condensedIngredients = ItemHelper.condenseIngredients(recipe.getIngredients());

        int size = condensedIngredients.size();
        int xOffset = 8 + (size < 3 ? (3 - size) * 19 / 2 : 0);
        int i = 1;

        SizedFluidIngredient fluidIngredient = recipe.getRequiredFluid();
        addFluidSlot(builder, xOffset, 33, fluidIngredient);

        for (Pair<Ingredient, MutableInt> pair : condensedIngredients) {
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack itemStack : pair.getFirst().getItems()) {
                ItemStack copy = itemStack.copy();
                copy.setCount(pair.getSecond().getValue());
                stacks.add(copy);
            }

            builder.addSlot(RecipeIngredientRole.INPUT, xOffset + (i % 3) * 19, 33)
                .setBackground(getRenderedSlot(), -1, -1)
                .addItemStacks(stacks);
            i++;
        }

        addFluidSlot(builder, 142, 35, recipe.getFluidResults().get(0));
    }

    @Override
    public void draw(AgeingRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        PoseStack stack = graphics.pose();
        AllGuiTextures.JEI_DOWN_ARROW.render(graphics, 136, 14);
        AllGuiTextures.JEI_SHADOW.render(graphics, 81, 50);

        // Aging Barrel block render
        stack.pushPose();
        stack.translate(getBackground().getWidth() / 2f + 4, 51, 0);
        blockRenderer.renderBlock(
            DestroyBlocks.AGING_BARREL.getDefaultState()
                .setValue(AgingBarrelBlock.IS_OPEN, true)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
            graphics, 23);
        stack.popPose();

        // Duration text · format: mm:ss
        PetrolparkGuiTexture.JEI_TEXT_BOX_LONG.render(graphics, 4, 63);
        int seconds = (recipe.getProcessingDuration() % 1200) / 20;
        graphics.drawString(
            Minecraft.getInstance().font,
            DestroyLang.translate(
                "tooltip.aging_barrel.aging_time",
                "" + recipe.getProcessingDuration() / 1200 + ":" + (seconds < 10 ? "0" : "") + seconds
            ).string(),
            9, 69, 0xFFFFFF, false);
    }
}
