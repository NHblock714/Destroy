package petrolpark.mc.destroy.compat.jei.category;

import java.util.Collections;
import java.util.List;

import petrolpark.mc.library.core.client.rendering.PetrolparkGuiTexture;
import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;
import petrolpark.mc.library.core.data.recipe.ingredient.BlockIngredient;
import com.simibubi.create.foundation.item.TooltipHelper;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;

import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.config.DestroyAllConfigs;
import petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial;

/**
 * JEI category for {@link VatMaterial} · one display-only recipe per registered block-material
 * entry. Shows the block stack + a 5-segment progress bar for max pressure & thermal conductivity
 * + a tick/cross for transparency. Nerd-mode overlays raw numbers under each bar.
*/
public class VatMaterialCategory extends PetrolparkRecipeCategory<VatMaterialCategory.VatMaterialRecipe> {

    public static final Palette DARK_GRAY = Palette.ofColors(ChatFormatting.DARK_GRAY, ChatFormatting.DARK_GRAY);

    private final Minecraft mc;

    public VatMaterialCategory(Info<VatMaterialRecipe> info, IJeiHelpers helpers) {
        super(info, helpers);
        mc = Minecraft.getInstance();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, VatMaterialRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 2, 2)
            .setBackground(getRenderedSlot(), -1, -1)
            .addItemStacks(recipe.blockIngredient.getDisplayedItemStacks());
    }

    @Override
    public List<Component> getTooltipStrings(VatMaterialRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        if (mouseY > textY && mouseY < textY + textSeparation * 5) {
            if (mouseY < textY + textSeparation * 2) {
                return TooltipHelper.cutStringTextComponent(DestroyLang.translate("tooltip.vat_material.pressure.description").string(), DestroyLang.WHITE_AND_WHITE);
            } else if (mouseY < textY + textSeparation * 4) {
                return TooltipHelper.cutStringTextComponent(DestroyLang.translate("tooltip.vat_material.conductivity.description").string(), DestroyLang.WHITE_AND_WHITE);
            } else {
                return TooltipHelper.cutStringTextComponent(DestroyLang.translate("tooltip.vat_material.transparent.description").string(), DestroyLang.WHITE_AND_WHITE);
            }
        }
        return Collections.emptyList();
    }

    private final int textX = 2;
    private final int textY = 28;
    private final int textSeparation = 11;

    @Override
    public void draw(VatMaterialRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        guiGraphics.drawString(mc.font,
            recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT).get(0).getDisplayedItemStack().get().getHoverName(),
            24, 7, 0xFFFFFF);
        PetrolparkGuiTexture.JEI_LINE.render(guiGraphics, 2, 22);
        VatMaterial material = recipe.material;
        guiGraphics.drawString(mc.font, DestroyLang.vatMaterialMaxPressure(material, Palette.GRAY_AND_WHITE), textX, textY, 0xFFFFFF);
        guiGraphics.drawString(mc.font, DestroyLang.vatMaterialConductivity(material, Palette.GRAY_AND_WHITE), textX, textY + textSeparation * 2, 0xFFFFFF);
        guiGraphics.drawString(mc.font, DestroyLang.vatMaterialTransparent(material, Palette.GRAY_AND_WHITE), textX, textY + textSeparation * 4, 0xFFFFFF);
        if (DestroyAllConfigs.CLIENT.chemistry.nerdMode.get()) {
            guiGraphics.drawString(mc.font,
                DestroyLang.translate("tooltip.vat_material.pressure.nerd_mode", material.maxPressure() / 1000f).style(ChatFormatting.WHITE).component(),
                textX, textY + textSeparation, 0xFFFFFF);
            guiGraphics.drawString(mc.font,
                DestroyLang.translate("tooltip.vat_material.conductivity.nerd_mode", material.thermalConductivity()).style(ChatFormatting.WHITE).component(),
                textX, textY + textSeparation * 3, 0xFFFFFF);
        }
    }

    /**
 * Build one JEI recipe per registered VatMaterial entry, filtering out empty displayed-item
 * entries and the VAT_CONTROLLER self-entry (which has the UNBREAKABLE sentinel material · not
 * a "real" vat-material recipe). Returns pre-wrapped {@link net.minecraft.world.item.crafting.RecipeHolder}s
 * so DestroyJEI can register directly (pattern shared with MixableExplosiveCategory).
*/
    public static List<net.minecraft.world.item.crafting.RecipeHolder<VatMaterialRecipe>> getAllRecipes() {
        return VatMaterial.BLOCK_MATERIALS.entrySet().stream()
            .filter(entry -> !entry.getKey().getDisplayedItemStacks().isEmpty()
                && !entry.getKey().getDisplayedItemStacks().stream().anyMatch(DestroyBlocks.VAT_CONTROLLER::isIn))
            .map(entry -> new VatMaterialRecipe(entry.getValue(), entry.getKey()))
            .map(r -> new net.minecraft.world.item.crafting.RecipeHolder<>(r.recipeId, r))
            .toList();
    }

    /**
 * Inner synthetic recipe. we cache it in {@code recipeId} for DestroyJEI's
 * wrapping at registration time (pattern shared with
 * {@link MixableExplosiveCategory.MixableExplosiveRecipe}).
*/
    public static class VatMaterialRecipe extends ShapelessRecipe {

        private static int id = 0;

        public final net.minecraft.resources.ResourceLocation recipeId;
        public final BlockIngredient<?> blockIngredient;
        public final VatMaterial material;

        public VatMaterialRecipe(VatMaterial material, BlockIngredient<?> blockIngredient) {
            super("", CraftingBookCategory.MISC, ItemStack.EMPTY, NonNullList.create());
            this.recipeId = Destroy.asResource("vat_material_" + id++);
            this.blockIngredient = blockIngredient;
            this.material = material;
        }
    }
}
