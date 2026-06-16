package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.engine_room.flywheel.lib.transform.TransformStack;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import petrolpark.mc.destroy.client.DestroyGuiTextures;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosivePropertiesEntry;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosiveProperty;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosivePropertyCondition;
import petrolpark.mc.destroy.core.item.tooltip.DestroyTooltipComponent;

/**
 * GUI tooltip component for {@link ExplosiveProperties} — renders the 5-row property chart +
 * condition markers (thresholds met / unmet) when hovering a mixed-explosive item.
 *
 * <ul>
 * <li>{@link DestroyGuiTextures} CUSTOM_EXPLOSIVE_CHART / BAR / FULFILLED_LESS / FULFILLED_ZERO
 * / FULFILLED_GREATER / UNFULFILLED_LESS / UNFULFILLED_ZERO / UNFULFILLED_GREATER — all 8
 * texture entries.</li>
 * <li>{@link ExplosiveProperties} with inner types (ExplosiveProperty / ExplosivePropertyCondition
 * / ExplosivePropertiesEntry).</li>
 * <li>{@link TransformStack} — Flywheel {@code dev.engine_room.flywheel.lib.transform.TransformStack}
 * path preserved in 1.21.</li>
 * </ul>
 *
 * <p>Structural pieces:</p>
 * <ul>
 * <li>{@link Selectable} interface (1-method).</li>
 * <li>{@link #EMPTY} no-op Selectable constant.</li>
 * <li>Outer class inheritance {@code extends DestroyTooltipComponent<Self, ClientSelf>}.</li>
 * <li>{@link ClientExplosivePropertiesTooltip#getHeight getHeight} / {@code getWidth} — fixed 77 / 76.</li>
 * </ul>
 *
 * <p>Render components:</p>
 * <ul>
 * <li>{@link #renderProperties}(properties, font, graphics, mX, mY) — 5-row iteration with value
 * bars ({@link DestroyGuiTextures#CUSTOM_EXPLOSIVE_BAR}) + condition icon dispatch (zero /
 * negative / positive threshhold → fulfilled/unfulfilled variants) + symbol draw.</li>
 * <li>{@link #getSelected}(properties, mX, mY) — hover-position math → returns the
 * {@link Selectable} (ExplosiveProperty row or ExplosivePropertyCondition icon) the mouse is
 * over, else {@link #EMPTY}.</li>
 * <li>{@link ClientExplosivePropertiesTooltip#renderImage} — pose push/translate(x, y, 250f) +
 * renderProperties call with mX/mY = -1 (no hover selection in tooltip-only context) +
 * pose pop.</li>
 * </ul>
*/
public class ExplosivePropertiesTooltip extends DestroyTooltipComponent<ExplosivePropertiesTooltip, ExplosivePropertiesTooltip.ClientExplosivePropertiesTooltip> {

    private final ExplosiveProperties properties;

    public ExplosivePropertiesTooltip(ExplosiveProperties properties) {
        this.properties = properties;
    }

    @Override
    public ClientExplosivePropertiesTooltip getClientTooltipComponent() {
        return new ClientExplosivePropertiesTooltip();
    }

    public class ClientExplosivePropertiesTooltip implements ClientTooltipComponent {

        @Override
        public int getHeight() {
            return 77;
        }

        @Override
        public int getWidth(Font pFont) {
            return 76;
        }

        @Override
        public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
            // renderProperties with mX=-1/mY=-1 (tooltip context, no hover selection) → pose pop.
            PoseStack ms = guiGraphics.pose();
            ms.pushPose();
            ms.translate(x, y, 250f);
            renderProperties(properties, font, guiGraphics, -1d, -1d);
            ms.popPose();
        }
    }

    /**
 * Render the Explosive-Properties 5-row chart. Draws:
 * <ul>
 * <li>CUSTOM_EXPLOSIVE_CHART background texture at (0, 0).</li>
 * <li>For each of 5 {@link ExplosiveProperty} rows:
 * <ul>
 * <li>Value bars: {@code |value|} copies of CUSTOM_EXPLOSIVE_BAR aligned by sign (positive
 * right of center, negative left of center).</li>
 * <li>Condition icons: one of 6 variants (FULFILLED / UNFULFILLED × LESS / ZERO / GREATER)
 * depending on the condition's threshhold sign + whether the property fulfils.</li>
 * <li>Selected-icon highlight: 1.2× scale via TransformStack if the condition matches
 * {@code selected} (set by {@link #getSelected}).</li>
 * <li>Property symbol: drawn at (34, y+1) with white color 0xFFFFFF.</li>
 * </ul>
 * </li>
 * </ul>
 *
 * @param properties the explosive-properties map to render
 * @param font font to draw symbols with
 * @param graphics target gui graphics
 * @param mX mouse X relative to top-left of chart; pass -1 if no hover context
 * @param mY mouse Y relative to top-left of chart; pass -1 if no hover context
*/
    public static void renderProperties(ExplosiveProperties properties, Font font, GuiGraphics graphics, double mX, double mY) {
        PoseStack ms = graphics.pose();

        DestroyGuiTextures.CUSTOM_EXPLOSIVE_CHART.render(graphics, 0, 0);
        int y = 3;

        Selectable selected = getSelected(properties, mX, mY);
        for (Entry<ExplosiveProperty, ExplosivePropertiesEntry> entry : properties.entrySet()) {

            // Bars
            float value = entry.getValue().value;
            for (int i = 0; i < Math.abs(value); i++) {
                DestroyGuiTextures.CUSTOM_EXPLOSIVE_BAR.render(graphics, 37 + (8 + 3 * i) * (int)(Math.signum(value)), y + 3);
            }

            // Conditions
            for (ExplosivePropertyCondition condition : entry.getValue().conditions) {
                DestroyGuiTextures conditionIcon;
                float renderCenter;
                float renderOffset;
                boolean fulfilled = properties.fulfils(condition);
                if (condition.threshhold == 0f) {
                    conditionIcon = fulfilled ? DestroyGuiTextures.CUSTOM_EXPLOSIVE_FULFILLED_ZERO : DestroyGuiTextures.CUSTOM_EXPLOSIVE_UNFULFILLED_ZERO;
                    renderCenter = 38;
                    renderOffset = 6;
                } else if (condition.negative()) {
                    conditionIcon = fulfilled ? DestroyGuiTextures.CUSTOM_EXPLOSIVE_FULFILLED_LESS : DestroyGuiTextures.CUSTOM_EXPLOSIVE_UNFULFILLED_LESS;
                    renderCenter = 33 + condition.threshhold * 3f;
                    renderOffset = 4;
                } else {
                    conditionIcon = fulfilled ? DestroyGuiTextures.CUSTOM_EXPLOSIVE_FULFILLED_GREATER : DestroyGuiTextures.CUSTOM_EXPLOSIVE_UNFULFILLED_GREATER;
                    renderCenter = 43 + condition.threshhold * 3f;
                    renderOffset = 4;
                }
                ms.pushPose();
                ms.translate(renderCenter, y + 5, 0f);
                if (selected == condition) TransformStack.of(ms).scale(1.2f);
                ms.translate(-renderOffset, -6f, 0f);
                conditionIcon.render(graphics, 0, 0);
                ms.popPose();
            }

            // Symbols
            graphics.drawString(font, entry.getKey().getSymbol(), 34, y + 1, 0xFFFFFF);
            y += 15;
        }
    }

    /**
 * Hover-position dispatch. Returns:
 * <ul>
 * <li>{@link #EMPTY} if mX out-of-bounds [0, 76] or mY row index out of [0, 5).</li>
 * <li>The {@link ExplosivePropertyCondition} whose icon is under the cursor (x-range check
 * depends on threshhold sign: zero at [31, 43], negative at [25+3T, 33+3T], positive at
 * [39+3T, 47+3T]).</li>
 * <li>The {@link ExplosiveProperty} for the row if cursor is in the row but not on a condition
 * icon.</li>
 * </ul>
 *
 * @param properties the explosive-properties map with registered conditions
 * @param mX mouse X relative to chart top-left
 * @param mY mouse Y relative to chart top-left
 * @return the Selectable under the cursor, or {@link #EMPTY} if out of bounds
*/
    public static Selectable getSelected(ExplosiveProperties properties, double mX, double mY) {
        if (mX < 0d || mX > 76d) return EMPTY;
        int propertyIndex = (int)((mY - 3D) / 15d);
        if (propertyIndex < 0 || propertyIndex >= ExplosiveProperty.values().length) return EMPTY;
        ExplosiveProperty property = ExplosiveProperty.values()[propertyIndex];
        for (ExplosivePropertyCondition condition : properties.get(property).conditions) {
            double minX;
            double maxX;
            if (condition.threshhold == 0f) {
                minX = 31d;
                maxX = 43d;
            } else if (condition.threshhold < 0f) {
                minX = 25f + 3d * (condition.threshhold);
                maxX = 33f + 3d * (condition.threshhold);
            } else {
                minX = 39f + 3d * (condition.threshhold);
                maxX = 47f + 3d * (condition.threshhold);
            }
            if (mX > minX && mX < maxX) return condition;
        }
        return property;
    }

    /**
 * Marker interface for tooltip-rendering dispatch. Implemented by both
 * {@link ExplosiveProperties.ExplosiveProperty} (shows property name + description) and
 * {@link ExplosiveProperties.ExplosivePropertyCondition} (shows condition description + active
 * state). Used by {@link #getSelected} + downstream
 * {@code ExplosivePropertiesTooltip.Selectable} consumers.
*/
    public static interface Selectable {
        List<Component> getTooltip(ExplosiveProperties properties);
    }

    /**
 * Default no-op {@link Selectable} — used as fallback when hover doesn't match any property or
 * condition. Returns empty list (no tooltip text).
*/
    public static final Selectable EMPTY = (properties) -> Collections.emptyList();
}
