package petrolpark.mc.destroy.core.chemistry.storage.testtube;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import petrolpark.mc.library.util.RayHelper;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyVoxelShapes;
import petrolpark.mc.destroy.core.chemistry.storage.IMixtureStorageItem;
import petrolpark.mc.destroy.core.chemistry.storage.ISpecialMixtureContainerBlock;

/**
 * Rack that holds up to 4 TEST_TUBE items. Right-click a specific slot to insert/remove a tube;
 * right-click with a MixtureStorageItem targeting a specific tube fills/empties that tube via
 * the item's FluidHandler cap.
*/
public class TestTubeRackBlock extends Block implements IBE<TestTubeRackBlockEntity>, IWrenchable, ISpecialMixtureContainerBlock {

    public static final MapCodec<TestTubeRackBlock> CODEC = simpleCodec(TestTubeRackBlock::new);

    public static final BooleanProperty X = BooleanProperty.create("x");

    public TestTubeRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(X, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(X);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(X, context.getHorizontalDirection().getAxis() == Axis.Z);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(X) ? DestroyVoxelShapes.TEST_TUBE_RACK_X : DestroyVoxelShapes.TEST_TUBE_RACK_Z;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide() || hand != InteractionHand.MAIN_HAND) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        int tube = getTargetedTube(state, pos, player);
        if (tube == -1) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return onBlockEntityUseItemOn(level, pos, be -> {
            ItemStack oldStack = be.inv.getStackInSlot(tube).copy();
            if (stack.isEmpty() && oldStack.isEmpty()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!be.inv.isItemValid(tube, stack) && !stack.isEmpty()) return ItemInteractionResult.FAIL;
            be.inv.setStackInSlot(tube, stack.copy());
            stack.shrink(1);
            if (!oldStack.isEmpty()) {
                if (stack.isEmpty()) {
                    player.setItemInHand(hand, oldStack);
                } else {
                    player.getInventory().placeItemBackInInventory(oldStack);
                }
            }
            return ItemInteractionResult.SUCCESS;
        });
    }

    /** IBE helper — Create 1.21 exposes this for ItemInteractionResult based BE delegation.*/
    private <R> ItemInteractionResult onBlockEntityUseItemOn(Level level, BlockPos pos,
                                                             java.util.function.Function<TestTubeRackBlockEntity, ItemInteractionResult> fn) {
        TestTubeRackBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        return fn.apply(be);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            TestTubeRackBlockEntity be = getBlockEntity(world, pos);
            if (be != null) ItemHelper.dropContents(world, pos, be.inv);
        }
        super.onRemove(state, world, pos, newState, isMoving);
    }

    public static int getTargetedTube(BlockState state, BlockPos pos, Player player) {
        Vec3 start = player.getEyePosition();
        Vec3 end = player.getEyePosition().add(player.getLookAngle().scale(player.blockInteractionRange()));
        return RayHelper.getHit(List.of(getTubeBox(state, pos, 0), getTubeBox(state, pos, 1),
            getTubeBox(state, pos, 2), getTubeBox(state, pos, 3)), start, end);
    }

    public static AABB getTubeBox(BlockState state, BlockPos pos, int tube) {
        if (tube < 0 || tube >= 4) return new AABB(0d, 0d, 0d, 0d, 0d, 0d);
        boolean x = state.getValue(X);
        double boxStart = tube * 4 / 16d;
        return new AABB(
            Vec3.atLowerCornerOf(pos).add(x ? boxStart + 0.5 / 16d : 6.5 / 16d, 2.1 / 16d, x ? 6.5 / 16d : boxStart + 0.5 / 16d),
            Vec3.atLowerCornerOf(pos).add(x ? boxStart + 3.5 / 16d : 9.5 / 16d, 10 / 16d, x ? 9.5 / 16d : boxStart + 3.5 / 16d));
    }

    @Override
    public Class<TestTubeRackBlockEntity> getBlockEntityClass() {
        return TestTubeRackBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends TestTubeRackBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.TEST_TUBE_RACK.get();
    }

    @Override
    @Nullable
    public IFluidHandler getTankForMixtureStorageItems(IMixtureStorageItem item, Level level, BlockPos pos,
                                                      BlockState state, @Nullable Direction face,
                                                      Player player, InteractionHand hand, ItemStack stack,
                                                      boolean rightClick) {
        TestTubeRackBlockEntity be = getBlockEntity(level, pos);
        if (be == null) return null;
        int tube = getTargetedTube(state, pos, player);
        if (tube == -1) return null;
        ItemStack tubeStack = be.inv.getStackInSlot(tube);
        return tubeStack.getCapability(Capabilities.FluidHandler.ITEM);
    }
}
