package petrolpark.mc.destroy.compat.createbigcannons.entity;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;
import rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBigCannonProjectile;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonCommonShellProperties;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonFuzePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.big_cannon.config.BigCannonProjectilePropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.BallisticPropertiesComponent;
import rbasamoyai.createbigcannons.munitions.config.components.EntityDamagePropertiesComponent;

import petrolpark.mc.destroy.compat.createbigcannons.block.CreateBigCannonsBlocks;
import petrolpark.mc.destroy.compat.createbigcannons.block.CustomExplosiveMixShellBlock;
import petrolpark.mc.destroy.compat.createbigcannons.block.entity.CustomExplosiveMixShellBlockEntity;
import petrolpark.mc.destroy.config.DestroyConfigs;
import petrolpark.mc.destroy.core.explosion.SmartExplosion;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.CustomExplosiveMixExplosion;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveInventory;

/**
 * Cannon projectile carrying a {@link MixedExplosiveInventory} — on fuze-triggered
 * detonation, spawns a {@link CustomExplosiveMixExplosion} with the carried mix properties. Dye
 * color is synced to the renderer.
 *
 * <p>Extends CBC's {@link FuzedBigCannonProjectile} for inherited fuze countdown + ballistics +
 * HE-shell common shell property binding.</p>
*/
public class CustomExplosiveMixShellProjectile extends FuzedBigCannonProjectile {

    /** Blast-radius multiplier for a fired shell's detonation; the charge and hand bomb use 1.0. */
    public static final float SHELL_EXPLOSION_RADIUS_MULTIPLIER = 1.6f;

    protected MixedExplosiveInventory inv;
    public int color = 0xFFFFFF;

    public CustomExplosiveMixShellProjectile(EntityType<? extends CustomExplosiveMixShellProjectile> type, Level level) {
        super(type, level);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Color", color);
        if (inv != null) tag.put("ExplosiveMix", inv.serializeNBT(level().registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        color = tag.getInt("Color");
        // Build the inventory with the shell's conditions so getExplosiveProperties registers
        // CAN_EXPLODE, which detonate() tests before exploding.
        inv = new MixedExplosiveInventory(DestroyConfigs.server().compat.customExplosiveMixShellSize.get(),
            CustomExplosiveMixShellBlockEntity.EXPLOSIVE_PROPERTY_CONDITIONS);
        if (tag.contains("ExplosiveMix")) inv.deserializeNBT(level().registryAccess(), tag.getCompound("ExplosiveMix"));
    }

    public void setExplosiveInventory(MixedExplosiveInventory inv) {
        this.inv = inv;
    }

    public BlockState getRenderedBlockState() {
        return CreateBigCannonsBlocks.CUSTOM_EXPLOSIVE_MIX_SHELL.getDefaultState()
            .setValue(CustomExplosiveMixShellBlock.FACING, Direction.NORTH);
    }

    protected BigCannonFuzePropertiesComponent getFuzeProperties() {
        return getShellProperties().fuze();
    }

    protected void detonate(Position position) {
        if (level() instanceof ServerLevel serverLevel && inv != null && !inv.isEmpty()
            && inv.getExplosiveProperties().fulfils(ExplosiveProperties.CAN_EXPLODE)) {
            SmartExplosion.explode(serverLevel,
                CustomExplosiveMixExplosion.create(serverLevel, inv, this,
                    new Vec3(position.x(), position.y(), position.z()), SHELL_EXPLOSION_RADIUS_MULTIPLIER));
        }
    }

    protected BigCannonProjectilePropertiesComponent getBigCannonProjectileProperties() {
        return getShellProperties().bigCannonProperties();
    }

    public EntityDamagePropertiesComponent getDamageProperties() {
        return getShellProperties().damage();
    }

    protected BallisticPropertiesComponent getBallisticProperties() {
        return getShellProperties().ballistics();
    }

    public BigCannonCommonShellProperties getShellProperties() {
        return CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE.getPropertiesOf(this);
    }
}
