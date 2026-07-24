package petrolpark.mc.destroy.content.processing.treetap;

import com.mojang.serialization.MapCodec;
import petrolpark.mc.library.compat.create.core.world.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyVoxelShapes;

/**
 * Tree Tap — a {@link DirectionalKineticBlock} that attaches to a tappable wood block on its
 * facing direction and slowly drips fluid (currently latex) into an internal tank.
*/
public class TreeTapBlock extends DirectionalKineticBlock implements IBE<TreeTapBlockEntity>, ProperWaterloggedBlock {

    public static final MapCodec<TreeTapBlock> CODEC = simpleCodec(TreeTapBlock::new);

    public TreeTapBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<TreeTapBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DestroyVoxelShapes.TREE_TAP;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor world, BlockPos pos, BlockPos neighbourPos) {
        updateWater(world, state, pos);
        return state;
    }

    /**
 *
 * <p>The original Create-style trigger fires {@code destroyNextTick()} on **every** neighbor
 * change, which produced two unwanted side-effects (stopping then resuming stress added a
 * spurious break-progress crack to the tapped log, and repeatedly updating the block below
 * the tap accumulated break progress on the working block):</p>
 * <ol>
 * <li>Stress shaft cogwheel state changes (Speed BlockState property) trigger neighborChanged
 * on the TreeTap → destroyNextTick → spurious +1 progress on stop+resume cycles.</li>
 * <li>Manually updating any block adjacent to the TreeTap (e.g. the block at {@code
 * pos.relative(FACING)} — directly between the tap and the trunk-above-tap that's the
 * actual breakingPos) triggers neighborChanged → +1 progress per update.</li>
 * </ol>
 *
 * <p>{@link TreeTapBlockEntity#lazyTick} already handles recovery (resets ticksUntilNextProgress
 * when it hits -1 after a successful break), so neighborChanged-driven recovery is redundant.
 * Trade-off: when the trunk above the tap is broken externally (e.g. player chops it), tap
 * takes up to 20 ticks (~1 s) to detect via lazyTick instead of instantly. Acceptable.</p>
*/
    @Override
    protected void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        // intentionally no-op — see Javadoc above
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // constrain to horizontal even though the FACING property accepts up/down.
        // Sable mixin reads BlockStateProperties.FACING and horizontal semantics are required for
        // the tap to function (it taps the side of a tree).
        return withWater(defaultBlockState().setValue(FACING, context.getHorizontalDirection()), context);
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(world, pos, state, placer, stack);
        AbstractRememberPlacerBehaviour.setPlacedBy(world, pos, placer);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() != Axis.Y && face.getAxis() != state.getValue(FACING).getAxis();
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getClockWise().getAxis();
    }

    @Override
    public Class<TreeTapBlockEntity> getBlockEntityClass() {
        return TreeTapBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TreeTapBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.TREE_TAP.get();
    }
}
