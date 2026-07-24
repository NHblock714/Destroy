package petrolpark.mc.destroy.compat.jei.category;

import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import com.mojang.blaze3d.vertex.PoseStack;
import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.runtime.IJeiRuntime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.client.DestroyGuiTextures;
import petrolpark.mc.destroy.compat.jei.DestroyJEI;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosivePropertiesTooltip;

/**
 * JEI category for mixable-explosive items (T3b). Displays each item with registered
 * {@link ExplosiveProperties} as a JEI entry showing its property chart via
 * {@link ExplosivePropertiesTooltip}. Used by the MixedExplosiveScreen's "view JEI" button
 * to open this category directly filtered by a clicked mix item.
*/
public class MixableExplosiveCategory extends PetrolparkRecipeCategory<MixableExplosiveCategory.MixableExplosiveRecipe> {

    public static RecipeType<RecipeHolder<MixableExplosiveRecipe>> TYPE;

    public MixableExplosiveCategory(Info<MixableExplosiveRecipe> info, IJeiHelpers helpers) {
        super(info, helpers);
        TYPE = info.recipeType();
    }

    public static void openCategoryView() {
        DestroyJEI.jeiRuntime.ifPresent(runtime ->
            runtime.getRecipesGui().showTypes(Collections.singletonList(TYPE)));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MixableExplosiveRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 2, 2)
            .setBackground(getRenderedSlot(), -1, -1)
            .addItemStack(new ItemStack(recipe.item));
    }

    @Override
    public List<Component> getTooltipStrings(MixableExplosiveRecipe recipe, IRecipeSlotsView recipeSlotsView, double mouseX, double mouseY) {
        return ExplosivePropertiesTooltip.getSelected(recipe.properties, mouseX - 51, mouseY - 33).getTooltip(recipe.properties);
    }

    @Override
    public void draw(MixableExplosiveRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();

        guiGraphics.drawString(mc.font,
            recipeSlotsView.getSlotViews(RecipeIngredientRole.INPUT).get(0).getDisplayedItemStack().get().getHoverName(),
            24, 7, 0xFFFFFF);

        PoseStack ms = guiGraphics.pose();

        DestroyGuiTextures.CUSTOM_EXPLOSIVE_JEI_BACKGROUND.render(guiGraphics, 45, 24);
        ms.pushPose();
        ms.translate(51f, 33f, 0f);
        ExplosivePropertiesTooltip.renderProperties(recipe.properties, mc.font, guiGraphics, mouseX - 51d, mouseY - 33d);
        ms.popPose();
    }

    public static List<RecipeHolder<MixableExplosiveRecipe>> getAllRecipes() {
        return ExplosiveProperties.ITEM_EXPLOSIVE_PROPERTIES.entrySet().stream()
            .map(MixableExplosiveRecipe::new)
            .map(r -> new RecipeHolder<>(r.getRecipeId(), r))
            .toList();
    }

    /**
 * Synthetic shapeless recipe for JEI display only.
*/
    public static class MixableExplosiveRecipe extends ShapelessRecipe {

        private static int id = 0;

        private final ResourceLocation recipeId;
        public final Item item;
        public final ExplosiveProperties properties;

        public MixableExplosiveRecipe(Entry<Item, ExplosiveProperties> e) {
            this(e.getKey(), e.getValue());
        }

        public MixableExplosiveRecipe(Item item, ExplosiveProperties properties) {
            super("", CraftingBookCategory.MISC, ItemStack.EMPTY, NonNullList.create());
            this.recipeId = Destroy.asResource("explosive_properties_" + id++);
            this.item = item;
            this.properties = properties;
        }

        public ResourceLocation getRecipeId() {
            return recipeId;
        }
    }
}
