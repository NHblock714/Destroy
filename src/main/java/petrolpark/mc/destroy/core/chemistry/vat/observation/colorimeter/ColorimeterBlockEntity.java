package petrolpark.mc.destroy.core.chemistry.vat.observation.colorimeter;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Optional;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity;
import petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial;
import petrolpark.mc.destroy.core.chemistry.vat.observation.RedstoneQuantityMonitorBehaviour;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;

/**
 * Block entity for the Colorimeter — a Vat-observation block that detects a configured molecule
 * in the adjacent Vat's mixture + emits redstone signal proportional to the molar concentration.
 *
 * <p>1.21 migrations applied:</p>
*/
public class ColorimeterBlockEntity extends SmartBlockEntity {

    public static final DecimalFormat df = new DecimalFormat();
    static {
        df.setMinimumFractionDigits(8);
        df.setMaximumFractionDigits(8);
    }

    /** True = observe gas phase · false = liquid phase.*/
    public boolean observingGas;

    /** The molecule being tracked. Null = no observation yet.*/
    protected LegacySpecies molecule;

    /** Redstone-out behaviour that drives the BLUSHING + POWERED block states + comparator signal.*/
    public RedstoneQuantityMonitorBehaviour redstoneMonitor;

    /** Awards COLORIMETER advancement every tick the colorimeter is actively observing.*/
    protected DestroyAdvancementBehaviour advancementBehaviour;

    /** Set to true after configuration change or neighbor block update; processed on the next tick.*/
    protected boolean updateVatNextTick;

    public ColorimeterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void initialize() {
        super.initialize();
        updateVatNextTick = true;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        advancementBehaviour = new DestroyAdvancementBehaviour(this, DestroyAdvancementTrigger.COLORIMETER);
        behaviours.add(advancementBehaviour);

        redstoneMonitor = new RedstoneQuantityMonitorBehaviour(this)
            .withLabel(f -> DestroyLang.translate("tooltip.colorimeter.menu.current_concentration", df.format(f)).component())
            .onStrengthChanged(strength -> {
                if (hasLevel()) {
                    getLevel().setBlockAndUpdate(getBlockPos(),
                        getBlockState().setValue(ColorimeterBlock.POWERED, strength != 0));
                }
            });
        behaviours.add(redstoneMonitor);
    }

    @Override
    @SuppressWarnings("null")
    public void tick() {
        super.tick();
        if (molecule != null && advancementBehaviour != null
            && (getVatOptional().isPresent() || getWindowedFluidHandler().isPresent())) {
            advancementBehaviour.awardDestroyAdvancement(DestroyAdvancementTrigger.COLORIMETER);
        }
        if (updateVatNextTick) {
            updateVatNextTick = false;
            updateVat();
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        // ColorimeterScreen open-screen guard dropped (screen not ported yet).
        configure(LegacySpecies.getMolecule(tag.getString("Molecule")), tag.contains("ObservingGas"));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (molecule != null) tag.putString("Molecule", molecule.getFullID());
        if (observingGas) tag.putBoolean("ObservingGas", true);
    }

    /** Set observed molecule + phase, then schedule a vat-rebind on the next tick.*/
    public void configure(LegacySpecies observedMolecule, boolean observingGas) {
        this.observingGas = observingGas;
        setMolecule(observedMolecule);
        updateVatNextTick = true;
    }

    public LegacySpecies getMolecule() {
        return molecule;
    }

    public void setMolecule(LegacySpecies molecule) {
        this.molecule = molecule;
        notifyUpdate();
    }

    /**
 * Look up the VatControllerBE the Colorimeter is looking into. Two-hop: via the adjacent
 * VatSideBE (must be transparent material) → its controller. Returns empty if the adjacent
 * block isn't a transparent Vat side.
*/
    @SuppressWarnings("null")
    public Optional<VatControllerBlockEntity> getVatOptional() {
        if (!hasLevel()) return Optional.empty();
        BlockPos vatPos = getBlockPos().relative(getBlockState().getValue(ColorimeterBlock.FACING));
        return getLevel().getBlockEntity(vatPos, DestroyBlockEntityTypes.VAT_SIDE.get()).map(vbe -> {
            if (!VatMaterial.getMaterial(vbe.getMaterial()).map(VatMaterial::transparent).orElse(false)) return null;
            return vbe.getController();
        });
    }

    /** When the FACING block isn't a Vat side, try to read
 * fluid contents from any block that exposes {@link Capabilities#FluidHandler} <b>and</b> has
 * a {@code shape} BlockState property whose value contains "window". This covers:
 *
 * <ul>
 * <li>Create's {@code FluidTankBlock} (shape: PLAIN / WINDOW / WINDOW_NW / WINDOW_SW /
 * WINDOW_NE / WINDOW_SE) — only the WINDOW* variants gate detection</li>
 * <li>Create-Connected's {@code FluidVesselBlock} which uses the same shape-property design</li>
 * <li>Any third-party fluid-tank-like block that opts in via the {@code shape: window}
 * BlockState convention</li>
 * </ul>
 *
 * <p>The "window" gate mirrors the Vat-side transparent-material requirement: visual line of
 * sight to the fluid is needed for the colorimeter to "see" its color.</p>
 *
 * <p>Multi-block tanks (Create FluidTank 3×3×3, etc.): {@link Capabilities#FluidHandler#BLOCK}
 * for any tank cell is delegated to the multi-block controller's combined inventory, so this
 * naturally reads the whole multi-block contents without explicit controller lookup.</p>
*/
    @SuppressWarnings("null")
    public Optional<IFluidHandler> getWindowedFluidHandler() {
        if (!hasLevel()) return Optional.empty();
        BlockPos targetPos = getBlockPos().relative(getBlockState().getValue(ColorimeterBlock.FACING));
        BlockState targetState = getLevel().getBlockState(targetPos);
        if (!hasWindowShape(targetState)) return Optional.empty();
        IFluidHandler handler = getLevel().getCapability(
            Capabilities.FluidHandler.BLOCK, targetPos, null);
        return Optional.ofNullable(handler);
    }

    /**
 * Check whether a BlockState has a {@code shape} property whose value contains "window"
 * (case-insensitive). Generic enough to cover Create + addon fluid-tank variants without
 * hard-binding to specific block classes.
*/
    private static boolean hasWindowShape(BlockState state) {
        for (Property<?> prop : state.getProperties()) {
            if (!"shape".equals(prop.getName())) continue;
            Object value = state.getValue(prop);
            String name = (value instanceof Enum<?> e) ? e.name() : value.toString();
            if (name.toLowerCase().contains("window")) return true;
        }
        return false;
    }

    /** Schedules a vat-rebind for next tick.
*/
    public void onNeighborChanged(BlockPos neighborPos) {
        if (neighborPos.equals(getBlockPos().relative(getBlockState().getValue(ColorimeterBlock.FACING)))) {
            updateVatNextTick = true;
        }
    }

    /**
 * Rebind {@link RedstoneQuantityMonitorBehaviour#quantityObserved} to a supplier that reads
 * the configured molecule's concentration. Two source types in priority order:
 *
 * <ol>
 * <li><b>Vat side</b>: adjacent transparent VatSide → controller's
 * liquid or gas tank (gated on {@code observingGas}).</li>
 * <li>any block with {@code Capabilities.FluidHandler}
 * + {@code shape: window} BlockState. Reads tank #0 fluid; {@code observingGas} is
 * ignored (fluid tanks have no separate gas phase). Covers Create's FluidTank,
 * create_connected FluidVessel, addon multi-block tanks.</li>
 * </ol>
*/
    public void updateVat() {
        if (molecule == null) {
            redstoneMonitor.quantityObserved = Optional.empty();
            return;
        }

        // Path 1 — Vat side (transparent material gate)
        Optional<VatControllerBlockEntity> vat = getVatOptional();
        if (vat.isPresent()) {
            redstoneMonitor.quantityObserved = Optional.of(() -> {
                FluidStack mixtureStack = observingGas ? vat.get().getGasTankContents() : vat.get().getLiquidTankContents();
                return concentrationOf(mixtureStack);
            });
            return;
        }

        // Path 2 windowed fluid tank
        Optional<IFluidHandler> tankHandler = getWindowedFluidHandler();
        if (tankHandler.isPresent()) {
            redstoneMonitor.quantityObserved = Optional.of(() -> {
                IFluidHandler handler = tankHandler.get();
                if (handler.getTanks() == 0) return 0f;
                return concentrationOf(handler.getFluidInTank(0));
            });
            return;
        }

        redstoneMonitor.quantityObserved = Optional.empty();
    }

    /**
 * Read the configured molecule's concentration from a FluidStack, treating non-Mixture
 * fluids as zero concentration. Shared between the Vat-side and fluid-tank paths.
*/
    private float concentrationOf(FluidStack mixtureStack) {
        if (!DestroyFluids.isMixture(mixtureStack)) return 0f;
        ReadOnlyMixture mixture = ReadOnlyMixture.readNBT(ReadOnlyMixture::new,
            mixtureStack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
        return mixture.getConcentrationOf(molecule);
    }

    
    public static class ColorimeterDisplaySource
        extends com.simibubi.create.api.behaviour.display.DisplaySource {

        private static final java.text.DecimalFormat df = new java.text.DecimalFormat();
        static {
            df.setMinimumFractionDigits(3);
            df.setMaximumFractionDigits(3);
        }

        @Override
        public java.util.List<net.minecraft.network.chat.MutableComponent> provideText(
                com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context,
                com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats stats) {
            if (!(context.getSourceBlockEntity() instanceof ColorimeterBlockEntity cbe))
                return java.util.Collections.emptyList();
            if (cbe.molecule == null) return java.util.Collections.emptyList();

            // Resolve fluid source — Vat path first, then windowed-tank fallback.
            FluidStack fluid;
            java.util.Optional<VatControllerBlockEntity> vat = cbe.getVatOptional();
            if (vat.isPresent()) {
                fluid = cbe.observingGas ? vat.get().getGasTankContents() : vat.get().getLiquidTankContents();
            } else {
                java.util.Optional<net.neoforged.neoforge.fluids.capability.IFluidHandler> handler =
                    cbe.getWindowedFluidHandler();
                if (handler.isEmpty() || handler.get().getTanks() == 0)
                    return java.util.Collections.emptyList();
                fluid = handler.get().getFluidInTank(0);
            }
            if (!DestroyFluids.isMixture(fluid)) return java.util.Collections.emptyList();

            ReadOnlyMixture mixture = ReadOnlyMixture.readNBT(ReadOnlyMixture::new,
                fluid.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));

            boolean showSpecies = context.sourceConfig().getBoolean("ShowSpeciesName");
            boolean iupac = !context.sourceConfig().getBoolean("MoleculeNameType");

            net.createmod.catnip.lang.LangBuilder b = DestroyLang.builder();
            if (showSpecies) {
                b.add(cbe.molecule.getName(!iupac).copy()).add(net.minecraft.network.chat.Component.literal(" "));
            }
            b.add(DestroyLang.quantity(mixture.getConcentrationOf(cbe.molecule), false, df));
            return java.util.Collections.singletonList(b.component());
        }

        @Override
        public void initConfigurationWidgets(
                com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context,
                com.simibubi.create.foundation.gui.ModularGuiLineBuilder builder,
                boolean isFirstLine) {
            if (isFirstLine) {
                builder.addSelectionScrollInput(0, 137, (si, l) -> {
                    si.forOptions(java.util.List.of(
                            DestroyLang.translate("display_source.colorimeter.species_name.dont_include").component(),
                            DestroyLang.translate("display_source.colorimeter.species_name").component()))
                        .titled(DestroyLang.translate("display_source.colorimeter.species_name").component());
                }, "ShowSpeciesName");
            } else {
                petrolpark.mc.destroy.core.chemistry.MixtureContentsDisplaySource
                    .addMoleculeNameTypeSelection(builder);
            }
        }
    }
}
