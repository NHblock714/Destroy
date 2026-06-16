package petrolpark.mc.destroy.content.processing.glassblowing;

import java.util.List;
import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import petrolpark.mc.destroy.client.DestroyFluidRenderer;
import petrolpark.mc.destroy.client.DestroyGuiTextures;
import petrolpark.mc.destroy.client.DestroyLang;

/**
 * Blowpipe recipe-selector GUI — scrollable 3-row list of {@link GlassblowingRecipe}s showing
 * each recipe's input fluid + output item. Clicking a row fires a
 * {@link SelectGlassblowingRecipeC2SPacket} then closes the screen. Opened via
 * {@link BlowpipeItem#openScreen(InteractionHand)}.
*/
public class BlowpipeScreen extends AbstractSimiScreen {

    private static final Object recipeCacheKey = new Object();

    protected DestroyGuiTextures background;

    protected final InteractionHand hand;
    protected List<RecipeHolder<GlassblowingRecipe>> recipes;

    protected int scroll;

    public BlowpipeScreen(InteractionHand hand) {
        super(DestroyLang.translate("tooltip.blowpipe.select_recipe").component());
        this.hand = hand;
        background = DestroyGuiTextures.BLOWPIPE_BACKGROUND;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void init() {
        setWindowSize(background.width, background.height);
        super.init();
        // keep wrapped for rh.id() access.
        recipes = RecipeFinder.get(recipeCacheKey, minecraft.level,
                rh -> rh.value() instanceof GlassblowingRecipe)
            .stream()
            .map(rh -> (RecipeHolder<GlassblowingRecipe>) rh)
            .toList();
        refreshRecipeButtons();
    }

    public void refreshRecipeButtons() {
        clearWidgets();
        for (int i = 0; i < 3; i++) {
            if (scroll + i >= recipes.size()) return;
            addRenderableWidget(new RecipeButton(scroll + i, guiLeft + 8, 15 + guiTop + (i * 18)));
        }
    }

    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            if (recipes.size() <= 3) return false;
            scroll = Mth.clamp((int) (scroll - scrollY), 0, recipes.size() - 3);
            refreshRecipeButtons();
            return true;
        }
        return true;
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        background.render(graphics, guiLeft, guiTop);
        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(guiLeft, guiTop, 100);
        mouseX -= guiLeft;
        mouseY -= guiTop;
        graphics.drawString(font, title, 6, 5, 0x8B8B8B, false);

        // Recipes
        for (int i = 0; i < 3; i++) {
            if (scroll + i >= recipes.size()) continue;
            GlassblowingRecipe recipe = recipes.get(scroll + i).value();
            int y = 16 + i * 18;

            // Item
            ItemStack stack = recipe.getResultItem(minecraft.level.registryAccess());
            graphics.renderItem(stack, 56, y, i);

            SizedFluidIngredient ingredient = recipe.getFluidIngredients().get(0);
            FluidStack[] ingredients = ingredient.getFluids();
            FluidStack fluidStack = ingredients[(AnimationTickHolder.getTicks() / 20) % ingredients.length];
            DestroyFluidRenderer.renderFluidSquare(graphics, 9, y, fluidStack);

            // Tooltips
            if (mouseY >= y && mouseY <= y + 16) {
                if (mouseX >= 9 && mouseX <= 27) {
                    graphics.renderTooltip(font, List.of(
                        fluidStack.getHoverName(),
                        DestroyLang.builder()
                            .add(Component.literal("" + ingredient.amount()))
                            .add(CreateLang.translate("generic.unit.millibuckets").component())
                            .style(ChatFormatting.GRAY)
                            .component()
                    ), Optional.empty(), mouseX, mouseY);
                }
                if (mouseX >= 56 && mouseX <= 72) {
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }

        // Scroll bar
        if (recipes.size() <= 3) {
            DestroyGuiTextures.BLOWPIPE_SCROLL_LOCKED.render(graphics, 75, 15);
        } else {
            DestroyGuiTextures.BLOWPIPE_SCROLL.render(graphics, 75,
                15 + (int) (39f * (float) scroll / (float) (recipes.size() - 3f)));
        }

        ms.popPose();
    }

    public class RecipeButton extends AbstractSimiWidget {

        protected RecipeButton(int recipeNo, int x, int y) {
            super(x, y, 64, 18);
            withCallback(() -> {
                if (recipeNo >= recipes.size()) return;
                CatnipServices.NETWORK.sendToServer(
                    new SelectGlassblowingRecipeC2SPacket(hand, recipes.get(recipeNo).id()));
                if (minecraft != null && minecraft.player != null) minecraft.player.closeContainer();
            });
        }

        @Override
        protected void doRender(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            (isMouseOver(mouseX, mouseY) ? DestroyGuiTextures.BLOWPIPE_RECIPE_SELECTED : DestroyGuiTextures.BLOWPIPE_RECIPE)
                .render(graphics, getX(), getY());
        }
    }
}
