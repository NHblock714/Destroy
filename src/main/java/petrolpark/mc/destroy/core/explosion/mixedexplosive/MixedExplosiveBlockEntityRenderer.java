package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.data.Iterate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import petrolpark.mc.destroy.Destroy;

/**
 * BER for {@link MixedExplosiveBlockEntity} — draws the 4-direction "LABEL" text (custom name
 * truncated to ≤16px width, A-Z/0-9 subset) using a custom {@code destroy:explosive} bitmap font.
 *
 * <p>Wired to {@link petrolpark.mc.destroy.DestroyBlockEntityTypes#CUSTOM_EXPLOSIVE_MIX} via
 * Registrate {@code .renderer(() -> MixedExplosiveBlockEntityRenderer::new)}.</p>
*/
public class MixedExplosiveBlockEntityRenderer extends SafeBlockEntityRenderer<MixedExplosiveBlockEntity> {

    public static final ResourceLocation FONT_LOCATION = Destroy.asResource("explosive");
    public static final Style FONT = Style.EMPTY.withFont(FONT_LOCATION);
    public static final String ALLOWED_CHARACTERS = " 1234567890QWERTYUIOPASDFGHJKLZXCVBNM";

    public MixedExplosiveBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    protected void renderSafe(MixedExplosiveBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        Component nameComponent = be.getCustomName();
        if (nameComponent == null) return;
        renderTruncated(ms, bufferSource, d -> LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().offset(d.getNormal())), nameComponent.getString());
    }

    /**
 * Render a truncated uppercase label on all 4 horizontal faces of the block. Truncation rule:
 * keep only {@link #ALLOWED_CHARACTERS} (A-Z / 0-9 / space), stop when total width exceeds
 * 16px.
 *
 * @param ms pose stack (caller has translated to block origin)
 * @param buffer multibuffer source (preferably {@link BufferSource} for endBatch finalization)
 * @param lightGetter per-face light provider (block uses neighbor light; entity uses constant light)
 * @param string raw input text (pre-uppercase)
*/
    public static void renderTruncated(PoseStack ms, MultiBufferSource buffer, LightGetter lightGetter, String string) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        string = string.toUpperCase(mc.getLocale());

        String result = "";
        int width = 0;
        for (int i = 0; i < string.length(); i++) {
            String s = string.substring(i, i + 1);
            if (!ALLOWED_CHARACTERS.contains(s)) continue;
            int sWidth = font.width(FormattedText.of(s, FONT));
            if (width + sWidth > 16) continue;
            result += s;
            width += sWidth;
        }

        for (Direction face : Iterate.horizontalDirections) {
            ms.pushPose();
            TransformStack.of(ms)
                .center()
                .scale(-1 / 16f)
                .rotateToFace(face);
            ms.translate(-8d, -5d, 8.02d);
            font.drawInBatch(FormattedCharSequence.forward(result, Style.EMPTY.withFont(FONT_LOCATION)), (17 - width) / 2f, 0, 0xFFFFFF, false, ms.last().pose(), buffer, Font.DisplayMode.NORMAL, 0xFFFFFF, lightGetter.get(face));
            ms.popPose();
        }

        // renderType(Font.DisplayMode.NORMAL)) but getFontSet() is non-public in 1.21. Fall back to
        // no-arg endBatch() which flushes all pending vertex data — net-effect equivalent for text
        // rendering since the custom font's vertices are the only outstanding batch at this point
        // in the BER pipeline.
        if (buffer instanceof BufferSource bufferSource) {
            bufferSource.endBatch();
        }
    }

    @FunctionalInterface
    public static interface LightGetter {
        int get(Direction face);
    }
}
