package petrolpark.mc.destroy;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.api.equipment.potatoCannon.PotatoProjectileBlockHitAction;
import com.simibubi.create.api.registry.CreateRegistries;

import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 1.21 registry-frozen crash fix (same pattern as DestroyItemAttributeTypes). 1.21 NeoForge freezes Create's mod-owned registries during NewRegistryEvent ·
 * static init is too late. Switched to {@link DeferredRegister} on mod event bus.
*/
@SuppressWarnings({"rawtypes", "unchecked"})
public class DestroyPotatoProjectileBlockHitActions {

    private static final DeferredRegister<MapCodec<? extends PotatoProjectileBlockHitAction>> ACTIONS =
        (DeferredRegister) DeferredRegister.create(CreateRegistries.POTATO_PROJECTILE_BLOCK_HIT_ACTION, Destroy.MOD_ID);

    public static final Supplier<MapCodec<? extends PotatoProjectileBlockHitAction>> EXPLODE_BLOCK =
        ACTIONS.register("explode_block", () -> ExplodeBlock.CODEC);

    /** Register the DeferredRegister onto the mod event bus — called from Destroy.java ctor.*/
    public static void register(IEventBus modEventBus) {
        ACTIONS.register(modEventBus);
    }

    
    public static void init() {}

    private record ExplodeBlock(float radius, boolean causesFire, Level.ExplosionInteraction mode) implements PotatoProjectileBlockHitAction {

        public static final MapCodec<ExplodeBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.POSITIVE_FLOAT.fieldOf("radius").forGetter(ExplodeBlock::radius),
            Codec.BOOL.fieldOf("causesFire").forGetter(ExplodeBlock::causesFire),
            Codec.of(Codec.STRING.comap(Enum::name), Codec.STRING.map(v -> Enum.valueOf(Level.ExplosionInteraction.class, v)))
                .fieldOf("mode").forGetter(ExplodeBlock::mode)
        ).apply(instance, ExplodeBlock::new));

        @Override
        public boolean execute(LevelAccessor level, ItemStack projectile, BlockHitResult ray) {
            if (level.isClientSide()) return true;
            BlockPos pos = ray.getBlockPos();
            if (level instanceof Level l && !l.isLoaded(pos)) return true;
            ((Level) level).explode(null, pos.getX(), pos.getY(), pos.getZ(), radius, causesFire, mode);
            return false;
        }

        @Override
        public MapCodec<? extends PotatoProjectileBlockHitAction> codec() {
            return CODEC;
        }
    }
}
