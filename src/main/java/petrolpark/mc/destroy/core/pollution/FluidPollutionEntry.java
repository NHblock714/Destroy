package petrolpark.mc.destroy.core.pollution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

/**
 * DataMap value attached to a {@link PollutionType}: which Fluid tag counts towards that type of
 * pollution, and how much a Fluid so tagged contributes per 250mB relative to the baseline.
 */
public record FluidPollutionEntry(TagKey<Fluid> fluidTag, float multiplier) {

    public static final Codec<FluidPollutionEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        TagKey.codec(Registries.FLUID).fieldOf("fluid_tag").forGetter(FluidPollutionEntry::fluidTag),
        Codec.FLOAT.optionalFieldOf("multiplier", 1.0f).forGetter(FluidPollutionEntry::multiplier)
    ).apply(instance, FluidPollutionEntry::new));
}
