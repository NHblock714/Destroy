package petrolpark.mc.destroy.core.explosion;

import static petrolpark.mc.library.compat.create.PetrolparkCreateClient.OUTLINER;

import java.util.Arrays;
import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.config.DestroyAllConfigs;
import petrolpark.mc.destroy.core.block.entity.ISpecialWhenHoveredBlockEntity;
import petrolpark.mc.destroy.core.bettervaluesettings.SidedScrollValueBehaviour;

/**
 * Per-side excavation-radius BlockEntity for {@link DynamiteBlock}. Stores a 6-int array (one per
 * face) via {@link SidedScrollValueBehaviour} that the player wrench-scrolls to size the
 * asymmetric excavation AABB. Hover-renders the AABB as a red outline so the player can
 * preview the blast area.
*/
public class DynamiteBlockEntity extends SmartBlockEntity implements ISpecialWhenHoveredBlockEntity {

    public BlockPos excavationAreaUpperCorner;
    public BlockPos excavationAreaLowerCorner;
    protected SidedScrollValueBehaviour scrollValueBehaviour;

    private int initializationTicks;

    public DynamiteBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        excavationAreaLowerCorner = getBlockPos();
        excavationAreaUpperCorner = getBlockPos();
        initializationTicks = 3;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        scrollValueBehaviour = new SidedScrollValueBehaviour(
                DestroyLang.translate("tooltip.dynamite.excavation_radius").component(),
                this, new DynamiteValueBox())
            .between(0, DestroyAllConfigs.SERVER.blocks.dynamiteMaxRadius.get())
            .oppositeSides()
            .withCallback((d, i) -> updateExcavationArea());
        Arrays.fill(scrollValueBehaviour.values, 2); // Default radius per face
        behaviours.add(scrollValueBehaviour);

        updateExcavationArea();
    }

    @Override
    public void tick() {
        super.tick();
        if (initializationTicks > 0) {
            initializationTicks--;
            if (initializationTicks == 1) updateExcavationArea();
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        excavationAreaUpperCorner = NbtUtils.readBlockPos(tag, "UpperCorner").orElseGet(this::getBlockPos);
        excavationAreaLowerCorner = NbtUtils.readBlockPos(tag, "LowerCorner").orElseGet(this::getBlockPos);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("UpperCorner", NbtUtils.writeBlockPos(excavationAreaUpperCorner));
        tag.put("LowerCorner", NbtUtils.writeBlockPos(excavationAreaLowerCorner));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void whenLookedAt(LocalPlayer player, BlockHitResult blockHitResult) {
        // 1.21 — `new AABB(BlockPos, BlockPos)` ctor removed; build from corner coords directly.
        BlockPos lo = excavationAreaLowerCorner;
        BlockPos hi = excavationAreaUpperCorner;
        AABB area = new AABB(lo.getX(), lo.getY(), lo.getZ(),
            hi.getX() + 1, hi.getY() + 1, hi.getZ() + 1);
        OUTLINER.chaseAABB(Pair.of("excavationArea", getBlockPos()), area).colored(0xFF_d80051);
    }

    private void updateExcavationArea() {
        excavationAreaLowerCorner = getBlockPos();
        excavationAreaUpperCorner = getBlockPos();
        int[] values = scrollValueBehaviour.values;
        for (Direction direction : Direction.values()) {
            if (direction.getAxisDirection() == AxisDirection.POSITIVE) {
                excavationAreaUpperCorner = excavationAreaUpperCorner.relative(direction,
                    values[direction.getOpposite().ordinal()] + 1);
            } else {
                excavationAreaLowerCorner = excavationAreaLowerCorner.relative(direction,
                    values[direction.getOpposite().ordinal()]);
            }
        }
        sendData();
    }

    /** Front-face value box positioning (south-facing voxel anchor).*/
    protected class DynamiteValueBox extends ValueBoxTransform.Sided {
        @Override
        protected Vec3 getSouthLocation() {
            return VecHelper.voxelSpace(8, 8, 15.5);
        }
    }
}
