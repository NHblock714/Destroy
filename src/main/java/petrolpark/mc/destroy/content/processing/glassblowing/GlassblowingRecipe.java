package petrolpark.mc.destroy.content.processing.glassblowing;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.MapCodec;
import com.petrolpark.compat.create.core.recipe.AdvancedProcessingRecipe;
import com.petrolpark.compat.create.core.recipe.AdvancedProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.DestroyRecipeTypes;

/**
 * Glassblowing recipe — 1 molten-glass fluid input + sequence of {@link BlowingShape} steps
 * → 1 glass item output. Used by the Blowpipe item (not yet ported) to visualize + craft custom
 * glass bottles/bulbs based on player-specified blowing shapes.
*/
public class GlassblowingRecipe extends AdvancedProcessingRecipe<RecipeInput> {

    /**
 * List of blowing shapes per-recipe. Fully serialized via {@link GlassblowingRecipeParams}
 * custom codec. If the incoming params is a
 * {@link GlassblowingRecipeParams} instance (from the custom serializer), copy its shape list;
 * otherwise default to empty (e.g., if a plain AdvancedProcessingRecipeParams slipped through).
*/
    public List<BlowingShape> blowingShapes;

    public GlassblowingRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.GLASSBLOWING, params);
        if (params instanceof GlassblowingRecipeParams gp) {
            blowingShapes = gp.blowingShapes;
        } else {
            blowingShapes = new ArrayList<>();
        }
    }

    @Override
    public boolean matches(RecipeInput container, Level level) {
        return true;
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 1;
    }

    @Override
    protected int getMaxInputCount() {
        return 0;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    public static record BlowingShape(float length, float radius) {}

    /**
 * Custom serializer using {@link GlassblowingRecipeParams#CODEC} instead of the
 * parent {@link AdvancedProcessingRecipeParams#CODEC}, so {@code blowing_shapes} JSON field
 * is deserialized into the recipe.
 *
 * <p>Manually inlined xmap (vs {@code ProcessingRecipe.codec}) because GlassblowingRecipe's
 * P type is locked to AdvancedProcessingRecipeParams by its parent class · generic constraint
 * {@code Factory<P, R extends ProcessingRecipe<?, P>>} can't bind P=GlassblowingRecipeParams
 * to R=GlassblowingRecipe. The upcast from GlassblowingRecipeParams → AdvancedProcessingRecipeParams
 * at the ctor call site is implicit (Java IS-A).</p>
*/
    public static class Serializer implements RecipeSerializer<GlassblowingRecipe> {

        public static final MapCodec<GlassblowingRecipe> CODEC =
            GlassblowingRecipeParams.CODEC.xmap(
                params -> new GlassblowingRecipe(params),
                recipe -> (GlassblowingRecipeParams) recipe.getParams());

        public static final StreamCodec<RegistryFriendlyByteBuf, GlassblowingRecipe> STREAM_CODEC =
            GlassblowingRecipeParams.STREAM_CODEC.map(
                params -> new GlassblowingRecipe(params),
                recipe -> (GlassblowingRecipeParams) recipe.getParams());

        @Override
        public MapCodec<GlassblowingRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, GlassblowingRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
