package petrolpark.mc.destroy.core.universalarmortrim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Either;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BlockModel;
import net.minecraft.client.renderer.block.model.ItemModelGenerator;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.neoforged.neoforge.client.model.CompositeModel;

import petrolpark.mc.destroy.client.DestroySpriteSource;
import petrolpark.mc.destroy.client.DummyBaker;

/**
 * ItemOverrides chain that, on resolve, splices a trim overlay model atop the base armor model
 * for any trim material listed in {@link DestroySpriteSource#UNIVERSAL_ARMOR_TRIMS}. This is what
 * lets non-vanilla trim materials (chromium / fluorite / etc., per the 15 trim_material datapacks)
 * actually appear when the player wears trimmed armor.
*/
public class UniversalArmorTrimItemOverrides extends ItemOverrides {

    public static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();

    /** Memoised armor-material → armor-type → trim-material → composited BakedModel cache.*/
    public static Map<ArmorMaterial, Map<ArmorItem.Type, Map<TrimMaterial, BakedModel>>> MODELS = new HashMap<>();

    private final ItemOverrides wrapped;

    public UniversalArmorTrimItemOverrides(ItemOverrides wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public BakedModel resolve(BakedModel model, ItemStack stack, ClientLevel level, LivingEntity entity, int seed) {
        BakedModel defaultModel = wrapped.resolve(model, stack, level, entity, seed);
        if (level == null) return defaultModel;
        // 1.21 DataComponents path: TRIM component (null-safe).
        ArmorTrim trim = stack.get(DataComponents.TRIM);
        if (trim == null) return defaultModel;
        if (!(stack.getItem() instanceof ArmorItem armorItem)) return defaultModel;
        return getModel(
            // Calling ItemRenderer#getModel would recurse; this isn't a Trident or
            // Spyglass, so the direct ItemModelShaper path is fine.
            Minecraft.getInstance().getItemRenderer().getItemModelShaper().getItemModel(stack),
            armorItem.getMaterial().value(),
            armorItem.getType(),
            trim.material().value()
        ).orElse(defaultModel);
    }

    @Override
    public ImmutableList<BakedOverride> getOverrides() {
        return wrapped.getOverrides();
    }

    @SuppressWarnings("deprecation")
    public static Optional<BakedModel> getModel(BakedModel baseModel, ArmorMaterial armorMaterial,
                                                ArmorItem.Type armorType, TrimMaterial trimMaterial) {
        if (!DestroySpriteSource.UNIVERSAL_ARMOR_TRIMS.contains(trimMaterial.assetName())) return Optional.empty();
        Map<ArmorItem.Type, Map<TrimMaterial, BakedModel>> armorTypeMap = MODELS.computeIfAbsent(armorMaterial, am -> new HashMap<>());
        Map<TrimMaterial, BakedModel> trimMaterialMap = armorTypeMap.computeIfAbsent(armorType, at -> new HashMap<>());
        BakedModel overlayModel = getOverlayModel(armorType, trimMaterial);
        if (overlayModel == null) return Optional.empty();
        BakedModel composited = new CompositeModel.Baked(
            false, false, false,
            baseModel.getParticleIcon(),
            baseModel.getTransforms(),
            ItemOverrides.EMPTY,
            ImmutableMap.of(),
            ImmutableList.of(baseModel, overlayModel));
        trimMaterialMap.put(trimMaterial, composited);
        return Optional.of(composited);
    }

    public static final ResourceLocation defaultItemModelRl = ResourceLocation.withDefaultNamespace("generated");

    @Nullable
    @SuppressWarnings("deprecation")
    public static BakedModel getOverlayModel(ArmorItem.Type armorType, TrimMaterial trimMaterial) {
        ResourceLocation trimTextureId = ResourceLocation.withDefaultNamespace(
            armorType.getName() + "_trim_" + trimMaterial.assetName());
        Material trimTexture = new Material(TextureAtlas.LOCATION_BLOCKS,
            trimTextureId.withPrefix("trims/items/"));
        if (trimTexture.sprite() == null) return null;
        return DummyBaker.bake(
            ITEM_MODEL_GENERATOR.generateBlockModel(
                Material::sprite,
                new BlockModel(
                    defaultItemModelRl,
                    new ArrayList<>(),
                    Map.of("layer0", Either.left(trimTexture)),
                    null,
                    null,
                    ItemTransforms.NO_TRANSFORMS,
                    new ArrayList<>())),
            trimTextureId);
    }
}
