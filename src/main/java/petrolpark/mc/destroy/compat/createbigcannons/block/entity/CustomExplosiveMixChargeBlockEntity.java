package petrolpark.mc.destroy.compat.createbigcannons.block.entity;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import petrolpark.mc.destroy.config.DestroyConfigs;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveInventory;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveBlockEntity;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosivePropertyCondition;

/**
 * BE for {@code custom_explosive_mix_charge}. Extends
 * {@link MixedExplosiveBlockEntity} to inherit dye color + name + mixed-explosive inv +
 * MenuProvider + IDyeableMixedExplosiveBlockEntity surface. The charge's inventory uses a custom
 * size (config-driven) and a narrowed condition set — only {@code CAN_EXPLODE} applies because a
 * charge is propellant, not a detonator.
*/
public class CustomExplosiveMixChargeBlockEntity extends MixedExplosiveBlockEntity {

    public static final ExplosivePropertyCondition[] EXPLOSIVE_PROPERTY_CONDITIONS = new ExplosivePropertyCondition[] {
        ExplosiveProperties.CAN_EXPLODE
    };

    public CustomExplosiveMixChargeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public MixedExplosiveInventory createInv() {
        return new MixedExplosiveInventory(DestroyConfigs.server().compat.customExplosiveMixChargeSize.get(), EXPLOSIVE_PROPERTY_CONDITIONS);
    }

    @Override
    public void explode(@Nullable Player cause) {
        // No-op: charge doesn't self-detonate; CBC's cannon reads propellant props instead.
    }

    @Override
    public ExplosivePropertyCondition[] getApplicableExplosionConditions() {
        return EXPLOSIVE_PROPERTY_CONDITIONS;
    }

    @Override
    public String getExplosivePropertyDescriptionTranslationKeySuffix() {
        return "createbigcannons_charge";
    }
}
