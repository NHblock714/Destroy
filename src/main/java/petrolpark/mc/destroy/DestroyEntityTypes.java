package petrolpark.mc.destroy;

import static petrolpark.mc.destroy.Destroy.REGISTRATE;

import com.tterrag.registrate.util.entry.EntityEntry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.EntityFactory;
import petrolpark.mc.destroy.core.explosion.PrimedBombEntity;
import petrolpark.mc.destroy.core.explosion.PrimedBombEntityRenderer;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveEntity;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveEntityRenderer;

/**
 * Destroy 的 EntityType 注册表。
*/
public class DestroyEntityTypes {

    // Legacy explosives
    public static final EntityEntry<PrimedBombEntity.Anfo> PRIMED_ANFO =
        bomb("primed_anfo", PrimedBombEntity.Anfo::new);
    public static final EntityEntry<PrimedBombEntity.PicricAcid> PRIMED_PICRIC_ACID =
        bomb("primed_picric_acid", PrimedBombEntity.PicricAcid::new);
    public static final EntityEntry<PrimedBombEntity.Cordite> PRIMED_CORDITE =
        bomb("primed_cordite", PrimedBombEntity.Cordite::new);
    public static final EntityEntry<PrimedBombEntity.Nitrocellulose> PRIMED_NITROCELLULOSE =
        bomb("primed_nitrocellulose", PrimedBombEntity.Nitrocellulose::new);

    // mixed explosive · dedicated MixedExplosiveEntityRenderer inlined
    // (not via bomb() helper, which hard-wires PrimedBombEntityRenderer).
    public static final EntityEntry<MixedExplosiveEntity> PRIMED_CUSTOM_EXPLOSIVE =
        REGISTRATE.<MixedExplosiveEntity>entity("primed_custom_explosive", MixedExplosiveEntity::new, MobCategory.MISC)
            .properties(p -> p.fireImmune().sized(0.98f, 0.98f).clientTrackingRange(10).updateInterval(10))
            .renderer(() -> MixedExplosiveEntityRenderer::new)
            .register();

    private static <T extends PrimedBombEntity> EntityEntry<T> bomb(String name, EntityFactory<T> factory) {
        return REGISTRATE.entity(EntityType.TNT, name, factory, MobCategory.MISC)
            .renderer(() -> PrimedBombEntityRenderer::new)
            .register();
    }

    public static void register() {
        // class-load trigger
    }
}
