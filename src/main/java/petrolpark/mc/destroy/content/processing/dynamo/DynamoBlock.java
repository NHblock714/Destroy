package petrolpark.mc.destroy.content.processing.dynamo;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import petrolpark.mc.library.compat.create.core.world.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import petrolpark.mc.library.core.data.recipe.ingredient.BlockIngredient;
import petrolpark.mc.library.core.data.recipe.ingredient.BlockIngredient.BlockTagIngredient;
import com.simibubi.create.api.contraption.BlockMovementChecks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.kinetics.base.KineticBlock;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.Lazy;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyTags;
import petrolpark.mc.destroy.DestroyVoxelShapes;
import petrolpark.mc.destroy.content.processing.dynamo.arcfurnace.ArcFurnaceLidBlock;

/**
 * Dynamo block — a kinetic "charging" machine driven by a horizontal shaft. Emits redstone
 * signal based on shaft speed. Morphs into an Arc Furnace when placed atop an
 * {@link ArcFurnaceLidBlock} (via the mutable {@link #ARC_FURNACE} blockstate flag); the cube
 * below gets swapped to an Arc Furnace Lid with its rotation axis synced to the Dynamo's AXIS.
*/
public class DynamoBlock extends KineticBlock implements IBE<DynamoBlockEntity> {

    public static final MapCodec<DynamoBlock> CODEC = simpleCodec(DynamoBlock::new);

    public BlockIngredient<?> arcFurnaceBlockIngredient = BlockIngredient.IMPOSSIBLE;

    public static final EnumProperty<Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty ARC_FURNACE = BooleanProperty.create("arc_furnace");

    public DynamoBlock(Properties properties) {
        super(properties);
        arcFurnaceBlockIngredient = new BlockTagIngredient(DestroyTags.Blocks.ARC_FURNACE_TRANSFORMABLE.tag);

        registerDefaultState(
            defaultBlockState()
                .setValue(AXIS, Axis.Z)
                .setValue(ARC_FURNACE, false));
    }

    @Override
    protected MapCodec<DynamoBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder
            .add(AXIS)
            .add(ARC_FURNACE);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return Axis.Y;
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (direction != Direction.UP) return 0;
        return getBlockEntityOptional(level, pos).map(DynamoBlockEntity::getRedstoneSignal).orElse(0);
    }

    @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == Direction.UP;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        AbstractRememberPlacerBehaviour.setPlacedBy(level, pos, placer);
        checkForArcFurnace(level, pos, state);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()
            && DestroyBlocks.ARC_FURNACE_LID.has(level.getBlockState(pos.below()))) {
            getBlockEntityOptional(level, pos).ifPresent(be -> level.setBlock(pos.below(), be.arcFurnaceBlock.get(), 3));
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        checkForArcFurnace(level, pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext pContext) {
        return state.getValue(ARC_FURNACE) ? DestroyVoxelShapes.DYNAMO_ARC_FURNACE : DestroyVoxelShapes.DYNAMO;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockState stateBelow = level.getBlockState(context.getClickedPos().below());
        if (stateBelow.getBlock() instanceof ArcFurnaceLidBlock) {
            level.setBlock(context.getClickedPos().below(), stateBelow.cycle(ArcFurnaceLidBlock.AXIS), 3);
        }
        level.setBlock(context.getClickedPos(), state.cycle(AXIS), 3);

        if (level.getBlockState(context.getClickedPos()) != state) {
            IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
        }

        return InteractionResult.SUCCESS;
    }

    /**
 * Contraption movement checker — refuses to move Dynamos in Arc Furnace mode or their
 * {@link ArcFurnaceLidBlock} counterpart. Static so it can be passed as a method-reference
 * to {@link #registerMovementChecks} at mod-init.
 *
 * <p>Signature matches {@link BlockMovementChecks.MovementAllowedCheck#isMovementAllowed
 * MovementAllowedCheck} 1.21 API (javap verified on create-1.21.1-6.0.9-216):
 * {@code (BlockState, Level, BlockPos) -> CheckResult}.</p>
*/
    public static BlockMovementChecks.CheckResult isMovementAllowed(BlockState state, Level world, BlockPos pos) {
        if ((state.getBlock() instanceof DynamoBlock && state.getValue(ARC_FURNACE))
            || state.getBlock() instanceof ArcFurnaceLidBlock) {
            return BlockMovementChecks.CheckResult.FAIL;
        }
        return BlockMovementChecks.CheckResult.PASS;
    }

    /**
 * Register the {@link #isMovementAllowed} check with {@link BlockMovementChecks}. Must be
 * called exactly once at mod-init from {@code Destroy} constructor — Create 1.21
 * {@code BlockMovementChecks.registerMovementAllowedCheck} is a simple static registry (no
 * dedicated Event), called eagerly.
*/
    public static void registerMovementChecks() {
        BlockMovementChecks.registerMovementAllowedCheck(DynamoBlock::isMovementAllowed);
    }

    /**
 * Check the block below: if it's tagged {@link DestroyTags.Blocks#ARC_FURNACE_TRANSFORMABLE}
 * or an {@link ArcFurnaceLidBlock}, toggle the ARC_FURNACE flag on this Dynamo (and swap
 * the lower block to/from Arc Furnace Lid, copying this Dynamo's AXIS). Mutation is
 * one-shot per neighbor/place event.
*/
    public void checkForArcFurnace(Level level, BlockPos pos, BlockState state) {
        BlockState stateBelow = level.getBlockState(pos.below());
        boolean belowIsArcFurnaceLidBlock =
            stateBelow.is(DestroyBlocks.CARBON_FIBER_BLOCK.get())
                || level.getBlockState(pos.below()).getBlock() instanceof ArcFurnaceLidBlock;

        if (state.getValue(ARC_FURNACE)) {
            if (!belowIsArcFurnaceLidBlock) {
                level.getBlockEntity(pos, DestroyBlockEntityTypes.DYNAMO.get())
                    .ifPresent(dynamo -> dynamo.arcFurnaceBlock = Lazy.of(() -> Blocks.AIR.defaultBlockState()));
                level.setBlock(pos, state.setValue(ARC_FURNACE, false), 3);
            }
        } else {
            if (belowIsArcFurnaceLidBlock) {
                level.getBlockEntity(pos, DestroyBlockEntityTypes.DYNAMO.get())
                    .ifPresent(dynamo -> dynamo.arcFurnaceBlock = Lazy.of(() -> stateBelow));
                level.setBlock(pos.below(),
                    DestroyBlocks.ARC_FURNACE_LID.getDefaultState()
                        .setValue(ArcFurnaceLidBlock.AXIS, state.getValue(AXIS)),
                    3);
                level.setBlock(pos, state.setValue(ARC_FURNACE, true), 3);
            }
        }
    }

    @Override
    public BlockState rotate(BlockState state, LevelAccessor level, BlockPos pos, Rotation rotation) {
        return (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)
            ? state.cycle(AXIS)
            : state;
    }

    @Override
    public Class<DynamoBlockEntity> getBlockEntityClass() {
        return DynamoBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DynamoBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.DYNAMO.get();
    }
}
