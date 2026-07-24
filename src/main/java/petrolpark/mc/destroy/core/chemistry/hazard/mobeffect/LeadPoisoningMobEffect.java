package petrolpark.mc.destroy.core.chemistry.hazard.mobeffect;

import petrolpark.mc.library.util.RayHelper;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

import petrolpark.mc.destroy.core.mobeffect.UncurableMobEffect;

/**
 * Chronic lead-poisoning effect. Erratically drains player XP + causes uncontrolled swings at
 * whatever the player is looking at, modeling lead-induced cognitive erratic behavior.
*/
public class LeadPoisoningMobEffect extends UncurableMobEffect {

    public LeadPoisoningMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        if (!livingEntity.level().isClientSide() && livingEntity instanceof Player player) {
            player.giveExperiencePoints(-1);
            if (RayHelper.getHitResult(livingEntity, 1f, false) instanceof EntityHitResult result) {
                Entity target = result.getEntity();
                if (target instanceof LivingEntity) {
                    player.attack(target);
                    player.swing(InteractionHand.MAIN_HAND, true);
                }
            }
        }
        return super.applyEffectTick(livingEntity, amplifier);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % Math.max(1, (int) (500f / (float) (1 + amplifier))) == 0;
    }
}
