package petrolpark.mc.destroy.mixin.compat.create_connected;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hlysine.create_connected.content.fluidvessel.FluidVesselBlockEntity;
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
 * Replace {@link FluidVesselBlockEntity}'s default {@link SmartFluidTank} with {@link
 * GeniusFluidTank} so Mixture-bearing fluids retain their {@code MIXTURE} DataComponent when
 * inserted into a Create:Connected Fluid Vessel. Without this, two Mixture FluidStacks with
 * differing component values would refuse to merge (vanilla SmartFluidTank does NBT/component
 * equality), losing chemistry-relevant fluids dropped into a Vessel.
 *
 * <p>Auto-loaded via {@link petrolpark.mc.destroy.mixin.plugin.DestroyMixinPlugin}
 * (inherited from PetrolparkMixinPlugin): the {@code compat.<modid>.} package convention causes
 * {@code shouldApplyMixin} to gate this mixin behind FML's {@code create_connected} mod presence
 * check, so the mixin silently no-ops when CC isn't installed.</p>
*/
@Mixin(FluidVesselBlockEntity.class)
public abstract class FluidVesselBlockEntityMixin {

    @Overwrite
    protected SmartFluidTank createInventory() {
        return new GeniusFluidTank(FluidTankBlockEntity.getCapacityMultiplier(),
            this::invokeOnFluidStackChanged);
    }

    @Invoker("onFluidStackChanged")
    public abstract void invokeOnFluidStackChanged(FluidStack stack);

    /**
 * Same root cause / fix applied to Create:Connected's FluidVessel: when the multi-block
 * topology changes (1→2 cells, etc.), neighbouring pumps don't see any block-state-change
 * event → their {@link com.simibubi.create.content.fluids.FluidNetwork} state stays stale →
 * transfers stop until the pump is broken+replaced. Force a {@link
 * FluidPropagator#propagateChangedPipe} on each adjacent pipe so pumps reset their networks.
 *
 * <p>See {@link
 * petrolpark.mc.destroy.mixin.compat.create.FluidTankBlockEntityMixin}'s class javadoc for
 * the full root-cause analysis. FluidVessel's
 * {@code notifyMultiUpdated} signature is identical to Create's
 * (javap-verified against {@code create_connected-1.1.14-mc1.21.1}).</p>
*/
    @Inject(method = "notifyMultiUpdated", at = @At("RETURN"), remap = false)
    private void destroy$resetAdjacentPumpNetworks(CallbackInfo ci) {
        FluidVesselBlockEntity self = (FluidVesselBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide) return;
        BlockPos pos = self.getBlockPos();

        level.invalidateCapabilities(pos);

        for (Direction d : Direction.values()) {
            BlockPos adjacentPos = pos.relative(d);
            FluidTransportBehaviour pipe = FluidPropagator.getPipe(level, adjacentPos);
            if (pipe == null) continue;
            BlockState adjacentState = level.getBlockState(adjacentPos);
            FluidPropagator.propagateChangedPipe(level, adjacentPos, adjacentState);
        }
    }
}
