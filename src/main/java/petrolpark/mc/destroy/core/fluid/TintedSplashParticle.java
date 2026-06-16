package petrolpark.mc.destroy.core.fluid;

import com.simibubi.create.content.equipment.bell.BasicParticleData;
import com.simibubi.create.content.equipment.bell.BasicParticleData.IBasicParticleFactory;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.core.particles.ParticleType;

import petrolpark.mc.destroy.client.DestroyParticleTypes;

/**
 * Tinted water-drop splash particle — extends vanilla {@link WaterDropParticle} with per-particle
 * (r, g, b) color tinting. Used as the common base for colored rain-drop animations (e.g.
 * acid-rain green-tinted drops) in Ponder scenes.
 *
 * <p>Registered via {@link DestroyParticleTypes#TINTED_SPLASH}.</p>
*/
public class TintedSplashParticle extends WaterDropParticle {

    public TintedSplashParticle(ClientLevel level, double x, double y, double z, double r, double g, double b, SpriteSet sprites) {
        super(level, x, y, z);
        this.gravity = 0.04f;
        pickSprite(sprites);
        setColor((float) r, (float) g, (float) b);
    }

    public static class Data extends BasicParticleData<TintedSplashParticle> {

        @Override
        public ParticleType<?> getType() {
            return DestroyParticleTypes.TINTED_SPLASH.get();
        }

        @Override
        public IBasicParticleFactory<TintedSplashParticle> getBasicFactory() {
            return TintedSplashParticle::new;
        }
    }
}
