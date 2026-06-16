package petrolpark.mc.destroy.content.oil.seismology;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import petrolpark.mc.destroy.client.DestroyGuiTextures;
import petrolpark.mc.destroy.content.oil.seismology.SeismographItem.Seismograph;
import petrolpark.mc.destroy.content.oil.seismology.SeismographItem.Seismograph.Mark;

/**
 * Custom one-handed renderer for {@link SeismographItem}. When the player holds a Seismograph in
 * first-person view, replace the default MapItem rendering with a nonogram-overlayed view: the
 * vanilla map tile (via {@link MapItem#getSavedData}) forms the background, with Seismograph
 * nonogram rows/columns + player Marks rendered on top using {@link DestroyGuiTextures}
 * atlas-sourced quads. Third-person / other contexts fall back to the wrapped vanilla item model.
*/
public class SeismographItemRenderer extends CustomRenderedItemModelRenderer {

    public static final RenderType BACKGROUND = DestroyGuiTextures.SEISMOGRAPH_BACKGROUND.asTextRenderType();
    public static final RenderType OVERLAY = DestroyGuiTextures.SEISMOGRAPH_OVERLAY.asTextRenderType();
    public static final RenderType TICK = DestroyGuiTextures.SEISMOGRAPH_TICK.asTextRenderType();
    public static final RenderType CROSS = DestroyGuiTextures.SEISMOGRAPH_CROSS.asTextRenderType();
    public static final RenderType GUESSED_TICK = DestroyGuiTextures.SEISMOGRAPH_GUESSED_TICK.asTextRenderType();
    public static final RenderType GUESSED_CROSS = DestroyGuiTextures.SEISMOGRAPH_GUESSED_CROSS.asTextRenderType();

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer,
                          int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ItemRenderer itemRenderer = mc.getItemRenderer();
        ItemInHandRenderer handItemRenderer = mc.getEntityRenderDispatcher().getItemInHandRenderer();
        float partialTicks = AnimationTickHolder.getPartialTicks();

        if (transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            // TODO(SeismographScreen port): restore `if (mc.screen instanceof SeismographScreen) return;`
            // guard once SeismographScreen is ported. Currently no screen exists, so in-hand render is always active.

            if (player == null) return; // guard for offline/preview contexts

            // Logic replicated from vanilla ItemInHandRenderer
            InteractionHand swingingHand = player.swingingArm;
            HumanoidArm arm = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            if (swingingHand == null) swingingHand = InteractionHand.MAIN_HAND;
            float equippedProgress = 1f - (hand == InteractionHand.MAIN_HAND
                ? Mth.lerp(partialTicks, handItemRenderer.oMainHandHeight, handItemRenderer.mainHandHeight)
                : Mth.lerp(partialTicks, handItemRenderer.oOffHandHeight, handItemRenderer.offHandHeight));
            float swingProgress = swingingHand == hand ? player.getAttackAnim(partialTicks) : 0f;

            // Undo the Item Stack model transforms (Create's CustomRenderedItemModelRenderer
            // does 3 pushPose translates before our render method; we pop those to draw in map-origin space)
            ms.popPose();
            ms.popPose();
            ms.popPose();
            renderOneHandedSeismograph(ms, buffer, light, equippedProgress, arm, swingProgress, stack, mc, handItemRenderer);
            ms.pushPose();
            ms.pushPose();
            ms.pushPose();
        } else {
            itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay, model.getOriginalModel());
        }
    }

    /**
 * Largely copied from {@link net.minecraft.client.renderer.ItemInHandRenderer}'s
 * {@code renderOneHandedMap} + Seismograph-specific nonogram overlay on top of the base map tile.
*/
    public static void renderOneHandedSeismograph(PoseStack ms, MultiBufferSource buffer, int light,
                                                  float equippedProgress, HumanoidArm hand, float swingProgress,
                                                  ItemStack stack, Minecraft mc, ItemInHandRenderer itemRenderer) {
        // Hand rotation
        ms.pushPose();
        float handRotation = hand == HumanoidArm.RIGHT ? 1f : -1f;
        ms.translate(handRotation * 0.125f, -0.125f, 0f);
        if (mc.player != null && !mc.player.isInvisible()) {
            ms.pushPose();
            // vanilla renderOneHandedMap uses `f * 10F` (handRotation * 10°), not a fixed
            // 10°. With a fixed 10°, the off-hand (handRotation = -1) rotates the arm in the
            // wrong direction, visually pushing the held map toward the bottom-right corner instead
            // of the bottom-left where the off-hand should sit. The main hand looks correct either
            // way because handRotation = +1 makes the multiplication a no-op. This matches vanilla
            // 1.21 ItemInHandRenderer exactly.
            ms.mulPose(Axis.ZP.rotationDegrees(handRotation * 10f));
            itemRenderer.renderPlayerArm(ms, buffer, light, equippedProgress, swingProgress, hand);
            ms.popPose();
        }

        // Vanilla transformation for held map
        ms.translate(handRotation * 0.51f, -0.08f + equippedProgress * -1.2f, -0.75f);
        float sqrtSwing = Mth.sqrt(swingProgress);
        float swingAngle = Mth.sin(sqrtSwing * (float) Math.PI);
        ms.translate(handRotation * -0.5f * swingAngle,
            0.4f * Mth.sin(sqrtSwing * ((float) Math.PI * 2f)) - 0.3f * swingAngle,
            -0.3f * Mth.sin(swingProgress * (float) Math.PI));
        ms.mulPose(Axis.XP.rotationDegrees(swingAngle * -45f));
        // same handRotation-multiplier on the swing Y rotation. Vanilla does
        // `f * f1 * f2 * -30F`; the leading `f` (handRotation) is required. Without it the off-hand
        // swing animation rotates the wrong direction. Mostly invisible at rest (swingProgress = 0
        // zeros the term out), but a real off-by-mirror during attack swings.
        ms.mulPose(Axis.YP.rotationDegrees(handRotation * sqrtSwing * swingAngle * -30f));
        ms.mulPose(Axis.YP.rotationDegrees(180f));
        ms.mulPose(Axis.ZP.rotationDegrees(180f));
        ms.scale(0.38f, 0.38f, 0.38f);
        ms.translate(-0.5f, -0.5f, 0f);

        // Scale to the size of the vanilla map
        ms.scale(1 / 128f, 1 / 128f, 1 / 128f);
        ms.translate(-7f, -7f, 0f);
        ms.scale(142 / 64f, 142 / 64f, 1f);

        // Relevant data — 1.21 MapId DataComponent pattern
        MapId mapId = stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapData = MapItem.getSavedData(stack, mc.level);
        Seismograph seismograph = SeismographItem.readSeismograph(stack);

        // Render as normal (invertZ=true for one-handed view)
        renderSeismograph(ms, buffer, light, mapId, mapData, seismograph, mc, (t, x, y) -> t.render(ms, x, y), true);

        ms.popPose();
    }

    public static final DestroyGuiTextures[] numberSymbols = new DestroyGuiTextures[] {
        DestroyGuiTextures.SEISMOGRAPH_1, DestroyGuiTextures.SEISMOGRAPH_1,
        DestroyGuiTextures.SEISMOGRAPH_2, DestroyGuiTextures.SEISMOGRAPH_3,
        DestroyGuiTextures.SEISMOGRAPH_4, DestroyGuiTextures.SEISMOGRAPH_5,
        DestroyGuiTextures.SEISMOGRAPH_6, DestroyGuiTextures.SEISMOGRAPH_7,
        DestroyGuiTextures.SEISMOGRAPH_8
    };

    /**
 * Functional callback: renders one DestroyGuiTextures entry at (x, y). The caller provides the
 * rendering context (PoseStack + MultiBufferSource etc. captured by closure) — the renderer
 * only needs to map (texture, x, y) → draw command.
*/
    @FunctionalInterface
    public interface SeismographGuiTextureRenderer {
        void render(DestroyGuiTextures texture, float x, float y);
    }

    /**
 * Core Seismograph nonogram rendering pipeline — background → marks → row numbers → column
 * numbers → underlying vanilla map tile → overlay border. Shared between the one-handed
 * held-map rendering (via renderOneHandedSeismograph) and the future SeismographScreen
 * (which would pass a {@link SeismographGuiTextureRenderer} closing over a {@link net.minecraft.client.gui.GuiGraphics}).
 *
 * @param mapId may be null (for newly-created Seismograph without a backing map yet)
 * @param mapData may be null (map world-data not loaded yet)
 * @param invertZ true for one-handed view (adjusts z-ordering signs); false for GUI flat view
*/
    public static void renderSeismograph(PoseStack ms, MultiBufferSource buffer, int light,
                                         MapId mapId, MapItemSavedData mapData,
                                         Seismograph seismograph, Minecraft mc,
                                         SeismographGuiTextureRenderer renderer, boolean invertZ) {
        ms.pushPose();

        float zm = invertZ ? -2f : 1f;

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        // Background
        renderer.render(DestroyGuiTextures.SEISMOGRAPH_BACKGROUND, 0f, 0f);

        // Marks (8×8 grid of player-placed TICK/CROSS/GUESSED_* at each chunk)
        ms.pushPose();
        ms.translate(13f, 13f, 0.02f * zm);
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                Mark mark = seismograph.getMark(x, z);
                if (mark != Mark.NONE) renderer.render(markToTexture(mark), x * 6f, z * 6f);
            }
        }
        ms.popPose();

        // Row numbers
        ms.pushPose();
        ms.translate(8f, 13f, 0.03f * zm);
        for (int z = 0; z < 8; z++) {
            ms.pushPose();
            ms.translate(0f, z * 6f, 0f);
            if (seismograph.isRowDiscovered(z)) {
                int[] numbers = seismograph.getRowDisplayed(z);
                for (int i = numbers.length - 1; i >= 0; i--) {
                    if (numbers[i] != 0) {
                        renderer.render(numberSymbols[numbers[i]], 0f, 0f);
                        ms.translate((numbers[i] <= 2) ? -2f : -3f, 0f, 0f);
                    }
                }
            } else {
                renderer.render(DestroyGuiTextures.SEISMOGRAPH_UNKNOWN, 0f, 0f);
            }
            ms.popPose();
        }
        ms.popPose();

        // Column numbers (rotated 90° so they read vertically)
        ms.pushPose();
        ms.translate(18f, 8f, 0.03f * zm);
        TransformStack.of(ms).rotateZDegrees(90);
        for (int x = 0; x < 8; x++) {
            ms.pushPose();
            ms.translate(0f, x * -6f, 0f);
            if (seismograph.isColumnDiscovered(x)) {
                int[] numbers = seismograph.getColumnDisplayed(x);
                for (int i = numbers.length - 1; i >= 0; i--) {
                    if (numbers[i] != 0) {
                        renderer.render(numberSymbols[numbers[i]], 0f, 0f);
                        ms.translate((numbers[i] <= 2) ? -2f : -3f, 0f, 0f);
                    }
                }
            } else {
                renderer.render(DestroyGuiTextures.SEISMOGRAPH_UNKNOWN, 0f, 0f);
            }
            ms.popPose();
        }
        ms.popPose();

        // Underlying vanilla map tile (scaled to fit the nonogram window) — 1.21 MapRenderer takes MapId
        ms.pushPose();
        ms.translate(13f, 13f, 0f);
        ms.scale(47 / 128f, 47 / 128f, 1f);
        ms.translate(0f, 0f, 0.01f * zm);
        if (mapId != null && mapData != null) mc.gameRenderer.getMapRenderer().render(ms, buffer, mapId, mapData, false, light);
        ms.popPose();

        // Overlay border on top
        ms.pushPose();
        ms.translate(0f, 0f, 0.04f * zm);
        renderer.render(DestroyGuiTextures.SEISMOGRAPH_OVERLAY, 0f, 0f);
        ms.popPose();
        ms.popPose();
    }

    /**
 * Map a {@link Mark} enum value to its corresponding {@link DestroyGuiTextures} icon. The
 * {@code Mark.icon} field was dropped to keep the Mark enum data-only, so this texture mapping
 * lives here in the renderer — the place that actually needs the textures. Mark.NONE → returned
 * value is irrelevant since callers guard with a NONE check.
*/
    private static DestroyGuiTextures markToTexture(Mark mark) {
        return switch (mark) {
            case TICK -> DestroyGuiTextures.SEISMOGRAPH_TICK;
            case CROSS -> DestroyGuiTextures.SEISMOGRAPH_CROSS;
            case GUESSED_TICK -> DestroyGuiTextures.SEISMOGRAPH_GUESSED_TICK;
            case GUESSED_CROSS -> DestroyGuiTextures.SEISMOGRAPH_GUESSED_CROSS;
            case NONE -> DestroyGuiTextures.SEISMOGRAPH_UNKNOWN; // never rendered (caller guards)
        };
    }
}
