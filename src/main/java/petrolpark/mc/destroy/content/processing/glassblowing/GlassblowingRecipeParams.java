package petrolpark.mc.destroy.content.processing.glassblowing;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import petrolpark.mc.destroy.content.processing.glassblowing.GlassblowingRecipe.BlowingShape;

/**
 * Extends {@link AdvancedProcessingRecipeParams} with a {@code blowing_shapes} JSON array field.
 *
 * <ul>
 * <li>{@code ProcessingRecipeParams.codec(Supplier<P> factory)} is {@code protected static} —
 * accessible from subclasses (this class extends AdvancedProcessingRecipeParams extends
 * ProcessingRecipeParams · we're 2 levels down so {@code protected} inheritance applies).</li>
 * <li>StreamCodec uses {@code streamCodec(Supplier<P>)} helper which calls
 * {@link #encode(RegistryFriendlyByteBuf)} / {@link #decode(RegistryFriendlyByteBuf)}
 * overrides below to append blowing_shapes after the parent-class encoded fields.</li>
 * </ul>
*/
public class GlassblowingRecipeParams extends AdvancedProcessingRecipeParams {

    // JSON values for length/radius are in BLOCK UNITS (0-16, like Blockbench geometry),
    // beaker.json has [length=7, radius=3]
    // for a 7-px-long, 3-px-radius shape). The renderer expects WORLD UNITS (0-1.0). Codec
    // divides on read, multiplies on write so JSON stays human-authorable while the in-memory
    // BlowingShape carries world-unit values that BlowpipeBlockEntityRenderer.render uses
    // directly.
    // would lose the scale); 1.21 codec is symmetric.
    private static final MapCodec<BlowingShape> SHAPE_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        Codec.FLOAT.xmap(b -> b / 16f, w -> w * 16f).fieldOf("length").forGetter(BlowingShape::length),
        Codec.FLOAT.xmap(b -> b / 16f, w -> w * 16f).fieldOf("radius").forGetter(BlowingShape::radius)
    ).apply(i, BlowingShape::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, BlowingShape> SHAPE_STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.FLOAT, BlowingShape::length,
            ByteBufCodecs.FLOAT, BlowingShape::radius,
            BlowingShape::new);

    /**
 * CODEC mirrors {@link AdvancedProcessingRecipeParams.CODEC} but uses a
 * {@code GlassblowingRecipeParams::new} factory and appends the {@code blowing_shapes} field.
*/
    public static final MapCodec<GlassblowingRecipeParams> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        codec(GlassblowingRecipeParams::new).forGetter(Function.identity()),
        Codec.BOOL.optionalFieldOf("book_required", false).forGetter(AdvancedProcessingRecipeParams::isBookRequired),
        RegistryCodecs.homogeneousList(Registries.BIOME).optionalFieldOf("biomes").forGetter(AdvancedProcessingRecipeParams::allowedBiomes),
        ResourceLocation.CODEC.optionalFieldOf("first_time_lucky_key").forGetter(AdvancedProcessingRecipeParams::firstTimeLuckyKey),
        SHAPE_CODEC.codec().listOf().optionalFieldOf("blowing_shapes", new ArrayList<>()).forGetter(p -> p.blowingShapes)
    ).apply(instance, (params, bookRequired, allowedBiomes, firstTimeLuckyKey, shapes) -> {
        params.bookRequired = bookRequired;
        params.allowedBiomes = allowedBiomes;
        params.firstTimeLuckyKey = firstTimeLuckyKey;
        params.blowingShapes = shapes;
        return params;
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, GlassblowingRecipeParams> STREAM_CODEC =
        streamCodec(GlassblowingRecipeParams::new);

    public List<BlowingShape> blowingShapes = new ArrayList<>();

    public GlassblowingRecipeParams() {
        super();
    }

    /** Append blowing_shapes after parent-class stream-codec-encoded fields.*/
    @Override
    protected void encode(RegistryFriendlyByteBuf buffer) {
        super.encode(buffer);
        ByteBufCodecs.VAR_INT.encode(buffer, blowingShapes.size());
        for (BlowingShape shape : blowingShapes) {
            SHAPE_STREAM_CODEC.encode(buffer, shape);
        }
    }

    @Override
    protected void decode(RegistryFriendlyByteBuf buffer) {
        super.decode(buffer);
        int n = ByteBufCodecs.VAR_INT.decode(buffer);
        blowingShapes = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            blowingShapes.add(SHAPE_STREAM_CODEC.decode(buffer));
        }
    }
}
