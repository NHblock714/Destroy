package petrolpark.mc.destroy.core.fluid.gasparticle;

import javax.annotation.Nullable;

import com.simibubi.create.content.fluids.particle.FluidStackParticle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.client.DestroyParticleTypes;
import petrolpark.mc.destroy.content.processing.distillation.BubbleCapBlockEntity;

/**
 * {@link FluidStackParticle} subclass for gas cloud particles — 2 modes:
 * <ul>
 * <li>{@link DestroyParticleTypes#DISTILLATION}: rises vertically for {@code blockHeight}
 * blocks at a fixed speed (animated BubbleCap tower distillation). Lifetime scaled to
 * tower height.</li>
 * <li>{@link DestroyParticleTypes#EVAPORATION}: floats with physics, lifetime proportional to
 * fluid amount (spray AABB evaporation).</li>
 * </ul>
*/
public class GasParticle extends FluidStackParticle {

    private static final int TICKS_PER_BLOCK = BubbleCapBlockEntity.getTankCapacity() / BubbleCapBlockEntity.getTransferRate();
    private static final float VERTICAL_SPEED = 1f / TICKS_PER_BLOCK;
    private final boolean isDistillation;

    private final float blockHeight;

    private GasParticle(ClientLevel level, FluidStack fluid, ParticleType<GasParticleData> type, float blockHeight,
                        double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, fluid, x, y, z, vx, vy, vz);

        pickSprite(sprites);
        gravity = 0f;
        quadSize *= 6.0f;
        this.blockHeight = blockHeight;

        if (type == DestroyParticleTypes.DISTILLATION.get() && blockHeight != 0) {
            isDistillation = true;
            lifetime = (int) (this.blockHeight * TICKS_PER_BLOCK);
            yd += VERTICAL_SPEED + (double) (random.nextFloat() / 500.0F);
        } else {
            isDistillation = false;
            // Clamp the lerp factor to [0,1]: Mth.lerp does not clamp, and a busy vat vents a large
            // FluidStack every tick, so an unbounded factor scales the particle lifetime to tens of
            // thousands of ticks — the 6x-size evaporation particles then pile into a VRAM-exhausting
            // overdraw cloud under shaders.
            lifetime = (int) Mth.lerp(Mth.clamp(fluid.getAmount() / 4000f, 0f, 1f), 60, 300);
        }

        hasPhysics = !isDistillation;
    }

    @Override
    public void tick() {
        super.tick();
        xd += (double) (random.nextFloat() / 5000f * (float) (random.nextBoolean() ? 1 : -1));
        zd += (double) (random.nextFloat() / 5000f * (float) (random.nextBoolean() ? 1 : -1));

        if (isDistillation) {
            this.move(0d, VERTICAL_SPEED, 0d);
        }

        if (lifetime - age < TICKS_PER_BLOCK && alpha > 0.010f) {
            alpha -= 0.015f;
        }
    }

    @Override
    protected float getU0() {
        return sprite.getU0();
    }

    @Override
    protected float getU1() {
        return sprite.getU1();
    }

    @Override
    protected float getV0() {
        return sprite.getV0();
    }

    @Override
    protected float getV1() {
        return sprite.getV1();
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected boolean canEvaporate() {
        return false;
    }

    public static class Provider implements ParticleProvider<GasParticleData> {
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        @Nullable
        public Particle createParticle(GasParticleData data, ClientLevel level,
                                       double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new GasParticle(level, data.getFluid(), data.getType(), data.getBlockHeight(),
                x, y, z, vx, vy, vz, spriteSet);
        }
    }
}
