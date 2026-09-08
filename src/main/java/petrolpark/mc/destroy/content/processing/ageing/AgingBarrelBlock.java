package petrolpark.mc.destroy.content.processing.ageing;

import com.simibubi.create.api.contraption.transformable.TransformableBlock;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.fluids.transfer.GenericItemFilling;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.fluid.FluidHelper;
import com.simibubi.create.foundation.item.ItemHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroySoundEvents;
import petrolpark.mc.destroy.DestroyVoxelShapes;

/**
 * The Ageing Barrel. Both {@link #useWithoutItem} and {@link #useItemOn} try
 * {@link AgeingBarrelBlockEntity#tryOpen} first, which only succeeds once the ageing timer has run
 * out; failing that they fall through to taking the contents out (empty hand) or fluid transfer
 * (held item). {@link #PROGRESS} doubles as the comparator output, and Item Entities collide
 * against the interior shape only, so they land inside and get inserted.
 */
public class AgingBarrelBlock extends HorizontalDirectionalBlock implements IBE<AgeingBarrelBlockEntity>, IWrenchable, TransformableBlock {

    public static final BooleanProperty IS_OPEN = BooleanProperty.create("open");
    /** How far the balloon on the lid has inflated: 0 = smallest, 4 = ageing complete. */
    public static final IntegerProperty PROGRESS = IntegerProperty.create("progress", 0, 4);

    public static final com.mojang.serialization.MapCodec<AgingBarrelBlock> CODEC = simpleCodec(AgingBarrelBlock::new);

    public AgingBarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
            .setValue(FACING, Direction.NORTH)
            .setValue(IS_OPEN, true)
            .setValue(PROGRESS, 0));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return onBlockEntityUse(level, pos, be -> {
            if (be.tryOpen()) {
                DestroySoundEvents.AGING_BARREL_OPEN.playOnServer(level, pos);
                DestroyAdvancementTrigger.OPEN_AGING_BARREL.award(level, player);
                return InteractionResult.SUCCESS;
            }
            // Empty hand: pick up items from the barrel.
            IItemHandlerModifiable inv = be.itemCapability != null ? be.itemCapability : new ItemStackHandler(1);
            boolean success = false;
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stackInSlot = inv.getStackInSlot(slot);
                if (stackInSlot.isEmpty()) continue;
                player.getInventory().placeItemBackInInventory(stackInSlot);
                inv.setStackInSlot(slot, ItemStack.EMPTY);
                success = true;
            }
            if (success) {
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
                    1f + player.getRandom().nextFloat());
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        return onBlockEntityUseItemOn(level, pos, be -> {
            if (be.tryOpen()) {
                DestroySoundEvents.AGING_BARREL_OPEN.playOnServer(level, pos);
                DestroyAdvancementTrigger.OPEN_AGING_BARREL.award(level, player);
                return ItemInteractionResult.SUCCESS;
            }
            if (!stack.isEmpty()) {
                if (FluidHelper.tryEmptyItemIntoBE(level, player, hand, stack, be)) {
                    be.checkRecipe();
                    return ItemInteractionResult.SUCCESS;
                }
                if (FluidHelper.tryFillItemFromBE(level, player, hand, stack, be)) {
                    return ItemInteractionResult.SUCCESS;
                }
                if (GenericItemEmptying.canItemBeEmptied(level, stack) || GenericItemFilling.canItemBeFilled(level, stack)) {
                    return ItemInteractionResult.SUCCESS;
                }
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        });
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        BlockState newState = IWrenchable.super.getRotatedBlockState(state, Direction.UP); // always rotate around Y
        if (newState != state) {
            IWrenchable.playRotateSound(context.getLevel(), context.getClickedPos());
            updateAfterWrenched(state, context);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter worldIn, Entity entityIn) {
        super.updateEntityAfterFallOn(worldIn, entityIn);
        if (!DestroyBlocks.AGING_BARREL.has(worldIn.getBlockState(entityIn.blockPosition()))) return;
        if (!(entityIn instanceof ItemEntity itemEntity)) return;
        if (!entityIn.isAlive()) return;
        withBlockEntityDo(worldIn, entityIn.blockPosition(), be -> {
            ItemStack insertItem = ItemHandlerHelper.insertItem(be.inventory, itemEntity.getItem().copy(), false);
            be.checkRecipe();
            if (insertItem.isEmpty()) {
                itemEntity.discard();
                return;
            }
            itemEntity.setItem(insertItem);
        });
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof AgeingBarrelBlockEntity agingBarrelBE) {
            return agingBarrelBE.getLuminosity();
        }
        return 0;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(IS_OPEN)) {
            return DestroyVoxelShapes.AGING_BARREL_OPEN.get(state.getValue(FACING));
        }
        return DestroyVoxelShapes.agingBarrelClosed(state.getValue(PROGRESS));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // ItemEntity fall detection: shrink to interior so items register as they drop in.
        if (context instanceof EntityCollisionContext entityCollisionContext && entityCollisionContext.getEntity() instanceof ItemEntity) {
            return DestroyVoxelShapes.AGING_BARREL_INTERIOR;
        }
        return getShape(state, level, pos, context);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return DestroyVoxelShapes.AGING_BARREL_OPEN_RAYTRACE.get(state.getValue(FACING));
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return switch (state.getValue(PROGRESS)) {
            case 1 -> 3;
            case 2 -> 7;
            case 3 -> 11;
            case 4 -> 15;
            default -> 0;
        };
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
        if (!state.hasBlockEntity() || state.getBlock() == newState.getBlock()) return;
        withBlockEntityDo(level, pos, be -> ItemHelper.dropContents(level, pos, be.inventory));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        builder.add(FACING, IS_OPEN, PROGRESS);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Class<AgeingBarrelBlockEntity> getBlockEntityClass() {
        return AgeingBarrelBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends AgeingBarrelBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.AGING_BARREL.get();
    }

    @Override
    public BlockState transform(BlockState state, StructureTransform transform) {
        return state.setValue(FACING, transform.rotation.rotate(state.getValue(FACING)));
    }
}
