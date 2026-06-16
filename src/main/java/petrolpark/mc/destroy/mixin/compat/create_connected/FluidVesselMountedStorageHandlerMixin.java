package petrolpark.mc.destroy.mixin.compat.create_connected;

import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import com.hlysine.create_connected.content.fluidvessel.FluidVesselMountedStorage;

import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import petrolpark.mc.destroy.core.fluid.GeniusFluidTankBehaviour.GeniusFluidTank;

/**
 * Mounted-storage twin of {@link FluidVesselBlockEntityMixin}. When a Fluid Vessel is
 * disassembled into a contraption (cart/elevator/clockwork), CC packs its tank into a
 * {@link FluidVesselMountedStorage.Handler} for serialised storage — vanilla {@link FluidTank}
 * semantics blow away Mixture component data the same way SmartFluidTank does. This delegates
 * {@code fill}/{@code getFluid} through a lazy {@link GeniusFluidTank} so Mixture-aware merging
 * applies during contraption fluid I/O as well.
 *
 * <p>Auto-loaded via {@link petrolpark.mc.destroy.mixin.plugin.DestroyMixinPlugin} —
 * see sibling mixin {@link FluidVesselBlockEntityMixin} for the {@code compat.<modid>.}
 * gating mechanism.</p>
*/
@Mixin(FluidVesselMountedStorage.Handler.class)
public abstract class FluidVesselMountedStorageHandlerMixin extends FluidTank {

    @Unique
    public Lazy<GeniusFluidTank> geniusFluidTank = Lazy.of(
        () -> new GeniusFluidTank(this.capacity, f -> {}));

    /**
 * Shadow ctor — body is ignored by Mixin (constructor-merge isn't supported); exists only
 * so javac is satisfied that this is a valid subclass of {@link FluidTank}. The {@code @Unique}
 * field initializer above is the part that actually gets merged into the target's ctor.
*/
    public FluidVesselMountedStorageHandlerMixin(int capacity, FluidStack initialFluid) {
        super(capacity);
        throw new AssertionError();
    }

    /** {@code @Overwrite} requires the method to be declared on the
 * target class itself, so application failed at class-load with
 * {@code @Overwrite method fill ... was not located in the target class} and crashed any
 * contraption assembly that included a Fluid Vessel (e.g. rotating a Mechanical
 * Bearing structure with a bubble cap + fluid vessel together → server tick crash).
 *
 * <p>Removing {@code @Overwrite} and keeping only the plain method declaration tells Mixin
 * to <b>add</b> {@code fill} as a NEW member of {@code Handler}. Java virtual dispatch then
 * picks this version for {@code handler.fill(...)} calls, overriding the inherited {@link
 * FluidTank#fill} via standard inheritance — same end-effect as the prior {@code @Overwrite}
 * approach but tolerant of CC's choice not to override these methods themselves.</p>
 *
 * <p>{@code @Override} below is the Java annotation (signals override of inherited
 * FluidTank.fill), not Mixin's annotation. Same pattern applies to {@link #getFluid}.</p>
*/
    @Override
    public int fill(@NotNull FluidStack resource, FluidAction action) {
        GeniusFluidTank tank = geniusFluidTank.get();
        tank.setFluid(this.fluid);
        int filled = tank.fill(resource, action);
        if (filled > 0) {
            this.fluid = tank.getFluid();
            onContentsChanged();
        }
        return filled;
    }

    @Override
    @NotNull
    public FluidStack getFluid() {
        return this.fluid.isEmpty() ? FluidStack.EMPTY : this.fluid;
    }
}
