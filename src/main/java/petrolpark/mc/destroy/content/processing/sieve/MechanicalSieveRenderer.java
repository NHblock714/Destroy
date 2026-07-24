package petrolpark.mc.destroy.content.processing.sieve;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import petrolpark.mc.destroy.client.DestroyPartials;

/**
 * Mechanical Sieve BER — renders the 3-part mechanical sieve (shaft, sieve pan, linkages). Pan
 * and linkages animate in sync with the shaft rotation via sine driven by kinetic speed.
*/
public class MechanicalSieveRenderer extends KineticBlockEntityRenderer<MechanicalSieveBlockEntity> {

    public MechanicalSieveRenderer(Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(MechanicalSieveBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(be, partialTicks, ms, buffer, light, overlay);

        BlockState state = be.getBlockState();
        boolean x = state.getValue(MechanicalSieveBlock.X);
        // Grab the buffer just before each renderInto. RenderType.solid() and cutout() aren't among
        // the BufferSource's fixed buffers, so they share one ByteBufferBuilder and only one can be
        // building at a time — asking for the second ends the first, leaving any VertexConsumer held
        // for it stale, and writing to a stale one throws "Not building!".
        float angle = getAngleForBe(be, be.getBlockPos(), x ? Axis.X : Axis.Z);

        ms.pushPose();
        if (x) TransformStack.of(ms).rotateYDegrees(90);
        ms.pushPose();

        ms.translate(Mth.sin(angle) * 2 / 16d + (x ? -1d : 0d), 0d, 0d);
        CachedBuffers.partial(DestroyPartials.MECHANICAL_SIEVE, state)
            .light(light)
            .renderInto(ms, buffer.getBuffer(RenderType.cutout()));

        ms.pushPose();
        TransformStack.of(ms)
            .center()
            .rotateZ(-angle)
            .uncenter();
        CachedBuffers.partial(DestroyPartials.MECHANICAL_SIEVE_LINKAGES, state)
            .light(light)
            .renderInto(ms, buffer.getBuffer(RenderType.solid()));
        ms.popPose();

        ms.popPose();
        ms.popPose();
    }

    @Override
    protected SuperByteBuffer getRotatedModel(MechanicalSieveBlockEntity be, BlockState state) {
        return CachedBuffers.partialFacingVertical(DestroyPartials.MECHANICAL_SIEVE_SHAFT, state,
            state.getValue(MechanicalSieveBlock.X) ? Direction.WEST : Direction.NORTH);
    }
}
