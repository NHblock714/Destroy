package petrolpark.mc.destroy;

import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import petrolpark.mc.destroy.core.explosion.SmartExplosion;

/**
 * Destroy's {@link LootContextParam}s.
 *
 * <p>{@link #SMART_EXPLOSION} is added to the loot context by {@link SmartExplosion#explodeBlock};
 * {@code ObliterationCondition} reads it back to decide whether a block gives its obliteration
 * drops.</p>
 */
public class DestroyLootContextParams {

    public static final LootContextParam<SmartExplosion> SMART_EXPLOSION = create("smart_explosion");

    private static <T> LootContextParam<T> create(String id) {
        return new LootContextParam<>(Destroy.asResource(id));
    }
}
