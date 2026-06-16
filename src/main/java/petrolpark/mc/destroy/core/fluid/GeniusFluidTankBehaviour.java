package petrolpark.mc.destroy.core.fluid;

import java.util.Map;
import java.util.function.Consumer;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;

/**
 * Mixture-aware {@link SmartFluidTankBehaviour} — accepts Mixture fluids whose payload
 * {@code destroy:mixture} DataComponents differ, merging them by molar-weighted mix instead of
 * rejecting the insert like the stock Create tank would (which requires identical NBT).
*/
public class GeniusFluidTankBehaviour extends SmartFluidTankBehaviour {

    /**
 * Mostly copied from Create's {@link SmartFluidTankBehaviour}, swapping
 * {@link SmartFluidTank} for {@link GeniusFluidTank}, which allows Mixtures to be added even
 * if the NBT does not exactly match.
 *
 * @param type whether this is an input/output/generic behaviour type
 * @param be the BlockEntity to which this behaviour is attached
 * @param tanks number of tank segments
 * @param tankCapacity capacity (mB) per tank
 * @param enforceVariety passed to {@code InternalFluidHandler} — guards against mixing
 * incompatible fluids in the multi-tank combined wrapper
*/
    public GeniusFluidTankBehaviour(BehaviourType<SmartFluidTankBehaviour> type, SmartBlockEntity be,
                                    int tanks, int tankCapacity, boolean enforceVariety) {
        super(type, be, tanks, tankCapacity, enforceVariety);
        IFluidHandler[] handlers = new IFluidHandler[tanks];
        for (int i = 0; i < tanks; i++) {
            GeniusTankSegment tankSegment = new GeniusTankSegment(tankCapacity);
            this.tanks[i] = tankSegment;
            handlers[i] = tankSegment.getTank();
        }
        capability = new InternalFluidHandler(handlers, enforceVariety);
    }

    public void setCapacity(int capacity) {
        for (TankSegment tank : tanks) ((GeniusTankSegment) tank).setCapacity(capacity);
    }

    public class GeniusTankSegment extends TankSegment {

        public GeniusTankSegment(int capacity) {
            super(capacity);
            tank = new GeniusFluidTank(capacity, f -> onFluidStackChanged());
        }

        protected GeniusFluidTank getTank() {
            return (GeniusFluidTank) tank;
        }

        protected void setCapacity(int capacity) {
            tank.setCapacity(capacity);
        }
    }

    public static class GeniusFluidTank extends SmartFluidTank {

        public GeniusFluidTank(int capacity, Consumer<FluidStack> updateCallback) {
            super(capacity, updateCallback);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = super.fill(resource, action);
            // Non-zero fill or the tank is full already → regular Create semantics already applied.
            if (filled == 0 && getSpace() > 0) {
                // Differing NBT blocked the insert — try Mixture-aware merge if both sides are
                // Mixture fluids carrying MIXTURE DataComponents.
                if (!DestroyFluids.isMixture(resource) || !DestroyFluids.isMixture(fluid)) return 0;
                if (!resource.has(DestroyDataComponents.MIXTURE) || !fluid.has(DestroyDataComponents.MIXTURE)) return 0;

                int amountOfMixtureAdded = Math.min(getSpace(), resource.getAmount());
                int existingAmount = fluid.getAmount();
                if (action.simulate()) return amountOfMixtureAdded;

                // lite parse/mix variants skip refreshPossibleReactions /
                // updateName / updateColor / updateNextBoilingPoints. Those caches don't survive
                // NBT round-trip and the merged mixture is immediately re-serialized below
                // (LegacyMixture.writeNBT only emits contents/temperature/equilibrium/results).
                LegacyMixture existingMixture = LegacyMixture.readNBT(
                    fluid.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()), false);
                LegacyMixture addedMixture = LegacyMixture.readNBT(
                    resource.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()), false);

                ReadOnlyMixture newMixture = LegacyMixture.mix(Map.of(
                    existingMixture, (double) existingAmount / 1000d,
                    addedMixture, (double) amountOfMixtureAdded / 1000d), false);

                // Use {@code setFluid(MixtureFluid.of(...))} (instance replacement) rather than
                // mutating the existing FluidStack in place. Mutating in place to avoid FluidStack
                // instance churn (so pipe state machines see "stable" fluid identity) breaks
                // pump→pipe transfer entirely (symptom: a pump connected to a fluid pipe fails to
                // push fluid into the pipe).
                // <p>Why setFluid is correct: vanilla Create FluidStacks are ephemeral value-type
                // wrappers — pipe state machines snapshot fluid identity via {@code drain(1, SIMULATE)}
                // (which always returns a new instance), so they don't actually rely on instance
                // stability of the tank's internal {@code fluid} field. By contrast, mutating a
                // FluidStack's components in-place via {@code fluid.set(...)} can leak through
                // shared {@code DataComponentPatch} references (drain copies the patch reference,
                // not a deep clone) — downstream pipe state machines holding old drained stacks
                // can see their "snapshot" components mutate retroactively, producing
                // hard-to-diagnose flow-identity mismatches that propagate through pipe segments.
                // <p>The original "A→B→C stall" is now handled by the broader
                // FluidTransportBehaviourMixin, which relaxes {@code FluidStack.isSameFluidSameComponents}
                // for mixture pairs across all three pipe-network call sites. With that in
                // place, instance-replacement no longer triggers the stall.
                setFluid(MixtureFluid.of(existingAmount + amountOfMixtureAdded, newMixture));
                return amountOfMixtureAdded;
            }
            return filled;
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack stack = super.drain(maxDrain, action);
            // If the last contents were drained but the tank still "holds" a non-EMPTY Fluid type
            // (amount=0 but mixture/NBT lingering), reset to EMPTY so pooled containers of the same
            // underlying fluid type can stack again. 1.21 API change: getRawFluid() → getFluid().
            if (fluid.isEmpty() && fluid.getFluid() != Fluids.EMPTY)
                setFluid(FluidStack.EMPTY);
            return stack;
        }
    }
}
