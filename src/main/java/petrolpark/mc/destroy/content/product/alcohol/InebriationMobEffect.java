package petrolpark.mc.destroy.content.product.alcohol;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.DestroyDamageSources;
import petrolpark.mc.destroy.DestroyMobEffects;
import petrolpark.mc.destroy.MoveToPetrolparkLibrary;
import petrolpark.mc.destroy.config.DestroyConfigs;
import petrolpark.mc.destroy.core.mobeffect.UncurableMobEffect;

/**
 * Inebriation (alcohol intoxication) mob effect. Amplifies cumulatively: ≥3 adds Confusion,
 * ≥6 adds Blindness, ≥9 damages per period + awards VERY_DRUNK advancement. When the afflicted
 * player finishes sleeping, converts to {@link DestroyMobEffects#HANGOVER} for N ticks scaled by
 * the inebriation amplifier.
*/
@MoveToPetrolparkLibrary
@EventBusSubscriber(modid = Destroy.MOD_ID)
public class InebriationMobEffect extends UncurableMobEffect {

    public InebriationMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(LivingEntity livingEntity, int amplifier) {
        MobEffectInstance existing = livingEntity.getEffect(DestroyMobEffects.INEBRIATION.getDelegate());
        if (existing == null) return super.applyEffectTick(livingEntity, amplifier);
        int pDuration = existing.getDuration();
        if (!livingEntity.level().isClientSide()) {
            if (amplifier >= 3) {
                livingEntity.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 25, (amplifier - 2), true, false, false));
            }
            if (amplifier >= 6) {
                livingEntity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 25, (amplifier - 6), true, false, false));
            }
            if (amplifier >= 9) {
                if (pDuration % Math.round(250 / amplifier) == 0) {
                    livingEntity.hurt(DestroyDamageSources.alcohol(livingEntity.level()), 1f);
                }
                if (livingEntity instanceof Player player) {
                    DestroyAdvancementTrigger.VERY_DRUNK.award(player.level(), player);
                }
            }
        }
        return super.applyEffectTick(livingEntity, amplifier);
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @SubscribeEvent
    public static void onSleepFinished(SleepFinishedTimeEvent event) {
        for (Player player : event.getLevel().players()) {
            if (!player.isSleeping()) continue;
            MobEffectInstance effect = player.getEffect(DestroyMobEffects.INEBRIATION.getDelegate());
            if (effect != null) {
                // MobEffect.getCurativeItems() with EffectCure tokens on MobEffectInstance. The
                // default MobEffectInstance uses EffectCures.DEFAULT_CURES (includes MILK), which
                // would let a milk bucket cure HANGOVER. HANGOVER should be curable only by the
                // AspirinSyringe, so MILK is removed from the cures post-creation via the mutable
                // cures set below; AspirinSyringe.onInject still works via its direct
                // removeEffect call.
                MobEffectInstance hangover = new MobEffectInstance(DestroyMobEffects.HANGOVER.getDelegate(),
                    DestroyConfigs.server().substances.hangoverDuration.get() * (effect.getAmplifier() + 1));
                hangover.getCures().remove(EffectCures.MILK);
                player.addEffect(hangover);
                player.removeEffect(DestroyMobEffects.INEBRIATION.getDelegate());
                DestroyAdvancementTrigger.HANGOVER.award(player.level(), player);
            }
        }
    }
}
