package petrolpark.mc.destroy.content.processing.trypolithography.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternItem;

/** the result
 * inherits that pattern. All mask slots must carry the same pattern for the recipe to match.
 *
 * <ul>
 * <li>{@code mask}: ResourceLocation of mask item (e.g. {@code destroy:circuit_mask}).</li>
 * <li>{@code #} in pattern → mask slot (reserved, user JSON key must not define it).</li>
 * <li>{@code ' '} in pattern → empty slot (reserved).</li>
 * <li>Other characters → user-defined {@link Ingredient}s via {@code key}.</li>
 * </ul>
*/
public class ManualCircuitBoardRecipe implements CraftingRecipe {

    private final String group;
    private final CraftingBookCategory category;
    private final List<String> rawPattern;
    private final Map<String, Ingredient> rawKey; // excludes '#' (mask) and ' ' (empty)
    private final Item maskItem;
    private final ItemStack result;

    // derived / cached
    private final int width;
    private final int height;
    private final NonNullList<Ingredient> ingredients;
    private final int[] maskPositions;

    public ManualCircuitBoardRecipe(String group, CraftingBookCategory category,
                                    List<String> rawPattern, Map<String, Ingredient> rawKey,
                                    Item maskItem, ItemStack result) {
        this.group = group;
        this.category = category;
        this.rawPattern = List.copyOf(rawPattern);
        this.rawKey = Map.copyOf(rawKey);
        this.maskItem = maskItem;
        this.result = result;

        // Dissolve pattern
        this.height = rawPattern.size();
        int w = 0;
        for (String row : rawPattern) w = Math.max(w, row.length());
        this.width = w;

        Map<String, Ingredient> fullKey = new HashMap<>(rawKey);
        // The mask slot is a plain Ingredient, so JEI draws a blank white mask in it rather than a
        // patterned one. That cosmetic loss is not worth a custom IngredientType.
        fullKey.put("#", Ingredient.of(maskItem));
        fullKey.put(" ", Ingredient.EMPTY);

        NonNullList<Ingredient> ings = NonNullList.withSize(width * height, Ingredient.EMPTY);
        List<Integer> masks = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            String line = rawPattern.get(y);
            for (int x = 0; x < width; x++) {
                char c = x < line.length() ? line.charAt(x) : ' ';
                String k = String.valueOf(c);
                Ingredient ing = fullKey.getOrDefault(k, Ingredient.EMPTY);
                int idx = y * width + x;
                ings.set(idx, ing);
                if (c == '#') masks.add(idx);
            }
        }
        this.ingredients = ings;
        this.maskPositions = masks.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
 * Match the input against the recipe pattern.
 *
 * <p><b>Orientations tried (4 total)</b>:</p>
 * <ol>
 * <li>Original</li>
 * <li>Mirrored (horizontal flip)</li>
 * <li>Transposed (rotate 90° → swap x/y)</li>
 * <li>Transposed + mirrored (rotate 90° + flip)</li>
 * </ol>
 *
 * <p>Vanilla {@link net.minecraft.world.item.crafting.ShapedRecipe} only does (1) + (2). For our
 * 1×2 vertical "{@code #}/{@code C}" recipe, that means a horizontal placement (mask + copper
 * side-by-side) wouldn't match — but players reasonably expect the same craft to work in either
 * orientation since the recipe semantics is just "1 mask + 1 copper plate". Adding the transpose
 * variants keeps this user-friendly without changing the JSON or breaking existing crafts.</p>
*/
    @Override
    public boolean matches(CraftingInput input, Level level) {
        // Try original orientation (width × height)
        if (matchesWithDimensions(input, width, height, false)) return true;
        if (matchesWithDimensions(input, width, height, true)) return true;
        // Try transposed orientation (height × width — swap dimensions)
        if (matchesWithDimensions(input, height, width, false)) return true;
        if (matchesWithDimensions(input, height, width, true)) return true;
        return false;
    }

    /** Attempt match at every top-left offset within the given orientation.*/
    private boolean matchesWithDimensions(CraftingInput input, int w, int h, boolean transposed) {
        if (input.width() < w || input.height() < h) return false;
        for (int dx = 0; dx <= input.width() - w; dx++) {
            for (int dy = 0; dy <= input.height() - h; dy++) {
                if (matchesAt(input, dx, dy, false, transposed)) return true;
                if (matchesAt(input, dx, dy, true, transposed)) return true;
            }
        }
        return false;
    }

    private boolean matchesAt(CraftingInput input, int dx, int dy, boolean mirrored, boolean transposed) {
        int commonPattern = -1;
        // The "effective" recipe dimensions in the input space — depend on transpose.
        int effW = transposed ? height : width;
        int effH = transposed ? width : height;
        for (int col = 0; col < input.width(); col++) {
            for (int row = 0; row < input.height(); row++) {
                int x = col - dx;
                int y = row - dy;
                Ingredient ing = Ingredient.EMPTY;
                boolean isMaskSlot = false;
                if (x >= 0 && y >= 0 && x < effW && y < effH) {
                    // Map (x, y) in input space → (rx, ry) in recipe space, applying transpose + mirror.
                    int rx, ry;
                    if (transposed) {
                        rx = y;          // input row → recipe column
                        ry = x;          // input column → recipe row
                    } else {
                        rx = x;
                        ry = y;
                    }
                    if (mirrored) rx = width - 1 - rx;
                    int idx = ry * width + rx;
                    ing = ingredients.get(idx);
                    for (int p : maskPositions) if (p == idx) { isMaskSlot = true; break; }
                }
                ItemStack stack = input.getItem(col + row * input.width());
                if (isMaskSlot) {
                    if (!stack.is(maskItem)) return false;
                    int p = CircuitPatternItem.getPattern(stack);
                    if (commonPattern == -1) commonPattern = p;
                    else if (commonPattern != p) return false;
                } else {
                    if (!ing.test(stack)) return false;
                }
            }
        }
        // Match requires at least one mask slot covered (commonPattern can be 0 for blank masks).
        return commonPattern != -1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        // Find the common mask pattern.
        int pattern = 0;
        for (int i = 0; i < input.size(); i++) {
            ItemStack s = input.getItem(i);
            if (s.is(maskItem)) {
                pattern = CircuitPatternItem.getPattern(s);
                break;  // Match guarantees all masks share the same pattern, so first one is enough.
            }
        }
        ItemStack out = result.copy();
        CircuitPatternItem.putPattern(out, pattern);
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        // accept both original AND transposed orientation (matches the orientation-agnostic
        // matcher). E.g. a 1×2 recipe also fits in a 2×1 grid via transposition.
        return (w >= width && h >= height) || (w >= height && h >= width);
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        // JEI shows a blank board in the result slot; only assemble() propagates the mask's real
        // pattern, and only when the player actually crafts.
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    @Override
    public String getGroup() {
        return group;
    }

    @Override
    public CraftingBookCategory category() {
        return category;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return DestroyRecipeTypes.CIRCUIT_BOARD_MANUAL_CRAFTING.getSerializer();
    }

    public Item maskItem() { return maskItem; }
    public ItemStack rawResult() { return result; }
    public List<String> rawPattern() { return rawPattern; }
    public Map<String, Ingredient> rawKey() { return rawKey; }

    public static class Serializer implements RecipeSerializer<ManualCircuitBoardRecipe> {

        public static final MapCodec<ManualCircuitBoardRecipe> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(ManualCircuitBoardRecipe::getGroup),
            CraftingBookCategory.CODEC.optionalFieldOf("category", CraftingBookCategory.MISC).forGetter(ManualCircuitBoardRecipe::category),
            Codec.list(Codec.STRING).fieldOf("pattern").forGetter(ManualCircuitBoardRecipe::rawPattern),
            Codec.unboundedMap(Codec.STRING, Ingredient.CODEC).fieldOf("key").forGetter(ManualCircuitBoardRecipe::rawKey),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("mask").forGetter(ManualCircuitBoardRecipe::maskItem),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(ManualCircuitBoardRecipe::rawResult)
        ).apply(i, ManualCircuitBoardRecipe::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, ManualCircuitBoardRecipe> STREAM_CODEC =
            StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ManualCircuitBoardRecipe::getGroup,
                CraftingBookCategory.STREAM_CODEC, ManualCircuitBoardRecipe::category,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), ManualCircuitBoardRecipe::rawPattern,
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Ingredient.CONTENTS_STREAM_CODEC), ManualCircuitBoardRecipe::rawKey,
                ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM), ManualCircuitBoardRecipe::maskItem,
                ItemStack.STREAM_CODEC, ManualCircuitBoardRecipe::rawResult,
                ManualCircuitBoardRecipe::new);

        @Override public MapCodec<ManualCircuitBoardRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, ManualCircuitBoardRecipe> streamCodec() { return STREAM_CODEC; }
    }
}
