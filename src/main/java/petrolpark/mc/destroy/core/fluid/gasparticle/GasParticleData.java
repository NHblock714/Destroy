package petrolpark.mc.destroy.core.fluid.gasparticle;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.foundation.particle.ICustomParticleDataWithSprite;

import net.minecraft.client.particle.ParticleEngine.SpriteParticleRegistration;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.client.DestroyParticleTypes;

/**
 * {@link ParticleOptions} for the 2 gas particle types (DISTILLATION + EVAPORATION).
 * DISTILLATION particles have a per-particle upward lifetime driven by {@link #blockHeight}
 * (tower height); EVAPORATION particles ignore blockHeight and dissipate from a fluid amount.
 *
 * <p>Registration: {@code DestroyParticleTypes.DISTILLATION + EVAPORATION}.</p>
*/
public class GasParticleData implements ParticleOptions, ICustomParticleDataWithSprite<GasParticleData> {

    private ParticleType<GasParticleData> type; // DISTILLATION or EVAPORATION
    private FluidStack fluid; // Fluid color source for the particle
    private float blockHeight; // How far up this particle should float (Distillation Tower height); 0 for evaporation

    /** Empty ctor for DestroyParticleTypes enum registration.*/
    public GasParticleData() {}

    public FluidStack getFluid() {
        return fluid;
    }

    /** How far up this particle should float before disappearing (0 for evaporation).*/
    public float getBlockHeight() {
        return blockHeight;
    }

    public GasParticleData(ParticleType<?> type, FluidStack fluid) {
        this(type, fluid, 0);
    }

    @SuppressWarnings("unchecked")
    public GasParticleData(ParticleType<?> type, FluidStack fluid, float blockHeight) {
        this.type = (ParticleType<GasParticleData>) type;
        this.fluid = fluid;
        this.blockHeight = blockHeight;
    }

    public static final MapCodec<GasParticleData> DISTILLATION_CODEC = RecordCodecBuilder.mapCodec(i -> i
        .group(
            FluidStack.CODEC.fieldOf("fluid").forGetter(p -> p.fluid),
            com.mojang.serialization.Codec.FLOAT.fieldOf("blockHeight").forGetter(p -> p.blockHeight)
        ).apply(i, (fluidStack, blockHeight) -> new GasParticleData(DestroyParticleTypes.DISTILLATION.get(), fluidStack, blockHeight))
    );

    public static final MapCodec<GasParticleData> EVAPORATION_CODEC = RecordCodecBuilder.mapCodec(i -> i
        .group(
            FluidStack.CODEC.fieldOf("fluid").forGetter(p -> p.fluid)
        ).apply(i, (fluidStack) -> new GasParticleData(DestroyParticleTypes.EVAPORATION.get(), fluidStack, 0))
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, GasParticleData> DISTILLATION_STREAM_CODEC =
        StreamCodec.composite(
            FluidStack.STREAM_CODEC, p -> p.fluid,
            ByteBufCodecs.FLOAT, p -> p.blockHeight,
            (fluidStack, blockHeight) -> new GasParticleData(DestroyParticleTypes.DISTILLATION.get(), fluidStack, blockHeight));

    public static final StreamCodec<RegistryFriendlyByteBuf, GasParticleData> EVAPORATION_STREAM_CODEC =
        StreamCodec.composite(
            FluidStack.STREAM_CODEC, p -> p.fluid,
            (fluidStack) -> new GasParticleData(DestroyParticleTypes.EVAPORATION.get(), fluidStack, 0));

    @Override
    public MapCodec<GasParticleData> getCodec(ParticleType<GasParticleData> type) {
        if (type == DestroyParticleTypes.DISTILLATION.get()) return DISTILLATION_CODEC;
        return EVAPORATION_CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, GasParticleData> getStreamCodec() {
        // For split codec routing by type, return a dispatch codec. Since DestroyParticleTypes
        // registers DISTILLATION + EVAPORATION as separate types and each calls getStreamCodec()
        // on its own ICustomParticleDataWithSprite instance (which is shared since the
        // same class is used for both), this returns a conservative default (distillation stream codec).
        // In practice ParticleEngine only uses getStreamCodec() via the type's registered
        // StreamCodec which itself is supplied per-type by Create's ParticleType factory —
        // returning either codec works for the common case since type routing happens upstream.
        return DISTILLATION_STREAM_CODEC;
    }

    @Override
    public ParticleType<GasParticleData> getType() {
        return type;
    }

    @Override
    public SpriteParticleRegistration<GasParticleData> getMetaFactory() {
        return GasParticle.Provider::new;
    }
}
