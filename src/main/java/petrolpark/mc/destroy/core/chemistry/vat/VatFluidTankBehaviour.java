package petrolpark.mc.destroy.core.chemistry.vat;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture.Phases;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.core.chemistry.vat.VatFluidTankBehaviour.VatTankSegment.VatFluidTank;
import petrolpark.mc.destroy.core.fluid.GeniusFluidTankBehaviour;

/**
 * Multi-tank {@link SmartFluidTankBehaviour} for a {@link VatControllerBlockEntity} — holds 2
 * tank segments (liquid phase + gas phase) with automatic phase separation on fill + proportional
 * gas scaling when liquid volume changes.
*/
public class VatFluidTankBehaviour extends GeniusFluidTankBehaviour {

    protected boolean liquidFull;
    protected int vatCapacity;

    // removed tankContentVersion (option A revert). VatTank.onContentsChanged /
    // setFluid no longer increment a version counter; the controller's tick no longer compares.
    // To restore: re-add `public long tankContentVersion = 0L;` here + the `tankContentVersion++`
    // bumps in VatFluidTank.onContentsChanged + setFluid + the tick-top comparison in
    // VatControllerBlockEntity.tick.

    public VatFluidTankBehaviour(VatControllerBlockEntity be, int vatCapacity) {
        super(SmartFluidTankBehaviour.TYPE, be, 2, vatCapacity, false);

        IFluidHandler[] handlers = new IFluidHandler[2];
        for (int i = 0; i < 2; i++) {
            VatTankSegment tankSegment = new VatTankSegment(vatCapacity, i == 1);
            this.tanks[i] = tankSegment;
            handlers[i] = tankSegment.getTank();
        }
        capability = new VatFluidHandler(handlers);

        liquidFull = false;
        this.vatCapacity = vatCapacity;
    }

    public VatFluidTank getLiquidHandler() {
        return getLiquidTank().getTank();
    }

    public VatFluidTank getGasHandler() {
        return getGasTank().getTank();
    }

    public VatTankSegment getLiquidTank() {
        return (VatTankSegment) super.getPrimaryTank();
    }

    public VatTankSegment getGasTank() {
        return (VatTankSegment) tanks[1];
    }

    
    public IFluidHandler getCombinedFluidHandler() {
        return capability;
    }

    @Override
    public void setCapacity(int capacity) {
        vatCapacity = capacity;
        for (TankSegment tankSegment : tanks) {
            ((VatTankSegment) tankSegment).getTank().setCapacity(capacity);
        }
    }

    public boolean isFull() {
        return liquidFull;
    }

    /**
 * Get the Mixture with the Vat's full capacity volume, containing the same number of moles of
 * all Molecules present in both the liquid + gas phases.
*/
    public LegacyMixture getCombinedMixture() {
        Map<LegacyMixture, Double> mixtures = new HashMap<>(2);
        int totalVolume = 0;

        FluidStack liquidStack = getLiquidHandler().getFluid();
        if (!liquidStack.isEmpty()) {
            mixtures.put(LegacyMixture.readNBT(liquidStack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag())), liquidStack.getAmount() / 1000d);
            totalVolume += liquidStack.getAmount();
        }

        FluidStack gasStack = getGasHandler().getFluid();
        if (!gasStack.isEmpty()) {
            mixtures.put(LegacyMixture.readNBT(gasStack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag())), gasStack.getAmount() / 1000d);
            totalVolume += gasStack.getAmount();
        }

        LegacyMixture mixture = LegacyMixture.mix(mixtures);
        if (totalVolume > 0) mixture.scale((float) vatCapacity / (float) totalVolume);
        return mixture;
    }

    public ReadOnlyMixture getCombinedReadOnlyMixture() {
        Map<LegacySpecies, Float> moleculesAndMoles = new HashMap<>();
        ReadOnlyMixture mixture = new ReadOnlyMixture();
        int totalVolume = 0;

        FluidStack liquidStack = getLiquidHandler().getFluid();
        if (!liquidStack.isEmpty()) {
            ReadOnlyMixture liquidMixture = ReadOnlyMixture.readNBT(ReadOnlyMixture::new, liquidStack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
            liquidMixture.getContents(false).forEach(molecule -> moleculesAndMoles.merge(molecule, liquidMixture.getConcentrationOf(molecule) * liquidStack.getAmount(), (f1, f2) -> f1 + f2));
            totalVolume += liquidStack.getAmount();
        }

        FluidStack gasStack = getGasHandler().getFluid();
        if (!gasStack.isEmpty()) {
            ReadOnlyMixture gasMixture = ReadOnlyMixture.readNBT(ReadOnlyMixture::new, gasStack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
            gasMixture.getContents(false).forEach(molecule -> moleculesAndMoles.merge(molecule, gasMixture.getConcentrationOf(molecule) * gasStack.getAmount(), (f1, f2) -> f1 + f2));
            totalVolume += gasStack.getAmount();
        }

        if (totalVolume > 0) {
            for (Entry<LegacySpecies, Float> entry : moleculesAndMoles.entrySet()) {
                mixture.addMolecule(entry.getKey(), entry.getValue() / totalVolume);
            }
        }

        return mixture;
    }

    public void setMixture(LegacyMixture mixture, int amount) {
        capability.drain(vatCapacity, FluidAction.EXECUTE);
        liquidFull = false;
        capability.fill(MixtureFluid.of(amount, mixture), FluidAction.EXECUTE);
    }

    /**
 * Replace all the gas in the gas tank with room-temperature-and-pressure air.
 *
 * @return the gas that was previously stored
*/
    public FluidStack flush(float temperature) {
        FluidStack oldGas = getGasHandler().getFluid();
        getGasHandler().setFluid(DestroyFluids.air(vatCapacity - getLiquidHandler().getFluidAmount(), temperature));
        getGasHandler().flushed = true;
        return oldGas;
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        if (clientPacket) return;
        nbt.putBoolean("Full", liquidFull);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        if (clientPacket) return;
        liquidFull = nbt.getBoolean("Full");
        vatCapacity = getLiquidHandler().getCapacity();
    }

    /**
 * Rescale gas tank volume to match free space (called when liquid level changes).
*/
    public void updateGasVolume() {
        if (getGasHandler().isEmpty()) return;
        double freeSpace = vatCapacity - getLiquidHandler().getFluidAmount();
        double gasVolume = getGasHandler().getFluidAmount();
        LegacyMixture mixture = LegacyMixture.readNBT(getGasHandler().getFluid().getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
        mixture.scale((float) (freeSpace / gasVolume));
        getGasHandler().setFluid(MixtureFluid.of((int) freeSpace, mixture));
    }

    /**
 * Write {@code temperature} onto both phase tanks' stored Mixtures without altering their volumes
 * (see {@link VatTankSegment.VatFluidTank#syncMixtureTemperature}).
*/
    public void syncTankTemperatures(float temperature) {
        getLiquidHandler().syncMixtureTemperature(temperature);
        getGasHandler().syncMixtureTemperature(temperature);
    }

    public class VatFluidHandler extends InternalFluidHandler {

        public VatFluidHandler(IFluidHandler[] handlers) {
            super(handlers, false);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (!DestroyFluids.isMixture(resource)) return 0;

            boolean simulate = action == FluidAction.SIMULATE;

            Phases phases = LegacyMixture.readNBT(resource.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag())).separatePhases(resource.getAmount());
            double amountScale = 1d;

            if (phases.liquidVolume() > getLiquidHandler().getSpace() - 1) {
                if (!simulate) liquidFull = true;
                amountScale = ((double) getLiquidHandler().getSpace() - 1d) / (double) phases.liquidVolume();
            }

            int liquidFillAmount = (int) (phases.liquidVolume() * amountScale + 0.5d);

            // Add liquid - GeniusFluidTank's fill handles existing-Mixture merging.
            if (liquidFillAmount > 0) {
                getLiquidHandler().fill(MixtureFluid.of(liquidFillAmount, phases.liquidMixture(), ""), action);
            }

            // Add gas
            if (!simulate) {
                Map<LegacyMixture, Double> mixtures = new HashMap<>(3);
                double combinedVolume = 0d;
                int freeSpace = vatCapacity - getLiquidHandler().getFluidAmount();

                if (!getGasHandler().isEmpty()) {
                    FluidStack existingGas = getGasHandler().getFluid();
                    mixtures.put(LegacyMixture.readNBT(existingGas.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag())), (double) existingGas.getAmount());
                    combinedVolume += existingGas.getAmount();
                }

                if (!phases.gasMixture().isEmpty()) {
                    mixtures.put(phases.gasMixture(), phases.gasVolume());
                    combinedVolume += phases.gasVolume();
                }

                LegacyMixture combinedGasMixture = LegacyMixture.mix(mixtures);
                if (combinedVolume > 0d && !combinedGasMixture.isEmpty()) {
                    combinedGasMixture.scale((float) (freeSpace / combinedVolume));
                    getGasHandler().setFluid(MixtureFluid.of(freeSpace, combinedGasMixture));
                }
            }

            return (int) (resource.getAmount() * amountScale);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return DestroyFluids.isMixture(stack);
        }
    }

    public class VatTankSegment extends GeniusTankSegment {

        public VatTankSegment(int capacity, boolean isForGas) {
            super(capacity);
            tank = new VatFluidTank(capacity, isForGas, f -> onFluidStackChanged());
        }

        public VatFluidTank getTank() {
            return (VatFluidTank) tank;
        }

        public class VatFluidTank extends GeniusFluidTank {

            private final boolean isForGas;

            /** Whether this tank has been {@link VatFluidTankBehaviour#flush flushed}.*/
            protected boolean flushed;

            public VatFluidTank(int capacity, boolean isForGas, Consumer<FluidStack> updateCallback) {
                super(capacity, updateCallback);
                this.isForGas = isForGas;
                flushed = false;
            }

            public boolean isEmptyOrFullOfAir() {
                return isEmpty() || flushed;
            }

            @Override
            public boolean isFluidValid(int tank, FluidStack stack) {
                return DestroyFluids.isMixture(stack);
            }

            @Override
            public void onContentsChanged() {
                super.onContentsChanged();
                flushed = false;
                if (fluid.getAmount() < getCapacity() && !isForGas) liquidFull = false;
                // removed tankContentVersion bump + diagnostic log (option A).
            }

            @Override
            public void setFluid(FluidStack stack) {
                super.setFluid(stack);
                flushed = false;
                if (stack.getAmount() < getCapacity() && !isForGas) liquidFull = false;
                // removed tankContentVersion bump + diagnostic log (option A).
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return super.drain(maxDrain, action);
            }

            /**
 * Write a new temperature onto this tank's stored Mixture without changing its volume, molecule
 * content, or the {@code flushed}/{@code liquidFull} flags. No-op when the tank is empty,
 * payload-less, or already at this temperature.
*/
            public void syncMixtureTemperature(float temperature) {
                if (fluid.isEmpty() || !fluid.has(DestroyDataComponents.MIXTURE)) return;
                LegacyMixture mixture = LegacyMixture.readNBT(
                    fluid.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()), false);
                if (mixture.isEmpty() || Math.abs(mixture.getTemperature() - temperature) < 1e-3f) return;
                mixture.setTemperature(temperature);
                boolean wasFlushed = flushed;
                boolean wasLiquidFull = liquidFull;
                FluidStack updated = fluid.copy();
                updated.set(DestroyDataComponents.MIXTURE, mixture.writeNBT());
                setFluid(updated);          // marks the BE dirty + syncs to client
                flushed = wasFlushed;       // a temperature change must not re-open the gas-vent gate
                liquidFull = wasLiquidFull; // nor flip the liquid-full flag (volume is unchanged)
            }
        }
    }

    /** @deprecated Use {@link #getLiquidHandler()} and {@link #getGasHandler()} instead.*/
    @Deprecated
    @Override
    public SmartFluidTank getPrimaryHandler() {
        return null;
    }

    /** @deprecated Use {@link #getLiquidTank()} and {@link #getGasTank()} instead.*/
    @Deprecated
    @Override
    public TankSegment getPrimaryTank() {
        return null;
    }
}
