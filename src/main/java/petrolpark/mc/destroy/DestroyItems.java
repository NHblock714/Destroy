package petrolpark.mc.destroy;

import static petrolpark.mc.destroy.Destroy.REGISTRATE;

import com.petrolpark.PetrolparkTags;
import com.simibubi.create.AllTags.AllItemTags;
import com.tterrag.registrate.util.entry.ItemEntry;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.common.Tags;
import petrolpark.mc.destroy.config.DestroyAllConfigs;

/**
 * Destroy's item registry. Entries that depend on custom Item subclasses or block items tied to a
 * BlockEntityType reference those classes directly; plain entries use {@code Item::new} with tags.
*/
public class DestroyItems {

    // DUMMY ITEMS (no subclass, no tab — just registered so datagen lang/model exist)

    public static final ItemEntry<Item>

    LOGO = REGISTRATE.item("logo", Item::new)
        .removeTab(CreativeModeTabs.SEARCH)
        .register(),

    POLLUTION_SYMBOL = REGISTRATE.item("pollution_symbol", Item::new)
        .removeTab(CreativeModeTabs.SEARCH)
        .register();

    // PLASTICS

    public static final ItemEntry<Item>

    POLYETHENE_TEREPHTHALATE = REGISTRATE.item("polyethene_terephthalate", Item::new)
        .register(),
    POLYVINYL_CHLORIDE = REGISTRATE.item("polyvinyl_chloride", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RIGID_PLASTICS.tag)
        .register(),
    POLYETHENE = REGISTRATE.item("polyethene", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RIGID_PLASTICS.tag)
        .register(),
    POLYPROPENE = REGISTRATE.item("polypropene", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RIGID_PLASTICS.tag)
        .register(),
    POLYSTYRENE = REGISTRATE.item("polystyrene", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RIGID_PLASTICS.tag, DestroyTags.Items.POROUS_PLASTICS.tag)
        .register(),
    ABS = REGISTRATE.item("abs", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RIGID_PLASTICS.tag)
        .register(),
    POLYTETRAFLUOROETHENE = REGISTRATE.item("polytetrafluoroethene", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RIGID_PLASTICS.tag, DestroyTags.Items.INERT_PLASTICS.tag)
        .register(),
    NYLON = REGISTRATE.item("nylon", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RIGID_PLASTICS.tag, DestroyTags.Items.TEXTILE_PLASTICS.tag)
        .register(),
    POLYSTYRENE_BUTADIENE = REGISTRATE.item("polystyrene_butadiene", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RUBBER_PLASTICS.tag, Tags.Items.STRINGS)
        .register(),
    POLYACRYLONITRILE = REGISTRATE.item("polyacrylonitrile", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag)
        .register(),
    POLYISOPRENE = REGISTRATE.item("polyisoprene", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.TEXTILE_PLASTICS.tag, DestroyTags.Items.RUBBER_PLASTICS.tag)
        .register(),
    POLYURETHANE = REGISTRATE.item("polyurethane", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RIGID_PLASTICS.tag, DestroyTags.Items.TEXTILE_PLASTICS.tag, DestroyTags.Items.POROUS_PLASTICS.tag)
        .register(),
    POLYMETHYL_METHACRYLATE = REGISTRATE.item("polymethyl_methacrylate", Item::new)
        .tag(DestroyTags.Items.PLASTICS.tag, DestroyTags.Items.RIGID_PLASTICS.tag, DestroyTags.Items.TRANSPARENT_PLASTICS.tag)
        .register(),

    CARD_STOCK = REGISTRATE.item("card_stock", Item::new)
        .register(),
    CARBON_FIBER = REGISTRATE.item("carbon_fiber", Item::new)
        .register(),

    // INGOTS ETC

    FLUORITE = REGISTRATE.item("fluorite", Item::new)
        .tag(PetrolparkTags.commonItemTag("raw_materials/fluorite"), ItemTags.BEACON_PAYMENT_ITEMS, ItemTags.TRIM_MATERIALS, DestroyTags.Items.FLUXES.tag)
        .register(),
    NICKEL_INGOT = REGISTRATE.item("nickel_ingot", Item::new)
        .tag(DestroyTags.Items.DESTROY_INGOTS.tag, PetrolparkTags.commonItemTag("ingots/nickel"), Tags.Items.INGOTS, ItemTags.TRIM_MATERIALS)
        .register(),
    CHROMIUM_INGOT = REGISTRATE.item("chromium_ingot", Item::new)
        .tag(DestroyTags.Items.DESTROY_INGOTS.tag, PetrolparkTags.commonItemTag("ingots/chromium"), Tags.Items.INGOTS, ItemTags.TRIM_MATERIALS)
        .register(),
    LEAD_INGOT = REGISTRATE.item("lead_ingot", Item::new)
        .tag(DestroyTags.Items.DESTROY_INGOTS.tag, PetrolparkTags.commonItemTag("ingots/lead"), Tags.Items.INGOTS, ItemTags.TRIM_MATERIALS)
        .register(),
    OXIDIZED_SODIUM_INGOT = REGISTRATE.item("oxidized_sodium_ingot", Item::new)
        .tag(DestroyTags.Items.DESTROY_INGOTS.tag, Tags.Items.INGOTS, DestroyTags.Items.FLUXES.tag)
        .register(),
    PALLADIUM_INGOT = REGISTRATE.item("palladium_ingot", Item::new)
        .tag(DestroyTags.Items.DESTROY_INGOTS.tag, PetrolparkTags.commonItemTag("ingots/palladium"), Tags.Items.INGOTS, ItemTags.TRIM_MATERIALS)
        .register(),
    PLATINUM_INGOT = REGISTRATE.item("platinum_ingot", Item::new)
        .tag(DestroyTags.Items.DESTROY_INGOTS.tag, PetrolparkTags.commonItemTag("ingots/platinum"), Tags.Items.INGOTS, ItemTags.TRIM_MATERIALS)
        .register(),
    RHODIUM_INGOT = REGISTRATE.item("rhodium_ingot", Item::new)
        .tag(DestroyTags.Items.DESTROY_INGOTS.tag, PetrolparkTags.commonItemTag("ingots/rhodium"), Tags.Items.INGOTS, ItemTags.TRIM_MATERIALS)
        .register(),
    STAINLESS_STEEL_INGOT = REGISTRATE.item("stainless_steel_ingot", Item::new)
        .tag(DestroyTags.Items.DESTROY_INGOTS.tag, PetrolparkTags.commonItemTag("ingots/steel"), PetrolparkTags.commonItemTag("ingots/stainless_steel"), Tags.Items.INGOTS, ItemTags.TRIM_MATERIALS)
        .register(),
    PURE_GOLD_INGOT = REGISTRATE.item("pure_gold_ingot", Item::new)
        .tag(DestroyTags.Items.DESTROY_INGOTS.tag, Tags.Items.INGOTS, ItemTags.PIGLIN_LOVED, ItemTags.TRIM_MATERIALS)
        .register(),
    CHROMIUM_NUGGET = REGISTRATE.item("chromium_nugget", Item::new)
        .tag(Tags.Items.NUGGETS, PetrolparkTags.commonItemTag("nuggets/chromium"))
        .register(),
    ZINC_SHEET = REGISTRATE.item("zinc_sheet", Item::new)
        .tag(PetrolparkTags.commonItemTag("plates/zinc"), PetrolparkTags.commonItemTag("plates"))
        .register(),
    STAINLESS_STEEL_SHEET = REGISTRATE.item("stainless_steel_sheet", Item::new)
        .tag(PetrolparkTags.commonItemTag("plates/steel"), PetrolparkTags.commonItemTag("plates/stainless_steel"), PetrolparkTags.commonItemTag("plates"))
        .register(),
    STAINLESS_STEEL_ROD = REGISTRATE.item("stainless_steel_rod", Item::new)
        .tag(PetrolparkTags.commonItemTag("rods/steel"), PetrolparkTags.commonItemTag("rods/stainless_steel"), Tags.Items.RODS)
        .register();

    // RAW MATERIALS

    public static final ItemEntry<Item>

    RAW_NICKEL = REGISTRATE.item("raw_nickel", Item::new)
        .tag(PetrolparkTags.commonItemTag("raw_materials/nickel"), Tags.Items.RAW_MATERIALS)
        .register(),
    CRUSHED_RAW_CHROMIUM = REGISTRATE.item("crushed_raw_chromium", Item::new)
        .tag(AllItemTags.CRUSHED_RAW_MATERIALS.tag)
        .register(),
    CRUSHED_RAW_PALLADIUM = REGISTRATE.item("crushed_raw_palladium", Item::new)
        .tag(AllItemTags.CRUSHED_RAW_MATERIALS.tag)
        .register(),
    CRUSHED_RAW_RHODIUM = REGISTRATE.item("crushed_raw_rhodium", Item::new)
        .tag(AllItemTags.CRUSHED_RAW_MATERIALS.tag)
        .register(),
    PURE_GOLD_DUST = REGISTRATE.item("pure_gold_dust", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/gold"), Tags.Items.DUSTS, ItemTags.PIGLIN_LOVED)
        .register(),
    NETHER_CROCOITE = REGISTRATE.item("nether_crocoite", Item::new)
        .register(),
    BORAX = REGISTRATE.item("borax", Item::new)
        .tag(PetrolparkTags.commonItemTag("raw_materials/borax"), DestroyTags.Items.FLUXES.tag)
        .register(),
    SLAG = REGISTRATE.item("slag", Item::new)
        .register(),
    SILICA = REGISTRATE.item("silica", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/silica"), PetrolparkTags.commonItemTag("raw_materials/silica"))
        .register(),
    ZEOLITE = REGISTRATE.item("zeolite", Item::new)
        .register();

    // MOLTEN BUCKETS — SolidBucketItem (vanilla). The underlying block's asItem() routes back here.

    public static final com.tterrag.registrate.util.entry.ItemEntry<net.minecraft.world.item.SolidBucketItem>

    MOLTEN_STAINLESS_STEEL_BUCKET = REGISTRATE.item("molten_stainless_steel_bucket",
        p -> new net.minecraft.world.item.SolidBucketItem(DestroyBlocks.MOLTEN_STAINLESS_STEEL.get(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_LAVA, p))
        .properties(p -> p.stacksTo(1))  // bucket-style item, single stack
        .register(),

    MOLTEN_BOROSILICATE_GLASS_BUCKET = REGISTRATE.item("molten_borosilicate_glass_bucket",
        p -> new net.minecraft.world.item.SolidBucketItem(DestroyBlocks.MOLTEN_BOROSILICATE_GLASS.get(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY_LAVA, p))
        .properties(p -> p.stacksTo(1))  // bucket-style item, single stack
        .register();

    // DUSTS

    public static final ItemEntry<Item>

    COPPER_POWDER = REGISTRATE.item("copper_powder", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/copper"), Tags.Items.DUSTS)
        .register(),
    IRON_POWDER = REGISTRATE.item("iron_powder", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/iron"), Tags.Items.DUSTS)
        .register(),
    NICKEL_POWDER = REGISTRATE.item("nickel_powder", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/nickel"), Tags.Items.DUSTS)
        .register(),
    CHROMIUM_POWDER = REGISTRATE.item("chromium_powder", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/chromium"), Tags.Items.DUSTS)
        .register(),
    LEAD_POWDER = REGISTRATE.item("lead_powder", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/lead"), Tags.Items.DUSTS)
        .register(),
    PALLADIUM_POWDER = REGISTRATE.item("palladium_powder", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/palladium"), Tags.Items.DUSTS)
        .register(),
    PLATINUM_POWDER = REGISTRATE.item("platinum_powder", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/platinum"), Tags.Items.DUSTS)
        .register(),
    RHODIUM_POWDER = REGISTRATE.item("rhodium_powder", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/rhodium"), Tags.Items.DUSTS)
        .register(),
    ZINC_POWDER = REGISTRATE.item("zinc_powder", Item::new)
        .tag(PetrolparkTags.commonItemTag("dusts/zinc"), Tags.Items.DUSTS)
        .register(),

    // PRIMARY EXPLOSIVES

    ACETONE_PEROXIDE = REGISTRATE.item("acetone_peroxide", Item::new)
        .tag(DestroyTags.Items.PRIMARY_EXPLOSIVES.tag, Tags.Items.DUSTS)
        .register(),
    FULMINATED_MERCURY = REGISTRATE.item("fulminated_mercury", Item::new)
        .tag(DestroyTags.Items.PRIMARY_EXPLOSIVES.tag, Tags.Items.DUSTS)
        .register(),
    NICKEL_HYDRAZINE_NITRATE = REGISTRATE.item("nickel_hydrazine_nitrate", Item::new)
        .tag(DestroyTags.Items.PRIMARY_EXPLOSIVES.tag, Tags.Items.DUSTS)
        .register();

    public static final ItemEntry<petrolpark.mc.destroy.core.explosion.ContactExplosiveItem> TOUCH_POWDER =
        REGISTRATE.item("touch_powder", petrolpark.mc.destroy.core.explosion.ContactExplosiveItem::new)
            .tag(DestroyTags.Items.PRIMARY_EXPLOSIVES.tag, Tags.Items.DUSTS)
            .register();

    public static final ItemEntry<Item>

    // SECONDARY EXPLOSIVES

    ANFO = REGISTRATE.item("anfo", Item::new)
        .tag(DestroyTags.Items.SECONDARY_EXPLOSIVES.tag, Tags.Items.DUSTS)
        .register(),
    CORDITE = REGISTRATE.item("cordite_rods", Item::new)
        .tag(DestroyTags.Items.SECONDARY_EXPLOSIVES.tag)
        .register(),
    DYNAMITE = REGISTRATE.item("dynamite", Item::new)
        .tag(DestroyTags.Items.SECONDARY_EXPLOSIVES.tag)
        .register(),
    NITROCELLULOSE = REGISTRATE.item("nitrocellulose", Item::new)
        .tag(DestroyTags.Items.SECONDARY_EXPLOSIVES.tag)
        .register(),
    PICRIC_ACID_TABLET = REGISTRATE.item("picric_acid_tablet", Item::new)
        .tag(DestroyTags.Items.SECONDARY_EXPLOSIVES.tag)
        .register(),
    TNT_TABLET = REGISTRATE.item("tnt_tablet", Item::new)
        .tag(DestroyTags.Items.SECONDARY_EXPLOSIVES.tag)
        .register(),

    // COMPOUNDS

    CHALK_DUST = REGISTRATE.item("chalk_dust", Item::new)
        .tag(Tags.Items.DUSTS, PetrolparkTags.commonItemTag("dusts/chalk"), DestroyTags.Items.FLUXES.tag)
        .register(),
    CALCIUM_CARBIDE = REGISTRATE.item("calcium_carbide", Item::new)
        .register(),
    BABY_BLUE_CRYSTAL = REGISTRATE.item("baby_blue_crystal", Item::new)
        .register(),
    BABY_BLUE_POWDER = REGISTRATE.item("baby_blue_powder", Item::new)
        .properties(p -> p
            .food(DestroyFoods.BABY_BLUE_POWDER)
        ).tag(Tags.Items.DUSTS)
        .register(),

    // TOOLS / SIMPLE GEAR

    GAS_FILTER = REGISTRATE.item("gas_filter", Item::new)
        .register(),

    // SPRAY BOTTLES — SprayBottleItem ported

    SPRAY_BOTTLE = REGISTRATE.item("spray_bottle", Item::new)
        .tag(DestroyTags.Items.SPRAY_BOTTLES.tag)
        .register();

    public static final ItemEntry<petrolpark.mc.destroy.content.tool.SprayBottleItem>

    PERFUME_BOTTLE = REGISTRATE.item("perfume_bottle",
            p -> new petrolpark.mc.destroy.content.tool.SprayBottleItem(p,
                new net.minecraft.world.effect.MobEffectInstance(DestroyMobEffects.FRAGRANCE, 12000, 0)))
        .tag(DestroyTags.Items.SPRAY_BOTTLES.tag)
        .register(),
    SUNSCREEN_BOTTLE = REGISTRATE.item("sunscreen_bottle",
            p -> new petrolpark.mc.destroy.content.tool.SprayBottleItem(p,
                new net.minecraft.world.effect.MobEffectInstance(DestroyMobEffects.SUN_PROTECTION, 12000, 0, false, false, true)))
        .tag(DestroyTags.Items.SPRAY_BOTTLES.tag)
        .register();

    // TOYS — BucketAndSpade. Used on sand/red sand/soul sand → places sand castle block
    // (consuming 1 durability, max 4 uses). Dispensable via dispenser.

    public static final ItemEntry<petrolpark.mc.destroy.content.sandcastle.BucketAndSpadeItem> BUCKET_AND_SPADE =
        REGISTRATE.item("bucket_and_spade", petrolpark.mc.destroy.content.sandcastle.BucketAndSpadeItem::new)
            .properties(p -> p.durability(4))
            .register();

    // SWISS ARMY KNIFE — DiggerItem subclass that dynamically switches between
    // pickaxe/axe/shovel/hoe/shears based on what the player is looking at. 1.21 DiggerItem ctor
    // is 3-arg (Tier, TagKey, Properties); attack damage/speed supplied via Properties.attributes.
    public static final ItemEntry<petrolpark.mc.destroy.content.tool.swissarmyknife.SwissArmyKnifeItem> SWISS_ARMY_KNIFE =
        REGISTRATE.item("swiss_army_knife",
            p -> new petrolpark.mc.destroy.content.tool.swissarmyknife.SwissArmyKnifeItem(net.minecraft.world.item.Tiers.DIAMOND, p))
            .properties(p -> p
                .durability(1600)
                .attributes(net.minecraft.world.item.DiggerItem.createAttributes(net.minecraft.world.item.Tiers.DIAMOND, 5f, -1f)))
            .register();

    // CIRCUIT_BOARD.
    // CircuitBoardItem extends CircuitPatternItem to carry a 4×4 binary circuit pattern
    // for the trypolithography pipeline. Hover tooltip visualizes the pattern via
    // CircuitPatternTooltipComponent.
    public static final ItemEntry<petrolpark.mc.destroy.content.processing.trypolithography.CircuitBoardItem> CIRCUIT_BOARD =
        REGISTRATE.item("circuit_board",
            petrolpark.mc.destroy.content.processing.trypolithography.CircuitBoardItem::new)
            .register();

    // CIRCUIT_MASK.
    // CircuitMaskItem extends CircuitPatternItem + tracks up to 3 keypunch UUIDs via PUNCHED_BY
    // DataComponent. When 3rd punch exceeded → replaced with RUINED_CIRCUIT_MASK
    // (already registered above as plain Item at ~line 562).
    public static final ItemEntry<petrolpark.mc.destroy.content.processing.trypolithography.CircuitMaskItem> CIRCUIT_MASK =
        REGISTRATE.item("circuit_mask",
            petrolpark.mc.destroy.content.processing.trypolithography.CircuitMaskItem::new)
            .properties(p -> p.stacksTo(1)) // unique per-item punch state (pattern + PUNCHED_BY) — must not stack (matches upstream)
            .register();

    // SEISMOMETER + SEISMOGRAPH.
    // SeismometerItem handles ExplosionEvent.Start to populate Seismograph nonograms
    // on explosion events. SeismographItem extends MapItem with DataComponent-stored
    // nonogram state. Custom renderers deferred
    // (DestroyGuiTextures unported) — see BROKEN.md §2.7.
    public static final ItemEntry<petrolpark.mc.destroy.content.oil.seismology.SeismometerItem> SEISMOMETER =
        REGISTRATE.item("seismometer", petrolpark.mc.destroy.content.oil.seismology.SeismometerItem::new)
            .properties(p -> p.stacksTo(1))
            .register();

    public static final ItemEntry<petrolpark.mc.destroy.content.oil.seismology.SeismographItem> SEISMOGRAPH =
        REGISTRATE.item("seismograph", petrolpark.mc.destroy.content.oil.seismology.SeismographItem::new)
            .properties(p -> p.stacksTo(1))
            .register();

    // IODINE — IodineItem: dropped iodine on fire → Ender-dragon-breath cloud.
    // Tooltip (IDynamicItemDescription) not yet wired; gameplay unaffected.

    public static final ItemEntry<petrolpark.mc.destroy.content.product.IodineItem> IODINE =
        REGISTRATE.item("iodine", petrolpark.mc.destroy.content.product.IodineItem::new)
            .register();

    // CREATINE Eaten → permanent EXTRA_INVENTORY_SIZE / EXTRA_HOTBAR_SLOTS attribute
    // modifiers (values from SERVER.substances.creatineExtra{InventorySize, HotbarSlots}). 1.21.1
    // AttributeModifier ctor uses ResourceLocation + Operation.ADD_VALUE;

    public static final ItemEntry<petrolpark.mc.destroy.content.product.CreatineItem> CREATINE =
        REGISTRATE.item("creatine", petrolpark.mc.destroy.content.product.CreatineItem::new)
            .properties(p -> p.food(DestroyFoods.CREATINE))
            .tag(Tags.Items.DUSTS)
            .register();

    // HYPERACCUMULATING_FERTILIZER BoneMeal 增强品：右键作物触发 CropMutation，
    // 否则 fallback 到原版 BoneMeal 行为。详见 HyperaccumulatingFertilizerItem javadoc /

    public static final ItemEntry<petrolpark.mc.destroy.content.processing.phytomining.HyperaccumulatingFertilizerItem> HYPERACCUMULATING_FERTILIZER =
        REGISTRATE.item("hyperaccumulating_fertilizer", petrolpark.mc.destroy.content.processing.phytomining.HyperaccumulatingFertilizerItem::new)
            .tag(Tags.Items.DUSTS, DestroyTags.Items.BONEMEAL_BYPASSES_POLLUTION.tag)
            .register();

    // TEST_TUBE: 25 mB Mixture container. Right-click block to fill/empty via
    // FLUID_HANDLER capability. Used by JEI cheat mode + chemistry sampling gameplay.
    // Requires the TEST_TUBE_RACK_STORABLE tag; without it, the rack
    // block's ItemStackHandler.isItemValid returns false for TEST_TUBE and insertion/swap fails
    // silently.
    public static final ItemEntry<petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeItem> TEST_TUBE =
        REGISTRATE.item("test_tube", petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeItem::new)
            .tag(DestroyTags.Items.TEST_TUBE_RACK_STORABLE.tag)
            .register();

    // BALLOON: 500 mB gas-Mixture container. Primary use: capture gas-phase Mixtures from Vat.
    public static final ItemEntry<petrolpark.mc.destroy.core.chemistry.storage.BalloonItem> BALLOON =
        REGISTRATE.item("balloon", petrolpark.mc.destroy.core.chemistry.storage.BalloonItem::new)
            .register();

    // SODIUM_INGOT / QUICKLIME petrolpark-Library 1.21 的 data-component Decay 系统：
    // Item 定义产物 Supplier + 寿命，在第一次 inventoryTick 把 DECAY_PRODUCT / DECAY_TIME 组件 set
    // 到 stack；ItemStackMixin.copy() 钩子自动驱动 checkDecay；DecayingItemDecorator 渲染进度条。
    // SODIUM_HYDRIDE 依赖 SmartExplosion，留待 Explosion 批。详见 OxidizingItem / CarboxylatingItem
    // javadoc 与

    public static final ItemEntry<petrolpark.mc.destroy.content.product.OxidizingItem> SODIUM_INGOT =
        REGISTRATE.item("sodium_ingot", p -> new petrolpark.mc.destroy.content.product.OxidizingItem(
            p,
            OXIDIZED_SODIUM_INGOT::asStack,
            () -> DestroyAllConfigs.SERVER.substances.sodiumDecayTime.get(),
            "item.destroy.oxidizing_item.remaining"))
            .tag(DestroyTags.Items.DESTROY_INGOTS.tag, PetrolparkTags.commonItemTag("ingots/sodium"),
                Tags.Items.INGOTS, ItemTags.TRIM_MATERIALS)
            .register();

    public static final ItemEntry<petrolpark.mc.destroy.content.product.CarboxylatingItem> QUICKLIME =
        REGISTRATE.item("quicklime", p -> new petrolpark.mc.destroy.content.product.CarboxylatingItem(
            p,
            CHALK_DUST::asStack,
            () -> DestroyAllConfigs.SERVER.substances.quicklimeBaseDecayTime.get(),
            "item.destroy.carboxylating_item.remaining"))
            .tag(Tags.Items.DUSTS, PetrolparkTags.commonItemTag("dusts/lime"),
                DestroyTags.Items.FLUXES.tag)
            .register();

    // SODIUM_HYDRIDE 水/雨遇水即爆。依赖 SmartExplosion 本体— 已 port 基础
    // 爆炸（entity damage + block destroy 生效） 粒子/音效 sync 留 ，此期间爆炸
    // 客户端看不到粒子但伤害仍处理。详见 WaterSensitiveSpontaneouslyCombustingItem /

    public static final ItemEntry<petrolpark.mc.destroy.content.product.WaterSensitiveSpontaneouslyCombustingItem> SODIUM_HYDRIDE =
        REGISTRATE.item("sodium_hydride",
            petrolpark.mc.destroy.content.product.WaterSensitiveSpontaneouslyCombustingItem::new)
            .tag(Tags.Items.DUSTS)
            .register();

    // CONFETTI / WHITE_CONFETTI (Confetti mini-batch). Dispenser-driven item emits
    // ISpecialEffectExplosiveItem
    // (炸药混合特效) 留给 PrimedBomb 批。

    public static final ItemEntry<petrolpark.mc.destroy.content.confetti.ConfettiItem>

    CONFETTI = REGISTRATE.item("confetti", p -> new petrolpark.mc.destroy.content.confetti.ConfettiItem(
        p, petrolpark.mc.destroy.content.confetti.ConfettoParticleData::new))
        .register(),

    WHITE_CONFETTI = REGISTRATE.item("white_confetti", p -> new petrolpark.mc.destroy.content.confetti.ConfettiItem(
        p, petrolpark.mc.destroy.content.confetti.ConfettoParticleData.White::new))
        .register();

    public static final ItemEntry<Item>

    // FOOD

    BUTTER = REGISTRATE.item("butter", Item::new)
        .properties(p -> p.food(DestroyFoods.BUTTER))
        .register(),
    RAW_FRIES = REGISTRATE.item("raw_fries", Item::new)
        .properties(p -> p.food(DestroyFoods.RAW_FRIES))
        .register(),
    FRIES = REGISTRATE.item("fries", Item::new)
        .properties(p -> p.food(DestroyFoods.FRIES))
        .register(),
    MASHED_POTATO = REGISTRATE.item("mashed_potato", Item::new)
        .properties(p -> p.food(DestroyFoods.MASHED_POTATO))
        .register(),

    EMPTY_CARTON = REGISTRATE.item("empty_carton", Item::new)
        .tag(AllItemTags.UPRIGHT_ON_BELT.tag)
        .register(),

    // BEETROOT (infused variants need WithSecondaryItem — held)

    HEFTY_BEETROOT = REGISTRATE.item("hefty_beetroot", Item::new)
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag)
        .register(),
    BEETROOT_ASHES = REGISTRATE.item("beetroot_ashes", Item::new)
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag)
        .register();

    // HEFTY BEETROOT — infused variants (WithSecondaryItem: shift to peek at what's inside)

    public static final ItemEntry<petrolpark.mc.destroy.core.item.WithSecondaryItem>

    COAL_INFUSED_BEETROOT = REGISTRATE.item("coal_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL)))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    COPPER_INFUSED_BEETROOT = REGISTRATE.item("copper_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_COPPER)))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    DIAMOND_INFUSED_BEETROOT = REGISTRATE.item("diamond_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    EMERALD_INFUSED_BEETROOT = REGISTRATE.item("emerald_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD)))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    FLUORITE_INFUSED_BEETROOT = REGISTRATE.item("fluorite_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> FLUORITE.asStack()))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    GOLD_INFUSED_BEETROOT = REGISTRATE.item("gold_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_GOLD)))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    IRON_INFUSED_BEETROOT = REGISTRATE.item("iron_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON)))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    LAPIS_INFUSED_BEETROOT = REGISTRATE.item("lapis_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI)))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    NICKEL_INFUSED_BEETROOT = REGISTRATE.item("nickel_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> RAW_NICKEL.asStack()))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    NETHER_CROCOITE_INFUSED_BEETROOT = REGISTRATE.item("nether_crocoite_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> NETHER_CROCOITE.asStack()))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    QUARTZ_INFUSED_BEETROOT = REGISTRATE.item("quartz_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.QUARTZ)))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    REDSTONE_INFUSED_BEETROOT = REGISTRATE.item("redstone_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE)))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),
    ZINC_INFUSED_BEETROOT = REGISTRATE.item("zinc_infused_beetroot", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> com.simibubi.create.AllItems.RAW_ZINC.asStack()))
        .tag(DestroyTags.Items.HEFTY_BEETROOTS.tag).register(),

    // BEETROOT ASHES — infused variants

    COAL_INFUSED_BEETROOT_ASHES = REGISTRATE.item("coal_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COAL)))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    COPPER_INFUSED_BEETROOT_ASHES = REGISTRATE.item("copper_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_COPPER)))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    DIAMOND_INFUSED_BEETROOT_ASHES = REGISTRATE.item("diamond_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    EMERALD_INFUSED_BEETROOT_ASHES = REGISTRATE.item("emerald_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD)))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    FLUORITE_INFUSED_BEETROOT_ASHES = REGISTRATE.item("fluorite_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> FLUORITE.asStack()))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    GOLD_INFUSED_BEETROOT_ASHES = REGISTRATE.item("gold_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_GOLD)))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    IRON_INFUSED_BEETROOT_ASHES = REGISTRATE.item("iron_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.RAW_IRON)))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    LAPIS_INFUSED_BEETROOT_ASHES = REGISTRATE.item("lapis_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAPIS_LAZULI)))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    NICKEL_INFUSED_BEETROOT_ASHES = REGISTRATE.item("nickel_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> RAW_NICKEL.asStack()))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    NETHER_CROCOITE_INFUSED_BEETROOT_ASHES = REGISTRATE.item("nether_crocoite_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> NETHER_CROCOITE.asStack()))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    QUARTZ_INFUSED_BEETROOT_ASHES = REGISTRATE.item("quartz_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.QUARTZ)))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    REDSTONE_INFUSED_BEETROOT_ASHES = REGISTRATE.item("redstone_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE)))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register(),
    ZINC_INFUSED_BEETROOT_ASHES = REGISTRATE.item("zinc_infused_beetroot_ashes", p -> new petrolpark.mc.destroy.core.item.WithSecondaryItem(p, i -> com.simibubi.create.AllItems.RAW_ZINC.asStack()))
        .tag(DestroyTags.Items.BEETROOT_ASHES.tag).register();

    // SYRINGES — plain base + 3 functional variants.

    public static final ItemEntry<Item> SYRINGE = REGISTRATE.item("syringe", Item::new)
        .tag(DestroyTags.Items.SYRINGES.tag)
        .register();

    public static final ItemEntry<petrolpark.mc.destroy.content.product.AspirinSyringeItem> ASPIRIN_SYRINGE =
        REGISTRATE.item("aspirin_syringe", petrolpark.mc.destroy.content.product.AspirinSyringeItem::new)
            .tag(DestroyTags.Items.SYRINGES.tag)
            .register();

    public static final ItemEntry<petrolpark.mc.destroy.content.product.CisplatinSyringeItem> CISPLATIN_SYRINGE =
        REGISTRATE.item("cisplatin_syringe", petrolpark.mc.destroy.content.product.CisplatinSyringeItem::new)
            .tag(DestroyTags.Items.SYRINGES.tag)
            .register();

    public static final ItemEntry<petrolpark.mc.destroy.content.product.babyblue.BabyBlueSyringeItem> BABY_BLUE_SYRINGE =
        REGISTRATE.item("baby_blue_syringe",
            p -> new petrolpark.mc.destroy.content.product.babyblue.BabyBlueSyringeItem(p, 1200, 1))
            .tag(DestroyTags.Items.SYRINGES.tag)
            .register();

    // UNCATEGORISED (all plain Item::new with tags)

    public static final ItemEntry<Item>

    DISCHARGED_VOLTAIC_PILE = REGISTRATE.item("discharged_voltaic_pile", Item::new)
        .tag(DestroyTags.Items.LIABLE_TO_CHANGE.tag)
        .register(),
    MESH = REGISTRATE.item("mesh", Item::new)
        .register(),
    TEAR_BOTTLE = REGISTRATE.item("tear_bottle", Item::new)
        .tag(AllItemTags.UPRIGHT_ON_BELT.tag)
        .register(),
    URINE_BOTTLE = REGISTRATE.item("urine_bottle", Item::new)
        .tag(AllItemTags.UPRIGHT_ON_BELT.tag)
        .register(),
    VOLTAIC_PILE = REGISTRATE.item("voltaic_pile", Item::new)
        .tag(DestroyTags.Items.LIABLE_TO_CHANGE.tag)
        .register(),
    YEAST = REGISTRATE.item("yeast", Item::new)
        .tag(DestroyTags.Items.FERTILIZERS.tag, DestroyTags.Items.YEAST.tag)
        .register(),
    NANODIAMONDS = REGISTRATE.item("nanodiamonds", Item::new)
        .register(),
    RUINED_CIRCUIT_MASK = REGISTRATE.item("ruined_circuit_mask", Item::new)
        .register(),
    RUINED_CIRCUIT_BOARD = REGISTRATE.item("ruined_circuit_board", Item::new)
        .register(),

    // MUSIC RELATED
    // 1.21.1 note: net.minecraft.world.item.RecordItem was REMOVED. Music discs are now plain
    // Items carrying the DataComponents.JUKEBOX_PLAYABLE component pointing at a JukeboxSong.
    // A "blank" disc therefore needs no custom subclass — just a plain Item + MUSIC_DISCS tag
    // + .stacksTo(1). MUSIC_DISC_SPECTRUM uses a data-driven JukeboxSong stored
    // at data/destroy/jukebox_song/music_disc_spectrum.json — the item just carries a
    // JukeboxPlayable component referencing that ResourceKey.

    BLANK_MUSIC_DISC = REGISTRATE.item("blank_music_disc", Item::new)
        .properties(p -> p.stacksTo(1))
        .tag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.withDefaultNamespace("music_discs")))
        .register(),
    MUSIC_DISC_SPECTRUM = REGISTRATE.item("music_disc_spectrum", Item::new)
        .properties(p -> p
            .stacksTo(1)
            .component(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE,
                new net.minecraft.world.item.JukeboxPlayable(
                    new net.minecraft.world.item.EitherHolder<>(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.JUKEBOX_SONG,
                        petrolpark.mc.destroy.Destroy.asResource("music_disc_spectrum"))),
                    true)))
        .tag(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, net.minecraft.resources.ResourceLocation.withDefaultNamespace("music_discs")))
        .register();

    // DISC_STAMPER — reusable tool that stamps a specific music disc onto a
    // BLANK_MUSIC_DISC via a Create Deployer (DiscStampingRecipe). Each stamper carries one
    // music disc in its STAMPED_DISC DataComponent. Non-stacking.
    public static final ItemEntry<petrolpark.mc.destroy.content.processing.discstamping.DiscStamperItem> DISC_STAMPER =
        REGISTRATE.item("disc_stamper",
                petrolpark.mc.destroy.content.processing.discstamping.DiscStamperItem::new)
            .properties(p -> p.stacksTo(1))
            .register();

    // MOLECULE_DISPLAY — JEI-only informational Item carrying a LegacySpecies
    // reference via the MOLECULE DataComponent. Populated by
    // VatScreen species-click handler + JEI categories; not obtainable in survival.
    public static final ItemEntry<petrolpark.mc.destroy.core.chemistry.MoleculeDisplayItem> MOLECULE_DISPLAY =
        REGISTRATE.item("molecule_display", petrolpark.mc.destroy.core.chemistry.MoleculeDisplayItem::new)
            .register();

    // CHEWING GUM

    public static final ItemEntry<petrolpark.mc.destroy.content.product.ChewingGumItem> CHEWING_GUM =
        REGISTRATE.item("chewing_gum", petrolpark.mc.destroy.content.product.ChewingGumItem::new)
            .properties(p -> p.food(DestroyFoods.CHEWING_GUM))
            .register();

    // BOWL FOODS

    public static final ItemEntry<petrolpark.mc.destroy.core.item.StackableBowlFoodItem> BANGERS_AND_MASH =
        REGISTRATE.item("bangers_and_mash", petrolpark.mc.destroy.core.item.StackableBowlFoodItem::new)
            .properties(p -> p.food(DestroyFoods.BANGERS_AND_MASH))
            .register();

    // DRINKS (carton returned on finish)

    public static final ItemEntry<petrolpark.mc.destroy.core.item.DrinkItem> APPLE_JUICE_CARTON =
        REGISTRATE.item("apple_juice_carton", petrolpark.mc.destroy.core.item.DrinkItem::new)
            .properties(p -> p
                .food(DestroyFoods.APPLE_JUICE)
                .craftRemainder(EMPTY_CARTON.get())
            ).tag(AllItemTags.UPRIGHT_ON_BELT.tag)
            .register();

    // ALCOHOLIC DRINKS

    public static final ItemEntry<petrolpark.mc.destroy.content.product.alcohol.AlcoholicDrinkItem>

    UNDISTILLED_MOONSHINE_BOTTLE = REGISTRATE.item("undistilled_moonshine_bottle", p -> new petrolpark.mc.destroy.content.product.alcohol.AlcoholicDrinkItem(p, 1))
        .properties(p -> p
            .food(DestroyFoods.MOONSHINE)
            .craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)
            .stacksTo(16)
        ).tag(DestroyTags.Items.ALCOHOLIC_DRINKS.tag, AllItemTags.UPRIGHT_ON_BELT.tag)
        .register(),

    MOONSHINE_BOTTLE = REGISTRATE.item("moonshine_bottle", p -> new petrolpark.mc.destroy.content.product.alcohol.AlcoholicDrinkItem(p, 3))
        .properties(p -> p
            .food(DestroyFoods.MOONSHINE)
            .craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)
            .stacksTo(16)
        ).tag(DestroyTags.Items.ALCOHOLIC_DRINKS.tag, AllItemTags.UPRIGHT_ON_BELT.tag)
        .register();

    // CHORUS_WINE_BOTTLE Capability→AttachmentType 批：瞬移玩家到 N 秒前的位置。
    // 依赖 PlayerPreviousPositions attachment + PlayerPreviousPositionsHandler PlayerTickEvent.Post。
    // IDynamicItemDescription tooltip 留给 tooltip 批。

    public static final ItemEntry<petrolpark.mc.destroy.content.product.alcohol.ChorusWineItem> CHORUS_WINE_BOTTLE =
        REGISTRATE.item("chorus_wine_bottle", p -> new petrolpark.mc.destroy.content.product.alcohol.ChorusWineItem(p, 1))
            .properties(p -> p
                .food(DestroyFoods.MOONSHINE)
                .craftRemainder(net.minecraft.world.item.Items.GLASS_BOTTLE)
                .stacksTo(16)
            ).tag(DestroyTags.Items.ALCOHOLIC_DRINKS.tag, AllItemTags.UPRIGHT_ON_BELT.tag)
            .register();

    public static final ItemEntry<petrolpark.mc.destroy.content.product.MilkCartonItem> MILK_CARTON =
        REGISTRATE.item("milk_carton", petrolpark.mc.destroy.content.product.MilkCartonItem::new)
            .properties(p -> p
                .food(DestroyFoods.MILK_CARTON)
                .craftRemainder(EMPTY_CARTON.get())
            ).tag(AllItemTags.UPRIGHT_ON_BELT.tag)
            .register();

    // MAGIC (Liable to change showcase)
    // Replaced with
    // plain Item + ENCHANTMENT_GLINT_OVERRIDE data component = true to reproduce the foiled look
    // without a custom subclass.

    public static final ItemEntry<Item>

    MAGIC_OXIDANT = REGISTRATE.item("magic_oxidant", Item::new)
        .properties(p -> p.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))
        .tag(DestroyTags.Items.LIABLE_TO_CHANGE.tag)
        .register(),
    MAGIC_REDUCTANT = REGISTRATE.item("magic_reductant", Item::new)
        .properties(p -> p.component(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true))
        .tag(DestroyTags.Items.LIABLE_TO_CHANGE.tag)
        .register();

    // SEQUENCED ASSEMBLY INTERMEDIATES
    // 1.21.1 Registrate's tab(...) no longer
    // accepts null. No .tab(...) call is made — these items are excluded from the
    // Destroy creative tab via its builder filter in DestroyCreativeModeTabs. They
    // still appear in SEARCH unless removed there.

    public static final ItemEntry<com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem>

    UNFINISHED_BLACKLIGHT = REGISTRATE.item("unfinished_blacklight", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .register(),
    UNFINISHED_CATALYTIC_CONVERTER = REGISTRATE.item("unfinished_catalytic_converter", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .register(),
    UNFINISHED_CIRCUIT_BOARD = REGISTRATE.item("unfinished_circuit_board", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .register(),
    UNFINISHED_CARD_STOCK = REGISTRATE.item("unfinished_card_stock", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .register(),
    UNFINISHED_VOLTAIC_PILE = REGISTRATE.item("unfinished_voltaic_pile", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .register(),
    UNFINISHED_UNVARNISHED_PLYWOOD = REGISTRATE.item("unfinished_unvarnished_plywood", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .register(),
    UNPROCESSED_MASHED_POTATO = REGISTRATE.item("unprocessed_mashed_potato", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .register(),
    UNPROCESSED_NAPALM_SUNDAE = REGISTRATE.item("unprocessed_napalm_sundae", com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem::new)
        .tag(AllItemTags.UPRIGHT_ON_BELT.tag)
        .register();

    // PERSONAL PROTECTIVE EQUIPMENT (headwear — ChemistryProtectionHeadwearItem)
    // 1.21.1 notes:
    // • CuriosSetup → PetrolparkCuriosSetup (library rename; transforms goggles()/renderOnHead()
    // unchanged in shape).
    // • CreateRegistrate.itemModel(() -> XxxModel::new) is wired for each PPE to swap
    // the default 2D generated item sprite for a 3D BakedModel when rendered on the head slot.
    // XxxModel classes are BakedModelWrapper that override applyTransform(HEAD, ...) to return
    // DestroyPartials.XXX's preloaded block model. CreateRegistrate's
    // static itemModel helper is preserved in Create 6.0 unchanged.
    // The 1.21.1 schema is
    // {order,icon,validators} — but since the default `head` slot is used, no slot JSON is
    // needed. Items opt in via the `curios:tag` validator — see
    // data/curios/tags/item/head.json.

    public static final ItemEntry<petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem>

    LABORATORY_GOGGLES = REGISTRATE.item("laboratory_goggles", petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem::new)
        .properties(p -> p.stacksTo(1))
        .tag(DestroyTags.Items.CHEMICAL_PROTECTION_EYES.tag)
        .transform(com.petrolpark.compat.curios.PetrolparkCuriosSetup.renderOnHead())
        .transform(com.petrolpark.compat.curios.PetrolparkCuriosSetup.goggles())
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.goggles())
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.durability(() -> petrolpark.mc.destroy.config.DestroyConfigs.server().equipment.laboratoryGogglesDurability))
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.repairIngredient(() -> net.minecraft.world.item.crafting.Ingredient.of(DestroyTags.Items.TRANSPARENT_PLASTICS.tag)))
        .onRegister(com.simibubi.create.foundation.data.CreateRegistrate.itemModel(() -> petrolpark.mc.destroy.core.chemistry.hazard.protection.LaboratoryGogglesModel::new))
        .register(),

    GOLD_LABORATORY_GOGGLES = REGISTRATE.item("gold_laboratory_goggles", petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem::new)
        .properties(p -> p.stacksTo(1))
        .tag(DestroyTags.Items.CHEMICAL_PROTECTION_EYES.tag)
        .transform(com.petrolpark.compat.curios.PetrolparkCuriosSetup.renderOnHead())
        .transform(com.petrolpark.compat.curios.PetrolparkCuriosSetup.goggles())
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.goggles())
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.durability(() -> petrolpark.mc.destroy.config.DestroyConfigs.server().equipment.goldLaboratoryGogglesDurability))
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.repairIngredient(() -> net.minecraft.world.item.crafting.Ingredient.of(PetrolparkTags.commonItemTag("plates/gold"))))
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.enchantable())
        .onRegister(com.simibubi.create.foundation.data.CreateRegistrate.itemModel(() -> petrolpark.mc.destroy.core.chemistry.hazard.protection.GoldLaboratoryGogglesModel::new))
        .register(),

    PAPER_MASK = REGISTRATE.item("paper_mask", petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem::new)
        .properties(p -> p.stacksTo(1))
        .tag(DestroyTags.Items.CHEMICAL_PROTECTION_NOSE.tag, DestroyTags.Items.CHEMICAL_PROTECTION_MOUTH.tag)
        .transform(com.petrolpark.compat.curios.PetrolparkCuriosSetup.renderOnHead())
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.durability(() -> petrolpark.mc.destroy.config.DestroyConfigs.server().equipment.paperMaskDurability))
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.repairIngredient(() -> net.minecraft.world.item.crafting.Ingredient.of(net.minecraft.world.item.Items.PAPER)))
        .onRegister(com.simibubi.create.foundation.data.CreateRegistrate.itemModel(() -> petrolpark.mc.destroy.core.chemistry.hazard.protection.PaperMaskModel::new))
        .register(),

    // — Big Cannons compat is deferred; omit that tag for now (TODO Big Cannons batch).
    GAS_MASK = REGISTRATE.item("gas_mask", petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem::new)
        .properties(p -> p.stacksTo(1))
        .tag(DestroyTags.Items.CHEMICAL_PROTECTION_HEAD.tag, DestroyTags.Items.CHEMICAL_PROTECTION_EYES.tag, DestroyTags.Items.CHEMICAL_PROTECTION_NOSE.tag, DestroyTags.Items.CHEMICAL_PROTECTION_MOUTH.tag, DestroyTags.Items.CONTAMINABLE.tag)
        .transform(com.petrolpark.compat.curios.PetrolparkCuriosSetup.renderOnHead())
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.goggles())
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.durability(() -> petrolpark.mc.destroy.config.DestroyConfigs.server().equipment.gasMaskDurability))
        .onRegister(petrolpark.mc.destroy.core.chemistry.hazard.protection.ChemistryProtectionHeadwearItem.repairIngredient(() -> net.minecraft.world.item.crafting.Ingredient.of(DestroyTags.Items.TEXTILE_PLASTICS.tag)))
        .onRegister(com.simibubi.create.foundation.data.CreateRegistrate.itemModel(() -> petrolpark.mc.destroy.core.chemistry.hazard.protection.GasMaskModel::new))
        .register();

    // HAZMAT SUIT (chest/legs/boots — HazmatSuitArmorItem extends Create's BaseArmorItem)

    public static final ItemEntry<petrolpark.mc.destroy.core.chemistry.hazard.protection.HazmatSuitArmorItem>

    HAZMAT_SUIT = REGISTRATE.item("hazmat_suit", p -> new petrolpark.mc.destroy.core.chemistry.hazard.protection.HazmatSuitArmorItem(net.minecraft.world.item.ArmorItem.Type.CHESTPLATE, p))
        .properties(p -> p.stacksTo(1))
        .tag(DestroyTags.Items.CHEMICAL_PROTECTION_CHEST.tag, DestroyTags.Items.CONTAMINABLE.tag)
        .register(),
    HAZMAT_LEGGINGS = REGISTRATE.item("hazmat_leggings", p -> new petrolpark.mc.destroy.core.chemistry.hazard.protection.HazmatSuitArmorItem(net.minecraft.world.item.ArmorItem.Type.LEGGINGS, p))
        .properties(p -> p.stacksTo(1))
        .tag(DestroyTags.Items.CHEMICAL_PROTECTION_LEGS.tag, DestroyTags.Items.CONTAMINABLE.tag)
        .register(),
    WELLINGTON_BOOTS = REGISTRATE.item("wellington_boots", p -> new petrolpark.mc.destroy.core.chemistry.hazard.protection.HazmatSuitArmorItem(net.minecraft.world.item.ArmorItem.Type.BOOTS, p))
        .properties(p -> p.stacksTo(1))
        .tag(DestroyTags.Items.CHEMICAL_PROTECTION_FEET.tag, DestroyTags.Items.CONTAMINABLE.tag)
        .register();

    // BLAZE BURNER TREATS
    // 1.21.1 notes:
    // • Create 6.0.8 dropped com.simibubi.create.foundation.item.CombustibleItem; replaced with
    // local petrolpark.mc.destroy.core.item.CombustibleItem (plain-Item peer of the existing
    // CombustibleBlockItem). See
    // `BLAZE_BURNER_FUEL_SPECIAL` /
    // `UPRIGHT_ON_BELT` both exist in Create 6.0.8's AllTags$AllItemTags (verified via javap).

    public static final ItemEntry<petrolpark.mc.destroy.core.item.CombustibleItem>

    EMPTY_BOMB_BON = REGISTRATE.item("empty_bomb_bon", petrolpark.mc.destroy.core.item.CombustibleItem::new)
        .tag(AllItemTags.UPRIGHT_ON_BELT.tag)
        .onRegister(i -> i.setBurnTime(1000))
        .register(),
    BOMB_BON = REGISTRATE.item("bomb_bon", petrolpark.mc.destroy.core.item.CombustibleItem::new)
        .tag(AllItemTags.BLAZE_BURNER_FUEL_SPECIAL.tag, AllItemTags.UPRIGHT_ON_BELT.tag)
        .onRegister(i -> i.setBurnTime(20000))
        .register(),
    NAPALM_SUNDAE = REGISTRATE.item("napalm_sundae", petrolpark.mc.destroy.core.item.CombustibleItem::new)
        .tag(AllItemTags.BLAZE_BURNER_FUEL_SPECIAL.tag, AllItemTags.UPRIGHT_ON_BELT.tag)
        .onRegister(i -> i.setBurnTime(20000))
        .register(),
    THERMITE_BROWNIE = REGISTRATE.item("thermite_brownie", petrolpark.mc.destroy.core.item.CombustibleItem::new)
        .tag(AllItemTags.BLAZE_BURNER_FUEL_SPECIAL.tag)
        .onRegister(i -> i.setBurnTime(20000))
        .register();

    // PHYTOMINING SEEDS (block items)

    public static final ItemEntry<net.minecraft.world.item.ItemNameBlockItem>

    MAGIC_BEETROOT_SEEDS = REGISTRATE.item("magic_beetroot_seeds", p -> new net.minecraft.world.item.ItemNameBlockItem(DestroyBlocks.MAGIC_BEETROOT_SHOOTS.get(), p))
        .tag(Tags.Items.SEEDS)
        .tag(Tags.Items.SEEDS_BEETROOT)
        .register();

    public static void register() {
        // class-load trigger; REGISTRATE handles actual bus registration
    }
}
