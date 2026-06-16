package petrolpark.mc.destroy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import com.petrolpark.client.creativemodetab.CustomTab;
import com.petrolpark.client.creativemodetab.CustomTab.ITabEntry;
import com.simibubi.create.AllCreativeModeTabs;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import petrolpark.mc.destroy.config.DestroySubstancesConfigs;

/** Item/block references are resolved by registry ID (not compile-time field
 * access), so any 1.21 port-deferred entry is silently skipped instead of breaking compile.
*/
public class DestroyCreativeModeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Destroy.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BASE = TABS.register("base",
        () -> {
            List<ITabEntry> entries = new ArrayList<>();

            add(entries, sub("chemistry_equipment"));
            add(entries,
                d("vat_controller"), d("stainless_steel_block"), d("borosilicate_glass"),
                mc("create:fluid_pipe"), d("creative_pump"), mc("create:creative_fluid_tank"),
                d("siphon"), mc("create:blaze_burner"), d("cooler"), d("bubble_cap"),
                d("centrifuge"), d("dynamo"), d("blacklight"), d("colorimeter"),
                d("catalytic_converter"), d("beaker"), d("test_tube"), d("test_tube_rack"),
                d("measuring_cylinder"), d("round_bottomed_flask"),
                d("paper_mask"), d("laboratory_goggles"),
                d("gas_mask"), d("hazmat_suit"), d("hazmat_leggings"), d("wellington_boots"));

            add(entries, sub("processing"));
            add(entries,
                d("aging_barrel"), d("mechanical_sieve"), d("extrusion_die"), d("keypunch"),
                d("pumpjack"), d("tree_tap"), d("blowpipe"), d("seismometer"),
                d("redstone_programmer"), d("pollutometer"));

            add(entries, sub("metals"));
            add(entries, ITabEntry.LINE_BREAK,
                d("chromium_ingot"), d("chromium_block"), d("chromium_powder"),
                d("crushed_raw_chromium"), d("chromium_nugget"));
            add(entries, ITabEntry.LINE_BREAK,
                mc("minecraft:copper_ingot"), mc("minecraft:copper_block"), d("copper_powder"),
                mc("create:crushed_copper"), mc("create:copper_sheet"),
                mc("minecraft:raw_copper"), mc("minecraft:raw_copper_block"),
                mc("minecraft:copper_ore"), mc("minecraft:deepslate_copper_ore"));
            add(entries, ITabEntry.LINE_BREAK,
                d("stainless_steel_ingot"), d("stainless_steel_block"),
                ITabEntry.EMPTY, ITabEntry.EMPTY,
                d("stainless_steel_sheet"), d("stainless_steel_rod"), d("stainless_steel_rods_block"),
                d("molten_stainless_steel_bucket"));
            add(entries, ITabEntry.LINE_BREAK,
                mc("minecraft:iron_ingot"), mc("minecraft:iron_block"), d("iron_powder"),
                mc("create:crushed_iron"), mc("create:iron_sheet"),
                mc("minecraft:raw_iron"), mc("minecraft:raw_iron_block"),
                mc("minecraft:iron_ore"), mc("minecraft:deepslate_iron_ore"));
            add(entries, ITabEntry.LINE_BREAK,
                d("lead_ingot"), d("lead_block"), d("lead_powder"),
                mc("create:crushed_lead"), ITabEntry.EMPTY,
                d("nether_crocoite"), d("nether_crocoite_block"), d("nether_crocoite_ore"));
            add(entries, ITabEntry.LINE_BREAK,
                d("nickel_ingot"), d("nickel_block"), d("nickel_powder"),
                mc("create:crushed_nickel"), ITabEntry.EMPTY,
                d("raw_nickel"), d("raw_nickel_block"), d("nickel_ore"), d("deepslate_nickel_ore"));
            add(entries, ITabEntry.LINE_BREAK,
                d("palladium_ingot"), d("palladium_block"), d("palladium_powder"),
                d("crushed_raw_palladium"));
            add(entries, ITabEntry.LINE_BREAK,
                d("platinum_ingot"), d("platinum_block"), d("platinum_powder"),
                mc("create:crushed_platinum"));
            add(entries, ITabEntry.LINE_BREAK,
                d("rhodium_ingot"), d("rhodium_block"), d("rhodium_powder"),
                d("crushed_raw_rhodium"), ITabEntry.EMPTY, ITabEntry.EMPTY,
                d("chiseled_rhodium_block"));
            add(entries, ITabEntry.LINE_BREAK,
                d("sodium_ingot"), ITabEntry.EMPTY, ITabEntry.EMPTY,
                ITabEntry.EMPTY, ITabEntry.EMPTY,
                d("oxidized_sodium_ingot"));
            add(entries, ITabEntry.LINE_BREAK,
                mc("create:zinc_ingot"), mc("create:zinc_block"), d("zinc_powder"),
                mc("create:crushed_zinc"), d("zinc_sheet"),
                mc("create:raw_zinc"), mc("create:raw_zinc_block"),
                mc("create:zinc_ore"), mc("create:deepslate_zinc_ore"));

            add(entries, sub("plastics"));
            add(entries,
                d("polyvinyl_chloride"), d("polyethene"), d("polypropene"), d("polystyrene"),
                d("abs"), d("polytetrafluoroethene"), d("nylon"), d("polystyrene_butadiene"),
                d("polyacrylonitrile"), d("polyisoprene"), d("polyurethane"),
                d("polymethyl_methacrylate"), d("card_stock"));

            add(entries, sub("resources"));
            add(entries,
                d("fluorite"), d("fluorite_block"), d("fluorite_ore"),
                d("deepslate_fluorite_ore"), d("end_fluorite_ore"),
                d("borax"), d("silica"), d("molten_borosilicate_glass_bucket"),
                d("borosilicate_glass_fiber"), d("borosilicate_glass"), d("fiberglass_block"),
                d("insulated_stainless_steel_block"),
                d("iodine"), d("iodine_block"),
                d("carbon_fiber"), d("carbon_fiber_block"),
                d("unvarnished_plywood"), d("plywood"),
                d("clay_monolith"), d("ceramic_monolith"),
                d("chalk_dust"), d("quicklime"), d("calcium_carbide"), d("sodium_hydride"),
                d("zeolite"), d("nanodiamonds"), d("slag"));  // slag was missing from tab
            // crude_oil_bucket removed from creative tab — crude oil should be obtainable
            // only through Pumpjack extraction (preserves intended progression / chemistry gating).

            add(entries, sub("explosives"));
            add(entries, ITabEntry.LINE_BREAK,
                d("acetone_peroxide"), d("fulminated_mercury"), d("nickel_hydrazine_nitrate"),
                d("touch_powder"));
            // `cordite` → `cordite_rods`. Java field name {@code DestroyItems.CORDITE}
            // is registered with id "cordite_rods" (line 239 of DestroyItems.java). The tab
            // lookup was using the Java field name not the actual registry id → {@code
            // resolve(destroy:cordite)} returned null → silently skipped (the d() helper's
            // null-skip semantics meant no error, just missing item), so cordite rods were
            // absent from the explosives creative tab.
            add(entries, ITabEntry.LINE_BREAK,
                d("anfo"), d("cordite_rods"), d("dynamite"), d("nitrocellulose"),
                d("picric_acid_tablet"), d("tnt_tablet"));
            add(entries, ITabEntry.LINE_BREAK,
                d("custom_explosive_mix"),
                // CBC compat: shell + charge blocks only register when createbigcannons
                // is loaded (Mods.BIG_CANNONS.executeIfInstalled guard in Destroy.java). Tab
                // resolver silently skips null lookups, so entries degrade cleanly.
                cond("custom_explosive_mix_charge", () -> com.petrolpark.compat.Mods.BIG_CANNONS.isLoaded()),
                cond("custom_explosive_mix_shell", () -> com.petrolpark.compat.Mods.BIG_CANNONS.isLoaded()),
                d("dynamite_block"),
                d("cordite_block"), d("extruded_cordite_block"));

            add(entries, sub("pharmaceuticals"));
            add(entries,
                d("syringe"), d("aspirin_syringe"), d("cisplatin_syringe"),
                cond("baby_blue_syringe", DestroySubstancesConfigs::babyBlueEnabled),
                cond("baby_blue_crystal", DestroySubstancesConfigs::babyBlueEnabled),
                cond("baby_blue_powder", DestroySubstancesConfigs::babyBlueEnabled),
                d("spray_bottle"), d("perfume_bottle"), d("sunscreen_bottle"), d("creatine"));

            add(entries, sub("food"));
            add(entries,
                d("napalm_sundae"), d("thermite_brownie"), d("bomb_bon"), d("empty_bomb_bon"),
                d("butter"), d("mashed_potato"), d("mashed_potato_block"),
                d("raw_fries"), d("raw_fries_block"), d("fries"), d("bangers_and_mash"),
                d("chewing_gum"), d("empty_carton"), d("apple_juice_carton"), d("milk_carton"),
                cond("undistilled_moonshine_bottle", DestroySubstancesConfigs::alcoholEnabled),
                cond("moonshine_bottle", DestroySubstancesConfigs::alcoholEnabled),
                d("chorus_wine_bottle"));

            add(entries, sub("beetroots"));
            add(entries, ITabEntry.LINE_BREAK,
                d("hefty_beetroot"),
                d("coal_infused_beetroot"), d("copper_infused_beetroot"),
                d("diamond_infused_beetroot"), d("emerald_infused_beetroot"),
                d("fluorite_infused_beetroot"), d("gold_infused_beetroot"),
                d("iron_infused_beetroot"), d("lapis_infused_beetroot"));
            add(entries, ITabEntry.LINE_BREAK,
                d("beetroot_ashes"),
                d("coal_infused_beetroot_ashes"), d("copper_infused_beetroot_ashes"),
                d("diamond_infused_beetroot_ashes"), d("emerald_infused_beetroot_ashes"),
                d("fluorite_infused_beetroot_ashes"), d("gold_infused_beetroot_ashes"),
                d("iron_infused_beetroot_ashes"), d("lapis_infused_beetroot_ashes"));
            add(entries, ITabEntry.LINE_BREAK,
                d("nether_crocoite_infused_beetroot"), d("nickel_infused_beetroot"),
                d("quartz_infused_beetroot"), d("redstone_infused_beetroot"),
                d("zinc_infused_beetroot"),
                d("hyperaccumulating_fertilizer"), d("magic_beetroot_seeds"));
            add(entries, ITabEntry.LINE_BREAK,
                d("nether_crocoite_infused_beetroot_ashes"),
                d("nickel_infused_beetroot_ashes"), d("quartz_infused_beetroot_ashes"),
                d("redstone_infused_beetroot_ashes"), d("zinc_infused_beetroot_ashes"));

            add(entries, sub("periodic_table_blocks"));
            add(entries,
                d("element_tank"),
                d("hydrogen_periodic_table_block"), d("carbon_periodic_table_block"),
                d("nitrogen_periodic_table_block"), d("oxygen_periodic_table_block"),
                d("fluorine_periodic_table_block"), d("chlorine_periodic_table_block"),
                d("chromium_periodic_table_block"), d("iron_periodic_table_block"),
                d("nickel_periodic_table_block"), d("copper_periodic_table_block"),
                d("zinc_periodic_table_block"), d("rhodium_periodic_table_block"),
                d("palladium_periodic_table_block"), d("iodine_periodic_table_block"),
                d("platinum_periodic_table_block"), d("gold_periodic_table_block"),
                d("mercury_periodic_table_block"), d("lead_periodic_table_block"));

            add(entries, sub("misc"));
            add(entries,
                d("blank_music_disc"), d("music_disc_spectrum"),
                d("circuit_mask"), d("ruined_circuit_mask"),
                d("circuit_board"), d("ruined_circuit_board"),
                d("mesh"), d("gas_filter"), d("swiss_army_knife"), d("bucket_and_spade"),
                d("voltaic_pile"), d("discharged_voltaic_pile"),
                d("tear_bottle"), d("urine_bottle"), d("yeast"),
                d("confetti"), d("white_confetti"));

            CustomTab.Builder builder = new CustomTab.Builder(CreativeModeTab.Row.TOP, 0);
            builder.add(entries.toArray(new ITabEntry[0]));
            return builder
                .title(Component.translatable("itemGroup.destroy.base"))
                .withTabsBefore(AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getId())
                .icon(() -> DestroyItems.LOGO.asStack())
                .build();
        });

    // ---- helpers ----

    private static void add(List<ITabEntry> entries, ITabEntry... items) {
        for (ITabEntry e : items) if (e != null) entries.add(e);
    }

    /** Destroy-namespace item/block by path. Returns null if unregistered → skipped by {@link #add}.*/
    private static ITabEntry d(String path) {
        return resolve(ResourceLocation.fromNamespaceAndPath(Destroy.MOD_ID, path), null);
    }

    /** Fully-qualified namespace:path (cross-mod or vanilla).*/
    private static ITabEntry mc(String fullId) {
        return resolve(ResourceLocation.parse(fullId), null);
    }

    /** Destroy item gated on a config flag; skipped when {@code flag.getAsBoolean()} is false.*/
    private static ITabEntry cond(String path, BooleanSupplier flag) {
        return resolve(ResourceLocation.fromNamespaceAndPath(Destroy.MOD_ID, path), flag);
    }

    private static ITabEntry resolve(ResourceLocation id, BooleanSupplier gate) {
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == Blocks.AIR) return null;
            item = block.asItem();
            if (item == Items.AIR) return null;
        }
        final Item finalItem = item;
        Supplier<ItemStack> supplier = gate == null
            ? () -> new ItemStack(finalItem)
            : () -> gate.getAsBoolean() ? new ItemStack(finalItem) : ItemStack.EMPTY;
        return new ITabEntry.SingleItem(supplier);
    }

    private static ITabEntry.Subheading sub(String translationKey) {
        return new ITabEntry.Subheading(Component.translatable("itemGroup.destroy.base." + translationKey));
    }

    public static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }
}
