package petrolpark.mc.destroy.core.pollution;

import java.util.Collections;
import java.util.Set;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import petrolpark.mc.library.core.data.numberProvider.IEstimableNumberProvider;
import petrolpark.mc.library.core.data.numberProvider.NumberEstimate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.providers.number.LootNumberProviderType;
import net.minecraft.world.phys.Vec3;
import petrolpark.mc.destroy.DestroyNumberProviderTypes;
import petrolpark.mc.destroy.DestroyRegistries;

public record PollutionNumberProvider(Either<Holder<PollutionType<Level>>, Holder<PollutionType<ChunkAccess>>> pollutionType, boolean proportion) implements IEstimableNumberProvider {

    public static final MapCodec<PollutionNumberProvider> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.either(DestroyRegistries.LEVEL_POLLUTION_TYPES.holderByNameCodec(), DestroyRegistries.CHUNK_POLLUTION_TYPES.holderByNameCodec()).fieldOf("pollution_type").forGetter(PollutionNumberProvider::pollutionType),
        Codec.BOOL.optionalFieldOf("proportion", true).forGetter(PollutionNumberProvider::proportion)
    ).apply(instance, PollutionNumberProvider::new));

    @Override
    public float getFloat(LootContext lootContext) {
        // Chunk-pollution branch needs a spatial anchor (ORIGIN). Vanilla fishing loot
        // sets do declare ORIGIN as required, but third-party mods can invoke a fishing
        // loot table with a hand-built LootParams that omits it (Alex's Mobs'
        // ShoebillAIFish.spawnFishingLoot is one such caller). Crashing the world tick
        // with NoSuchElementException("minecraft:origin") is unacceptable — fall back to
        // 0 (treat as no pollution) when ORIGIN is absent. Level-pollution branch is
        // unaffected since it has no ORIGIN dependency.
        final int level = pollutionType().map(
            h -> PollutionHelper.getPollution(lootContext.getLevel(), h.value()),
            h -> {
                Vec3 origin = lootContext.getParamOrNull(LootContextParams.ORIGIN);
                if (origin == null) return 0;
                return PollutionHelper.getPollution(lootContext.getLevel(), BlockPos.containing(origin), h.value());
            }
        );
        return proportion() ? (float)level / (float)getMax() : level;
    };

    @Override
    public LootNumberProviderType getType() {
        return DestroyNumberProviderTypes.POLLUTION.get();
    };

    @Override
    public NumberEstimate getEstimate() {
        return proportion ? NumberEstimate.ranged(0f, 1f) : NumberEstimate.ranged(0f, getMax());
    };

    @Override
    public float getMaxFloat(LootContext context) {
        return getFloat(context);
    };

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return pollutionType().map(h -> Collections.emptySet(), h -> Collections.singleton(LootContextParams.ORIGIN));
    };

    private int getMax() {
        return pollutionType().map(h -> PollutionHelper.getLevelPollutionTypeProperties(h).max(), h -> PollutionHelper.getChunkPollutionTypeProperties(h).max());
    };
    
};
