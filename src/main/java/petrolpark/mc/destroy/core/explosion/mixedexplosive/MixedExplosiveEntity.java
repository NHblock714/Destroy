package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyEntityTypes;
import petrolpark.mc.destroy.core.explosion.PrimedBombEntity;
import petrolpark.mc.destroy.core.explosion.SmartExplosion;

/**
 * Thrown / primed entity for a {@link MixedExplosiveBlockEntity}. Spawns when the block ignites
 * (fuse start), carries the inventory's color + {@link MixedExplosiveInventory} through the air,
 * and detonates via {@link CustomExplosiveMixExplosion} after fuse expires.
 *
 * <p><b>Dependencies</b>:</p>
 * <ul>
 * <li>{@link PrimedBombEntity} — parent with explode / setFuse / owner tracking.</li>
 * <li>{@link CustomExplosiveMixExplosion#create} — property-driven explosion factory.</li>
 * <li>{@link MixedExplosiveInventory} — inventory container + property aggregation.</li>
 * <li>{@link DestroyEntityTypes#PRIMED_CUSTOM_EXPLOSIVE} registration.</li>
 * </ul>
*/
public class MixedExplosiveEntity extends PrimedBombEntity implements IEntityWithComplexSpawn {

    public int color;
    public MixedExplosiveInventory inv;

    public MixedExplosiveEntity(EntityType<? extends PrimedTnt> entityType, Level level) {
        super(entityType, level);
        color = 0xFFFFFF;
        inv = new MixedExplosiveInventory(0);
    }

    public MixedExplosiveEntity(Level level, BlockPos blockPos, BlockState state, @Nullable LivingEntity owner, int color, MixedExplosiveInventory inventory) {
        super(DestroyEntityTypes.PRIMED_CUSTOM_EXPLOSIVE.get(), level, blockPos, state, owner);
        this.color = color;
        this.inv = inventory;
    }

    @Override
    public BlockState getBlockStateToRender() {
        return DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.getDefaultState();
    }

    @Override
    public SmartExplosion getExplosion(Level level, Vec3 position, @Nullable Entity source) {
        return CustomExplosiveMixExplosion.create(level, inv, source, position);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("Color", color);
        compound.put("Inventory", inv.serializeNBT(level().registryAccess()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        color = compound.getInt("Color");
        // Placeholder: DestroyAllConfigs.SERVER.blocks.customExplosiveMixSize.get() pending config audit.
        inv = new MixedExplosiveInventory(9);
        if (compound.contains("Inventory")) {
            inv.deserializeNBT(level().registryAccess(), compound.getCompound("Inventory"));
        }
    }

    /**
 * IEntityWithComplexSpawn write — spawn-packet payload for client renderer.
 * {@link RegistryFriendlyByteBuf} required by 1.21 for DataComponent-aware stream codecs.
*/
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        CompoundTag compound = new CompoundTag();
        addAdditionalSaveData(compound);
        buffer.writeNbt(compound);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        CompoundTag tag = additionalData.readNbt();
        if (tag != null) readAdditionalSaveData(tag);
    }
}
