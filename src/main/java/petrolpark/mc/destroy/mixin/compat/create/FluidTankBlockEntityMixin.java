package petrolpark.mc.destroy.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.simibubi.create.content.fluids.FluidPropagator;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.core.fluid.GeniusFluidTankBehaviour.GeniusFluidTank;

/**
 *
 * <p>Stock {@link SmartFluidTank#fill} compares the incoming {@link FluidStack}'s components
 * via vanilla equality — two MIXTURE stacks with non-empty but different {@code Mixture}
 * components return {@code !canFillFluidType()} → fill returns 0. Mid-transfer expansion of a tank from 1 to 2 cells leaves the
 * adjacent pump stuck — the only recovery is breaking + re-placing the pump (which
 * triggers {@link FluidPropagator#propagateChangedPipe} via the block-state change).</p>
 *
 * <p><b>Root cause</b>: Create's {@link FluidTankBlockEntity#notifyMultiUpdated} (called
 * per-cell after every multi-block formation/split) does NOT propagate the topology change to
 * neighbouring fluid networks.</p>
 *
 * <ul>
 * <li>For the cell that was already controller and just grew (1→2 case), no
 * {@code setController} call fires → {@code refreshCapability} not called →
 * {@code invalidateCapabilities} skipped. Adjacent pump's {@code BlockCapabilityCache}
 * sees no signal.</li>
 * <li>For new cells joining the multi, {@code setController} → {@code refreshCapability}
 * → {@code invalidateCapabilities} fires correctly — but the cap invalidation alone
 * doesn't reset the pump's {@link com.simibubi.create.content.fluids.FluidNetwork} state
 * (frontier, visited set, source/target endpoints). Pump's network can stay in a stale
 * configuration despite the cache refresh.</li>
 * </ul>
 *
 * <p>{@link FluidPropagator#propagateChangedPipe} is exactly the mechanism that handles
 * neighbouring-block-state-change events for pumps: BFS through connected pipes, wipe pressure,
 * collect adjacent pumps, call {@link com.simibubi.create.content.fluids.pump.PumpBlockEntity#updatePipesOnSide}
 * (which sets {@code sidesToUpdate} → next tick the pump rebuilds its pipe network from scratch).</p>
 *
 * <p><b>Fix</b>: hook {@code notifyMultiUpdated} (called once per cell after every multi
 * formation/split). For each of the 6 face directions, if there's an adjacent pipe (i.e. a
 * {@link FluidTransportBehaviour}-bearing block), call {@link FluidPropagator#propagateChangedPipe}.
 * This treats the tank topology change as equivalent to a pipe-state change — pumps reset
 * their networks → re-discover the resized tank as endpoint → resume transfer.</p>
 *
 * <p>Also explicitly call {@code level.invalidateCapabilities(getBlockPos())} so that even cells
 * whose controller didn't change (the "controller stays controller" case) signal their adjacent
 * {@code BlockCapabilityCache} listeners to refresh.</p>
 *
 * <p><b>Performance</b>: {@code notifyMultiUpdated} only fires on actual multi-block formation
 * or split events — not per-tick. Per-cell × 6-direction propagation is acceptable; each
 * {@code propagateChangedPipe} BFS is bounded by configured pump range.</p>
*/
@Mixin(FluidTankBlockEntity.class)
public abstract class FluidTankBlockEntityMixin {

    @Inject(method = "createInventory", at = @At("RETURN"), cancellable = true, remap = false)
    private void destroy$createMixtureInventory(CallbackInfoReturnable<SmartFluidTank> cir) {
        // Keep specialized tanks supplied by other addons, including their fill restrictions.
        if (cir.getReturnValue().getClass() == SmartFluidTank.class) {
            cir.setReturnValue(new GeniusFluidTank(FluidTankBlockEntity.getCapacityMultiplier(),
                this::invokeOnFluidStackChanged));
        }
    }

    @Invoker(value = "onFluidStackChanged", remap = false)
    public abstract void invokeOnFluidStackChanged(FluidStack stack);

    /** After {@link FluidTankBlockEntity#notifyMultiUpdated} runs
 * (per cell after every multi-block formation / split), force adjacent pumps to rebuild
 * their pipe networks so they don't stay stuck holding stale endpoint references.
*/
    @Inject(method = "notifyMultiUpdated", at = @At("RETURN"), remap = false)
    private void destroy$resetAdjacentPumpNetworks(CallbackInfo ci) {
        FluidTankBlockEntity self = (FluidTankBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        BlockPos pos = self.getBlockPos();

        // Belt-and-suspenders: explicitly invalidate caps at this pos. For cells whose
        // controller stayed the same (1→2 grow with original cell remaining controller),
        // refreshCapability isn't called → invalidateCapabilities was never fired. This
        // closes that gap so adjacent BlockCapabilityCache listeners reset cleanly.
        level.invalidateCapabilities(pos);

        // Trigger pump network refresh on adjacent pipes. propagateChangedPipe BFS-walks
        // connected pipes, wipes their pressure, and calls PumpBlockEntity.updatePipesOnSide
        // on every discovered pump → next tick the pump rebuilds from scratch and re-discovers
        // this tank as endpoint with correct (post-resize) capability handle.
        for (Direction d : Direction.values()) {
            BlockPos adjacentPos = pos.relative(d);
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, adjacentPos);
            if (pipe == null) continue;
            BlockState adjacentState = level.getBlockState(adjacentPos);
            FluidPropagator.propagateChangedPipe(level, adjacentPos, adjacentState);
        }
    }
}
