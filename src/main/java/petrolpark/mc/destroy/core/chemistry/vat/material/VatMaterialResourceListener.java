package petrolpark.mc.destroy.core.chemistry.vat.material;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import petrolpark.mc.library.core.data.recipe.ingredient.BlockIngredient;
import petrolpark.mc.library.core.data.recipe.ingredient.BlockIngredient.BlockTagIngredient;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;

/**
 * Reload listener that populates {@link VatMaterial#BLOCK_MATERIALS} from
 * {@code data/<ns>/destroy_compat/vat_materials/<*>.json} files. Each JSON entry binds a list
 * of block IDs or block tags to pressure/conductivity/transparency properties.
*/
public class VatMaterialResourceListener extends SimpleJsonResourceReloadListener {

    private static final Gson GSON = new Gson();

    public VatMaterialResourceListener() {
        super(GSON, "destroy_compat/vat_materials");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> object, ResourceManager resourceManager, ProfilerFiller profiler) {
        // Remove any previously added Vat Materials
        VatMaterial.clearDatapackMaterials();

        final Map<BlockIngredient<?>, VatMaterial> datapackMaterials = new HashMap<>(object.size());

        // Add new ones
        for (Entry<ResourceLocation, JsonElement> entry : object.entrySet()) {
            JsonElement json = entry.getValue();
            ResourceLocation rl = entry.getKey();
            if (!json.isJsonObject()) throw new JsonSyntaxException("Cannot read Vat Material: " + rl);
            JsonObject jobj = json.getAsJsonObject();

            // data files don't use "conditions" fields (grep-verified). NeoForge 1.21 ICondition
            // API moved to ConditionalOps codec stream; not wired here.

            // Default values
            float maxPressureDifference = 100000f;
            float conductivity = 100f;
            boolean transparent = false;

            // Pressure
            if (jobj.has("max_pressure_difference")) {
                try {
                    maxPressureDifference = jobj.get("max_pressure_difference").getAsFloat();
                    if (maxPressureDifference <= 0f) throw new IllegalArgumentException();
                } catch (Throwable e) {
                    throw new JsonSyntaxException("Vat Material " + rl + " specifies an invalid maximum pressure difference");
                }
            }

            // Conductivity
            if (jobj.has("conductivity")) {
                try {
                    conductivity = jobj.get("conductivity").getAsFloat();
                    if (conductivity <= 0f) throw new IllegalArgumentException();
                } catch (Throwable e) {
                    throw new JsonSyntaxException("Vat Material " + rl + " specifies an invalid conductivity");
                }
            }

            // Transparency.
            // {@code jobj.has("conductivity")} but read {@code jobj.get("transparent")}. The bug
            // was harmless when both fields were always present together, but if a vat_material
            // JSON has "conductivity" without "transparent" (or vice versa), the typo'd check
            // would either NPE or skip transparent silently. Now correctly checks the matching
            // field name.
            if (jobj.has("transparent")) {
                try {
                    transparent = jobj.get("transparent").getAsBoolean();
                } catch (Throwable e) {
                    throw new JsonSyntaxException("Vat Material " + rl + " specifies transparency");
                }
            }

            VatMaterial material = new VatMaterial(maxPressureDifference, conductivity, transparent, false);

            // Register the materials
            if (!jobj.has("blocks") || !jobj.get("blocks").isJsonArray()) throw new JsonSyntaxException("Vat Material " + rl + " must specify at least one block.");
            jobj.get("blocks").getAsJsonArray().forEach(e -> {
                try {
                    String id = e.getAsString();
                    if (id.startsWith("#")) { // Tags
                        id = id.substring(1);
                        datapackMaterials.put(new BlockTagIngredient(TagKey.create(Registries.BLOCK, ResourceLocation.parse(id))), material);
                    } else { // Individual blocks — use inlined VatMaterial.SingleBlockIngredient
                        Optional<? extends Holder<Block>> blockOptional = BuiltInRegistries.BLOCK.asLookup().get(ResourceKey.create(Registries.BLOCK, ResourceLocation.parse(id)));
                        if (blockOptional.isEmpty()) throw new IllegalArgumentException();
                        datapackMaterials.put(new VatMaterial.SingleBlockIngredient(blockOptional.get().value()), material);
                    }
                } catch (Throwable error) {
                    throw new JsonSyntaxException("Vat material " + rl + " specifies an invalid block or block tag: " + e.toString());
                }
            });
        }

        // Add new materials server-side too
        VatMaterial.BLOCK_MATERIALS.putAll(datapackMaterials);

        // Send to clients, if possible
        try {
            CatnipServices.NETWORK.sendToAllClients(new SyncVatMaterialsS2CPacket(datapackMaterials));
        } catch (NullPointerException e) {
            // Expected during server startup before player network infrastructure is up.
        }
    }
}
