package petrolpark.mc.destroy.core.chemistry.vat;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Side cell of a {@link Vat} multi-block — one block per face of the Vat's outer shell. Holds the
 * {@link DisplayType} that configures the face's role: normal wall, pipe-connector, thermometer /
 * barometer gauge, or open/closed vent. The Vat's {@link VatControllerBlockEntity} uses the 6 side
 * cells to dispatch fluid I/O + display reads.
 *
 * <p><b>Functional</b>:</p>
*/
public class VatSideBlockEntity extends CopycatBlockEntity
    implements petrolpark.mc.destroy.core.block.entity.IHaveLabGoggleInformation,
               com.simibubi.create.api.equipment.goggles.IHaveHoveringInformation {

    private static final java.text.DecimalFormat df = new java.text.DecimalFormat();
    static {
        df.setMinimumFractionDigits(1);
        df.setMaximumFractionDigits(1);
    }

    // ============================================================
    // DisplayType per-variant label helpers
    // ============================================================

    
    private static net.minecraft.network.chat.Component noQuantityLabel(Float f) {
        return net.minecraft.network.chat.Component.empty();
    }

    
    private static net.minecraft.network.chat.Component pressureQuantityLabel(Float f) {
        return petrolpark.mc.destroy.client.DestroyLang.translate("tooltip.vat.pressure.absolute", df.format(f)).component();
    }

    
    private static net.minecraft.network.chat.Component temperatureQuantityLabel(Float f) {
        return petrolpark.mc.destroy.client.DestroyLang.translate("tooltip.vat.temperature",
            petrolpark.mc.destroy.client.DestroyLang.TemperatureUnit.KELVINS.of(f, df)).component();
    }

    public Direction direction; // The outward direction this side is facing
    public BlockPos controllerPosition;

    protected DisplayType displayType;

    
    // dropped {@code final} so addBehaviours can construct it (super-ctor calls
    // addBehaviours before this ctor runs; final-field init order doesn't fit this pattern).
    public petrolpark.mc.destroy.core.chemistry.vat.observation.RedstoneQuantityMonitorBehaviour redstoneMonitor;

    /** Fluid deposited here is transferred into the Vat controller's combined mixture
 * via the pipe-transfer logic.
*/
    public SmartFluidTankBehaviour inputBehaviour;

    /** Buffer-tank capacity for the side cell's input slot.*/
    public static final int BUFFER_TANK_CAPACITY = 1000;

    /** Stored
 * so {@link #setPowerFromAdjacentBlock} can compute a delta vs the previous contribution and
 * apply only the delta to {@link VatControllerBlockEntity#changeHeatingPower}.
*/
    protected float oldPower = 0f;

    /** Same delta
 * pattern as {@link #oldPower}.*/
    protected float oldUV = 0f;

    /** Prevents chained block placement during tryMakeVat from causing
 * rollback on fluid pipes that haven't finished connecting.
*/
    protected int initializationTicks = 3;

    public VatSideBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        displayType = DisplayType.NORMAL;
        // DON'T construct redstoneMonitor here. addBehaviours runs inside super(),
        // which is BEFORE this ctor's body executes. If redstoneMonitor were initialized in
        // this ctor body, addBehaviours would already have run with redstoneMonitor=null →
        // {@code behaviours.add(null)} → Create's SmartBlockEntity {@code behaviours.forEach
        // (b -> behaviourMap.put(b.getType(), b))} NPEs because b is null. So redstoneMonitor is
        // constructed inside addBehaviours itself (where {@code this} is fully
        // accessible — the BlockEntity is sufficient init for the behaviour ctor's needs).
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // instantiate-and-add in one step. Field-level init was racing with
        // super-ctor's behaviour-collection pass — see ctor doc comment above.
        redstoneMonitor = new petrolpark.mc.destroy.core.chemistry.vat.observation.RedstoneQuantityMonitorBehaviour(this);
        behaviours.add(redstoneMonitor);
        // inputBehaviour for fluid insertion. forbidExtraction ensures the buffer only
        // accepts fluid; drain routes via the VatSideFluidCapability's drain override (liquid
        // or gas depending on pipe-submerged state).
        inputBehaviour = new SmartFluidTankBehaviour(
            SmartFluidTankBehaviour.TYPE, this, 1, BUFFER_TANK_CAPACITY, false).forbidExtraction();
        behaviours.add(inputBehaviour);
    }

    /**
 *
 * <ul>
 * <li>stored items drop at the broken position;</li>
 * <li>liquid + gas tank contents emit pollution to the world via
 * {@link petrolpark.mc.destroy.core.pollution.PollutionHelper#pollute};</li>
 * <li>all remaining side cells revert to their wrapped material via
 * {@link VatControllerBlockEntity#deleteVat(BlockPos)}.</li>
 * </ul>
 *
 * <p>The {@code !underDeconstruction} guard prevents re-entry: {@code deleteVat} itself walks
 * {@link Vat#getSideBlockPositions()} and replaces each side via
 * {@code level.setBlockAndUpdate(pos, vatSide.getMaterial())}, which fires
 * {@link BlockEntity#destroy()} on each replaced side cell — without the guard each replacement
 * would re-enter {@code deleteVat} and infinite-loop / double-pollute.</p>
*/
    @Override
    public void destroy() {
        super.destroy();
        VatControllerBlockEntity vatController = getController();
        if (vatController != null && !vatController.underDeconstruction) {
            vatController.deleteVat(getBlockPos());
        }
    }

    // ============================================================
    // ============================================================

    /**
 * <ul>
 * <li>{@link Capabilities.FluidHandler#BLOCK}: a {@link VatSideFluidCapability} that
 * combines the local input buffer with the controller's liquid/gas output tanks.
 * Drain dispatches to liquid or gas based on pipe-submerged state.</li>
 * <li>{@link Capabilities.ItemHandler#BLOCK}: forwards to the controller's inventory
 * field (pipe-inserted items route through side cells into the controller).</li>
 * </ul>
 *
 * <p>Wire in {@link petrolpark.mc.destroy.Destroy#Destroy Destroy}: {@code
 * modEventBus.addListener(VatSideBlockEntity::registerCapabilities)}.</p>
*/
    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK,
            petrolpark.mc.destroy.DestroyBlockEntityTypes.VAT_SIDE.get(),
            (be, side) -> {
                VatControllerBlockEntity vc = be.getController();
                if (vc == null || be.inputBehaviour == null) return null;
                return new VatSideFluidCapability(
                    be,
                    vc.getLiquidTank(),
                    vc.getGasTank(),
                    be.inputBehaviour.getCapability());
            });
        event.registerBlockEntity(
            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
            petrolpark.mc.destroy.DestroyBlockEntityTypes.VAT_SIDE.get(),
            (be, side) -> {
                VatControllerBlockEntity vc = be.getController();
                return vc == null ? null : vc.inventory;
            });
    }

    // ============================================================
    // tick + NBT save/load incremental port
    // ============================================================

    
    @Override
    @SuppressWarnings("null")
    public void tick() {
        super.tick();
        if (!hasLevel()) return;

        // initializationTicks grace period before first updateNeighborShapes fires.
        if (initializationTicks > 0) {
            initializationTicks--;
            if (!getLevel().isClientSide() && initializationTicks <= 0) {
                getBlockState().updateNeighbourShapes(getLevel(), getBlockPos(), 3);
            }
        }

        // Client-side animation
        if (getLevel().isClientSide()) {
            ventOpenness.tickChaser();
            if (spoutingTicks > 0) {
                spoutingTicks--;
                // restore spout particle emission when pipe is emerged (submerged pipe is
                // inside fluid so no spout visual needed).
                if (!isPipeSubmerged(true, null)) spawnParticles(spoutingFluid, getLevel());
            }
        } else {
            // server-side: transfer fluid from local buffer to Vat controller each tick.
            tryInsertFluidInVat();
        }
    }

    /** Falls back to super if no controller yet.
*/
    @Override
    @SuppressWarnings("null")
    protected net.minecraft.world.phys.AABB createRenderBoundingBox() {
        VatControllerBlockEntity vc = getController();
        if (vc != null) return vc.wholeVatAABB();
        return super.createRenderBoundingBox();
    }

    /** Called server-side every tick.
 *
 * <p>Flow: SIMULATE drain the whole buffer → offer to controller.addFluid(EXECUTE) → drain the
 * accepted amount actual · on success, set {@code spoutingTicks=10} + {@code spoutingFluid}
 * for client animation + {@code sendData()} sync.</p>
*/
    @SuppressWarnings("null")
    public void tryInsertFluidInVat() {
        VatControllerBlockEntity vatController = getController();
        if (vatController == null || inputBehaviour == null || inputBehaviour.getPrimaryHandler().isEmpty()) return;
        inputBehaviour.allowExtraction();
        net.neoforged.neoforge.fluids.FluidStack drainedFluid = inputBehaviour.getPrimaryHandler()
            .drain(BUFFER_TANK_CAPACITY, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        int drainedAmount = vatController.addFluid(drainedFluid,
            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        inputBehaviour.getPrimaryHandler().drain(drainedAmount,
            net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        if (drainedAmount > 0) {
            spoutingTicks = 10;
            spoutingFluid = drainedFluid;
            sendData();
        }
        inputBehaviour.forbidExtraction();
    }

    /**
 * 1:1 21 {@link com.simibubi.create.content.fluids.FluidFX#getFluidParticle}
 * + catnip {@link net.createmod.catnip.math.VecHelper}.
*/
    @SuppressWarnings("null")
    protected void spawnParticles(net.neoforged.neoforge.fluids.FluidStack fluid, net.minecraft.world.level.Level level) {
        if (isVirtual() || direction == null) return;
        net.minecraft.world.phys.Vec3 position = net.createmod.catnip.math.VecHelper.getCenterOf(
                getBlockPos().relative(direction.getOpposite()))
            .subtract(0d, direction == Direction.UP ? 0d : 3 / 16d, 0d)
            .add(net.minecraft.world.phys.Vec3.atLowerCornerOf(direction.getNormal()).scale(3 / 16f));
        net.minecraft.core.particles.ParticleOptions particle =
            com.simibubi.create.content.fluids.FluidFX.getFluidParticle(fluid);
        net.minecraft.world.phys.Vec3 motion = net.createmod.catnip.math.VecHelper.offsetRandomly(
            net.minecraft.world.phys.Vec3.ZERO, level.random, 0.05f);
        motion = new net.minecraft.world.phys.Vec3(motion.x, Math.abs(motion.y), motion.z);
        level.addAlwaysVisibleParticle(particle, position.x, position.y, position.z, motion.x, motion.y, motion.z);
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("Side")) {
            direction = Direction.values()[tag.getInt("Side")];
        } else {
            direction = null;
        }
        if (tag.contains("ControllerPosition")) {
            // Stored relative to this block's position (offset back on read).
            controllerPosition = NbtUtils.readBlockPos(tag, "ControllerPosition")
                .map(p -> p.offset(getBlockPos()))
                .orElse(null);
        }
        displayType = DisplayType.values()[tag.getInt("DisplayType")];
        // cached power contributions (server-only NBT).
        oldPower = tag.getFloat("OldHeatingPower");
        oldUV = tag.getFloat("OldUVPower");
        if (tag.contains("InitializationTicks", Tag.TAG_INT)) {
            initializationTicks = tag.getInt("InitializationTicks");
        } else {
            initializationTicks = 0;
        }
        if (clientPacket) {
            spoutingTicks = tag.getInt("SpoutingTicks");
            spoutingFluid = net.neoforged.neoforge.fluids.FluidStack.parseOptional(
                registries, tag.getCompound("SpoutingFluid"));
            ventOpenness.chase(displayType == DisplayType.OPEN_VENT ? 1f : 0f, 0.3f, LerpedFloat.Chaser.EXP);
        }
        // Rebind the redstoneMonitor's quantityObserved supplier from the loaded displayType
        // using VatControllerBE's getPressure + getTemperature. Without this rebind, after a
        // world reload the BE has displayType=THERMOMETER but quantityObserved=Optional.empty
        // (set in addBehaviours before NBT was read) → redstoneMonitor.tick reads 0 → no
        // redstone signal output (symptom: a Vat side wall's thermometer / barometer emits no
        // redstone signal after reload).
        rebindQuantityObserved();
    }

    /** Maps the current displayType's optional quantityObserved Function (defined in the enum)
 * onto a Supplier that reads live from this side's controller. Empty Optional (NORMAL / PIPE /
 * VENT) leaves the supplier empty (no signal).
 *
 * <p><b>Defer controller lookup</b>: the controller might not be loaded yet at read-time
 * (chunk-load order dependent). Capture the lookup as a closure so each tick re-resolves the
 * controller — handles the case where the side BE loads first, then controller chunk loads later.</p>
*/
    @SuppressWarnings("null")
    private void rebindQuantityObserved() {
        if (redstoneMonitor == null) return;
        redstoneMonitor.quantityObserved = displayType.quantityObserved.map(f -> () -> {
            VatControllerBlockEntity vc = getController();
            return vc == null ? 0f : f.apply(vc);
        });
        redstoneMonitor.withLabel(displayType.quantityLabel);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (direction != null) tag.putInt("Side", direction.ordinal());
        if (controllerPosition != null) {
            tag.put("ControllerPosition", NbtUtils.writeBlockPos(controllerPosition.subtract(getBlockPos())));
        }
        tag.putInt("DisplayType", displayType.ordinal());
        // cached power contributions (server-only NBT).
        tag.putFloat("OldHeatingPower", oldPower);
        tag.putFloat("OldUVPower", oldUV);
        if (initializationTicks > 0) tag.putInt("InitializationTicks", initializationTicks);
        if (clientPacket) {
            tag.putInt("SpoutingTicks", spoutingTicks);
            if (!spoutingFluid.isEmpty()) {
                tag.put("SpoutingFluid", spoutingFluid.saveOptional(registries));
            }
        }
    }

    public DisplayType getDisplayType() {
        return displayType;
    }

    /**
 * Set the display type of this side. Beyond the field write, this updates the vent state,
 * neighbour shapes, the open-vent registration on the controller, and rebinds the redstone
 * monitor's observed quantity + thresholds. (Ponder scene use via
 * SetVatSideTypePonderInstruction effectively only exercises the field write, since no
 * controller/level side effects apply there.)
*/
    @SuppressWarnings("null")
    public void setDisplayType(DisplayType displayType) {
        DisplayType oldDisplayType = this.displayType;
        if (oldDisplayType == displayType) return;
        boolean updateVent = oldDisplayType == DisplayType.OPEN_VENT;
        this.displayType = displayType;
        if (!hasLevel()) return;

        if (getLevel().isClientSide()) {
            ventOpenness.chase(displayType == DisplayType.OPEN_VENT ? 1f : 0f, 0.3f, LerpedFloat.Chaser.EXP);
        }

        if (!getLevel().isClientSide() && direction != null) {
            getBlockState().updateNeighbourShapes(getLevel(), getBlockPos(), 3);
            updateDisplayType(getBlockPos().relative(direction)); // Check for a Pipe
        }
        sendData();

        VatControllerBlockEntity controller = getController();
        if (controller == null) return;
        if (updateVent) controller.removeVent();
        if (displayType == DisplayType.OPEN_VENT) {
            if (controller.cachedMixture != null) controller.cachedMixture.disturbEquilibrium();
            if (controller.openVentPos == null) controller.openVentPos = getBlockPos();
        }

        // observer + label rebind when DisplayType changed the observed quantity.
        // delegate to shared rebindQuantityObserved helper (defers controller lookup so
        // re-binds work even if controller chunk loads later).
        rebindQuantityObserved();
        if (oldDisplayType.quantityObserved != this.displayType.quantityObserved) {
            if (this.displayType.showsPressure) {
                redstoneMonitor.lowerThreshold = 0f;
                redstoneMonitor.upperThreshold = getVatOptional().isPresent()
                    ? getVatOptional().get().getMaxPressure() : 100000f;
            } else if (this.displayType.showsTemperature) {
                redstoneMonitor.lowerThreshold = 273f;
                redstoneMonitor.upperThreshold = 373f;
            } else {
                redstoneMonitor.lowerThreshold = 0f;
                redstoneMonitor.upperThreshold = 1f;
            }
            redstoneMonitor.update();
        }
    }

    // ============================================================
    // Renderer-support getters + fields (VatSideRenderer / VatSideFluidCapability)
    // ============================================================

    /**
 * Fuse-style spout animation tick counter · set to 10 in {@link #tryInsertFluidInVat} on
 * a successful fluid transfer, decremented in {@link #tick} server-tick loop. Drives
 * VatSideRenderer's PIPE-mode fluid-stream draw window.
*/
    public int spoutingTicks = 0;

    /**
 * Vent openness LerpedFloat (0=closed, 1=open) for VatRenderer OPEN_VENT/CLOSED_VENT bars
 * rotation animation. Chase target updated in {@link #setDisplayType} to 1f when
 * DisplayType becomes OPEN_VENT, 0f otherwise; ticked client-side in {@link #tick}.
*/
    public final net.createmod.catnip.animation.LerpedFloat ventOpenness = net.createmod.catnip.animation.LerpedFloat.linear().startWithValue(0f);

    /**
 * Current spouting fluid (pipe-output) · set to the drained fluid in
 * {@link #tryInsertFluidInVat} when a side-cell pipe successfully transfers fluid
 * into the vat's main tank. Read by VatSideRenderer for PIPE-mode fluid-stream colour.
*/
    public net.neoforged.neoforge.fluids.FluidStack spoutingFluid = net.neoforged.neoforge.fluids.FluidStack.EMPTY;

    /** Returns null if not
 * placed yet or controllerPosition wasn't NBT-deserialized yet (read-before-level-ready).
*/
    public VatControllerBlockEntity getController() {
        if (!hasLevel() || controllerPosition == null) return null;
        BlockEntity be = getLevel().getBlockEntity(controllerPosition);
        if (!(be instanceof VatControllerBlockEntity vatController)) return null;
        return vatController;
    }

    /**
 * <ul>
 * <li>DOWN-facing side: always submerged (pipe nozzle is under liquid)</li>
 * <li>UP-facing side: submerged iff Vat can't fit more fluid (full tank)</li>
 * <li>Horizontal side: pipe height < fluid level (client uses getRenderedFluidLevel, server
 * uses the authoritative getFluidLevel)</li>
 * </ul>
*/
    public boolean isPipeSubmerged(boolean client, @Nullable Float partialTicks) {
        VatControllerBlockEntity controller = getController();
        if (controller == null) return false;
        if (direction == Direction.DOWN) return true;
        if (direction == Direction.UP) return !controller.canFitFluid();
        // Server path uses the authoritative {@link VatControllerBlockEntity#getFluidLevel}
        // (server-side tank.getFluidAmount); client path keeps the lerped renderer value.
        // Using {@code getRenderedFluidLevel(0f)} for both sides breaks server-side
        // extraction: the per-tick {@code setMixture} writeback
        // round-trips the liquid tank (drain to 0 → fill back), and
        // {@code getTotalUnits(0f)} returns the animation START frame (= 0 right after the
        // drain step), so isPipeSubmerged saw fluid level = 0 and routed
        // {@link VatSideFluidCapability#drain} to the GAS tank instead of LIQUID — second-
        // and-onward extractions pulled air while the liquid level appeared frozen.
        if (client) {
            return pipeHeightAboveVatBase() < controller.getRenderedFluidLevel(partialTicks == null ? 0f : partialTicks);
        }
        return pipeHeightAboveVatBase() < controller.getFluidLevel();
    }

    /** Used for fluid-stream position calculation in VatSideRenderer + isPipeSubmerged.
*/
    public float pipeHeightAboveVatBase() {
        Optional<Vat> vatOpt = getVatOptional();
        if (vatOpt.isEmpty() || direction == Direction.DOWN) return 0f;
        if (direction == Direction.UP) return vatOpt.get().getInternalHeight();
        return getBlockPos().getY() - vatOpt.get().getInternalLowerCorner().getY() + 4 / 16f;
    }

    
    public Optional<Vat> getVatOptional() {
        VatControllerBlockEntity controller = getController();
        if (controller == null) return Optional.empty();
        return controller.getVatOptional();
    }

    // ============================================================
    // tryMakeVat support methods
    // ============================================================

    /** {@link #setMaterial}/{@link #getMaterial}/{@link #setConsumedItem}
 * are inherited; we only override {@link #setMaterial} to add a self-wrap guard.
*/
    @Override
    public void setMaterial(BlockState blockState) {
        // Guard: Vat side should never wrap itself (tryMakeVat passes the outgoing pre-replaced
        // state, which on re-entry during chunk load could be VAT_SIDE if restart was mid-update).
        if (blockState.is(petrolpark.mc.destroy.DestroyBlocks.VAT_SIDE.get())) return;
        super.setMaterial(blockState);
    }

    /** Ponder world is
 * exempt (type is scripted there).
*/
    @SuppressWarnings("null")
    public void updateDisplayType(BlockPos neighborPos) {
        if (getController() == null) return;
        if (getLevel() instanceof net.createmod.ponder.api.level.PonderLevel) return;

        com.simibubi.create.content.fluids.FluidTransportBehaviour behaviour = BlockEntityBehaviour.get(
            getLevel(), neighborPos, com.simibubi.create.content.fluids.FluidTransportBehaviour.TYPE);
        boolean nextToPipe = false;
        if (behaviour != null) {
            com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes attachment =
                behaviour.getRenderedRimAttachment(getLevel(), neighborPos,
                    behaviour.blockEntity.getBlockState(), direction.getOpposite());
            if (attachment == com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.DRAIN
                || attachment == com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.PARTIAL_DRAIN) {
                nextToPipe = true;
            }
        }
        boolean nextToAir = getLevel().getBlockState(neighborPos).isAir();

        DisplayType oldDisplayType = getDisplayType();
        if (nextToPipe) {
            setDisplayType(DisplayType.PIPE);
        } else if (!nextToAir) {
            if (getDisplayType().showsPressure) setDisplayType(DisplayType.BAROMETER_BLOCKED);
            else if (getDisplayType().showsTemperature) setDisplayType(DisplayType.THERMOMETER_BLOCKED);
            else setDisplayType(DisplayType.NORMAL);
        } else {
            if (getDisplayType() == DisplayType.PIPE) setDisplayType(DisplayType.NORMAL);
            if (getDisplayType() == DisplayType.THERMOMETER_BLOCKED) setDisplayType(DisplayType.THERMOMETER);
            if (getDisplayType() == DisplayType.BAROMETER_BLOCKED) setDisplayType(DisplayType.BAROMETER);
            switch (direction) {
                case UP:
                    if (!getDisplayType().validForTop) setDisplayType(DisplayType.NORMAL);
                    break;
                case DOWN:
                    if (!getDisplayType().validForBottom()) setDisplayType(DisplayType.NORMAL);
                    break;
                default:
                    if (!getDisplayType().validForSide) setDisplayType(DisplayType.NORMAL);
                    break;
            }
        }

        if (getDisplayType() != oldDisplayType) {
            redstoneMonitor.update();
            invalidateRenderBoundingBox();
        }
    }

    /** Transparent-material side
 * cells facing up also receive sky-UV via {@link #getSkyUV} (pollution-modulated when enabled).
*/
    @SuppressWarnings("null")
    public void setPowerFromAdjacentBlock(BlockPos heaterOrLampPos) {
        if (!hasLevel() || getLevel().isClientSide()) return;
        VatControllerBlockEntity vatController = getController();
        if (vatController == null) return;

        float newPower = IVatHeaterBlock.getHeatingPower(getLevel(), heaterOrLampPos, direction.getOpposite());
        if (newPower != oldPower) {
            vatController.changeHeatingPower(newPower - oldPower);
            oldPower = newPower;
        }

        float newUVPower = 0f;
        if (petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial
                .getMaterial(getMaterial())
                .map(petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial::transparent)
                .orElse(false)) {
            newUVPower = petrolpark.mc.destroy.core.chemistry.vat.uv.IUVLampBlock.getUVPower(
                getLevel(), heaterOrLampPos, direction.getOpposite());
            if (newUVPower == 0f && direction == Direction.UP) newUVPower = getSkyUV();
        }
        if (newUVPower != oldUV) {
            vatController.changeUVPower(newUVPower - oldUV);
            oldUV = newUVPower;
        }

        sendData();
    }

    /** When the side receives any
 * redstone signal it closes; when the signal drops, it opens. Called by VatSideBlock
 * neighborChanged.
*/
    @SuppressWarnings("null")
    public void updateRedstoneInput() {
        if (!hasLevel()) return;
        boolean hasPower = getLevel().hasNeighborSignal(getBlockPos());
        if (getDisplayType() == DisplayType.OPEN_VENT && hasPower) setDisplayType(DisplayType.CLOSED_VENT);
        if (getDisplayType() == DisplayType.CLOSED_VENT && !hasPower) setDisplayType(DisplayType.OPEN_VENT);
    }

    /** pressure
 * header + current/max entries via {@code controller.getPressure} / {@code vat.getMaxPressure};
 * fallback to vatFluidTooltip for NORMAL/PIPE sides.
*/
    @Override
    @SuppressWarnings("null")
    public boolean addToGoggleTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        VatControllerBlockEntity controller = getController();
        if (!getVatOptional().isPresent() || controller == null) return false;
        if (getDisplayType().showsTemperature) {
            petrolpark.mc.destroy.client.DestroyLang.TemperatureUnit unit =
                mapConfigTempUnit(petrolpark.mc.destroy.config.DestroyAllConfigs.CLIENT.chemistry.temperatureUnit.get());
            petrolpark.mc.destroy.client.DestroyLang.translate("tooltip.vat.temperature",
                unit.of(controller.getTemperature(), df))
                .style(net.minecraft.ChatFormatting.WHITE).forGoggles(tooltip);
            if (petrolpark.mc.destroy.config.DestroyAllConfigs.CLIENT.chemistry.nerdMode.get()) {
                petrolpark.mc.destroy.client.DestroyLang.translate("tooltip.vat.power",
                    df.format(controller.heatingPower / 1000f)).forGoggles(tooltip);
            }
        } else if (getDisplayType().showsPressure) {
            Vat vat = getVatOptional().get();
            petrolpark.mc.destroy.client.DestroyLang.translate("tooltip.vat.pressure.header")
                .style(net.minecraft.ChatFormatting.WHITE).forGoggles(tooltip);
            petrolpark.mc.destroy.client.DestroyLang.translate("tooltip.vat.pressure.current",
                df.format(controller.getPressure() / 1000f))
                .style(net.minecraft.ChatFormatting.GRAY).forGoggles(tooltip, 1);
            petrolpark.mc.destroy.client.DestroyLang.translate("tooltip.vat.pressure.max",
                df.format(vat.getMaxPressure() / 1000f),
                vat.getWeakestBlock().getBlock().getName().getString())
                .style(net.minecraft.ChatFormatting.GRAY).forGoggles(tooltip, 1);
        } else {
            VatControllerBlockEntity.vatFluidTooltip(controller, tooltip);
        }
        return true;
    }

    /** PIPE-display-type side shows a
 * "pipe not submerged" warning tooltip when the adjacent pipe is above the fluid surface.
 * Returns false (doesn't fully override the default block overlay).
*/
    @Override
    public boolean addToTooltip(java.util.List<net.minecraft.network.chat.Component> tooltip, boolean isPlayerSneaking) {
        if (getDisplayType() == DisplayType.PIPE && !isPipeSubmerged(false, null)) {
            petrolpark.mc.destroy.client.DestroyLang.translate("tooltip.vat.not_submerged.header")
                .style(net.minecraft.ChatFormatting.GOLD).forGoggles(tooltip);
            com.simibubi.create.foundation.item.TooltipHelper.cutTextComponent(
                petrolpark.mc.destroy.client.DestroyLang.translate("tooltip.vat.not_submerged").component(),
                net.createmod.catnip.lang.FontHelper.Palette.GRAY_AND_WHITE
            ).forEach(component ->
                petrolpark.mc.destroy.client.DestroyLang.builder().add(component.copy()).forGoggles(tooltip));
            tooltip.add(net.minecraft.network.chat.Component.literal(""));
        }
        return false;
    }

    /** Same pattern as
 * MoleculeDisplayItem.mapConfigUnit. The two TemperatureUnit enums stay separate until the
 * config and DestroyLang variants are unified (see the TODO in DestroyClientChemistryConfigs).
*/
    private static petrolpark.mc.destroy.client.DestroyLang.TemperatureUnit mapConfigTempUnit(
            petrolpark.mc.destroy.config.DestroyClientChemistryConfigs.TemperatureUnit c) {
        return switch (c) {
            case KELVIN -> petrolpark.mc.destroy.client.DestroyLang.TemperatureUnit.KELVINS;
            case DEGREES_CELCIUS -> petrolpark.mc.destroy.client.DestroyLang.TemperatureUnit.DEGREES_CELCIUS;
            case DEGREES_FAHRENHEIT -> petrolpark.mc.destroy.client.DestroyLang.TemperatureUnit.DEGREES_FARENHEIT;
        };
    }

    /** Ozone
 * depletion scales it up when pollution tracking is on (amplifies solar UV as pollution
 * increases).
*/
    @SuppressWarnings("null")
    public float getSkyUV() {
        if (!getLevel().canSeeSky(getBlockPos())) return 0f;
        float uvPower = 10f;
        if (petrolpark.mc.destroy.core.pollution.PollutionHelper.isPollutionEnabled()
            && petrolpark.mc.destroy.config.DestroyAllConfigs.SERVER.pollution.vatUVPowerAffected.get()) {
            uvPower += 20f * petrolpark.mc.destroy.core.pollution.PollutionHelper.getPollutionProportion(
                getLevel(),
                petrolpark.mc.destroy.DestroyPollutionTypes.OZONE_DEPLETION.get());
        }
        return uvPower;
    }

    /** No-op; capabilities are resolved per-query in 1.21, so callers in
 * {@link VatControllerBlockEntity#tryMakeVat} have nothing to refresh.
*/
    public void refreshFluidCapability() {
    }

    
    public void refreshItemCapability() {
        // No-op.
    }

    /** Invalidate the cached render bounding box (CopycatBlockEntity base). No-op here —
 * the SmartBlockEntity base has no stale bounding box to invalidate.*/
    public void invalidateRenderBoundingBox() {
        // No-op.
    }

    /** Each variant encodes validity for placement on
 * top/side/bottom, whether it's a vent (open/closed), whether it displays pressure/temperature,
 * and the {@link Function} to observe the gauged quantity from the
 * {@link VatControllerBlockEntity} (used by the redstone monitor + tooltip).
*/
    public static enum DisplayType {

        NORMAL(true, true, false, false, false, Optional.empty(), VatSideBlockEntity::noQuantityLabel),
        BAROMETER(false, true, false, true, false, Optional.of(VatControllerBlockEntity::getPressure), VatSideBlockEntity::pressureQuantityLabel),
        BAROMETER_BLOCKED(false, true, false, true, false, Optional.of(VatControllerBlockEntity::getPressure), VatSideBlockEntity::pressureQuantityLabel),
        THERMOMETER(false, true, false, false, true, Optional.of(VatControllerBlockEntity::getTemperature), VatSideBlockEntity::temperatureQuantityLabel),
        THERMOMETER_BLOCKED(false, true, false, false, true, Optional.of(VatControllerBlockEntity::getTemperature), VatSideBlockEntity::temperatureQuantityLabel),
        PIPE(true, true, false, false, false, Optional.empty(), VatSideBlockEntity::noQuantityLabel),
        CLOSED_VENT(true, false, true, false, false, Optional.empty(), VatSideBlockEntity::noQuantityLabel),
        OPEN_VENT(true, false, true, false, false, Optional.empty(), VatSideBlockEntity::noQuantityLabel);

        public final boolean validForTop, validForSide, isVent, showsPressure, showsTemperature;
        
        public final Optional<Function<VatControllerBlockEntity, Float>> quantityObserved;
        /** NORMAL / PIPE / VENT return empty.*/
        public final Function<Float, Component> quantityLabel;

        private DisplayType(boolean validForTop, boolean validForSide, boolean isVent, boolean showsPressure, boolean showsTemperature,
                            Optional<Function<VatControllerBlockEntity, Float>> quantityObserved,
                            Function<Float, Component> quantityLabel) {
            this.validForTop = validForTop;
            this.validForSide = validForSide;
            this.isVent = isVent;
            this.showsPressure = showsPressure;
            this.showsTemperature = showsTemperature;
            this.quantityObserved = quantityObserved;
            this.quantityLabel = quantityLabel;
        }

        public boolean validForBottom() {
            return validForTop && !isVent;
        }
    }
}
