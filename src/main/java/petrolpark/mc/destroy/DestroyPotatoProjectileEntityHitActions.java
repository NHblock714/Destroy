package petrolpark.mc.destroy;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileEntityHitAction;
import com.simibubi.create.api.registry.CreateRegistries;

import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 1.21 registry-frozen crash fix (same pattern as DestroyItemAttributeTypes +
 * DestroyPotatoProjectileBlockHitActions).
*/
@SuppressWarnings({"rawtypes", "unchecked"})
public class DestroyPotatoProjectileEntityHitActions {

    private static final DeferredRegister<MapCodec<? extends PotatoProjectileEntityHitAction>> ACTIONS =
        (DeferredRegister) DeferredRegister.create(CreateRegistries.POTATO_PROJECTILE_ENTITY_HIT_ACTION, Destroy.MOD_ID);

    public static final Supplier<MapCodec<? extends PotatoProjectileEntityHitAction>> EXPLODE_ENTITY =
        ACTIONS.register("explode_entity", () -> ExplodeEntity.CODEC);

    /** Register the DeferredRegister onto the mod event bus — called from Destroy.java ctor.*/
    public static void register(IEventBus modEventBus) {
        ACTIONS.register(modEventBus);
    }

    
    public static void init() {}

    private record ExplodeEntity(float radius, boolean causesFire, Level.ExplosionInteraction mode) implements PotatoProjectileEntityHitAction {

        public static final MapCodec<ExplodeEntity> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("radius").forGetter(ExplodeEntity::radius),
            Codec.BOOL.fieldOf("causesFire").forGetter(ExplodeEntity::causesFire),
            Codec.of(Codec.STRING.comap(Enum::name), Codec.STRING.map(v -> Enum.valueOf(Level.ExplosionInteraction.class, v)))
                .fieldOf("mode").forGetter(ExplodeEntity::mode)
        ).apply(instance, ExplodeEntity::new));

        @Override
        public boolean execute(ItemStack projectile, EntityHitResult ray, Type type) {
            Entity entity = ray.getEntity();
            entity.level().explode(entity, entity.getX(), entity.getY(), entity.getZ(), radius, causesFire, mode);
            return false;
        }

        @Override
        public MapCodec<? extends PotatoProjectileEntityHitAction> codec() {
            return CODEC;
        }
    }
}
