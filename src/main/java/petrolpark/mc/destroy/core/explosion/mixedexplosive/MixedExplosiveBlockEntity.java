package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosivePropertyCondition;

/**
 * BE holding a {@link MixedExplosiveInventory} + dye color + optional custom name for a
 * {@code custom_explosive_mix} Block. Inventory mutation drives the block's {@link
 * ExplosiveProperties} at detonation time (CustomExplosiveMixExplosion.create reads this BE's
 * inventory via the BlockEntityFactory).
 *
 * <p>Parent hierarchy simplified; some interfaces dropped.</p>
*/
public class MixedExplosiveBlockEntity extends SmartBlockEntity implements IDyeableMixedExplosiveBlockEntity {

    public static final ExplosivePropertyCondition[] EXPLOSIVE_PROPERTY_CONDITIONS = new ExplosivePropertyCondition[] {
        ExplosiveProperties.CAN_EXPLODE,
        ExplosiveProperties.DROPS_EXPERIENCE,
        ExplosiveProperties.DROPS_HEADS,
        ExplosiveProperties.ENTITIES_PUSHED,
        ExplosiveProperties.EVAPORATES_FLUIDS,
        ExplosiveProperties.EXPLODES_RANDOMLY,
        ExplosiveProperties.ITEMS_DESTROYED,
        ExplosiveProperties.OBLITERATES,
        ExplosiveProperties.NO_FUSE,
        ExplosiveProperties.SILK_TOUCH,
        ExplosiveProperties.SOUND_ACTIVATED,
        ExplosiveProperties.UNDERWATER
    };

    protected MixedExplosiveInventory inv;
    protected int color;
    @Nullable
    protected Component name;

    public MixedExplosiveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.color = 0xFFFFFF;
        this.inv = createInv();
    }

    /**
 * Factory for this BE's default inventory. Hardcodes a sensible default (9 slots) pending a
 * DestroyAllConfigs audit, after which the configured size would be read.
*/
    public MixedExplosiveInventory createInv() {
        // Placeholder: DestroyAllConfigs.SERVER.blocks.customExplosiveMixSize.get() pending config audit.
        return new MixedExplosiveInventory(9, EXPLOSIVE_PROPERTY_CONDITIONS);
    }

    /**
 * Trigger detonation. Currently a no-op; the trigger is handled by MixedExplosiveBlock +
 * the onCaughtFire chain.
*/
    public void explode(@Nullable Player cause) {
        // No-op: detonation is dispatched via MixedExplosiveBlock.onCaughtFire.
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // No specific behaviours (the SimpleDyeableNameable parent was empty too).
    }

    public MixedExplosiveInventory getExplosiveInventory() {
        return inv;
    }

    public void setExplosiveInventory(MixedExplosiveInventory inv) {
        this.inv = inv;
        setChanged();
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        setChanged();
    }

    @Nullable
    public Component getCustomName() {
        return name;
    }

    public void setCustomName(@Nullable Component name) {
        this.name = name;
        setChanged();
    }

    @Override
    public ExplosivePropertyCondition[] getApplicableExplosionConditions() {
        return EXPLOSIVE_PROPERTY_CONDITIONS;
    }

    /** Returns custom name if set,
 * else the block's registered name.
*/
    @Override
    public net.minecraft.network.chat.Component getDisplayName() {
        return name != null ? name : getBlockState().getBlock().getName();
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        color = tag.getInt("Color");
        // CustomName is stored as a plain literal string; rich-text names are not supported here.
        if (tag.contains("CustomName")) {
            String s = tag.getString("CustomName");
            name = s.isEmpty() ? null : Component.literal(s);
        } else {
            name = null;
        }
        inv = createInv();
        if (tag.contains("ExplosiveMix")) {
            inv.deserializeNBT(registries, tag.getCompound("ExplosiveMix"));
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Color", color);
        if (name != null) tag.putString("CustomName", name.getString());
        tag.put("ExplosiveMix", inv.serializeNBT(registries));
    }
}
