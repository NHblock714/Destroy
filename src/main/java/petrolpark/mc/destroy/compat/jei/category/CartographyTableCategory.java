package petrolpark.mc.destroy.compat.jei.category;

import java.util.List;

import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;
import com.simibubi.create.foundation.gui.AllGuiTextures;
import com.simibubi.create.foundation.utility.CreateLang;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.saveddata.maps.MapId;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.client.DestroyLang;

/**
 * JEI category for CARTOGRAPHY_TABLE virtual recipes · shows vanilla (+ Destroy seismograph)
 * cartography-table operations as crafting-table-style 2-in-1-out layouts.
*/
public class CartographyTableCategory extends PetrolparkRecipeCategory<CartographyTableCategory.CartographyTableRecipe> {

    private final Minecraft mc;

    private static final ResourceLocation seismographRecipeId = Destroy.asResource("seismograph");
    private static final ResourceLocation scalingRecipeId = ResourceLocation.withDefaultNamespace("scaling");
    private static final ResourceLocation lockingRecipeId = ResourceLocation.withDefaultNamespace("locking");

    public static List<RecipeHolder<CartographyTableRecipe>> getAllRecipes() {
        ItemStack map = new ItemStack(Items.FILLED_MAP);
        map.set(DataComponents.MAP_ID, new MapId(52));
        ItemStack scaledMap = map.copy();
        scaledMap.set(DataComponents.MAP_ID, new MapId(53));
        return List.of(
            wrap(new CartographyTableRecipe(seismographRecipeId, DestroyItems.SEISMOGRAPH.asStack(), map, DestroyItems.SEISMOMETER.get())),
            wrap(new CartographyTableRecipe(ResourceLocation.withDefaultNamespace("duplicating"), map.copyWithCount(2), map, Items.MAP)),
            wrap(new CartographyTableRecipe(scalingRecipeId, scaledMap, map, Items.PAPER)),
            wrap(new CartographyTableRecipe(lockingRecipeId, map, map, Items.GLASS_PANE))
        );
    }

    private static RecipeHolder<CartographyTableRecipe> wrap(CartographyTableRecipe recipe) {
        return new RecipeHolder<>(recipe.getRecipeId(), recipe);
    }

    public CartographyTableCategory(Info<CartographyTableRecipe> info, IJeiHelpers helpers) {
        super(info, helpers);
        mc = Minecraft.getInstance();
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CartographyTableRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 2, 2)
            .setBackground(getRenderedSlot(), -1, -1)
            .addIngredients(recipe.getIngredients().get(0));

        builder.addSlot(RecipeIngredientRole.INPUT, 21, 2)
            .setBackground(getRenderedSlot(), -1, -1)
            .addIngredients(recipe.getIngredients().get(1))
            .addRichTooltipCallback((view, tt) -> {
                if (recipe.getRecipeId().equals(seismographRecipeId)) {
                    tt.add(CreateLang.translateDirect("recipe.deploying.not_consumed").withStyle(ChatFormatting.GOLD));
                }
            });

        builder.addSlot(RecipeIngredientRole.OUTPUT, 107, 2)
            .setBackground(getRenderedSlot(), -1, -1)
            .addItemStack(recipe.getResultItem(mc.level.registryAccess()))
            .addRichTooltipCallback((view, tt) -> {
                if (recipe.getRecipeId().equals(scalingRecipeId)) {
                    tt.add(DestroyLang.translate("recipe.cartography_table.scaled").style(ChatFormatting.GOLD).component());
                } else if (recipe.getRecipeId().equals(lockingRecipeId)) {
                    tt.add(Component.translatable("filled_map.locked", 52).withStyle(ChatFormatting.GRAY));
                }
            });
    }

    @Override
    public void draw(CartographyTableRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        AllGuiTextures.JEI_LONG_ARROW.render(guiGraphics, 36, 6);
    }

    /**
 * Synthetic shapeless recipe for JEI display only. Stores {@link #recipeId} separately since
 * 1.21 ShapelessRecipe no longer carries an id field.
*/
    public static class CartographyTableRecipe extends ShapelessRecipe {

        private final ResourceLocation recipeId;

        public CartographyTableRecipe(ResourceLocation id, ItemStack result, ItemStack mapIngredient, ItemLike nonMapIngredient) {
            super("", CraftingBookCategory.MISC, result,
                NonNullList.of(Ingredient.EMPTY, Ingredient.of(mapIngredient), Ingredient.of(nonMapIngredient)));
            this.recipeId = id;
        }

        public ResourceLocation getRecipeId() {
            return recipeId;
        }
    }
}
