package petrolpark.mc.destroy.content.processing.centrifuge;

import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;

import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.model.Models;

import petrolpark.mc.destroy.client.DestroyPartials;

/**
 * Flywheel visual for the Centrifuge — instance-renders the spinning inner cog as a
 * {@link SingleAxisRotatingVisual} (Create helper that wires up auto-rotation based on the BE's
 * kinetic speed). When Flywheel visualization is enabled, this replaces the cog render path of
 * {@link CentrifugeRenderer}'s {@code getRotatedModel} override.
 *
 * <p>Note: this class only handles the cog Visual. The full Centrifuge BE rendering (fluid
 * output-face indicators / spinning particles / chemistry mixture visuals) lives with the
 * block entity.</p>
*/
public class CentrifugeCogVisual extends SingleAxisRotatingVisual<CentrifugeBlockEntity> {

    public CentrifugeCogVisual(VisualizationContext ctx, CentrifugeBlockEntity blockEntity, float partialTick) {
        super(ctx, blockEntity, partialTick, Models.partial(DestroyPartials.CENTRIFUGE_COG));
    }
}
