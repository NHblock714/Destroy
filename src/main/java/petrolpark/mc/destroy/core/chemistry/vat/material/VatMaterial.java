package petrolpark.mc.destroy.core.chemistry.vat.material;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import petrolpark.mc.library.core.data.recipe.ingredient.BlockIngredient;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyBlocks;

/**
 * Material properties of a block from which a {@code Vat} can be constructed. Determines the
 * structural + thermal + optical behaviour of the Vat:
 *
 * <ul>
 * <li>{@code maxPressure} — maximum internal pressure (Pa) the material can withstand before
 * the Vat explodes.</li>
 * <li>{@code thermalConductivity} — watts per block-side-length-kelvin.</li>
 * <li>{@code transparent} — whether sunlight and UV light pass through the material. Used by
 * {@code vatUVWithoutBlackLight} / {@code vatUVWithBlackLight} Ponder scenes + Vat UV
 * reaction logic.</li>
 * <li>{@code builtIn} — {@code true} if the material is hardcoded in Destroy (not removable
 * by datapack unregister); {@code false} if registered via datapack.</li>
 * </ul>
*/
public record VatMaterial(float maxPressure, float thermalConductivity, boolean transparent, boolean builtIn) {

    public static final Map<BlockIngredient<?>, VatMaterial> BLOCK_MATERIALS = new HashMap<>();

    public static final VatMaterial UNBREAKABLE = new VatMaterial(Float.MAX_VALUE, 0f, false, true);

    /**
 * Whether the given Block can be used to construct a Vat.
*/
    public static boolean isValid(BlockState state) {
        return BLOCK_MATERIALS.keySet().stream().anyMatch(ingredient -> ingredient.isValid(state));
    }

    public static Optional<VatMaterial> getMaterial(BlockState state) {
        return BLOCK_MATERIALS.entrySet().stream().filter(entry -> entry.getKey().isValid(state)).map(Entry::getValue).findFirst();
    }

    public static void clearDatapackMaterials() {
        for (Iterator<Entry<BlockIngredient<?>, VatMaterial>> iterator = BLOCK_MATERIALS.entrySet().iterator(); iterator.hasNext();) {
            if (!iterator.next().getValue().builtIn()) iterator.remove();
        }
    }

    /**
 * Register Destroy's built-in Vat materials. Currently: VAT_CONTROLLER → UNBREAKABLE.
*/
    public static void registerDestroyVatMaterials() {
        BlockIngredient.registerType(SingleBlockIngredient.TYPE);
        BLOCK_MATERIALS.put(new SingleBlockIngredient(DestroyBlocks.VAT_CONTROLLER.get()), UNBREAKABLE);
    }

    
    public static class SingleBlockIngredient implements BlockIngredient<SingleBlockIngredient> {

        public static final Type TYPE = new Type();

        public final Block block;

        public SingleBlockIngredient(Block block) {
            this.block = block;
        }

        @Override
        public BlockIngredient.BlockIngredientType<SingleBlockIngredient> getType() {
            return TYPE;
        }

        @Override
        public boolean isValid(BlockState state) {
            return state.is(block);
        }

        @Override
        public NonNullList<ItemStack> getDisplayedItemStacks() {
            return NonNullList.of(ItemStack.EMPTY, new ItemStack(block.asItem()));
        }

        @Override
        public void write(FriendlyByteBuf buffer) {
            ResourceLocation rl = BuiltInRegistries.BLOCK.getKey(block);
            if (rl == null) throw new IllegalArgumentException(String.format("Block %s does not exist", block.getName().getString()));
            buffer.writeResourceLocation(rl);
        }

        protected static class Type implements BlockIngredient.BlockIngredientType<SingleBlockIngredient> {

            public static final ResourceLocation ID = Destroy.asResource("single_block");

            @Override
            public SingleBlockIngredient read(FriendlyByteBuf buffer) {
                return new SingleBlockIngredient(BuiltInRegistries.BLOCK.get(buffer.readResourceLocation()));
            }

            @Override
            public ResourceLocation getId() {
                return ID;
            }
        }
    }
}
