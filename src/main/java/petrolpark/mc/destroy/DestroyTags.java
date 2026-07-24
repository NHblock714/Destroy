package petrolpark.mc.destroy;

import petrolpark.mc.library.util.Lang;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;

/**
 * 1.21.1 notes:
 * <ul>
 * <li>{@code net.createmod.catnip.lang.Lang} → {@link petrolpark.mc.library.util.Lang} (Petrolpark's copy in Create-Library).</li>
 * <li>{@code ItemTags.create(id)} / {@code BlockTags.create(id)} still exist but wrap
 * {@code TagKey.create(Registries.ITEM, id)} / {@code TagKey.create(Registries.BLOCK, id)}.</li>
 * <li>{@code ForgeRegistries.MOB_EFFECTS} removed — MobEffects enum would now go through
 * {@code BuiltInRegistries.MOB_EFFECT} or a {@code Holder<MobEffect>} directly. Not needed
 * for this migration batch: CAUSES_INFERTILITY MobEffect tag can live on {@link petrolpark.mc.library.PetrolparkTags.MobEffects#CAUSES_INFERTILITY}.</li>
 * </ul>
*/
public class DestroyTags {

    public enum Blocks {

        ACID_RAIN_DESTROYS,
        ACID_RAIN_SETS_DEAD_BUSH,
        ACID_RAIN_SETS_DIRT,
        ARC_FURNACE_TRANSFORMABLE,
        BEETROOTS,
        ACID_RAIN_DESTRUCTIBLE,
        GANGUE,
        ACID_RAIN_DIRT_REPLACEABLE,
        ;

        public final TagKey<Block> tag;

        Blocks() {
            this(null);
        }

        Blocks(String path) {
            ResourceLocation id = Destroy.asResource(path == null ? Lang.asId(name()) : path);
            tag = BlockTags.create(id);
        }

        @SuppressWarnings("deprecation")
        public boolean matches(Block block) {
            return block.builtInRegistryHolder().is(tag);
        }
    }

    public enum Items {

        ALCOHOLIC_DRINKS,
        BEETROOT_ASHES,
        BONEMEAL_BYPASSES_POLLUTION("bonemeal/bypasses_pollution"),
        DESTROY_INGOTS,
        EYES,
        FERTILIZERS,
        FLUXES,
        HEFTY_BEETROOTS,
        LIABLE_TO_CHANGE,
        SPRAY_BOTTLES,
        SYRINGES,
        TEST_TUBE_RACK_STORABLE,
        YEAST,

        CHEMICAL_PROTECTION_EYES("chemical_protection/eyes"),
        CHEMICAL_PROTECTION_NOSE("chemical_protection/nose"),
        CHEMICAL_PROTECTION_MOUTH("chemical_protection/mouth"),
        CHEMICAL_PROTECTION_HEAD("chemical_protection/head"),
        CHEMICAL_PROTECTION_CHEST("chemical_protection/chest"),
        CHEMICAL_PROTECTION_LEGS("chemical_protection/legs"),
        CHEMICAL_PROTECTION_FEET("chemical_protection/feet"),
        CONTAMINABLE,

        PLASTICS,
        RIGID_PLASTICS("plastics/rigid"),
        TEXTILE_PLASTICS("plastics/textile"),
        POROUS_PLASTICS("plastics/porous"),
        INERT_PLASTICS("plastics/inert"),
        RUBBER_PLASTICS("plastics/rubber"),
        TRANSPARENT_PLASTICS("plastics/transparent"),

        PRIMARY_EXPLOSIVES("explosives/primary"),
        SCHEMATICANNON_FUELS,
        SECONDARY_EXPLOSIVES("explosives/secondary"),
        OBLITERATION_EXPLOSIVES, // Only used for JEI display.
        ;

        public final TagKey<Item> tag;

        Items() {
            this(null);
        }

        Items(String path) {
            ResourceLocation id = Destroy.asResource(path == null ? Lang.asId(name()) : path);
            tag = ItemTags.create(id);
        }

        @SuppressWarnings("deprecation")
        public boolean matches(Item item) {
            return item.builtInRegistryHolder().is(tag);
        }

        public boolean matches(ItemStack stack) {
            return stack.is(tag);
        }

        public static void init() {}
    }

    public enum Fluids {
        AMPLIFIES_SMOG,
        ACIDIFIES_RAIN,
        DEPLETES_OZONE,
        GREENHOUSE_GAS,
        RADIOACTIVE,
        COOLANT;

        public final TagKey<Fluid> tag;

        Fluids() {
            tag = FluidTags.create(Destroy.asResource(Lang.asId(name())));
        }

        @SuppressWarnings("deprecation")
        public boolean matches(Fluid fluid) {
            return fluid.builtInRegistryHolder().is(tag);
        }

        public static void init() {}
    }

    public static void register() {
        Items.init();
        Fluids.init();
    }
}
