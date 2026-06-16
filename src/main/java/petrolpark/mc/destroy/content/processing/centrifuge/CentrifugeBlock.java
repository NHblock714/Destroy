package petrolpark.mc.destroy.content.processing.centrifuge;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.petrolpark.compat.create.core.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyVoxelShapes;

/**
 * The Centrifuge block — kinetic block that spins a Y-axis cog to separate a fluid mixture into a
 * "dense" output (sent out one horizontal face — {@link #DENSE_OUTPUT_FACE}) + a "light" output
 * (sent out the opposite face). Flat 4-voxel-tall slab ({@link DestroyVoxelShapes#CENTRIFUGE}).
 *
 * <p>Blockstate: {@link #DENSE_OUTPUT_FACE} {@link DirectionProperty} (HORIZONTAL_FACING shared).
 * Default WEST. Auto-rotation via {@link CentrifugeBlockEntity#attemptRotation(boolean)};
 * the auto-orientation logic lives in the block entity.</p>
*/
public class CentrifugeBlock extends KineticBlock implements IBE<CentrifugeBlockEntity>, ICogWheel {

    public static final MapCodec<CentrifugeBlock> CODEC = simpleCodec(CentrifugeBlock::new);

    public static final DirectionProperty DENSE_OUTPUT_FACE = BlockStateProperties.HORIZONTAL_FACING;

    public CentrifugeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DENSE_OUTPUT_FACE, Direction.WEST));
    }

    @Override
    protected MapCodec<CentrifugeBlock> codec() {
        return CODEC;
    }

    @Override
    public void onPlace(BlockState state, Level worldIn, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, worldIn, pos, oldState, isMoving);
        if (oldState.getBlock() == state.getBlock() || isMoving) return; // Guard against infinite loop if attemptRotation writes the block state
        withBlockEntityDo(worldIn, pos, be -> be.attemptRotation(false));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DestroyVoxelShapes.CENTRIFUGE;
    }

    @Override
    public void onRemove(BlockState state, Level worldIn, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, worldIn, pos, newState);
    }

    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        withBlockEntityDo(level, pos, be -> be.attemptRotation(false));
        super.onNeighborChange(state, level, pos, neighbor);
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(DENSE_OUTPUT_FACE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return Axis.Y;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        if (!context.getLevel().isClientSide()) {
            CentrifugeBlockEntity be = getBlockEntity(context.getLevel(), context.getClickedPos());
            if (be == null) return InteractionResult.PASS;
            if (be.attemptRotation(true)) {
                IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
                updateAfterWrenched(state, context);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        AbstractRememberPlacerBehaviour.setPlacedBy(level, pos, placer);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public Class<CentrifugeBlockEntity> getBlockEntityClass() {
        return CentrifugeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CentrifugeBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.CENTRIFUGE.get();
    }

    
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

}
