package petrolpark.mc.destroy.compat.jei.category;

import petrolpark.mc.library.core.client.rendering.PetrolparkGuiTexture;
import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;
import com.simibubi.create.content.processing.recipe.HeatCondition;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.compat.jei.animation.HeatConditionRenderer;
import petrolpark.mc.destroy.content.processing.distillation.DistillationRecipe;

/**
 * JEI category for {@link DistillationRecipe} · displays stacked distillation-tower layout with
 * fluid input at the bottom + multiple fractionated fluid outputs at varying heights + bubble-cap
 * count indicator.
*/
public class DistillationCategory extends PetrolparkRecipeCategory<DistillationRecipe> {

    public DistillationCategory(Info<DistillationRecipe> info, IJeiHelpers helpers) {
        super(info, helpers);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, DistillationRecipe recipe, IFocusGroup focuses) {
        int fractions = recipe.getFluidResults().size();

        // Bubble-cap catalyst count indicator.
        builder.addSlot(RecipeIngredientRole.CATALYST, 18, 30)
            .setBackground(getRenderedSlot(), -1, -1)
            .addItemStack(new ItemStack(DestroyBlocks.BUBBLE_CAP.get(), fractions + 1));

        addOptionalRequiredBiomeSlot(builder, recipe, 18, 49);

        addFluidSlot(builder, 18, 81, recipe.getRequiredFluid());

        for (int i = 0; i < fractions; i++) {
            FluidStack result = recipe.getFluidResults().get(i);
            addFluidSlot(builder, i % 2 == 0 ? 94 : 74, 74 - (12 * i), result);
        }

        // HeatConditionRenderer.addHeatConditionSlots: Cooler / Blaze Burner / Blaze Cake
        // catalyst slots based on recipe's HeatCondition. Refrigerant fluid slot degraded (see
        // HeatConditionRenderer class javadoc · MoleculeTagFluidIngredient not ported).
        HeatCondition requiredHeat = recipe.getRequiredHeat();
        if (requiredHeat != null) {
            HeatConditionRenderer.addHeatConditionSlots(builder, 18, 111, requiredHeat);
        }
    }

    @Override
    public void draw(DistillationRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        for (int i = 0; i < recipe.getFluidResults().size(); i++) {
            PetrolparkGuiTexture.JEI_DISTILLATION_TOWER_MIDDLE.render(graphics, 55, 76 - (12 * i));
            if (i % 2 == 0) PetrolparkGuiTexture.JEI_DISTILLATION_TOWER_BRANCH.render(graphics, 75, 81 - (12 * i));
        }
        PetrolparkGuiTexture.JEI_DISTILLATION_TOWER_TOP.render(graphics, 55, 2 + (7 - recipe.getFluidResults().size()) * 12);
        PetrolparkGuiTexture.JEI_DISTILLATION_TOWER_BOTTOM.render(graphics, 55, 88);
        PetrolparkGuiTexture.JEI_DISTILLATION_TOWER_BRANCH.render(graphics, 35, 90);
        PetrolparkGuiTexture.JEI_TEXT_BOX_SHORT.render(graphics, 4, 102);

        // HeatConditionRenderer.drawHeatConditionName: paint the condition label inside
        // the text-box frame.
        HeatCondition requiredHeat = recipe.getRequiredHeat();
        if (requiredHeat == null) return;
        HeatConditionRenderer.drawHeatConditionName(net.minecraft.client.Minecraft.getInstance().font,
            graphics, 8, 105, requiredHeat);
    }
}
