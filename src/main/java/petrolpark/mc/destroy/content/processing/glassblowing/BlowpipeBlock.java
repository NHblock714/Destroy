package petrolpark.mc.destroy.content.processing.glassblowing;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.petrolpark.compat.create.core.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import com.simibubi.create.AllBlockEntityTypes;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.foundation.block.IBE;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyVoxelShapes;
import petrolpark.mc.destroy.core.block.IPickUpPutDownBlock;

/**
 * The vertical glass-blowing pipe block — a single-voxel block rendered as a thin vertical shaft
 * which holds molten glass and can be "blown" by an encased fan pointed at its top face. Also
 * placeable and retrievable as a {@link BlowpipeItem} carrying its blowing-state NBT with it.
*/
public class BlowpipeBlock extends DirectionalBlock implements IBE<BlowpipeBlockEntity>, IPickUpPutDownBlock {

    public static final MapCodec<BlowpipeBlock> CODEC = simpleCodec(BlowpipeBlock::new);

    public BlowpipeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<BlowpipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> pBuilder) {
        super.createBlockStateDefinition(pBuilder);
        pBuilder.add(FACING);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        AbstractRememberPlacerBehaviour.setPlacedBy(level, pos, placer);
        // restore blowing-state from stack's typed DataComponents via readFromItem.
        withBlockEntityDo(level, pos, be -> be.readFromItem(stack));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        if (context.getPlayer() instanceof DeployerFakePlayer deployer) {
            DeployerBlockEntity be = getDeployerPlacer(context.getLevel(), deployer);
            if (be != null) facing = Direction.get(be.getSpeed() < 0f ? AxisDirection.NEGATIVE : AxisDirection.POSITIVE, AllBlocks.DEPLOYER.get().getRotationAxis(be.getBlockState()));
        }
        return super.getStateForPlacement(context).setValue(FACING, facing);
    }

    /**
 * Locate the {@link DeployerBlockEntity} that just tried to place this Blowpipe via its
 * {@link DeployerFakePlayer}. Scans all 6 neighbors-of-neighbors (offset 2) of the fake
 * player's position — Deployer's "hand" position is offset by 2 blocks from the BE.
*/
    public static DeployerBlockEntity getDeployerPlacer(Level level, DeployerFakePlayer player) {
        for (Direction direction : Iterate.directions) {
            BlockPos pos = player.blockPosition().relative(direction, 2);
            Optional<DeployerBlockEntity> op = level.getBlockEntity(pos, AllBlockEntityTypes.DEPLOYER.get());
            if (op.filter(be -> be.getPlayer() == player).isPresent()) {
                if (op.get().getLevel() == level) return op.get();
                break;
            }
        }
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DestroyVoxelShapes.BLOWPIPE.get(state.getValue(FACING).getAxis());
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos, DestroyBlockEntityTypes.BLOWPIPE.get()).map(b -> b.luminosity).orElse(super.getLightEmission(state, level, pos));
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        return Collections.singletonList(getItemStack(be));
    }

    /**
 * 1.21 Block.getCloneItemStack signature: 3-arg {@code (LevelReader, BlockPos, BlockState)}
 *.
*/
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BlockEntity be = level instanceof Level l ? l.getBlockEntity(pos) : null;
        return getItemStack(be);
    }

    /**
 * Build a Blowpipe {@link ItemStack} carrying this BE's blowing state.
*/
    public ItemStack getItemStack(@Nullable BlockEntity be) {
        ItemStack stack = DestroyBlocks.BLOWPIPE.asStack();
        if (be instanceof BlowpipeBlockEntity blowpipe) blowpipe.writeToItem(stack);
        return stack;
    }

    @Override
    public Class<BlowpipeBlockEntity> getBlockEntityClass() {
        return BlowpipeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BlowpipeBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.BLOWPIPE.get();
    }
}
