package petrolpark.mc.destroy.core.pollution;

import net.createmod.ponder.api.element.WorldSectionElement;
import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;

import petrolpark.mc.destroy.DestroyPollutionTypes;

/**
 * Ponder scene instruction that sets the current SMOG pollution value and forces all
 * {@link WorldSectionElement}s to re-render (so the smog-responsive Block tint / fog immediately
 * shows the new pollution level).
 *
 * <ul>
 * <li>**Attachment-based per-Level + per-Chunk storage**: {@code DestroyAttachmentTypes.LEVEL_POLLUTION}
 * holds LevelPollution (Level-scoped types like GREENHOUSE / ACID_RAIN / OZONE_DEPLETION);
 * {@code CHUNK_POLLUTION} holds ChunkPollution (chunk-scoped types like SMOG /
 * RADIOACTIVITY).</li>
 * <li>**Per-type registry** via {@code DestroyRegistries.LEVEL_POLLUTION_TYPES +
 * CHUNK_POLLUTION_TYPES} DeferredRegister. SMOG is registered as
 * {@code DestroyPollutionTypes.SMOG = REGISTRATE.chunkPollutionType("smog", ...)} —
 * returns {@code RegistryEntry<PollutionType<ChunkAccess>, PollutionType<ChunkAccess>>},
 * i.e. **SMOG is chunk-scoped**, which is why the tick below writes it to a grid of chunks
 * rather than to the Level.</li>
 * </ul>
 */
public class SmogPonderInstruction extends PonderInstruction {

    public final int value;

    public SmogPonderInstruction(int value) {
        this.value = Mth.clamp(value, 0, getSmogMax());
    }

    /**
 * Read the configured SMOG max from the {@link PollutionType.Properties} data-map entry.
*/
    private static int getSmogMax() {
        return PollutionHelper.getChunkPollutionTypeProperties(DestroyPollutionTypes.SMOG.get()).max();
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void tick(PonderScene scene) {
        Level world = scene.getWorld();
        // 3×3 chunk grid centered at origin — covers typical Ponder scene bounds (~48 blocks wide).
        // SMOG is chunk-scoped, so the value has to be written to every chunk the scene touches:
        // small scenes (~11×11 blocks) mostly fit in chunk (0,0), larger ones spill into the
        // eight neighbours.
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                ChunkAccess chunk = world.getChunk(cx, cz);
                if (chunk != null) {
                    PollutionHelper.setPollution(chunk, DestroyPollutionTypes.SMOG.get(), value);
                }
            }
        }
        scene.forEach(WorldSectionElement.class, WorldSectionElement::queueRedraw);
    }
}
