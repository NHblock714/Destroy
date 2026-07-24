package petrolpark.mc.destroy;

import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;
import petrolpark.mc.library.util.Lang;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import petrolpark.mc.destroy.content.processing.ageing.AgeingRecipe;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugationRecipe;
import petrolpark.mc.destroy.content.processing.discstamping.DiscElectroplatingRecipe;
import petrolpark.mc.destroy.content.processing.distillation.DistillationRecipe;
import petrolpark.mc.destroy.content.processing.dynamo.ChargingRecipe;
import petrolpark.mc.destroy.content.processing.dynamo.ElectrolysisRecipe;
import petrolpark.mc.destroy.content.processing.dynamo.arcfurnace.ArcFurnaceRecipe;
import petrolpark.mc.destroy.content.processing.extrusion.ExtrusionRecipe;
import petrolpark.mc.destroy.content.processing.glassblowing.GlassblowingRecipe;
import petrolpark.mc.destroy.content.processing.phytomining.PhytominingRecipe;
import petrolpark.mc.destroy.content.processing.sieve.SievingRecipe;
import petrolpark.mc.destroy.content.processing.treetap.TappingRecipe;
import petrolpark.mc.destroy.content.product.fireretardant.FlameRetardantApplicationRecipe;
import petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe;

/**
 * Destroy 的 RecipeType / RecipeSerializer 清单 —— 1.21.1 改造后照抄 petrolpark-Library
 * {@code PetrolparkCreateRecipeTypes} 的 enum + {@link IRecipeTypeInfo} 模式。
 *
 * <p>每一项自带自己的 serializer/type 注册句柄；条目顺序决定类加载顺序，所以保持按字母排序。
 * 新增条目时只需追加一行 enum 常量 + 构造参数。</p>
 *
 * <p>1.21.1 核心要点：
 * <ul>
 * <li>RecipeSerializer 不再是 {@code IRecipeSerializer}，改纯 {@code MapCodec} + {@code StreamCodec}；
 * {@link AdvancedProcessingRecipe.Serializer} 自动 wrap factory + {@link AdvancedProcessingRecipeParams#CODEC}，
 * 子类只需给 {@code ProcessingRecipe.Factory<AdvancedProcessingRecipeParams, R>}。</li>
 * <li>{@link IRecipeTypeInfo#getType()} 现在泛型形参 {@code <I extends RecipeInput, R extends Recipe<I>>}。</li>
 * <li>静态 init 由 {@link #register()} 触发类加载，随后在 {@link Destroy} 构造函数里调
 * {@link #registerDeferred(IEventBus)} 挂 DeferredRegister。</li>
 * </ul>
*/
public enum DestroyRecipeTypes implements IRecipeTypeInfo {

    AGING(AgeingRecipe::new),
    // BasinRecipe subclass for Arc Furnace (Dynamo + Carbon Fiber Block below).
    // Uses StandardProcessingRecipe.Serializer path (BasinRecipe extends StandardProcessingRecipe,
    // not AdvancedProcessingRecipe).
    ARC_FURNACE(() -> new StandardProcessingRecipe.Serializer<>(ArcFurnaceRecipe::new)),
    CENTRIFUGATION(CentrifugationRecipe::new),
    // ManualCircuitBoardRecipe: custom shaped-crafting that propagates circuit mask pattern
    // to resulting circuit board. Non-processing RecipeSerializer (not AdvancedProcessingRecipe).
    CIRCUIT_BOARD_MANUAL_CRAFTING(() -> new petrolpark.mc.destroy.content.processing.trypolithography.recipe.ManualCircuitBoardRecipe.Serializer()),
    // CircuitDeployerApplicationRecipe is a DeployerApplicationRecipe variant that flags
    // circuit-pattern-conferring steps in SequencedAssembly chains. JSON `"type":
    // "destroy:circuit_deploying"` (used by sequenced_assembly/circuit_board.json sequence step).
    CIRCUIT_DEPLOYING(() -> new petrolpark.mc.destroy.content.processing.trypolithography.recipe.CircuitDeployerApplicationRecipe.Serializer()),
    // CircuitSequencedAssemblyRecipe wraps SequencedAssemblyRecipeSerializer with codec
    // validator (must have exactly one pattern-conferring step). JSON `"type":
    // "destroy:circuit_sequenced_assembly"` (used by sequenced_assembly/*.json with pattern-bearing chains).
    CIRCUIT_SEQUENCED_ASSEMBLY(() -> new petrolpark.mc.destroy.content.processing.trypolithography.recipe.CircuitSequencedAssemblyRecipe.Serializer()),
    // Dynamo BELT/WORLD mode single-item charging recipe (IAssemblyRecipe).
    // AdvancedProcessingRecipe.Serializer standard path.
    CHARGING(ChargingRecipe::new),
    // BasinRecipe subclass for Dynamo-driven music disc electroplating. Runtime
    // copyWithDisc spawns per-disc recipes dynamically (DynamoBE.getMatchingRecipes — pending).
    DISC_ELECTROPLATING(() -> new StandardProcessingRecipe.Serializer<>(DiscElectroplatingRecipe::new)),
    DISTILLATION(DistillationRecipe::new),
    // BasinRecipe subclass for Dynamo-driven electrolysis (fluid/item separation).
    ELECTROLYSIS(() -> new StandardProcessingRecipe.Serializer<>(ElectrolysisRecipe::new)),
    // Tank periodic-table block filling (molten metal/liquid element → tank
    // variant block). SingleFluidRecipe subclass; AdvancedProcessingRecipe.Serializer path.
    ELEMENT_TANK_FILLING(petrolpark.mc.destroy.content.product.periodictable.ElementTankFillingRecipe::new),
    EXTRUSION(ExtrusionRecipe::new),
    FLAME_RETARDANT_APPLICATION(FlameRetardantApplicationRecipe::new),
    // GLASSBLOWING uses custom Serializer with GlassblowingRecipeParams codec to
    // support blowing_shapes JSON field.
    GLASSBLOWING(() -> new GlassblowingRecipe.Serializer()),
    MIXTURE_CONVERSION(MixtureConversionRecipe::new),
    MUTATION(PhytominingRecipe::new),
    // Obliteration display-only recipe (JEI only · loot-table driven at runtime).
    OBLITERATION(petrolpark.mc.destroy.core.explosion.ObliterationRecipe::new),
    // Reaction display-only recipe (JEI only · chemistry-engine driven at runtime).
    REACTION(petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe::new),
    SIEVING(SievingRecipe::new),
    TAPPING(TappingRecipe::new),
    ;

    private final ResourceLocation id;
    private final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<?>> serializerObject;
    private final @Nullable DeferredHolder<RecipeType<?>, RecipeType<?>> typeObject;
    private final Supplier<RecipeType<?>> type;

    <R extends AdvancedProcessingRecipe<?>> DestroyRecipeTypes(ProcessingRecipe.Factory<AdvancedProcessingRecipeParams, R> processingFactory) {
        this(() -> new AdvancedProcessingRecipe.Serializer<>(processingFactory));
    }

    DestroyRecipeTypes(Supplier<RecipeSerializer<?>> serializerSupplier) {
        String name = Lang.asId(name());
        id = Destroy.asResource(name);
        serializerObject = Registers.SERIALIZERS.register(name, serializerSupplier);
        typeObject = Registers.TYPES.register(name, () -> RecipeType.simple(id));
        type = typeObject;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends RecipeSerializer<?>> T getSerializer() {
        return (T) serializerObject.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
        return (RecipeType<R>) type.get();
    }

    public <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> find(I inv, Level level) {
        return level.getRecipeManager().getRecipeFor(getType(), inv, level);
    }

    /** Class-load trigger so the enum constants construct their DeferredHolders.*/
    public static void register() {}

    // non-enum vanilla crafting recipe serializers (ExtendedDurationFireworkRocketRecipe).
    // Registered alongside the enum-based serializers via the same DeferredRegister so the
    // mod-bus RegisterEvent picks them up in one shot. Two variants (4 / 5 flight duration).
    @SuppressWarnings("unused")
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<petrolpark.mc.destroy.core.explosion.ExtendedDurationFireworkRocketRecipe>>
        DURATION_4_FIREWORK_ROCKET = Registers.SERIALIZERS.register("duration_4_firework_rocket_crafting",
            () -> petrolpark.mc.destroy.core.explosion.ExtendedDurationFireworkRocketRecipe.DURATION_4_FIREWORK_ROCKET);
    @SuppressWarnings("unused")
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<petrolpark.mc.destroy.core.explosion.ExtendedDurationFireworkRocketRecipe>>
        DURATION_5_FIREWORK_ROCKET = Registers.SERIALIZERS.register("duration_5_firework_rocket_crafting",
            () -> petrolpark.mc.destroy.core.explosion.ExtendedDurationFireworkRocketRecipe.DURATION_5_FIREWORK_ROCKET);

    // FillMixedExplosiveItemRecipe holds its serializer DeferredHolder on itself; touch it here so
    // the class loads and registers the serializer alongside these, before registerDeferred.
    @SuppressWarnings("unused")
    private static final Object FILL_MIXED_EXPLOSIVE_SERIALIZER_LOAD =
        petrolpark.mc.destroy.core.explosion.mixedexplosive.FillMixedExplosiveItemRecipe.SERIALIZER;

    /** Hook the nested {@link Registers} DeferredRegisters onto the mod bus.*/
    public static void registerDeferred(IEventBus modEventBus) {
        Registers.SERIALIZERS.register(modEventBus);
        Registers.TYPES.register(modEventBus);
    }

    public static class Registers {
        public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(BuiltInRegistries.RECIPE_SERIALIZER, Destroy.MOD_ID);
        public static final DeferredRegister<RecipeType<?>> TYPES =
            DeferredRegister.create(BuiltInRegistries.RECIPE_TYPE, Destroy.MOD_ID);
    }
}
