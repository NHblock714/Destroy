package petrolpark.mc.destroy.core.fluid.gasparticle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.foundation.particle.ICustomParticleDataWithSprite;

import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.client.DestroyParticleTypes;

/**
 * {@link ParticleOptions} for boiling-fluid bubble particle (rises from hot fluid surfaces).
 *
 * <p>Registration: {@code DestroyParticleTypes.BOILING_FLUID_BUBBLE}.</p>
*/
public class BoilingFluidBubbleParticleData implements ParticleOptions, ICustomParticleDataWithSprite<BoilingFluidBubbleParticleData> {

    protected ParticleType<BoilingFluidBubbleParticleData> type;
    protected FluidStack fluid;

    /** Empty ctor for DestroyParticleTypes enum registration.*/
    public BoilingFluidBubbleParticleData() {}

    public FluidStack getFluid() {
        return fluid;
    }

    @SuppressWarnings("unchecked")
    public BoilingFluidBubbleParticleData(ParticleType<?> type, FluidStack fluid) {
        this.type = (ParticleType<BoilingFluidBubbleParticleData>) type;
        this.fluid = fluid;
    }

    public BoilingFluidBubbleParticleData(FluidStack fluid) {
        this(DestroyParticleTypes.BOILING_FLUID_BUBBLE.get(), fluid);
    }

    public static final MapCodec<BoilingFluidBubbleParticleData> CODEC = RecordCodecBuilder.mapCodec(i -> i
        .group(FluidStack.CODEC.fieldOf("fluid").forGetter(p -> p.fluid))
        .apply(i, fs -> new BoilingFluidBubbleParticleData(DestroyParticleTypes.BOILING_FLUID_BUBBLE.get(), fs))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BoilingFluidBubbleParticleData> STREAM_CODEC =
        StreamCodec.composite(
            FluidStack.STREAM_CODEC, p -> p.fluid,
            fs -> new BoilingFluidBubbleParticleData(DestroyParticleTypes.BOILING_FLUID_BUBBLE.get(), fs));

    @Override
    public MapCodec<BoilingFluidBubbleParticleData> getCodec(ParticleType<BoilingFluidBubbleParticleData> type) {
        return CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, BoilingFluidBubbleParticleData> getStreamCodec() {
        return STREAM_CODEC;
    }

    @Override
    public ParticleType<BoilingFluidBubbleParticleData> getType() {
        return type;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public ParticleEngine.SpriteParticleRegistration<BoilingFluidBubbleParticleData> getMetaFactory() {
        return BoilingFluidBubbleParticle.Provider::new;
    }
}
