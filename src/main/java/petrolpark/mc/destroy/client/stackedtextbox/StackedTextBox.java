package petrolpark.mc.destroy.client.stackedtextbox;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Strings;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.gui.RemovedGuiUtils;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Concrete {@link AbstractStackedTextBox} · displays a paragraph of text with a lifetime bar;
 * spawns child StackedTextBoxes when the user hovers definition words.
*/
public class StackedTextBox extends AbstractStackedTextBox {

    private static final int PERMANENCE_LIFETIME = 15;

    private FontHelper.Palette palette;
    private String plainText = "";

    private Area activationArea;
    private boolean isActivationAreaHovered;

    private List<Pair<Area, String>> possibleChildActivationAreas;

    private int lifetime;

    private final List<Component> lines;

    /** Defaults to 0 → no flip. {@link
 * petrolpark.mc.destroy.compat.jei.category.HoverableTextCategory} sets these to the actual
 * panel size so the flip stays inside the visible recipe layout.
*/
    private int layoutWidth = 0;
    private int layoutHeight = 0;

    public StackedTextBox(Minecraft minecraft, int x, int y, AbstractStackedTextBox parent) {
        super(x, y, parent, AbstractStackedTextBox.NOTHING);

        this.minecraft = minecraft;
        palette = FontHelper.Palette.GRAY_AND_WHITE;
        activationArea = new Area(x, y, width, height);
        isActivationAreaHovered = true;
        possibleChildActivationAreas = List.of();
        lifetime = 0;
        lines = new ArrayList<>();
    }

    public StackedTextBox withPalette(FontHelper.Palette palette) {
        this.palette = palette;
        return this;
    }

    public StackedTextBox withActivationArea(Area area) {
        activationArea = area;
        return this;
    }

    /**
 * Call before {@link #withText} so child boxes spawned from definition-words inherit the same.
*/
    public StackedTextBox withLayoutBounds(int layoutWidth, int layoutHeight) {
        this.layoutWidth = layoutWidth;
        this.layoutHeight = layoutHeight;
        return this;
    }

    public StackedTextBox withText(String text) {
        plainText = text;
        updateTextBoxSize(text);
        return this;
    }

    protected void updateTextBoxSize(String text) {
        LinesAndActivationAreas result = getTextAndActivationAreas(text, getX(), getY(), 200,
            minecraft.screen, minecraft.font, palette, true);

        lines.clear();
        lines.addAll(result.lines());
        possibleChildActivationAreas = result.areas();
        width = result.width();
        height = result.height();

        setX(result.startX());
        setY(result.startY());
    }

    @Override
    protected void beforeRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.beforeRender(guiGraphics, mouseX, mouseY, partialTicks);
        isActivationAreaHovered = activationArea.isIn(mouseX, mouseY);

        if (child == AbstractStackedTextBox.NOTHING && lifetime >= PERMANENCE_LIFETIME) {
            for (Pair<Area, String> pair : possibleChildActivationAreas) {
                Area area = pair.getFirst();
                if (area.isIn(mouseX, mouseY)) {
                    child = new StackedTextBox(minecraft, mouseX, mouseY, this)
                        .withActivationArea(area)
                        .withPalette(palette)
                        .withLayoutBounds(layoutWidth, layoutHeight)   // propagate
                        .withText(Component.translatable(pair.getSecond()).getString());
                }
            }
        }
    }

    /**
 * 1.21 override point: catnip's {@code doRender} replaces the now-final
 * {@code AbstractWidget.render}. Delegation chain: AbstractWidget.render(final) →
 * AbstractSimiWidget.renderWidget → doRender.
*/
    @Override
    public void doRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // manual beforeRender pump: bypasses AbstractSimiWidget.renderWidget which
        // normally invokes beforeRender (and afterRender). Without it isHovered /
        // isActivationAreaHovered never update.
        // afterRender (in finally) must also be called to balance beforeRender's pushPose.
        // {@link net.createmod.catnip.gui.widget.AbstractSimiWidget#beforeRender} does
        // {@code pose.pushPose()} expecting {@link
        // net.createmod.catnip.gui.widget.AbstractSimiWidget#afterRender} to {@code popPose}.
        // An unbalanced PoseStack push accumulates each frame. JEI's renderer expects a balanced
        // stack, so the leak desynced the matrix and shifted the entire JEI overlay (catalyst
        // sidebar, inventory grid, top-bar navigation) down-right by the cumulative push offset
        // (symptom: the whole JEI interface drifted toward the lower-right, the drift tracking the
        // hyperlink-word font position). The offset scaled with cursor Y because beforeRender's
        // downstream ElementWidget.beforeRender conditionally adds a fade-related translate that
        // depends on widget state. Each leaked push compounded that.
        // Also the previous early-return-on-!isActive path skipped popping → another leak.
        // Wrapping the whole body in try/finally ensures afterRender always fires.
        beforeRender(guiGraphics, mouseX, mouseY, partialTicks);
        super.doRender(guiGraphics, mouseX, mouseY, partialTicks);

        try {
            doRenderInternal(guiGraphics, mouseX, mouseY, partialTicks);
        } finally {
            afterRender(guiGraphics, mouseX, mouseY, partialTicks);
        }
    }

    private void doRenderInternal(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (!isActive()) return;

        PoseStack ms = guiGraphics.pose();
        ms.pushPose();
        ms.translate(0, 0, 300);

        Screen screen = minecraft.screen;
        if (screen != null) {
            // only X flip uses layout bounds (recipe panel width). Y flip / clamp uses
            // FULL screen height because JEI {@code category.getHeight()} returns a *single*
            // recipe's layout height (~103 px), not the whole JEI panel — so Y-clamping to
            // layoutHeight would falsely jam the tooltip up to the top of the current recipe
            // even when there's plenty of room above/below in the JEI overlay (multiple recipes
            // stacked vertically, or other free space).
            // (symptom: a trigger word near a recipe's bottom pushed the tooltip increasingly far
            // from the cursor as it moved down.) Root cause: passing
            // layoutHeight as the screenHeight to RemovedGuiUtils + a separate Y flip both fired
            // simultaneously on small layoutHeight, double-shifting the tooltip far up.
            // Approaches that failed:
            // hardcoded 88/60 thresholds — over-flipped narrow panels.
            // PoseStack matrix m30/m31 reading — m30 unreliable.
            // layout bounds for both X and Y — Y was over-clamping.
            // X flips within the recipe panel; Y uses screen height so the tooltip can extend
            // above/below the single-recipe area, like vanilla MC tooltips.
            int boxX = getX();
            int boxY = getY();
            // X flip: keep tooltip inside the recipe layout's horizontal bounds. RemovedGuiUtils
            // renders bg starting at (mouseX_param + 12 - 3). With mouseX_param = boxX - 22,
            // bg right edge = boxX + width - 7.
            if (layoutWidth > 0 && boxX + width - 7 > layoutWidth) {
                boxX -= width;
            }
            // X clamp: don't go off panel left edge after flip
            if (boxX < 0) boxX = 0;

            ms.translate(0, 0, 10);
            List<Component> allLines = new ArrayList<>(lines);
            allLines.add(progressBar());
            // Pass layoutWidth as screenWidth (so internal X flip respects recipe panel right),
            // but pass screen.height (NOT layoutHeight) so internal Y clamp uses the real
            // game screen, letting the tooltip extend above/below the single-recipe area.
            int boundsW = layoutWidth > 0 ? layoutWidth : screen.width;
            RemovedGuiUtils.drawHoveringText(guiGraphics, allLines, boxX - 22, boxY + 5,
                boundsW, screen.height, -1, minecraft.font);
        }

        ms.pushPose();
        ms.translate(0, 0, 1);
        // Child is also a StackedTextBox (or NOTHING · which has doRender = no-op via AbstractSimiWidget default).
        if (child instanceof StackedTextBox childBox) {
            childBox.doRender(guiGraphics, mouseX, mouseY, partialTicks);
        }
        ms.popPose();

        ms.popPose();
    }

    @Override
    public void tick() {
        super.tick();
        if (lifetime < PERMANENCE_LIFETIME) {
            lifetime++;
        }
        if (!isActive()) close();
        child.tick();
    }

    @Override
    public boolean isActive() {
        if (!active || !visible) return false;
        if (child.isActive()) return true;
        if (lifetime < PERMANENCE_LIFETIME) {
            return isActivationAreaHovered;
        }
        return isHovered || isActivationAreaHovered;
    }

    @Override
    public void close() {
        lifetime = 0;
        child.close();
        if (parent != AbstractStackedTextBox.NOTHING) parent.child = AbstractStackedTextBox.NOTHING;
    }

    private Component progressBar() {
        float charWidth = minecraft.font.width("|");

        int total = (int) ((width - 5) / charWidth);
        int current = (int) ((float) lifetime * total / (float) PERMANENCE_LIFETIME);

        String bars = "";
        bars += ChatFormatting.GRAY + Strings.repeat("|", current);
        bars += ChatFormatting.DARK_GRAY + Strings.repeat("|", total - current);
        return Component.literal(bars);
    }
}
