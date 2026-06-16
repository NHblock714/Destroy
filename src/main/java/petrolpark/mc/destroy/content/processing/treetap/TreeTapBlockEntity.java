package petrolpark.mc.destroy.content.processing.treetap;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.simibubi.create.AllTags.AllBlockTags;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.config.DestroyConfigs;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;
import petrolpark.mc.destroy.core.fluid.GeniusFluidTankBehaviour;

/**
 * Tree Tap BE — kinetic block-breaker variant specialized for tapping wood. Every few ticks of
 * rotation it "breaks" the wood in front of it, consumes nothing (mixin prevents drop), and fills
 * the internal tank with the tapping's result fluid (latex). Awards TAP_TREE advancement on each
 * successful tapping.
*/
public class TreeTapBlockEntity extends KineticBlockEntity {

    // fields copied from BlockBreakingKineticBlockEntity (this class no longer extends it).
    public static final AtomicInteger NEXT_BREAKER_ID = new AtomicInteger();
    protected int ticksUntilNextProgress;
    protected int destroyProgress;
    protected int breakerId = -NEXT_BREAKER_ID.incrementAndGet();
    protected BlockPos breakingPos;

    public GeniusFluidTankBehaviour tank;

    protected DestroyAdvancementBehaviour advancementBehaviour;

    protected BlockTapping currentTapping;

    public TreeTapBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    public void destroyNextTick() {
        ticksUntilNextProgress = 1;
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);

        // Kick on speed-resume from 0, so the tap doesn't get permanently stuck if its
        // {@code ticksUntilNextProgress} state was inconsistent with the new kinetic state.
        // <p>Symptom: with the tap mid-progress, stopping stress then resuming it leaves the tap
        // permanently unable to break the target; replacing the tap (fresh BE) fixes it.</p>
        // <p>Hypothesis: when stress goes to 0, tick() early-returns at the speed check and
        // {@code ticksUntilNextProgress} freezes at whatever value it was. When stress resumes,
        // tick decrements normally but the post-break-logic recompute
        // {@code ticksUntilNextProgress = blockHardness / breakSpeed} can produce a HUGE value if
        // {@code breakSpeed} is microscopic during a transient mid-network-update read, plus other
        // edge cases that are hard to reproduce in code review.</p>
        // <p>Defensive: on every 0→non-zero speed transition, reset
        // {@code ticksUntilNextProgress} to 0 so the next tick falls through into break logic.
        // The fallthrough either:
        // <ul>
        // <li>fires {@code canBreak} → if true, +1 progress (one-time on resume; acceptable),
        // then sets {@code ticksUntilNextProgress} from a freshly-computed {@code breakSpeed}</li>
        // <li>fires {@code canBreak} → if false (target is air / non-tappable), resets
        // {@code destroyProgress} to 0 and clears visual</li>
        // </ul>
        // Either way, the BE exits the stuck state.</p>
        // <p>Note: this avoids the existing {@link #destroyNextTick()} (which sets to 1) because
        // fallthrough is wanted on the very next tick, not after one decrement-cycle. Functionally
        // negligible difference (1 tick = 50ms) but cleaner intent.</p>
        if (prevSpeed == 0 && getSpeed() != 0) {
            ticksUntilNextProgress = 0;
        }

        if (destroyProgress == -1)
            destroyNextTick();
    }

    @Override
    public void lazyTick() {
        super.lazyTick();
        if (ticksUntilNextProgress == -1)
            destroyNextTick();
    }

    /**
 * The block this tap targets — one block in the FACING direction, then up one (the trunk
 * block above the tap's mount point). Since this class no longer extends
 * BlockBreakingKineticBlockEntity, Sable's @Redirect doesn't apply, so this position is
 * actually used by the local tick loop.
*/
    protected BlockPos getBreakingPos() {
        return getBlockPos().relative(getBlockState().getValue(TreeTapBlock.FACING)).above();
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt("Progress", destroyProgress);
        compound.putInt("NextTick", ticksUntilNextProgress);
        if (breakingPos != null)
            compound.put("Breaking", NbtUtils.writeBlockPos(breakingPos));
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        destroyProgress = compound.getInt("Progress");
        ticksUntilNextProgress = compound.getInt("NextTick");
        breakingPos = null;
        if (compound.contains("Breaking"))
            breakingPos = NBTHelper.readBlockPos(compound, "Breaking");
        super.read(compound, registries, clientPacket);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (level != null && !level.isClientSide && destroyProgress != 0 && breakingPos != null)
            level.destroyBlockProgress(breakerId, breakingPos, -1);
    }

    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) return;
        if (getSpeed() == 0) return;

        breakingPos = getBreakingPos();

        if (ticksUntilNextProgress < 0) return;
        if (ticksUntilNextProgress-- > 0) return;

        BlockState stateToBreak = level.getBlockState(breakingPos);
        float blockHardness = stateToBreak.getDestroySpeed(level, breakingPos);

        if (!canBreak(stateToBreak, blockHardness)) {
            if (destroyProgress != 0) {
                destroyProgress = 0;
                level.destroyBlockProgress(breakerId, breakingPos, -1);
            }
            return;
        }

        float breakSpeed = getBreakSpeed();
        destroyProgress += Mth.clamp((int) (breakSpeed / blockHardness), 1, 10 - destroyProgress);
        level.playSound(null, worldPosition, stateToBreak.getSoundType().getHitSound(),
            SoundSource.BLOCKS, .25f, 1);

        if (destroyProgress >= 10) {
            onBlockBroken(stateToBreak);
            destroyProgress = 0;
            ticksUntilNextProgress = -1;
            level.destroyBlockProgress(breakerId, breakingPos, -1);
            return;
        }

        ticksUntilNextProgress = (int) (blockHardness / breakSpeed);
        level.destroyBlockProgress(breakerId, breakingPos, (int) destroyProgress);
    }

    /**
 * Inlines {@code BlockBreakingKineticBlockEntity.canBreak} + {@code isBreakable}, then layers
 * TreeTap-specific tappable check. Returns true only if the target is a tappable wood and the
 * tank has space for the resulting fluid.
*/
    public boolean canBreak(BlockState stateToBreak, float blockHardness) {
        // From BBKBE.isBreakable
        if (stateToBreak.liquid()) return false;
        if (stateToBreak.getBlock() instanceof AirBlock) return false;
        if (blockHardness == -1) return false;
        if (AllBlockTags.NON_BREAKABLE.matches(stateToBreak)) return false;
        // TreeTap-specific
        if (currentTapping == null || !currentTapping.tappable.test(stateToBreak)) {
            currentTapping = null;
            for (BlockTapping tapping : BlockTapping.ALL_TAPPINGS) {
                if (tapping.tappable.test(stateToBreak)) currentTapping = tapping;
                break;
            }
        }
        return currentTapping != null && tank.getPrimaryHandler().fill(currentTapping.result, FluidAction.SIMULATE) > 0;
    }

    public void onBlockBroken(BlockState stateToBreak) {
        BlockHelper.destroyBlock(level, breakingPos, 1f, stack -> {}); // don't drop items
        tank.getPrimaryHandler().fill(currentTapping.result, FluidAction.EXECUTE);
        advancementBehaviour.awardDestroyAdvancement(DestroyAdvancementTrigger.TAP_TREE);
    }

    /** Inlined: {@code Math.abs(getSpeed() / 3200f)}.
*/
    protected float getBreakSpeed() {
        return Math.abs(getSpeed() / 3200f);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        tank = new GeniusFluidTankBehaviour(GeniusFluidTankBehaviour.TYPE, this, 1, getCapacity(), false);
        tank.forbidInsertion();
        behaviours.add(tank);

        advancementBehaviour = new DestroyAdvancementBehaviour(this, DestroyAdvancementTrigger.TAP_TREE);
        behaviours.add(advancementBehaviour);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return containedFluidTooltip(tooltip, isPlayerSneaking, tank.getCapability());
    }

    /**
 * Capability registration: fluid handler accessible from the face opposite the pump's
 * FACING (i.e. downstream fluid consumers plug into the "back" of the tap).
*/
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            DestroyBlockEntityTypes.TREE_TAP.get(),
            (be, context) -> {
                if (context == null) return be.tank.getCapability();
                Direction facing = be.getBlockState().getValue(TreeTapBlock.FACING);
                return context == facing.getOpposite() ? be.tank.getCapability() : null;
            });
    }

    public int getCapacity() {
        return DestroyConfigs.server().blocks.treeTapCapacity.get();
    }
}
