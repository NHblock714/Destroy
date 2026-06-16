package petrolpark.mc.destroy;

import static petrolpark.mc.destroy.Destroy.REGISTRATE;

import com.petrolpark.core.registrate.MobEffectEntry;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import petrolpark.mc.destroy.content.product.alcohol.HangoverMobEffect;
import petrolpark.mc.destroy.content.product.alcohol.InebriationMobEffect;
import petrolpark.mc.destroy.content.product.babyblue.BabyBlueHighMobEffect;
import petrolpark.mc.destroy.content.product.babyblue.BabyBlueWithdrawalMobEffect;
import petrolpark.mc.destroy.core.chemistry.hazard.mobeffect.ChemicalPoisonMobEffect;
import petrolpark.mc.destroy.core.chemistry.hazard.mobeffect.CryingMobEffect;
import petrolpark.mc.destroy.core.chemistry.hazard.mobeffect.LeadPoisoningMobEffect;
import petrolpark.mc.destroy.core.mobeffect.DestroyMobEffect;
import petrolpark.mc.destroy.core.mobeffect.UncurableMobEffect;

/**
 * Destroy's {@link MobEffect} registrations. Each entry binds a registry ID + holder to its
 * effect class; generic effects use {@link DestroyMobEffect}, while content-specific behaviours
 * (alcohol, baby blue, chemistry hazards) use their dedicated subclasses.
*/
public class DestroyMobEffects {

    public static final MobEffectEntry<UncurableMobEffect> CANCER = REGISTRATE
        .mobEffect("cancer", (cat, color) -> new UncurableMobEffect(cat, color))
        .category(MobEffectCategory.NEUTRAL)
        .color(0)
        .register();

    public static final MobEffectEntry<ChemicalPoisonMobEffect> CHEMICAL_POISON = REGISTRATE
        .mobEffect("chemical_poison", (cat, color) -> new ChemicalPoisonMobEffect(cat, color))
        .category(MobEffectCategory.HARMFUL)
        .color(0x6B8E23)
        .register();

    public static final MobEffectEntry<CryingMobEffect> CRYING = REGISTRATE
        .mobEffect("crying", (cat, color) -> new CryingMobEffect(cat, color))
        .category(MobEffectCategory.HARMFUL)
        .color(0x7EC0EE)
        .register();

    public static final MobEffectEntry<DestroyMobEffect> FRAGRANCE = REGISTRATE
        .mobEffect("fragrance", (cat, color) -> new DestroyMobEffect(cat, color))
        .category(MobEffectCategory.BENEFICIAL)
        .color(0xF294D9)
        .register();

    public static final MobEffectEntry<DestroyMobEffect> FULL_BLADDER = REGISTRATE
        .mobEffect("full_bladder", (cat, color) -> new DestroyMobEffect(cat, color))
        .category(MobEffectCategory.NEUTRAL)
        .color(0xF7F75D)
        .register();

    public static final MobEffectEntry<HangoverMobEffect> HANGOVER = REGISTRATE
        .mobEffect("hangover", (cat, color) -> new HangoverMobEffect(cat, color))
        .category(MobEffectCategory.HARMFUL)
        .color(0x8B4513)
        .register();

    public static final MobEffectEntry<InebriationMobEffect> INEBRIATION = REGISTRATE
        .mobEffect("inebriation", (cat, color) -> new InebriationMobEffect(cat, color))
        .category(MobEffectCategory.NEUTRAL)
        .color(0xD2B48C)
        .register();

    public static final MobEffectEntry<LeadPoisoningMobEffect> LEAD_POISONING = REGISTRATE
        .mobEffect("lead_poisoning", (cat, color) -> new LeadPoisoningMobEffect(cat, color))
        .category(MobEffectCategory.HARMFUL)
        .color(0x555555)
        .register();

    public static final MobEffectEntry<BabyBlueHighMobEffect> BABY_BLUE_HIGH = REGISTRATE
        .mobEffect("baby_blue_high", (cat, color) -> new BabyBlueHighMobEffect(cat, color))
        .category(MobEffectCategory.BENEFICIAL)
        .color(0x8BDCEB)
        .register();

    public static final MobEffectEntry<BabyBlueWithdrawalMobEffect> BABY_BLUE_WITHDRAWAL = REGISTRATE
        .mobEffect("baby_blue_withdrawal", (cat, color) -> new BabyBlueWithdrawalMobEffect(cat, color))
        .category(MobEffectCategory.HARMFUL)
        .color(0x91B1B7)
        .register();

    public static final MobEffectEntry<DestroyMobEffect> SUN_PROTECTION = REGISTRATE
        .mobEffect("sun_protection", (cat, color) -> new DestroyMobEffect(cat, color))
        .category(MobEffectCategory.BENEFICIAL)
        .color(0xFFFFFE)
        .register();

    public static void register() {
        // Class-load trigger for static initializers.
    }

    public static MobEffectInstance cancerInstance() {
        return new MobEffectInstance(CANCER.getDelegate(), MobEffectInstance.INFINITE_DURATION, 0, false, false, true);
    }

    /**
 * Incrementally adjusts the amplifier of {@code effect} on the entity by {@code level}.
 * 1.21.1 note: {@link LivingEntity#hasEffect(Holder)} / {@link LivingEntity#getEffect(Holder)} /
 * {@link LivingEntity#removeEffect(Holder)} now take a {@link Holder}&lt;{@link MobEffect}&gt; rather
 * than a {@link MobEffect} directly.
*/
    @SuppressWarnings("null")
    public static void increaseEffectLevel(LivingEntity entity, final Holder<MobEffect> effect, int level, int addedDurationPerLevel) {
        boolean infinite = addedDurationPerLevel == -1;
        if (entity.hasEffect(effect)) {
            int currentAmplifier = entity.getEffect(effect).getAmplifier();
            int currentDuration = entity.getEffect(effect).getDuration();
            entity.removeEffect(effect);
            int newLevel = currentAmplifier + level;
            if (newLevel <= 0) return;
            entity.addEffect(new MobEffectInstance(effect, infinite ? -1 : Math.max(currentDuration + (addedDurationPerLevel * level), 0), Math.max(currentAmplifier + level, 0), false, false, true));
        } else if (level >= 1) {
            entity.addEffect(new MobEffectInstance(effect, infinite ? -1 : addedDurationPerLevel * level, level - 1, false, false, true));
        }
    }
}
