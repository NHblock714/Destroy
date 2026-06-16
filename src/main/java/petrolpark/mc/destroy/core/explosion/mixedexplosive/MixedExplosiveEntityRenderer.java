package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;

import petrolpark.mc.destroy.client.DestroyPartials;
import petrolpark.mc.destroy.core.explosion.PrimedBombEntityRenderer;

/**
 * EntityRenderer for {@link MixedExplosiveEntity} — draws the base {@link DestroyPartials#CUSTOM_EXPLOSIVE_MIX_BASE}
 * partial tinted with {@code entity.color} (dye) + overlay {@link DestroyPartials#CUSTOM_EXPLOSIVE_MIX_OVERLAY}
 * + white-blink every 5 ticks to signal fuse-burn (vanilla TNT flash).
*/
public class MixedExplosiveEntityRenderer extends PrimedBombEntityRenderer<MixedExplosiveEntity> {

    public MixedExplosiveEntityRenderer(Context context) {
        super(context);
    }

    @Override
    public void renderBlock(MixedExplosiveEntity entity, PoseStack ms, MultiBufferSource buffer, int light, int fuse) {
        BlockState state = entity.getBlockStateToRender();
        VertexConsumer vc = buffer.getBuffer(Sheets.cutoutBlockSheet());
        SuperByteBuffer base = CachedBuffers.partial(DestroyPartials.CUSTOM_EXPLOSIVE_MIX_BASE, state)
            .disableDiffuse()
            .light(light)
            .color(entity.color);

        // Removed asymmetric {@code .center()} on label.
        // <p>Symptom: once primed, the entity rendered as a white cube with a thin rectangular
        // extrusion sticking out one side.</p>
        // {@code .center()} was applied on {@code label} but NOT on {@code base}. {@code .center()} translates
        // SuperByteBuffer vertices by (-0.5, -0.5, -0.5) in block units (with no later
        // {@code .uncenter()} to cancel it out — unlike e.g. TreeTapRenderer which uses
        // {@code .center() ... .uncenter()} as a rotation-pivot pattern).</p>
        // <p>With {@code base} at vertex span (0,0,0)–(1,1,1) and {@code label} (centered) at
        // (-0.5,-0.5,-0.5)–(0.5,0.5,0.5), once the entity-renderer's PoseStack
        // {@code T(-0.5, 0, -0.5)} is applied:</p>
        // <ul>
        // <li>base ends up at (-0.5, 0, -0.5)–(0.5, 1, 0.5) — correct, vanilla-TNT-style</li>
        // <li>label ends up at (-1, -0.5, -1)–(0, 0.5, 0) — offset 0.5 SW + 0.5 down</li>
        // </ul>
        // <p>The non-overlap region of label (the half-cube that sticks out beyond base) renders
        // as the visible "extrusion". Most visible during the white-fuse-blink phase since
        // the WHITE_OVERLAY hides the texture details.</p>
        // The fix aligns the transforms by removing the asymmetric {@code .center()}.</p>
        SuperByteBuffer label = CachedBuffers.partial(DestroyPartials.CUSTOM_EXPLOSIVE_MIX_OVERLAY, state)
            .light(light);

        if (fuse / 5 % 2 == 0) {
            int overlay = OverlayTexture.pack(OverlayTexture.u(1f), OverlayTexture.WHITE_OVERLAY_V);
            base.overlay(overlay);
            label.overlay(overlay);
        }

        // renderTruncated uses constant light (entity has no
        // neighbor-light context like block does).
        if (entity.hasCustomName()) {
            final int constLight = light;
            MixedExplosiveBlockEntityRenderer.renderTruncated(ms, buffer, d -> constLight, entity.getCustomName().getString());
        }

        base.renderInto(ms, vc);
        label.renderInto(ms, vc);
    }
}
