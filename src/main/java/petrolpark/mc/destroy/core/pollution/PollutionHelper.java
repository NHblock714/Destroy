package petrolpark.mc.destroy.core.pollution;

import java.util.Optional;
import java.util.function.Supplier;

import org.joml.Vector3f;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.DestroyAttachmentTypes;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyDataMapTypes;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.DestroyPollutionTypes;
import petrolpark.mc.destroy.DestroyRegistries;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.config.DestroyConfigs;

public class PollutionHelper {

    public static final boolean isPollutionEnabled() {
        // Server config may not be loaded yet when client-side cosmetic hooks fire
        // (notably {@link petrolpark.mc.destroy.client.FogHandler#colorFog} on the
        // title screen / menu render path, before any world is open). NeoForge's
        // {@code ModConfigSpec$ConfigValue.get} hard-fails with
        // {@code IllegalStateException: Cannot get config value before config is loaded}
        // which would crash the client just from looking at the main menu. Treat
        // "not loaded yet" as "not enabled" so callers gate themselves out cleanly;
        // by the time a world is loaded the config is available and this returns the
        // real configured value.
        try {
            return DestroyConfigs.server().pollution.enablePollution.get();
        } catch (IllegalStateException e) {
            return false;
        }
    };

    /**
 * Get the outdoor ("room") temperature at the given position, accounting for pollution-driven
 * temperature deltas + biome base temperature. Used by Basin/Vat chemistry reaction rate.
 *
 * @return Temperature in kelvins
*/
    public static final float getLocalTemperature(Level level, BlockPos pos) {
        return level.getData(DestroyAttachmentTypes.LEVEL_POLLUTION).getOutdoorTemperature()
            + 10f * level.getBiome(pos).value().getBaseTemperature();
    };

    public static final int getPollution(Level level, PollutionType<Level> pollutionType) {
        return level.getData(DestroyAttachmentTypes.LEVEL_POLLUTION).getPollution(pollutionType);
    };

    public static final float getPollutionProportion(Level level, PollutionType<Level> pollutionType) {
        return (float)getPollution(level, pollutionType) / (float)getLevelPollutionTypeProperties(pollutionType).max();
    };
  
    public static final int setPollution(Level level, PollutionType<Level> pollutionType, int value) {
        return level.getData(DestroyAttachmentTypes.LEVEL_POLLUTION).setPollution(pollutionType, value);
    };

    public static final int changePollution(Level level, PollutionType<Level> pollutionType, int change) {
        return level.getData(DestroyAttachmentTypes.LEVEL_POLLUTION).changePollution(pollutionType, change);
    };

    public static final int getPollution(Level level, Supplier<PollutionType<Level>> pollutionType) {
        return level.getData(DestroyAttachmentTypes.LEVEL_POLLUTION).getPollution(pollutionType.get());
    };

    public static final float getPollutionProportion(Level level, Supplier<PollutionType<Level>> pollutionType) {
        return getPollutionProportion(level, pollutionType.get());
    };
  
    public static final int setPollution(Level level, Supplier<PollutionType<Level>> pollutionType, int value) {
        return level.getData(DestroyAttachmentTypes.LEVEL_POLLUTION).setPollution(pollutionType.get(), value);
    };

    public static final int changePollution(Level level, Supplier<PollutionType<Level>> pollutionType, int change) {
        return level.getData(DestroyAttachmentTypes.LEVEL_POLLUTION).changePollution(pollutionType.get(), change);
    };

    public static final int getPollution(ChunkAccess chunk, PollutionType<ChunkAccess> pollutionType) {
        return chunk.getData(DestroyAttachmentTypes.CHUNK_POLLUTION).getPollution(pollutionType);
    };

    public static final float getPollutionProportion(ChunkAccess chunk, PollutionType<ChunkAccess> pollutionType) {
        return (float)getPollution(chunk, pollutionType) / (float)getChunkPollutionTypeProperties(pollutionType).max();
    };
  
    public static final int setPollution(ChunkAccess chunk, PollutionType<ChunkAccess> pollutionType, int value) {
        return chunk.getData(DestroyAttachmentTypes.CHUNK_POLLUTION).setPollution(pollutionType, value);
    };

    public static final int changePollution(ChunkAccess chunk, PollutionType<ChunkAccess> pollutionType, int change) {
        return chunk.getData(DestroyAttachmentTypes.CHUNK_POLLUTION).changePollution(pollutionType, change);
    };

    @SuppressWarnings("unchecked")
    public static final int getPollution(Level level, BlockPos pos, PollutionType<?> pollutionType) {
        if (!pollutionType.chunk) try {
            final PollutionType<Level> levelPollutionType = (PollutionType<Level>)pollutionType;
            return getPollution(level, levelPollutionType);
        } catch (ClassCastException e) {};
        try {
            final PollutionType<ChunkAccess> chunkPollutionType = (PollutionType<ChunkAccess>)pollutionType;
            return getPollution(level.getChunk(pos), chunkPollutionType);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("PollutionType must be of Level or Chunk");
        }
    };

    @SuppressWarnings("unchecked")
    public static final float getPollutionProportion(Level level, BlockPos pos, PollutionType<?> pollutionType) {
        if (!pollutionType.chunk) try {
            final PollutionType<Level> levelPollutionType = (PollutionType<Level>)pollutionType;
            return getPollutionProportion(level, levelPollutionType);
        } catch (ClassCastException e) {};
        try {
            final PollutionType<ChunkAccess> chunkPollutionType = (PollutionType<ChunkAccess>)pollutionType;
            return getPollutionProportion(level.getChunk(pos), chunkPollutionType);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("PollutionType must be of Level or Chunk");
        }
        
    };
  
    @SuppressWarnings("unchecked")
    public static final int setPollution(Level level, BlockPos pos, PollutionType<?> pollutionType, int value) {
        if (!pollutionType.chunk) try {
            final PollutionType<Level> levelPollutionType = (PollutionType<Level>)pollutionType;
            return setPollution(level, levelPollutionType, value);
        } catch (ClassCastException e) {};
        try {
            final PollutionType<ChunkAccess> chunkPollutionType = (PollutionType<ChunkAccess>)pollutionType;
            return setPollution(level.getChunk(pos), chunkPollutionType, value);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("PollutionType must be of Level or Chunk");
        }
    };

    @SuppressWarnings("unchecked")
    public static final int changePollution(Level level, BlockPos pos, PollutionType<?> pollutionType, int change) {
        if (!pollutionType.chunk) try {
            final PollutionType<Level> levelPollutionType = (PollutionType<Level>)pollutionType;
            return changePollution(level, levelPollutionType, change);
        } catch (ClassCastException e) {};
        try {
            final PollutionType<ChunkAccess> chunkPollutionType = (PollutionType<ChunkAccess>)pollutionType;
            return changePollution(level.getChunk(pos), chunkPollutionType, change);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("PollutionType must be of Level or Chunk");
        }
    };

    public static final int getPollution(Level level, BlockPos pos, Supplier<PollutionType<ChunkAccess>> pollutionType) {
        return getPollution(level, pos, pollutionType.get());
    };

    public static final float getPollutionProportion(Level level, BlockPos pos, Supplier<PollutionType<ChunkAccess>> pollutionType) {
        return getPollutionProportion(level, pos, pollutionType.get());
    };
  
    public static final int setPollution(Level level, BlockPos pos, Supplier<PollutionType<ChunkAccess>> pollutionType, int value) {
        return setPollution(level, pos, pollutionType, value);
    };

    public static final int changePollution(Level level, BlockPos pos, Supplier<PollutionType<ChunkAccess>> pollutionType, int change) {
        return changePollution(level, pos, pollutionType.get(), change);
    };

    public static final PollutionType.Properties getLevelPollutionTypeProperties(PollutionType<Level> pollutionType) {
        return getLevelPollutionTypeProperties(DestroyRegistries.LEVEL_POLLUTION_TYPES.wrapAsHolder(pollutionType));  
    };

    public static final PollutionType.Properties getLevelPollutionTypeProperties(Holder<PollutionType<Level>> pollutionTypeHolder) {
        return Optional.ofNullable(pollutionTypeHolder.getData(DestroyDataMapTypes.LEVEL_POLLUTION_PROPERTIES)).orElse(PollutionType.Properties.DEFAULT);
    };

    public static final PollutionType.Properties getChunkPollutionTypeProperties(PollutionType<ChunkAccess> pollutionType) {
        return getChunkPollutionTypeProperties(DestroyRegistries.CHUNK_POLLUTION_TYPES.wrapAsHolder(pollutionType));  
    };

    public static final PollutionType.Properties getChunkPollutionTypeProperties(Holder<PollutionType<ChunkAccess>> pollutionTypeHolder) {
        return Optional.ofNullable(pollutionTypeHolder.getData(DestroyDataMapTypes.CHUNK_POLLUTION_PROPERTIES)).orElse(PollutionType.Properties.DEFAULT);
    };

    @SuppressWarnings("unchecked")
    public static final PollutionType.Properties getPollutionTypeProperties(PollutionType<?> pollutionType) {
        if (!pollutionType.chunk) try {
            final PollutionType<Level> levelPollutionType = (PollutionType<Level>)pollutionType;
            return getLevelPollutionTypeProperties(levelPollutionType);
        } catch (ClassCastException e) {}
        try {
            final PollutionType<ChunkAccess> chunkPollutionType = (PollutionType<ChunkAccess>)pollutionType;
            return getChunkPollutionTypeProperties(chunkPollutionType);
        } catch (ClassCastException e) {
            throw new IllegalArgumentException("PollutionType must be of Level or Chunk");
        }
    };

    public static final DustParticleOptions getCropGrowthFailureParticles() {
        return new DustParticleOptions(new Vector3f(109 / 256f, 77 / 256f, 14 / 256f), 1f);
    };

    // =============================================================
    // fluid-driven pollution (non-mixture path).
    // 1) fluid 是 mixture → polluteMixture(...) — 依赖 chemistry engine（本仓尚缺）
    // 2) 普通 fluid → for (PollutionType t : enum.values()) 比对 fluidTag
    // 新架构下 PollutionType 已是 Registry + DataMap，
    // 这里实现分支 2（非 mixture 路径）。mixture 分支先 no-op，
    // 等 chemistry engine 批到位后在方法顶部加一个 `if (DestroyFluids.isMixture(stack))`
    // 路由即可，**调用者签名永久稳定**。
    // <1 时按概率 changePollution(+1)，>=1 时取整加。
    // =============================================================

    /**
 * Release the given Fluids into the environment.
*/
    public static final void pollute(Level level, BlockPos pos, FluidStack... fluidStacks) {
        pollute(level, pos, 1f, fluidStacks);
    };

    /**
 * Release the given Fluids with a pollution-amount multiplier.
*/
    public static final void pollute(Level level, BlockPos pos, float multiplier, FluidStack... fluidStacks) {
        pollute(level, pos, multiplier, true, fluidStacks);
    };

    /**
 * As {@link #pollute(Level, BlockPos, float, FluidStack...)}, but lets a caller suppress the
 * cosmetic evaporation particles while still applying the pollution. A continuous emitter (the
 * Vat vent pollutes every tick) passes a throttled flag so the 6x-size smoke particles don't
 * pile into a shader-crushing overdraw cloud.
*/
    public static final void pollute(Level level, BlockPos pos, float multiplier, boolean emitParticles, FluidStack... fluidStacks) {
        if (level.isClientSide()) return;
        for (FluidStack stack : fluidStacks) {
            polluteSingleFluid(level, pos, multiplier, stack);
            // emit visible evaporation particles to all clients tracking this position
            // (64-block range matches PollutingOpenEndedPipeEffectHandler).
            if (emitParticles && !stack.isEmpty() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClientsAround(
                    serverLevel, pos, 64d,
                    new petrolpark.mc.destroy.core.fluid.gasparticle.EvaporatingFluidS2CPacket(pos, stack));
            }
        };
    };

    /**
 * Mixture path lives in {@link #polluteMixture}.
*/
    private static final void polluteSingleFluid(Level level, BlockPos pos, float multiplier, FluidStack stack) {
        if (stack.isEmpty()) return;
        if (DestroyFluids.isMixture(stack)) {
            polluteMixture(level, pos, multiplier, stack);
            return;
        };

        // LEVEL-scoped pollution types
        for (PollutionType<Level> type : DestroyRegistries.LEVEL_POLLUTION_TYPES) {
            final Holder<PollutionType<Level>> holder = DestroyRegistries.LEVEL_POLLUTION_TYPES.wrapAsHolder(type);
            final FluidPollutionEntry entry = holder.getData(DestroyDataMapTypes.LEVEL_POLLUTION_FLUID_TAG);
            if (entry == null) continue;
            if (!stack.getFluid().defaultFluidState().is(entry.fluidTag())) continue;
            applyFractionalPollution(level, pos, type, multiplier * entry.multiplier() * (float) stack.getAmount() / 250f);
        };

        // CHUNK-scoped pollution types
        for (PollutionType<ChunkAccess> type : DestroyRegistries.CHUNK_POLLUTION_TYPES) {
            final Holder<PollutionType<ChunkAccess>> holder = DestroyRegistries.CHUNK_POLLUTION_TYPES.wrapAsHolder(type);
            final FluidPollutionEntry entry = holder.getData(DestroyDataMapTypes.CHUNK_POLLUTION_FLUID_TAG);
            if (entry == null) continue;
            if (!stack.getFluid().defaultFluidState().is(entry.fluidTag())) continue;
            applyFractionalPollution(level, pos, type, multiplier * entry.multiplier() * (float) stack.getAmount() / 250f);
        };
    };

    private static final void applyFractionalPollution(Level level, BlockPos pos, PollutionType<?> type, float pollutionAmount) {
        if (pollutionAmount <= 0f) return;
        if (pollutionAmount < 1f) {
            if (level.random.nextFloat() <= pollutionAmount) changePollution(level, pos, type, 1);
        } else {
            changePollution(level, pos, type, (int) pollutionAmount);
        };
    };

    // =============================================================
    // mixture-driven pollution.
    // 分配到对应 PollutionType：
    // Tags.GREENHOUSE → GREENHOUSE (level)
    // Tags.OZONE_DEPLETER → OZONE_DEPLETION (level)
    // Tags.ACID_RAIN → ACID_RAIN (level)
    // Tags.SMOG → SMOG (chunk)
    // 污染强度 = molecule 的 concentration × stack.getAmount() / 250f × multiplier
    // 即 250 mB × 1M 浓度 × 1 打 tag 的 molecule = 1 点污染
    // 迁移点：
    // - Mixture 来自 DestroyDataComponents.MIXTURE 而非 `stack.getOrCreateChildTag("Mixture")`
    // - PollutionType 引用改为 Registry-backed DestroyPollutionTypes.X.get()
    // =============================================================

    /**
 * Release the contents of a Mixture FluidStack into the environment — each Molecule contributes
 * to the pollution type matching its tag (GREENHOUSE / OZONE_DEPLETER / ACID_RAIN / SMOG).
*/
    private static final void polluteMixture(Level level, BlockPos pos, float multiplier, FluidStack stack) {
        if (!DestroyFluids.isMixture(stack)) return;
        if (level.isClientSide()) return;
        final ReadOnlyMixture mixture = ReadOnlyMixture.readNBT(ReadOnlyMixture::new,
            stack.getOrDefault(DestroyDataComponents.MIXTURE, new net.minecraft.nbt.CompoundTag()));
        if (mixture.isEmpty()) return;

        final float buckets = (float) stack.getAmount() / 250f * multiplier;

        for (LegacySpecies molecule : mixture.getContents(true)) {
            final float moleculePollutionUnits = mixture.getConcentrationOf(molecule) * buckets;
            if (moleculePollutionUnits <= 0f) continue;

            if (molecule.hasTag(DestroyMolecules.Tags.GREENHOUSE)) {
                applyFractionalPollution(level, pos, DestroyPollutionTypes.GREENHOUSE.get(), moleculePollutionUnits);
            }
            if (molecule.hasTag(DestroyMolecules.Tags.OZONE_DEPLETER)) {
                applyFractionalPollution(level, pos, DestroyPollutionTypes.OZONE_DEPLETION.get(), moleculePollutionUnits);
            }
            if (molecule.hasTag(DestroyMolecules.Tags.ACID_RAIN)) {
                applyFractionalPollution(level, pos, DestroyPollutionTypes.ACID_RAIN.get(), moleculePollutionUnits);
            }
            if (molecule.hasTag(DestroyMolecules.Tags.SMOG)) {
                applyFractionalPollution(level, pos, DestroyPollutionTypes.SMOG.get(), moleculePollutionUnits);
            }
        };
    };
};
