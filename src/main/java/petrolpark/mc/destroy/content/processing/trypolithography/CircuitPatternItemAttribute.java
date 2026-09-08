package petrolpark.mc.destroy.content.processing.trypolithography;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import petrolpark.mc.library.util.BinaryMatrix4x4;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttributeType;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.DestroyItemAttributeTypes;

/**
 * Create {@link ItemAttribute} describing a single cell of a {@link CircuitPatternItem}'s 4×4
 * pattern — "this cell (position {@code 0..15}) is {@code punched=true/false}". Used by Brass Funnel
 * / Belt filter UIs to route circuit items by their punch pattern.
*/
public record CircuitPatternItemAttribute(int position, boolean punched) implements ItemAttribute {

    public static final MapCodec<CircuitPatternItemAttribute> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        com.mojang.serialization.Codec.INT.fieldOf("position").forGetter(CircuitPatternItemAttribute::position),
        com.mojang.serialization.Codec.BOOL.fieldOf("punched").forGetter(CircuitPatternItemAttribute::punched)
    ).apply(i, CircuitPatternItemAttribute::new));

    public static final StreamCodec<ByteBuf, CircuitPatternItemAttribute> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, CircuitPatternItemAttribute::position,
        ByteBufCodecs.BOOL, CircuitPatternItemAttribute::punched,
        CircuitPatternItemAttribute::new);

    @Override
    public boolean appliesTo(ItemStack stack, Level world) {
        return stack.getItem() instanceof CircuitPatternItem
            && BinaryMatrix4x4.is1(CircuitPatternItem.getPattern(stack), position) == punched;
    }

    @Override
    public ItemAttributeType getType() {
        return DestroyItemAttributeTypes.IS_CIRCUIT_PATTERN_PUNCHED.get();
    }

    @Override
    public String getTranslationKey() {
        return punched ? "circuit_pattern_punched" : "circuit_pattern_punched.inverted";
    }

    @Override
    public Object[] getTranslationParameters() {
        return new Object[] { (position % 4) + 1, (position / 4) + 1 };
    }

    public static class Type implements ItemAttributeType {
        @Override
        public @NotNull ItemAttribute createAttribute() {
            return new CircuitPatternItemAttribute(0, false);
        }

        @Override
        public List<ItemAttribute> getAllAttributes(ItemStack stack, Level level) {
            if (!(stack.getItem() instanceof CircuitPatternItem)) return List.of();
            int pattern = CircuitPatternItem.getPattern(stack);
            List<ItemAttribute> attributes = new ArrayList<>(16);
            for (int i = 0; i < 16; i++) {
                attributes.add(new CircuitPatternItemAttribute(i, BinaryMatrix4x4.is1(pattern, i)));
            }
            return attributes;
        }

        @Override
        public MapCodec<? extends ItemAttribute> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<? super net.minecraft.network.RegistryFriendlyByteBuf, ? extends ItemAttribute> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
