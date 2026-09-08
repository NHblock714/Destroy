package petrolpark.mc.destroy.content.logistics.creativepump;

import java.util.List;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Creative Pump BE — infinite-source pump whose speed is player-settable via a scroll-value
 * behaviour, rather than by a kinetic network.
 *
 * <p>{@link #getSpeed()} reports {@link #simulatedSpeed}, but the inherited KineticBlockEntity
 * speed field stays at 0 because there is no kinetic source. Nothing ever fires
 * {@code onSpeedChanged}, and that is the hook a vanilla pump relies on to run
 * {@code updatePressureChange} and push pressure into the adjacent pipes — so without help this
 * pump never establishes a flow at all.</p>
 *
 * <p>Scrolling the speed does reach {@code updatePressureChange} through the behaviour's callback,
 * which is why a pump whose speed has been touched works. The default value, though, is set during
 * construction while {@code level} is still null, and the callback skips the refresh in that case.
 * The pressure refresh is therefore driven from {@link #initialize()} for placement, and from the
 * neighbour watch in {@link #tick()} for later topology changes.</p>
*/
public class CreativePumpBlockEntity extends PumpBlockEntity {

    public ScrollValueBehaviour pumpSpeedBehaviour;
    protected int simulatedSpeed = 16;

    /** Each tick, compare current
 * front+back adjacent states to these snapshots; on change, force {@link #updatePressureChange()}
 * so the pump's network state gets re-derived from current world topology instead of the
 * cached state from when flow was first established.
 *
 * <p>BlockState instances are interned in Minecraft (same state value = same reference), so
 * reference inequality (!=) is reliable for state-change detection.</p>
 *
 * <p>Initialized to {@code null} sentinels; first tick after BE attach captures the initial
 * snapshot without firing update (which would conflict with {@link #initialize()}'s already-
 * scheduled updatePressureChange).</p>
*/
    private BlockState lastFrontState;
    private BlockState lastBackState;
    private boolean stateTrackerInitialized = false;

    public CreativePumpBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
        pumpSpeedBehaviour = new ScrollValueBehaviour(Component.translatable("block.destroy.creative_pump.speed"), this, new CreativePumpValueSlot()) {
            @Override
            public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
                return new ValueSettingsBoard(label, max, 16, ImmutableList.of(Component.literal("\u2192").withStyle(ChatFormatting.BOLD)), new ValueSettingsFormatter(this::formatSettings));
            }

            public MutableComponent formatSettings(ValueSettings settings) {
                return CreateLang.number(Math.max(1, settings.value())).component();
            }
        }
            .between(1, AllConfigs.server().kinetics.maxRotationSpeed.get())
            .withCallback(i -> {
                simulatedSpeed = i;
                if (level != null && !level.isClientSide)
                    updatePressureChange();
            });
        pumpSpeedBehaviour.setValue(16);
        behaviours.add(pumpSpeedBehaviour);
    }

    @Override
    public float getSpeed() {
        return simulatedSpeed;
    }

    /** Triggers {@link PumpBlockEntity#updatePressureChange} on the
 * first {@link com.simibubi.create.foundation.blockEntity.SmartBlockEntity#tick} (when level
 * is set and BE is fully wired). This emulates what vanilla {@link PumpBlockEntity} does in
 * its {@code onSpeedChanged} → {@code updatePressureChange} chain when the kinetic network
 * first delivers non-zero speed; for Creative Pump kinetic speed stays 0 forever, so we have
 * to drive this manually.
 *
 * <p>{@code updatePressureChange} does three things at once:</p>
 * <ol>
 * <li>Calls {@link com.simibubi.create.content.fluids.FluidPropagator#propagateChangedPipe}
 * on the front + back adjacent positions — wipes adjacent pipe pressures, walks the
 * pipe network discovering pumps and notifies them.</li>
 * <li>Calls {@code wipePressure} on this pump's own behaviour — clears stale pipe
 * connections / sources / networks if any survived the BE construction.</li>
 * <li>Sets BOTH {@code sidesToUpdate} flags to true → next tick's {@code distributePressureTo}
 * fires for both sides → adjacent pipes get pressure.</li>
 * </ol>
*/
    @Override
    public void initialize() {
        super.initialize();
        if (level != null && !level.isClientSide) {
            updatePressureChange();
        }
    }

    /**
 * Watches the two adjacent positions along the pump's axis and calls {@link #updatePressureChange}
 * whenever either BlockState changes. A vanilla pump reaches that path through the speed pulse in
 * {@code onSpeedChanged}; this pump runs at a constant simulated speed and never pulses, so the
 * refresh has to be driven from here instead.
 *
 * <p>{@code updatePressureChange} does what a vanilla pump does on a speed change:
 * {@code propagateChangedPipe} on both sides, {@code wipePressure} on this pump's behaviour, and
 * both {@code sidesToUpdate} flags set. That clears the network's targets — including any stale
 * OpenEndedPipe references — redistributes pressure to the new neighbours, and lets the next tick
 * rebuild the topology from scratch.</p>
 *
 * <p>BlockStates are interned, so reference inequality is a sound change test and cheaper than
 * {@code equals}. The cost is two {@code getBlockState} calls per tick; the refresh itself only
 * runs when something actually changed.</p>
*/
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide) return;

        Direction front = getFront();
        if (front == null) return;

        BlockState currentFront = level.getBlockState(worldPosition.relative(front));
        BlockState currentBack = level.getBlockState(worldPosition.relative(front.getOpposite()));

        if (!stateTrackerInitialized) {
            // First tick after BE attach: capture initial snapshot, don't fire (initialize()
            // already calls updatePressureChange).
            lastFrontState = currentFront;
            lastBackState = currentBack;
            stateTrackerInitialized = true;
            return;
        }

        // BlockState instances are interned — reference inequality detects any state change.
        if (currentFront != lastFrontState || currentBack != lastBackState) {
            lastFrontState = currentFront;
            lastBackState = currentBack;
            updatePressureChange();
        }
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        simulatedSpeed = compound.getInt("SimulatedSpeed");
        pumpSpeedBehaviour.setValue(simulatedSpeed);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.putInt("SimulatedSpeed", simulatedSpeed);
    }

    public class CreativePumpValueSlot extends ValueBoxTransform.Sided {

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return state.getValue(CreativePumpBlock.FACING).getAxis() != direction.getAxis();
        }

        @Override
        protected Vec3 getSouthLocation() {
            // The pump body is (3,0,3,13,16,13), so its visible surface sits at z=13. The
            // inherited 12.5 puts the value box inside the model and the faces occlude it, while
            // the 15.5 used by full-block machines leaves it visibly floating off the narrower
            // pump. 13.5 sits flush against the surface.
            return VecHelper.voxelSpace(8d, 8d, 13.5d);
        }
    }
}
