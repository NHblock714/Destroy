package petrolpark.mc.destroy.content.oil.pumpjack;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

import petrolpark.mc.destroy.client.DestroyPartials;

/**
 * Block-Entity Renderer for the Pumpjack — draws 4 animated parts (cam + linkage + beam + pump)
 * synchronized to the cam's rotation angle. The cam rotates continuously; the linkage translates
 * vertically while wobbling with a phase-offset sine; the beam rocks around its pivot point; the
 * pump rises/falls with the beam's rear end.
*/
public class PumpjackRenderer extends SafeBlockEntityRenderer<PumpjackBlockEntity> {

    public PumpjackRenderer(BlockEntityRendererProvider.Context context) {}

    private static final double beamRotation = Math.asin(5 / 17d);

    @Override
    protected void renderSafe(PumpjackBlockEntity pumpjack, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        float angle = pumpjack.getRenderAngle();

        BlockState blockState = pumpjack.getBlockState();
        Direction facing = PumpjackBlock.getFacing(blockState);
        // Grab the buffer just before each renderInto. RenderType.solid() and cutout() aren't among
        // the BufferSource's fixed buffers, so they share one ByteBufferBuilder and only one can be
        // building at a time — asking for the second ends the first, leaving any VertexConsumer held
        // for it stale, and writing to a stale one throws "Not building!".

        transformed(DestroyPartials.PUMPJACK_CAM, blockState, facing)
            .translate(0d, 0d, 1d)
            .center()
            .rotateX(angle - Mth.HALF_PI)
            .center()
            .translate(0d, 0d, -1d)
            .uncenter()
            .uncenter()
            .light(light)
            .renderInto(ms, bufferSource.getBuffer(RenderType.solid()));

        transformed(DestroyPartials.PUMPJACK_LINKAGE, blockState, facing)
            .translate(0d, -4.5 / 16d, 1d)
            .translate(0d, Mth.sin(angle) * 5 / 16d, -Mth.cos(angle) * 5 / 16d)
            .center()
            .rotateX((float) ((Mth.cos(angle)) * beamRotation * 0.73d))
            .center()
            .translate(0d, 0d, -1d)
            .uncenter()
            .uncenter()
            .light(light)
            .renderInto(ms, bufferSource.getBuffer(RenderType.solid()));

        transformed(DestroyPartials.PUMPJACK_BEAM, blockState, facing)
            .translate(0d, 1d, 0d)
            .center()
            .rotateX((float) ((Mth.sin(angle)) * -beamRotation))
            .center()
            .translate(0d, -1d, 0d)
            .uncenter()
            .uncenter()
            .light(light)
            .renderInto(ms, bufferSource.getBuffer(RenderType.cutout()));

        transformed(DestroyPartials.PUMPJACK_PUMP, blockState, facing)
            .translate(0d, -Mth.sin(angle) * 3 / 16d, 0d)
            .light(light)
            .renderInto(ms, bufferSource.getBuffer(RenderType.solid()));
    }

    private SuperByteBuffer transformed(PartialModel model, BlockState blockState, Direction facing) {
        return CachedBuffers.partial(model, blockState)
            .center()
            .rotateYDegrees(AngleHelper.horizontalAngle(facing))
            .rotateXDegrees(AngleHelper.verticalAngle(facing))
            .uncenter();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
