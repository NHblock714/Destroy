package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyEntityTypes;
import petrolpark.mc.destroy.core.explosion.PrimeableBombBlock;
import petrolpark.mc.destroy.core.explosion.SmartExplosion;

/**
 * Placeable {@code custom_explosive_mix} block backed by a {@link MixedExplosiveBlockEntity} +
 * primed-entity spawn via {@link MixedExplosiveEntity}. Inherits fuse/ignition/dispense pipeline
 * from {@link PrimeableBombBlock} (which extends {@code TntBlock}).
 *
 * <p>Inherits TntBlock defaults where safe:</p>
 * <ul>
 * <li>{@code use} inherits TntBlock flint-and-steel ignite → calls {@link #onCaughtFire}
 * (overridden here) → dispatches to the {@link CustomExplosiveMixEntityFactory} → spawns
 * {@link MixedExplosiveEntity}. End-to-end placement + ignite pipeline functional.</li>
 * <li>{@link #explodeInstantly} — reads BE inventory, checks NO_FUSE property, triggers
 * {@link CustomExplosiveMixExplosion#create} instantly if set. Only activates on
 * {@code onCaughtFire} + {@code onBlockExploded} chain.</li>
 * <li>{@link #isRandomlyTicking} / {@link #randomTick} — EXPLODES_RANDOMLY property triggers
 * random ignition.</li>
 * <li>{@code onPlace} override — RS-signal detection on placement (vanilla TNT behavior).</li>
 * <li>{@code neighborChanged} — RS-pulse ignition (vanilla TNT behavior).</li>
 * <li>{@link #propagatesSkylightDown} — returns true so block-side text renders correctly.</li>
 * <li>IBE binding via {@link #getBlockEntityClass} + {@link #getBlockEntityType}.</li>
 * </ul>
*/
public class MixedExplosiveBlock extends PrimeableBombBlock<MixedExplosiveEntity> implements IBE<MixedExplosiveBlockEntity> {

    public MixedExplosiveBlock(Properties properties) {
        super(properties, new CustomExplosiveMixEntityFactory());
    }

    /**
 * Check if NO_FUSE property is fulfilled → if so, skip priming + trigger immediate
 * {@link CustomExplosiveMixExplosion} at block center. Returns {@code true} when instant
 * detonation happened (caller should suppress default fuse-priming chain).
*/
    public boolean explodeInstantly(BlockState state, Level level, BlockPos pos, @Nullable LivingEntity igniter) {
        Optional<MixedExplosiveBlockEntity> beOpt = getBlockEntityOptional(level, pos);
        if (beOpt.isEmpty()) return false;
        MixedExplosiveBlockEntity be = beOpt.get();
        MixedExplosiveInventory inv = be.getExplosiveInventory();
        if (inv.isEmpty()) return false;
        ExplosiveProperties properties = inv.getExplosiveProperties();
        if (properties.fulfils(ExplosiveProperties.NO_FUSE)) {
            level.removeBlock(pos, false);
            if (level instanceof ServerLevel serverLevel) {
                SmartExplosion.explode(serverLevel, CustomExplosiveMixExplosion.create(level, inv, igniter, Vec3.atCenterOf(pos)));
            }
            return true;
        }
        return false;
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {
        if (explodeInstantly(state, level, pos, igniter)) return;
        super.onCaughtFire(state, level, pos, face, igniter);
        level.removeBlock(pos, false);
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (explodeInstantly(state, level, pos, explosion.getIndirectSourceEntity())) return;
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        if (!oldState.is(state.getBlock()) && level.hasNeighborSignal(pos)) {
            onCaughtFire(oldState, level, pos, null, null);
        }
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block fromBlock, BlockPos fromPos, boolean isMoving) {
        if (level.hasNeighborSignal(pos)) {
            onCaughtFire(state, level, pos, null, null);
        }
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        withBlockEntityDo(level, pos, be -> {
            if (be.getExplosiveInventory().getExplosiveProperties().fulfils(ExplosiveProperties.EXPLODES_RANDOMLY)) {
                onCaughtFire(state, level, pos, null, null);
            }
        });
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true; // So block-side text renders correctly
    }

    // ============================================================
    // Interactive gameplay activation (uses BE interface defaults + Menu)
    // ============================================================

    /**
 * Empty-hand right-click → open {@link MixedExplosiveMenu} via
 * {@link ServerPlayer#openMenu(net.minecraft.world.MenuProvider, java.util.function.Consumer)}
 *. The BE's {@link IMixedExplosiveBlockEntity#writeToBuffer} serializes
 * inventory + color + conditions into the spawn packet.
*/
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            Optional<MixedExplosiveBlockEntity> beOpt = getBlockEntityOptional(level, pos);
            if (beOpt.isPresent()) {
                MixedExplosiveBlockEntity be = beOpt.get();
                serverPlayer.openMenu(be, be::writeToBuffer);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /**
 * Right-click with item:
 * <ul>
 * <li>Flint-and-steel / fire-charge + CAN_EXPLODE property → PASS_TO_DEFAULT_BLOCK_INTERACTION
 * so TntBlock's default ignite path runs.</li>
 * <li>DyeItem → delegate to {@link IDyeableMixedExplosiveBlockEntity#tryDye} (default impl
 * with DyedItemColor.applyDyes).</li>
 * <li>Else → PASS.</li>
 * </ul>
*/
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        boolean lighter = stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
        Optional<MixedExplosiveBlockEntity> beOpt = getBlockEntityOptional(level, pos);
        if (beOpt.isPresent()) {
            MixedExplosiveBlockEntity be = beOpt.get();
            if (lighter && be.getExplosiveInventory().getExplosiveProperties().fulfils(ExplosiveProperties.CAN_EXPLODE)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (stack.getItem() instanceof DyeItem) {
                InteractionResult result = be.tryDye(stack, (HitResult) hit, level, pos, player);
                if (result != InteractionResult.PASS) return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
 * Block-placement hook — transfers the placing stack's EXPLOSIVE_MIX + DYED_COLOR into
 * the newly-placed BE via IDyeableMixedExplosiveBlockEntity.onPlace default method
 *.
*/
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        withBlockEntityDo(level, pos, be -> {
            be.onPlace(stack, level.registryAccess());
            // onPlace transfers the inventory + dye colour but not the anvil-set name — copy it so
            // the placed block's BER label matches the item that was placed.
            if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME))
                be.setCustomName(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
        });
    }

    /**
 * Block drops — reads the BE's inventory+color via IDyeableMixedExplosiveBlockEntity.getFilledItemStack
 * default method so drop stack preserves custom mix + dye color.
*/
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(be instanceof MixedExplosiveBlockEntity ebe)) return Collections.emptyList();
        ItemStack drop = ebe.getFilledItemStack(DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.asStack(), ebe.getLevel().registryAccess());
        if (ebe.getCustomName() != null) drop.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, ebe.getCustomName());
        return Collections.singletonList(drop);
    }

    /**
 * Pick-block — returns a stack that carries this BE's inventory+color via the interface's
 * getFilledItemStack default. 1.21 Block.getCloneItemStack signature uses {@link
 * net.minecraft.world.level.LevelReader}.
*/
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof MixedExplosiveBlockEntity ebe) || ebe.getLevel() == null) return DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.asStack();
        ItemStack cloneStack = ebe.getFilledItemStack(DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.asStack(), ebe.getLevel().registryAccess());
        if (ebe.getCustomName() != null) cloneStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, ebe.getCustomName());
        return cloneStack;
    }

    @Override
    public Class<MixedExplosiveBlockEntity> getBlockEntityClass() {
        return MixedExplosiveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MixedExplosiveBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.CUSTOM_EXPLOSIVE_MIX.get();
    }

    /**
 * {@link PrimeableBombBlock.PrimedBombEntityFactory} that spawns a {@link MixedExplosiveEntity}
 * carrying the block's {@link MixedExplosiveBlockEntity} inventory + color + custom name.
*/
    public static class CustomExplosiveMixEntityFactory implements PrimeableBombBlock.PrimedBombEntityFactory<MixedExplosiveEntity> {

        @Override
        public MixedExplosiveEntity create(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity igniter) {
            if (!(level.getBlockEntity(pos) instanceof MixedExplosiveBlockEntity ebe)) {
                return DestroyEntityTypes.PRIMED_CUSTOM_EXPLOSIVE.get().create(level);
            }
            MixedExplosiveEntity entity = new MixedExplosiveEntity(level, pos, state, igniter, ebe.getColor(), ebe.getExplosiveInventory());
            entity.setCustomName(ebe.getCustomName());
            return entity;
        }
    }
}
