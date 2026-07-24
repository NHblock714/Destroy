package petrolpark.mc.destroy.compat.jei.category;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import petrolpark.mc.library.compat.jei.category.ITickableCategory;
import petrolpark.mc.library.compat.jei.category.PetrolparkRecipeCategory;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;

import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.crafting.Recipe;

import petrolpark.mc.destroy.client.stackedtextbox.AbstractStackedTextBox;
import petrolpark.mc.destroy.client.stackedtextbox.AbstractStackedTextBox.Area;
import petrolpark.mc.destroy.client.stackedtextbox.AbstractStackedTextBox.LinesAndActivationAreas;
import petrolpark.mc.destroy.client.stackedtextbox.StackedTextBox;

/**
 * Abstract category base for categories that render hoverable text paragraphs (with definition
 * words that pop child text boxes on hover). Used by {@link ReactionCategory}.
*/
public abstract class HoverableTextCategory<T extends Recipe<?>> extends PetrolparkRecipeCategory<T> implements ITickableCategory {

    private static final Map<Recipe<?>, Collection<LinesAndActivationAreas>> PARAGRAPHS = new HashMap<>();

    protected AbstractStackedTextBox textBoxStack = AbstractStackedTextBox.NOTHING;
    protected static Recipe<?> textBoxActivatingRecipe = null;

    public HoverableTextCategory(Info<T> info, IJeiHelpers helpers) {
        super(info, helpers);
    }

    /**
 * Subclasses override to generate the hoverable paragraphs for a given recipe. Returns a
 * collection of {@link LinesAndActivationAreas} each with its rendered lines + definition-word
 * activation areas.
*/
    public abstract Collection<LinesAndActivationAreas> getHoverableTexts(T recipe);

    public Palette getPaletteForBoxes() {
        return Palette.GRAY_AND_WHITE;
    }

    @Override
    public void tick() {
        textBoxStack.tick();
        if (!textBoxStack.isActive()) {
            textBoxStack = AbstractStackedTextBox.NOTHING;
        }
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, T recipe, IFocusGroup focuses) {
        textBoxStack = AbstractStackedTextBox.NOTHING;
        PARAGRAPHS.put(recipe, getHoverableTexts(recipe));
    }

    @Override
    public void draw(T recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics graphics, double mouseX, double mouseY) {
        PoseStack stack = graphics.pose();

        Collection<LinesAndActivationAreas> paragraphs = PARAGRAPHS.get(recipe);
        if (paragraphs == null) return;

        float partialTicks = AnimationTickHolder.getPartialTicks();
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        // Render hoverable paragraphs
        for (LinesAndActivationAreas paragraph : paragraphs) {
            for (int i = 0; i < paragraph.lines().size(); i++) {
                graphics.drawString(font, paragraph.lines().get(i),
                    paragraph.startX(), paragraph.startY() + (i * font.lineHeight),
                    0xFFFFFF, false);
            }
        }

        // Spawn a stacked text box if the cursor is over a definition word's activation area
        if (!textBoxStack.isActive()) {
            checkParagraphs: for (LinesAndActivationAreas paragraph : paragraphs) {
                for (Pair<Area, String> pair : paragraph.areas()) {
                    if (pair.getFirst().isIn((int) mouseX, (int) mouseY)) {
                        // pass JEI category layout bounds so the text box's flip logic
                        // can clamp tooltips to the actual recipe panel right/bottom edges.
                        textBoxStack = new StackedTextBox(minecraft, (int) mouseX, (int) mouseY, AbstractStackedTextBox.NOTHING)
                            .withActivationArea(pair.getFirst())
                            .withPalette(getPaletteForBoxes())
                            .withLayoutBounds(getWidth(), getHeight())
                            .withText(pair.getSecond());
                        textBoxActivatingRecipe = recipe;
                        break checkParagraphs;
                    }
                }
            }
        }

        // Render the current text-box stack if it belongs to this recipe
        if (textBoxActivatingRecipe == recipe && textBoxStack instanceof StackedTextBox box) {
            stack.pushPose();
            stack.translate(10, 0, 0);
            box.doRender(graphics, (int) mouseX, (int) mouseY, partialTicks);
            stack.popPose();
        }
    }
}
