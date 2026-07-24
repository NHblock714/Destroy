package petrolpark.mc.destroy.content.processing.trypolithography;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import petrolpark.mc.library.util.BinaryMatrix4x4;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;

import petrolpark.mc.destroy.Destroy;

/**
 * Server-side registry of named 4×4 circuit patterns (keyed by {@link ResourceLocation}) loaded
 * from {@code data/<ns>/destroy_compat/circuit_patterns/*.json}. Each entry is either an explicit
 * bitmask or a generator key ({@code "easy"} / {@code "hard"}) that randomly produces a fresh
 * pattern on first query. Patterns persist across server restarts via {@link SavedData} so that
 * players can rely on consistent circuit recipes within a world.
*/
public class CircuitPatternHandler extends SavedData {

    public Random random = new Random();

    public final Listener RELOAD_LISTENER = new Listener();

    public static final Map<String, Supplier<CircuitPatternGenerator>> GENERATOR_KEYS = new HashMap<>();

    private final Map<ResourceLocation, CircuitPatternGenerator> PATTERN_GENERATORS = new HashMap<>();
    private final Map<ResourceLocation, Integer> GENERATED_PATTERNS = new HashMap<>();

    static {
        GENERATOR_KEYS.put("easy", CircuitPatternGenerator.Easy::new);
        GENERATOR_KEYS.put("hard", CircuitPatternGenerator.Hard::new);
    }

    public Integer getPattern(ResourceLocation location) {
        if (!GENERATED_PATTERNS.containsKey(location) && !PATTERN_GENERATORS.containsKey(location)) {
            throw new IllegalStateException("No such circuit pattern " + location.toString());
        }
        return GENERATED_PATTERNS.computeIfAbsent(location, rl -> {
            CircuitPatternGenerator gen = PATTERN_GENERATORS.get(rl);
            if (gen == null) return null;
            setDirty();
            return gen.generatePattern();
        });
    }

    public Map<ResourceLocation, Integer> getAllPatterns() {
        Map<ResourceLocation, Integer> patterns = new HashMap<>(PATTERN_GENERATORS.size());
        for (ResourceLocation rl : PATTERN_GENERATORS.keySet()) patterns.put(rl, getPattern(rl));
        return patterns;
    }

    public Set<ResourceLocation> getPatternsWithGenerators() {
        return PATTERN_GENERATORS.keySet();
    }

    public void removePattern(ResourceLocation pattern) {
        GENERATED_PATTERNS.remove(pattern);
    }

    public void setPatterns(Map<ResourceLocation, Integer> patterns) {
        GENERATED_PATTERNS.putAll(patterns);
    }

    public void onLevelLoaded(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null || server.overworld() != level) return;
        random = new Random();
        server.overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(() -> this, (tag, registries) -> read(tag)),
            "destroy_circuit_pattern");
    }

    public void onLevelUnloaded(LevelAccessor level) {
        MinecraftServer server = level.getServer();
        if (server == null || server.overworld() != level) return;
        GENERATED_PATTERNS.clear();
    }

    
    @Override
    public CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider registries) {
        GENERATED_PATTERNS.forEach((rl, pattern) -> {
            compoundTag.putShort(rl.toString(), (short) (pattern - Short.MAX_VALUE));
        });
        return compoundTag;
    }

    /**
 * Read saved data back into this handler. 1.21's Factory takes
 * {@code BiFunction<CompoundTag, HolderLookup.Provider, T>} — {@link #onLevelLoaded} wraps
 * this method in a lambda that ignores the Provider arg (same rationale as
 * {@link #save}: no registry-aware fields).
*/
    public CircuitPatternHandler read(CompoundTag tag) {
        GENERATED_PATTERNS.clear();
        tag.getAllKeys().forEach(key -> {
            GENERATED_PATTERNS.put(ResourceLocation.parse(key), (int) Short.MAX_VALUE + (int) tag.getShort(key));
        });
        return this;
    }

    public interface CircuitPatternGenerator {

        int generatePattern();

        class Specific implements CircuitPatternGenerator {

            public final int pattern;

            public Specific(int pattern) {
                this.pattern = pattern;
            }

            @Override
            public int generatePattern() {
                return pattern;
            }
        }

        /** Recommended for one-off crafts.*/
        class Easy implements CircuitPatternGenerator {

            public List<Integer> positions = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15));

            @Override
            public int generatePattern() {
                int pattern = 0;
                Collections.shuffle(positions);
                for (int i = 0; i < 3; i++) pattern |= (1 << positions.get(i));
                return pattern;
            }
        }

        /** Recommended for something which should be automated.*/
        class Hard implements CircuitPatternGenerator {

            public static final Integer[] outer = new Integer[] { 0, 3, 12, 15 };
            public static final Integer[] inner = new Integer[] { 5, 6, 9, 10 };
            public static final Integer[] right = new Integer[] { 2, 4, 11, 13 };
            public static final Integer[] left  = new Integer[] { 1, 7, 8, 14 };

            public static final Integer[] countWeights = new Integer[] { 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4 };

            @Override
            public int generatePattern() {
                int pattern = 0;
                for (Integer[] set : new Integer[][] { outer, inner, left, right }) {
                    List<Integer> values = Arrays.asList(set);
                    Collections.shuffle(values);
                    for (int i = 0; i < countWeights[Destroy.CIRCUIT_PATTERN_HANDLER.random.nextInt(countWeights.length)]; i++) {
                        pattern |= (1 << values.get(i));
                    }
                }
                return pattern;
            }
        }
    }

    public class Listener extends SimpleJsonResourceReloadListener {

        private static final Gson GSON = new Gson();

        public Listener() {
            super(GSON, "destroy_compat/circuit_patterns");
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
            for (Entry<ResourceLocation, JsonElement> entry : map.entrySet()) {
                ResourceLocation rl = entry.getKey();
                try {
                    String generationString = entry.getValue().getAsJsonObject().get("pattern").getAsString();
                    CircuitPatternGenerator generator;
                    if (GENERATOR_KEYS.containsKey(generationString)) {
                        generator = GENERATOR_KEYS.get(generationString).get();
                    } else if (generationString.length() == 16) {
                        int pattern = 0;
                        for (int i = 0; i < 16; i++) {
                            if (generationString.charAt(i) != ' ') pattern = BinaryMatrix4x4.set1(pattern, i);
                        }
                        generator = new CircuitPatternGenerator.Specific(pattern);
                    } else {
                        throw new JsonSyntaxException("Circuit patterns must specify an exact pattern or a circuit pattern generator");
                    }
                    PATTERN_GENERATORS.put(rl, generator);
                } catch (Throwable e) {
                    throw new JsonSyntaxException("Could not read circuit pattern generator '" + rl + "': ", e);
                }
            }
            // Broadcast to all clients; swallow exceptions defensively (sending before server fully up)
            try {
                CatnipServices.NETWORK.sendToAllClients(new CircuitPatternsS2CPacket(GENERATED_PATTERNS));
            } catch (Throwable e) {
            }
        }
    }
}
