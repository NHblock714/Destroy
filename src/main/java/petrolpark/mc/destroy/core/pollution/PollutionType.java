package petrolpark.mc.destroy.core.pollution;

import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import petrolpark.mc.library.util.codec.CodecHelper;
import com.simibubi.create.foundation.gui.AllIcons;

import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import petrolpark.mc.destroy.DestroyRegistries;

public class PollutionType<P extends IAttachmentHolder> {

    public static final Stream<PollutionType<?>> streamAll() {
        return Stream.concat(DestroyRegistries.LEVEL_POLLUTION_TYPES.stream(), DestroyRegistries.CHUNK_POLLUTION_TYPES.stream());
    };

    public final boolean chunk;
    protected final AllIcons icon;
    protected final String translationKey;
    protected Component name = null;

    public PollutionType(boolean chunk, AllIcons icon, String translationKey) {
        this.chunk = chunk;
        this.icon = icon;
        this.translationKey = translationKey;
    };

    public Component getName() {
        if (name == null) name = Component.translatable(translationKey);
        return name;
    };
    
    public static final record Properties(
        int max, int syncThreshold, float decayChancePerTick,
        int lightningStrikeChange, int treeGrowthChange,
        float breedingFailureChanceMultiplier, float growthFailureChanceMultiplier, float bonemealFailureChanceMultiplier, float villagerPriceMultiplier
    ) {

        public static final PollutionType.Properties DEFAULT = new PollutionType.Properties(
            65565, 1000, 0.002f,
            0, 0,
            0f, 0f, 0f, 0f
        );

        public static final Codec<PollutionType.Properties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CodecHelper.POS_INT.optionalFieldOf("max", DEFAULT.max()).forGetter(PollutionType.Properties::max),
            CodecHelper.POS_INT.optionalFieldOf("sync_threshold", DEFAULT.syncThreshold()).forGetter(PollutionType.Properties::syncThreshold),
            Codec.floatRange(0f, Float.MAX_VALUE).optionalFieldOf("decay_chance_per_tick", DEFAULT.decayChancePerTick()).forGetter(PollutionType.Properties::decayChancePerTick),
            Codec.INT.optionalFieldOf("lightning_strike_change", DEFAULT.lightningStrikeChange()).forGetter(PollutionType.Properties::lightningStrikeChange),
            Codec.INT.optionalFieldOf("tree_growth_change", DEFAULT.lightningStrikeChange()).forGetter(PollutionType.Properties::treeGrowthChange),
            Codec.floatRange(0f, 1f).optionalFieldOf("breeding_failure_chance_multiplier", DEFAULT.breedingFailureChanceMultiplier()).forGetter(PollutionType.Properties::breedingFailureChanceMultiplier),
            Codec.floatRange(0f, 1f).optionalFieldOf("growth_failure_chance_multiplier", DEFAULT.growthFailureChanceMultiplier()).forGetter(PollutionType.Properties::growthFailureChanceMultiplier),
            Codec.floatRange(0f, 1f).optionalFieldOf("bonemeal_failure_chance_multiplier", DEFAULT.bonemealFailureChanceMultiplier()).forGetter(PollutionType.Properties::bonemealFailureChanceMultiplier),
            Codec.FLOAT.optionalFieldOf("villager_price_multiplier", DEFAULT.villagerPriceMultiplier()).forGetter(PollutionType.Properties::villagerPriceMultiplier)
        ).apply(instance, PollutionType.Properties::new));
    };

    public static final record SpreadingProperties(float chance, float rate) {

        public static final PollutionType.SpreadingProperties DEFAULT = new PollutionType.SpreadingProperties(0.002f, 0.005f);

        public static final Codec<PollutionType.SpreadingProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.floatRange(0f, 1f).optionalFieldOf("chance", 0.002f).forGetter(PollutionType.SpreadingProperties::chance),
            Codec.floatRange(0f, 1f).optionalFieldOf("rate", 0.005f).forGetter(PollutionType.SpreadingProperties::rate)
        ).apply(instance, PollutionType.SpreadingProperties::new));
    };
};
