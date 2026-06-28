package petrolpark.mc.destroy.compat.createbigcannons.block;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.PowderChargeBlock;
import rbasamoyai.createbigcannons.munitions.big_cannon.propellant.config.BigCannonPropellantPropertiesComponent;

import petrolpark.mc.destroy.compat.createbigcannons.DestroyMunitionPropertiesHandlers;
import petrolpark.mc.destroy.compat.createbigcannons.block.entity.CreateBigCannonBlockEntityTypes;
import petrolpark.mc.destroy.compat.createbigcannons.block.entity.CustomExplosiveMixChargeBlockEntity;
import petrolpark.mc.destroy.compat.createbigcannons.item.CustomExplosiveMixChargeBlockItem;
import petrolpark.mc.destroy.config.DestroyConfigs;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosiveProperty;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveInventory;

/**
 * Cannon charge block whose propellant strength/stress/recoil/spread scale with the
 * explosive properties of its inner mix. Extends CBC's {@link PowderChargeBlock} to inherit cannon-
 * loading + rotation + water-logging behavior; overrides the four {@code get*} accessors to plug
 * in the custom mixed-property calculation.
*/
public class CustomExplosiveMixChargeBlock extends PowderChargeBlock implements IBE<CustomExplosiveMixChargeBlockEntity> {

    public CustomExplosiveMixChargeBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        withBlockEntityDo(level, pos, be -> be.onPlace(stack, level.registryAccess()));
    }

    /** Detonate the contained mix when caught in an explosion, instead of dropping the charge item. */
    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        if (destroy$detonate(level, pos)) return;
        super.onBlockExploded(state, level, pos, explosion);
    }

    /** A charge caught in an explosion detonates rather than dropping its item. */
    @Override
    public boolean canDropFromExplosion(BlockState state, BlockGetter level, BlockPos pos, net.minecraft.world.level.Explosion explosion) {
        return false;
    }

    private boolean destroy$detonate(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CustomExplosiveMixChargeBlockEntity be)) return false;
        MixedExplosiveInventory inv = be.getExplosiveInventory();
        if (inv.isEmpty() || !inv.getExplosiveProperties().fulfils(ExplosiveProperties.CAN_EXPLODE)) return false;
        level.removeBlock(pos, false);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // Null source: a chain detonation is not a deliberate player action, so it does not award
            // the detonation advancement or count against whatever triggered it.
            petrolpark.mc.destroy.core.explosion.SmartExplosion.explode(serverLevel,
                petrolpark.mc.destroy.core.explosion.mixedexplosive.CustomExplosiveMixExplosion.create(
                    level, inv, null, net.minecraft.world.phys.Vec3.atCenterOf(pos)));
        }
        return true;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult dyeResult = onBlockEntityUse(level, pos, be -> be.tryDye(stack, hit, level, pos, player));
        if (dyeResult != InteractionResult.PASS) return ItemInteractionResult.sidedSuccess(level.isClientSide());
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            withBlockEntityDo(level, pos, be -> serverPlayer.openMenu(be, be::writeToBuffer));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity be = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (!(be instanceof CustomExplosiveMixChargeBlockEntity ebe)) return Collections.emptyList();
        return Collections.singletonList(ebe.getFilledItemStack(
            petrolpark.mc.destroy.compat.createbigcannons.block.CreateBigCannonsBlocks.CUSTOM_EXPLOSIVE_MIX_CHARGE.asStack(),
            params.getLevel().registryAccess()));
    }

    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return getCloneItemStack(level, pos);
    }

    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CustomExplosiveMixChargeBlockEntity be)) return ItemStack.EMPTY;
        HolderLookup.Provider provider = (level instanceof Level lv) ? lv.registryAccess() : net.minecraft.core.RegistryAccess.EMPTY;
        return be.getFilledItemStack(
            petrolpark.mc.destroy.compat.createbigcannons.block.CreateBigCannonsBlocks.CUSTOM_EXPLOSIVE_MIX_CHARGE.asStack(),
            provider);
    }

    /**
     * CBC hand-loading integration — see {@link CustomExplosiveMixShellBlock#getHandloadingInfo}.
     * CBC's default writes the item's <em>DataComponents</em> into the bore tag, but
     * {@link #getPropellantProperties(StructureBlockInfo)} reads a raw {@code "ExplosiveMix"} NBT
     * key. Without this override a hand-loaded charge arrived with no mix → {@code inv.isEmpty()} →
     * DEFAULT (zero) propellant → the cannon couldn't fire. Rebuild the tag through the BE's own
     * serialization so the raw key is present.
     */
    @Override
    public StructureBlockInfo getHandloadingInfo(ItemStack stack, BlockPos localPos, Direction cannonOrientation, HolderLookup.Provider provider) {
        StructureBlockInfo base = super.getHandloadingInfo(stack, localPos, cannonOrientation, provider);
        CustomExplosiveMixChargeBlockEntity be = new CustomExplosiveMixChargeBlockEntity(getBlockEntityType(), base.pos(), base.state());
        be.onPlace(stack, provider); // explosive mix + dye color + custom name
        return new StructureBlockInfo(base.pos(), base.state(), be.saveWithId(provider));
    }

    /** Inverse of hand-loading — extract the bore block back to a filled charge item. */
    @Override
    public ItemStack getExtractedItem(StructureBlockInfo info, HolderLookup.Provider provider) {
        ItemStack stack = CreateBigCannonsBlocks.CUSTOM_EXPLOSIVE_MIX_CHARGE.asStack();
        if (info.nbt() == null) return stack;
        if (BlockEntity.loadStatic(info.pos(), info.state(), info.nbt(), provider) instanceof CustomExplosiveMixChargeBlockEntity ebe) {
            stack = ebe.getFilledItemStack(stack, provider);
        }
        return stack;
    }

    @Override
    public float getChargePower(StructureBlockInfo data) {
        return getPropellantProperties(data).strength();
    }

    @Override
    public float getChargePower(ItemStack stack) {
        return getPropellantProperties(stack).strength();
    }

    @Override
    public float getStressOnCannon(StructureBlockInfo data) {
        return getPropellantProperties(data).addedStress();
    }

    @Override
    public float getStressOnCannon(ItemStack stack) {
        return getPropellantProperties(stack).addedStress();
    }

    @Override
    public float getSpread(StructureBlockInfo data) {
        return getPropellantProperties(data).addedSpread();
    }

    @Override
    public float getRecoil(StructureBlockInfo data) {
        return getPropellantProperties(data).addedRecoil();
    }

    @Override
    public Class<CustomExplosiveMixChargeBlockEntity> getBlockEntityClass() {
        return CustomExplosiveMixChargeBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CustomExplosiveMixChargeBlockEntity> getBlockEntityType() {
        return CreateBigCannonBlockEntityTypes.CUSTOM_EXPLOSIVE_MIX_CHARGE.get();
    }

    public CustomExplosiveMixChargeBlockItem getItem() {
        return (CustomExplosiveMixChargeBlockItem) CreateBigCannonsBlocks.CUSTOM_EXPLOSIVE_MIX_CHARGE.asItem();
    }

    public BigCannonPropellantPropertiesComponent getPropellantProperties(ItemStack stack) {
        // Item carries EXPLOSIVE_MIX DataComponent (via IMixedExplosiveItem); read directly.
        CompoundTag tag = stack.get(petrolpark.mc.destroy.DestroyDataComponents.EXPLOSIVE_MIX);
        return getPropellantProperties(tag);
    }

    public BigCannonPropellantPropertiesComponent getPropellantProperties(StructureBlockInfo data) {
        // StructureBlockInfo holds BE-saved NBT; BE.saveAdditional writes inv under "ExplosiveMix".
        CompoundTag nbt = data.nbt();
        return getPropellantProperties(nbt == null ? null : nbt.getCompound("ExplosiveMix"));
    }

    public BigCannonPropellantPropertiesComponent getPropellantProperties(@Nullable CompoundTag explosiveMixTag) {
        if (explosiveMixTag == null || explosiveMixTag.isEmpty()) return BigCannonPropellantPropertiesComponent.DEFAULT;
        // Build the inventory with the charge's conditions so getExplosiveProperties registers
        // CAN_EXPLODE; fulfils() requires the condition to be present before it tests the threshold.
        MixedExplosiveInventory inv = new MixedExplosiveInventory(DestroyConfigs.server().compat.customExplosiveMixChargeSize.get(),
            CustomExplosiveMixChargeBlockEntity.EXPLOSIVE_PROPERTY_CONDITIONS);
        inv.deserializeNBT(net.minecraft.core.RegistryAccess.EMPTY, explosiveMixTag);
        if (inv.isEmpty()) return BigCannonPropellantPropertiesComponent.DEFAULT;
        CustomExplosiveMixChargeProperties chargeProperties = DestroyMunitionPropertiesHandlers.CUSTOM_EXPLOSIVE_MIX_CHARGE.getPropertiesOf(this);
        ExplosiveProperties explosiveProperties = inv.getExplosiveProperties();
        if (!explosiveProperties.fulfils(ExplosiveProperties.CAN_EXPLODE)) return BigCannonPropellantPropertiesComponent.DEFAULT;
        float strength = chargeProperties.basePropellantProperties().strength();
        float stress = chargeProperties.basePropellantProperties().addedStress();
        float recoil = chargeProperties.basePropellantProperties().addedRecoil();
        float spread = chargeProperties.basePropellantProperties().addedSpread();
        for (ExplosiveProperty property : ExplosiveProperty.values()) {
            BigCannonPropellantPropertiesComponent mod = chargeProperties.propellantPropertyModifiers().get(property);
            float propValue = explosiveProperties.get(property).value;
            strength += propValue * mod.strength();
            stress   += propValue * mod.addedStress();
            recoil   += propValue * mod.addedRecoil();
            spread   += propValue * mod.addedSpread();
        }
        BigCannonPropellantPropertiesComponent def = BigCannonPropellantPropertiesComponent.DEFAULT;
        return new BigCannonPropellantPropertiesComponent(strength, stress, recoil, spread,
            def.explosionPower(), def.dampAmmoStrengthDebuff(), def.dampAmmoDoesntIgniteAsStarter());
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, net.minecraft.world.level.LevelReader level, BlockPos pos, Player player) {
        if (level instanceof Level lv) return getCloneItemStack(lv, pos);
        return ItemStack.EMPTY;
    }
}
