package petrolpark.mc.destroy.client;

import java.util.function.Supplier;

import com.simibubi.create.foundation.particle.ICustomParticleData;

import net.createmod.catnip.lang.Lang;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.content.confetti.ConfettoParticleData;
import petrolpark.mc.destroy.core.chemistry.hazard.mobeffect.TearParticle;

/**
 * Registry of Destroy's {@link ParticleType}s, following the enum-driven pattern of
 * {@code com.simibubi.create.AllParticleTypes}: each constant supplies a {@link Supplier} of its
 * own {@link ICustomParticleData}, and the nested {@code ParticleEntry} takes care of the
 * {@code DeferredRegister} entry and the client-side factory.
 */
public enum DestroyParticleTypes {

    CONFETTO(ConfettoParticleData::new),
    WHITE_CONFETTO(ConfettoParticleData.White::new),
    TEAR(TearParticle.Data::new),
    // Fluid-tinted gas particles: distillation column vapour, evaporation, boiling bubbles.
    DISTILLATION(petrolpark.mc.destroy.core.fluid.gasparticle.GasParticleData::new),
    EVAPORATION(petrolpark.mc.destroy.core.fluid.gasparticle.GasParticleData::new),
    BOILING_FLUID_BUBBLE(petrolpark.mc.destroy.core.fluid.gasparticle.BoilingFluidBubbleParticleData::new),
    // Tinted drop particles, used by the pollution Ponder scenes for coloured precipitation.
    TINTED_SPLASH(petrolpark.mc.destroy.core.fluid.TintedSplashParticle.Data::new),
    RAIN(petrolpark.mc.destroy.core.fluid.RainParticle.Data::new),
    ;

    private final ParticleEntry<?> entry;

    <T extends ParticleOptions> DestroyParticleTypes(Supplier<? extends ICustomParticleData<T>> typeProvider) {
        this.entry = new ParticleEntry<>(Lang.asId(name()), typeProvider);
    }

    public ParticleType<?> get() {
        return entry.object.get();
    }

    public static void register(IEventBus modEventBus) {
        ParticleEntry.REGISTER.register(modEventBus);
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerFactories(RegisterParticleProvidersEvent event) {
        for (DestroyParticleTypes particle : values()) particle.entry.registerFactory(event);
    }

    private static class ParticleEntry<T extends ParticleOptions> {
        private static final DeferredRegister<ParticleType<?>> REGISTER =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Destroy.MOD_ID);

        private final String name;
        private final Supplier<? extends ICustomParticleData<T>> typeProvider;
        private final DeferredHolder<ParticleType<?>, ParticleType<T>> object;

        ParticleEntry(String name, Supplier<? extends ICustomParticleData<T>> typeProvider) {
            this.name = name;
            this.typeProvider = typeProvider;
            this.object = REGISTER.register(name, () -> typeProvider.get().createType());
        }

        @OnlyIn(Dist.CLIENT)
        public void registerFactory(RegisterParticleProvidersEvent event) {
            typeProvider.get().register(object.get(), event);
        }
    }
}
