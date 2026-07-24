package petrolpark.mc.destroy.content.processing.distillation;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import petrolpark.mc.library.compat.create.core.world.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyVoxelShapes;

/**
 * Bubble Cap block — stacks vertically to form a Distillation Tower. Two blockstate flags (TOP /
 * BOTTOM) track the block's position in the tower via neighbor-aware
 * {@link #stateForPositionInTower}. {@link Block} + {@link IBE}<{@link BubbleCapBlockEntity}> +
 * {@link IWrenchable} (default PASS wrench — handled via tower-level logic).
 *
 * <p>BubbleCapBlockEntity is a stub — createOrAddToTower + getLuminosity are no-ops pending the
 * full BE + DistillationTower implementation. Block registration + BE type registration complete;
 * recipe processing deferred.</p>
*/
public class BubbleCapBlock extends Block implements IBE<BubbleCapBlockEntity>, IWrenchable {

    public static final MapCodec<BubbleCapBlock> CODEC = simpleCodec(BubbleCapBlock::new);

    public static final BooleanProperty TOP = BooleanProperty.create("top"); // Whether this Bubble Cap is at the top of a Distillation Tower
    public static final BooleanProperty BOTTOM = BooleanProperty.create("bottom"); // Whether this Bubble Cap is at the bottom of a Distillation Tower

    public BubbleCapBlock(Properties pProperties) {
        super(pProperties);
        registerDefaultState(defaultBlockState()
            .setValue(TOP, true)
            .setValue(BOTTOM, true)
        );
    }

    @Override
    protected MapCodec<BubbleCapBlock> codec() {
        return CODEC;
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (oldState.getBlock() == state.getBlock() || isMoving) return; // Guard against infinite loop if createOrAddToTower writes the state
        withBlockEntityDo(level, pos, BubbleCapBlockEntity::createOrAddToTower);
        level.setBlock(pos, stateForPositionInTower(level, pos), 3);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(TOP, BOTTOM);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        BlockPos posAbove = pos.above();
        boolean isAbove = posAbove.getY() == neighbor.getY();
        boolean isBelow = pos.getY() - 1 == neighbor.getY();
        if (isAbove || isBelow) {
            withBlockEntityDo(level, pos, BubbleCapBlockEntity::createOrAddToTower);
        }
        BlockState stateAbove = level.getBlockState(posAbove);
        if (isBelow && stateAbove.is(DestroyBlocks.BUBBLE_CAP.get())) {
            stateAbove.onNeighborChange(level, posAbove, pos);
        }
        super.onNeighborChange(state, level, pos, neighbor);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor levelAccessor, BlockPos pos, BlockPos neighborPos) {
        return stateForPositionInTower(levelAccessor, pos);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        AbstractRememberPlacerBehaviour.setPlacedBy(level, pos, placer);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BubbleCapBlockEntity bubbleCapBE) {
            return bubbleCapBE.getLuminosity();
        }
        return 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DestroyVoxelShapes.bubbleCap(state.getValue(BOTTOM), state.getValue(TOP));
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, worldIn, pos, newState);
    }

    /**
 * Returns the appropriate Block State with TOP/BOTTOM flags set based on whether the adjacent
 * vertical blocks are also Bubble Caps.
*/
    public BlockState stateForPositionInTower(LevelReader level, BlockPos pos) {
        return defaultBlockState()
            .setValue(TOP, !level.getBlockState(pos.above()).is(DestroyBlocks.BUBBLE_CAP.get()))
            .setValue(BOTTOM, !level.getBlockState(pos.below()).is(DestroyBlocks.BUBBLE_CAP.get()));
    }

    @Override
    public Class<BubbleCapBlockEntity> getBlockEntityClass() {
        return BubbleCapBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BubbleCapBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.BUBBLE_CAP.get();
    }

}
