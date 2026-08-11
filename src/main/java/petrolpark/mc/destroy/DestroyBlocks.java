package petrolpark.mc.destroy;

import static petrolpark.mc.destroy.Destroy.REGISTRATE;

import static com.simibubi.create.foundation.data.CreateRegistrate.casingConnectivity;
import static com.simibubi.create.foundation.data.CreateRegistrate.connectedTextures;

import petrolpark.mc.library.PetrolparkTags;
import com.simibubi.create.content.decoration.encasing.CasingBlock;
import com.simibubi.create.content.decoration.encasing.EncasedCTBehaviour;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;
import com.simibubi.create.foundation.data.TagGen;
import com.tterrag.registrate.builders.BlockBuilder;
import com.tterrag.registrate.util.entry.BlockEntry;
import com.tterrag.registrate.util.nullness.NonNullFunction;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.ApplyBonusCount;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.common.Tags;
import petrolpark.mc.destroy.content.logistics.creativepump.CreativePumpBlock;
import petrolpark.mc.destroy.content.logistics.siphon.SiphonBlock;
import petrolpark.mc.destroy.content.processing.ageing.AgingBarrelBlock;
import petrolpark.mc.destroy.content.processing.phytomining.HeftyBeetrootBlock;
import petrolpark.mc.destroy.content.processing.phytomining.MagicBeetrootShootsBlock;
import petrolpark.mc.destroy.content.processing.cooler.CoolerBlock;
import petrolpark.mc.destroy.content.processing.extrusion.ExtrusionDieBlock;
import petrolpark.mc.destroy.content.processing.sieve.MechanicalSieveBlock;
import petrolpark.mc.destroy.content.oil.pumpjack.PumpjackBlock;
import petrolpark.mc.destroy.content.oil.pumpjack.PumpjackBlockItem;
import petrolpark.mc.destroy.content.oil.pumpjack.PumpjackCamBlock;
import petrolpark.mc.destroy.content.oil.pumpjack.PumpjackStructuralBlock;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugeBlock;
import petrolpark.mc.destroy.content.processing.distillation.BubbleCapBlock;
import petrolpark.mc.destroy.content.processing.dynamo.DynamoBlock;
import petrolpark.mc.destroy.content.processing.dynamo.arcfurnace.ArcFurnaceLidBlock;
import petrolpark.mc.destroy.content.processing.glassblowing.BlowpipeBlock;
import petrolpark.mc.destroy.content.processing.glassblowing.BlowpipeItem;
import petrolpark.mc.destroy.content.processing.treetap.TreeTapBlock;
import petrolpark.mc.destroy.content.processing.trypolithography.keypunch.KeypunchBlock;
import petrolpark.mc.destroy.content.product.alcohol.UrineCauldronBlock;
import petrolpark.mc.destroy.content.sandcastle.SandCastleBlock;
import petrolpark.mc.destroy.core.pollution.catalyticconverter.CatalyticConverterBlock;
import petrolpark.mc.destroy.core.block.FlippableRotatedPillarBlock;
import petrolpark.mc.destroy.core.block.FullyGrownCropBlock;
import petrolpark.mc.destroy.core.item.CombustibleBlockItem;

/**
 *
 * 1.21.1 notes:
 *
 * TODO: port remaining block entries batch-by-batch — priority order is
 * machines (siphon, bubble cap, centrifuge, etc.), then products (alcohol/babyblue racks),
 * then decoration (copper/aging-barrel variants), then explosives.
*/
public class DestroyBlocks {

    /**
 * Wrapper for {@link TagGen#tagBlockAndItem(TagKey, TagKey)} that builds the {@code c:<path>} /
 * {@code c:<path>} common tag pair.
*/
    private static <T extends Block, P> NonNullFunction<BlockBuilder<T, P>, com.tterrag.registrate.builders.ItemBuilder<BlockItem, BlockBuilder<T, P>>> tagBlockAndItemCommon(String path) {
        TagKey<Block> block = PetrolparkTags.commonBlockTag(path);
        TagKey<Item> item = PetrolparkTags.commonItemTag(path);
        return TagGen.tagBlockAndItem(block, item);
    }

    /**
 * Silk-touch-dispatch loot with Fortune bonus (ore default: drop 1 of {@code drop}). 1.21.1
 * notes:
 * <ul>
 * <li>{@code RegistrateBlockLootTables.createSilkTouchDispatchTable} / {@code applyExplosionDecay}
 * are now instance methods on the {@code lt} parameter.</li>
 * <li>{@code Enchantments.BLOCK_FORTUNE} → {@code Enchantments.FORTUNE}, now a
 * {@code ResourceKey<Enchantment>}; {@code ApplyBonusCount.addOreBonusCount} takes a
 * {@code Holder<Enchantment>} — resolved via {@code lt.getRegistries().lookupOrThrow(...).getOrThrow(...)}.</li>
 * </ul>
*/
    private static <B extends Block> com.tterrag.registrate.util.nullness.NonNullBiConsumer<com.tterrag.registrate.providers.loot.RegistrateBlockLootTables, B> oreLootFortune(java.util.function.Supplier<? extends ItemLike> drop) {
        return (lt, b) -> lt.add(b, lt.createSilkTouchDispatchTable(b,
            lt.applyExplosionDecay(b, LootItem.lootTableItem(drop.get())
                .apply(ApplyBonusCount.addOreBonusCount(lt.getRegistries().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE))))));
    }

    /**
 * Silk-touch-dispatch loot with Fortune bonus + uniform drop count (2–5 for crocoite). Same
 * 1.21.1 caveats as {@link #oreLootFortune}.
*/
    private static <B extends Block> com.tterrag.registrate.util.nullness.NonNullBiConsumer<com.tterrag.registrate.providers.loot.RegistrateBlockLootTables, B> oreLootFortuneUniform(java.util.function.Supplier<? extends ItemLike> drop, float min, float max) {
        return (lt, b) -> lt.add(b, lt.createSilkTouchDispatchTable(b,
            lt.applyExplosionDecay(b, LootItem.lootTableItem(drop.get())
                .apply(SetItemCountFunction.setCount(UniformGenerator.between(min, max)))
                .apply(ApplyBonusCount.addOreBonusCount(lt.getRegistries().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE))))));
    }

    /**
 * Silk-touch-dispatch loot that drops a fixed count of an item (EXTRUDED_CORDITE_BLOCK: 5
 * CORDITE; MAGIC_BEETROOT_SHOOTS seed drop would reuse this with count 1).
*/
    private static <B extends Block> com.tterrag.registrate.util.nullness.NonNullBiConsumer<com.tterrag.registrate.providers.loot.RegistrateBlockLootTables, B> silkTouchDispatchFixed(java.util.function.Supplier<? extends ItemLike> drop, float count) {
        return (lt, b) -> lt.add(b, lt.createSilkTouchDispatchTable(b,
            LootItem.lootTableItem(drop.get()).apply(SetItemCountFunction.setCount(ConstantValue.exactly(count)))));
    }

    // STORAGE BLOCKS

    public static final BlockEntry<Block>

    CARBON_FIBER_BLOCK = REGISTRATE.block("carbon_fiber_block", Block::new)
        .initialProperties(() -> Blocks.OBSIDIAN)
        .properties(p -> p.strength(40f, 800f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_IRON_TOOL)
        .tag(DestroyTags.Blocks.ARC_FURNACE_TRANSFORMABLE.tag)
        .transform(tagBlockAndItemCommon("storage_blocks/carbon_fiber"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    FLUORITE_BLOCK = REGISTRATE.block("fluorite_block", Block::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(p -> p.mapColor(MapColor.COLOR_PURPLE).requiresCorrectToolForDrops().strength(6f, 6f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_IRON_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
        .transform(tagBlockAndItemCommon("storage_blocks/fluorite"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    RAW_NICKEL_BLOCK = REGISTRATE.block("raw_nickel_block", Block::new)
        .initialProperties(() -> Blocks.RAW_IRON_BLOCK)
        .properties(p -> p.mapColor(MapColor.SAND).requiresCorrectToolForDrops().strength(5f, 6f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
        .transform(tagBlockAndItemCommon("storage_blocks/raw_nickel"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    CHROMIUM_BLOCK = REGISTRATE.block("chromium_block", Block::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(p -> p.requiresCorrectToolForDrops().strength(5f, 6f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
        .transform(tagBlockAndItemCommon("storage_blocks/chromium"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    IODINE_BLOCK = REGISTRATE.block("iodine_block", Block::new)
        .initialProperties(() -> Blocks.RAW_IRON_BLOCK)
        .properties(p -> p.mapColor(MapColor.COLOR_GRAY).strength(2f, 2f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.STORAGE_BLOCKS)
        .transform(tagBlockAndItemCommon("storage_blocks/iodine"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    NETHER_CROCOITE_BLOCK = REGISTRATE.block("nether_crocoite_block", Block::new)
        .initialProperties(() -> Blocks.RAW_IRON_BLOCK)
        .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2f, 2f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_IRON_TOOL, Tags.Blocks.STORAGE_BLOCKS)
        .item()
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    NICKEL_BLOCK = REGISTRATE.block("nickel_block", Block::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(p -> p.mapColor(MapColor.SAND).requiresCorrectToolForDrops().strength(5f, 6f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
        .transform(tagBlockAndItemCommon("storage_blocks/nickel"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    PALLADIUM_BLOCK = REGISTRATE.block("palladium_block", Block::new)
        .initialProperties(() -> Blocks.NETHERITE_BLOCK)
        .properties(p -> p.mapColor(MapColor.DIRT).instrument(NoteBlockInstrument.IRON_XYLOPHONE).requiresCorrectToolForDrops().strength(6f, 6f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_DIAMOND_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
        .transform(tagBlockAndItemCommon("storage_blocks/palladium"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    PLATINUM_BLOCK = REGISTRATE.block("platinum_block", Block::new)
        .initialProperties(() -> Blocks.DIAMOND_BLOCK)
        .properties(p -> p.requiresCorrectToolForDrops().instrument(NoteBlockInstrument.BELL).strength(6f, 6f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_IRON_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
        .transform(tagBlockAndItemCommon("storage_blocks/platinum"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    RHODIUM_BLOCK = REGISTRATE.block("rhodium_block", Block::new)
        .initialProperties(() -> Blocks.NETHERITE_BLOCK)
        .properties(p -> p.mapColor(MapColor.TERRACOTTA_LIGHT_BLUE).instrument(NoteBlockInstrument.BELL).requiresCorrectToolForDrops().strength(6f, 6f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_DIAMOND_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
        .transform(tagBlockAndItemCommon("storage_blocks/rhodium"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register(),

    LEAD_BLOCK = REGISTRATE.block("lead_block", Block::new)
        .initialProperties(() -> Blocks.IRON_BLOCK)
        .properties(p -> p.instrument(NoteBlockInstrument.IRON_XYLOPHONE).requiresCorrectToolForDrops().strength(7f, 6f))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
        .transform(tagBlockAndItemCommon("storage_blocks/lead"))
        .tag(Tags.Items.STORAGE_BLOCKS)
        .build()
        .register();

    public static final BlockEntry<CasingBlock> STAINLESS_STEEL_BLOCK =
        REGISTRATE.block("stainless_steel_block", CasingBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p
                .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .sound(SoundType.METAL)
                .strength(7f, 8f))
            .transform(TagGen.pickaxeOnly())
            .properties(p -> p.sound(SoundType.COPPER))
            .blockstate((c, p) -> p.simpleBlock(c.get()))
            .onRegister(connectedTextures(() -> new EncasedCTBehaviour(DestroySpriteShifts.STAINLESS_STEEL_BLOCK)))
            .onRegister(casingConnectivity((block, cc) -> cc.makeCasing(block, DestroySpriteShifts.STAINLESS_STEEL_BLOCK)))
            .tag(BlockTags.NEEDS_STONE_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
            .transform(tagBlockAndItemCommon("storage_blocks/stainless_steel"))
            .tag(Tags.Items.STORAGE_BLOCKS)
            .build()
            .register();

    public static final BlockEntry<RotatedPillarBlock> CHISELED_RHODIUM_BLOCK =
        REGISTRATE.block("chiseled_rhodium_block", RotatedPillarBlock::new)
            .initialProperties(RHODIUM_BLOCK)
            .transform(TagGen.pickaxeOnly())
            .tag(BlockTags.NEEDS_DIAMOND_TOOL, Tags.Blocks.STORAGE_BLOCKS, BlockTags.BEACON_BASE_BLOCKS)
            .transform(tagBlockAndItemCommon("storage_blocks/rhodium"))
            .tag(Tags.Items.STORAGE_BLOCKS)
            .build()
            .register();

    // DECORATION BLOCKS
    // 1.21.1 notes:
    // • Connected-texture wiring (SimpleCTBehaviour / EncasedCTBehaviour via
    // CreateRegistrate.connectedTextures / casingConnectivity) works on neo as-is — the static
    // helpers return NonNullConsumer<? super Block> independent of registrate type.
    // • EXTRUDED_CORDITE_BLOCK uses silk-touch-dispatch loot (drops 5 CORDITE, silk touch drops
    // the 1.21 Loot API migration pattern (instance methods on `lt`, Holder<Enchantment>).

    public static final BlockEntry<TransparentBlock> BOROSILICATE_GLASS = REGISTRATE.block("borosilicate_glass", TransparentBlock::new)
        .initialProperties(() -> Blocks.GLASS)
        .properties(p -> p.strength(2f))
        .onRegister(connectedTextures(() -> new SimpleCTBehaviour(DestroySpriteShifts.BOROSILICATE_GLASS)))
        .tag(Tags.Blocks.GLASS_BLOCKS, Tags.Blocks.GLASS_BLOCKS_COLORLESS, BlockTags.MINEABLE_WITH_PICKAXE)
        .item()
            .tag(Tags.Items.GLASS_BLOCKS, Tags.Items.GLASS_BLOCKS_COLORLESS)
            .build()
        .register();

    public static final BlockEntry<Block> CORDITE_BLOCK = REGISTRATE.block("cordite_block", Block::new)
        .initialProperties(() -> Blocks.CLAY)
        .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).sound(SoundType.SLIME_BLOCK).strength(0.2f))
        .tag(BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE)
        .simpleItem()
        .register();

    public static final BlockEntry<RotatedPillarBlock>

    INSULATED_STAINLESS_STEEL_BLOCK = REGISTRATE.block("insulated_stainless_steel_block", RotatedPillarBlock::new)
        .initialProperties(STAINLESS_STEEL_BLOCK)
        .properties(p -> p.mapColor(MapColor.METAL).requiresCorrectToolForDrops().strength(5f, 6f))
        .onRegister(connectedTextures(() -> new EncasedCTBehaviour(DestroySpriteShifts.STAINLESS_STEEL_BLOCK)))
        .onRegister(casingConnectivity((block, cc) -> cc.make(block, DestroySpriteShifts.STAINLESS_STEEL_BLOCK,
            (s, f) -> f.getAxis() == s.getValue(RotatedPillarBlock.AXIS))))
        .transform(TagGen.pickaxeOnly())
        .tag(BlockTags.NEEDS_STONE_TOOL)
        .simpleItem()
        .register(),

    EXTRUDED_CORDITE_BLOCK = REGISTRATE.block("extruded_cordite_block", RotatedPillarBlock::new)
        .initialProperties(() -> Blocks.CLAY)
        .properties(p -> p.mapColor(MapColor.COLOR_ORANGE).sound(SoundType.SLIME_BLOCK).strength(0.2f))
        .tag(BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE)
        .loot(silkTouchDispatchFixed(() -> DestroyItems.CORDITE.get(), 5f))
        .simpleItem()
        .register(),

    CLAY_MONOLITH = REGISTRATE.block("clay_monolith", RotatedPillarBlock::new)
        .initialProperties(() -> Blocks.CLAY)
        .tag(BlockTags.MINEABLE_WITH_SHOVEL)
        .simpleItem()
        .register(),

    CERAMIC_MONOLITH = REGISTRATE.block("ceramic_monolith", RotatedPillarBlock::new)
        .initialProperties(() -> Blocks.TERRACOTTA)
        .tag(BlockTags.MINEABLE_WITH_PICKAXE)
        .simpleItem()
        .register();

    // MOLTEN BLOCKS

    public static final BlockEntry<petrolpark.mc.destroy.content.processing.moltenblock.MoltenStainlessSteelBlock>
        MOLTEN_STAINLESS_STEEL = REGISTRATE.block("molten_stainless_steel",
            petrolpark.mc.destroy.content.processing.moltenblock.MoltenStainlessSteelBlock::new)
            .properties(p -> p
                .mapColor(MapColor.COLOR_ORANGE)
                .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .lightLevel(state -> 15)
                .noLootTable()
                .dynamicShape())
            .tag(com.simibubi.create.AllTags.AllBlockTags.MOVABLE_EMPTY_COLLIDER.tag)
            .register();

    public static final BlockEntry<petrolpark.mc.destroy.content.processing.moltenblock.MoltenBorosilicateGlassBlock>
        MOLTEN_BOROSILICATE_GLASS = REGISTRATE.block("molten_borosilicate_glass",
            petrolpark.mc.destroy.content.processing.moltenblock.MoltenBorosilicateGlassBlock::new)
            .properties(p -> p
                .mapColor(MapColor.COLOR_ORANGE)
                .instrument(NoteBlockInstrument.IRON_XYLOPHONE)
                .lightLevel(state -> 15)
                .noLootTable()
                .dynamicShape())
            .tag(com.simibubi.create.AllTags.AllBlockTags.MOVABLE_EMPTY_COLLIDER.tag)
            .register();

    public static final BlockEntry<petrolpark.mc.destroy.content.processing.moltenblock.FastCoolingMoltenPillarBlock>
        STAINLESS_STEEL_RODS = REGISTRATE.block("stainless_steel_rods_block",
            petrolpark.mc.destroy.content.processing.moltenblock.FastCoolingMoltenPillarBlock::new)
            .initialProperties(STAINLESS_STEEL_BLOCK)
            .properties(p -> p
                .mapColor(state -> state.getValue(petrolpark.mc.destroy.content.processing.moltenblock.FastCoolingMoltenPillarBlock.MOLTEN) ? MapColor.COLOR_ORANGE : MapColor.METAL)
                .lightLevel(state -> state.getValue(petrolpark.mc.destroy.content.processing.moltenblock.FastCoolingMoltenPillarBlock.MOLTEN) ? 15 : 0))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE)
            .item()
            .build()
            .register();

    public static final BlockEntry<petrolpark.mc.destroy.content.processing.moltenblock.BorosilicateGlassFiberBlock>
        BOROSILICATE_GLASS_FIBER = REGISTRATE.block("borosilicate_glass_fiber",
            petrolpark.mc.destroy.content.processing.moltenblock.BorosilicateGlassFiberBlock::new)
            .initialProperties(MOLTEN_BOROSILICATE_GLASS)
            .properties(p -> p
                .mapColor(state -> state.getValue(petrolpark.mc.destroy.content.processing.moltenblock.FastCoolingMoltenPillarBlock.MOLTEN) ? MapColor.COLOR_RED : MapColor.NONE)
                .lightLevel(state -> state.getValue(petrolpark.mc.destroy.content.processing.moltenblock.FastCoolingMoltenPillarBlock.MOLTEN) ? 15 : 0)
                .sound(SoundType.WOOL))
            .tag(BlockTags.MINEABLE_WITH_PICKAXE, BlockTags.WOOL)
            .item()
                .tag(ItemTags.WOOL)
                .build()
            .register();

    // ORES
    // 1.21.1 notes:
    // • Ore loot now wired via oreLootFortune / oreLootFortuneUniform helpers (silk-touch
    // dispatch + Fortune ore bonus).

    public static final BlockEntry<Block> NETHER_CROCOITE_ORE = REGISTRATE.block("nether_crocoite_ore", Block::new)
        .initialProperties(() -> Blocks.NETHER_QUARTZ_ORE)
        .properties(p -> p
            .mapColor(MapColor.COLOR_ORANGE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .sound(SoundType.NETHERRACK)
            .requiresCorrectToolForDrops())
        .onRegister(connectedTextures(() -> new SimpleCTBehaviour(DestroySpriteShifts.NETHER_CROCOITE_BLOCK)))
        .transform(TagGen.pickaxeOnly())
        .loot(oreLootFortuneUniform(() -> DestroyItems.NETHER_CROCOITE.get(), 2f, 5f))
        .tag(BlockTags.NEEDS_IRON_TOOL, Tags.Blocks.ORES, Tags.Blocks.ORES_IN_GROUND_NETHERRACK, Tags.Blocks.ORE_BEARING_GROUND_NETHERRACK)
        .item()
            .tag(Tags.Items.ORES)
            .build()
        .register();

    public static final BlockEntry<Block> FLUORITE_ORE = REGISTRATE.block("fluorite_ore", Block::new)
        .initialProperties(() -> Blocks.GOLD_ORE)
        .properties(p -> p
            .mapColor(MapColor.COLOR_PURPLE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .strength(3f, 3f))
        .transform(TagGen.pickaxeOnly())
        .loot(oreLootFortune(() -> DestroyItems.FLUORITE.get()))
        .tag(BlockTags.NEEDS_IRON_TOOL, Tags.Blocks.ORES,
            PetrolparkTags.commonBlockTag("ores/fluorite"), PetrolparkTags.commonBlockTag("ores_in_ground/stone"))
        .item()
            .tag(Tags.Items.ORES,
                PetrolparkTags.commonItemTag("ores/fluorite"), PetrolparkTags.commonItemTag("ores_in_ground/stone"))
            .build()
        .register();

    public static final BlockEntry<Block> DEEPSLATE_FLUORITE_ORE = REGISTRATE.block("deepslate_fluorite_ore", Block::new)
        .initialProperties(() -> Blocks.DEEPSLATE_GOLD_ORE)
        .properties(p -> p
            .mapColor(MapColor.COLOR_PURPLE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .sound(SoundType.DEEPSLATE)
            .strength(4.5f, 3f))
        .transform(TagGen.pickaxeOnly())
        .loot(oreLootFortune(() -> DestroyItems.FLUORITE.get()))
        .tag(BlockTags.NEEDS_IRON_TOOL, Tags.Blocks.ORES,
            PetrolparkTags.commonBlockTag("ores/fluorite"), PetrolparkTags.commonBlockTag("ores_in_ground/deepslate"))
        .item()
            .tag(Tags.Items.ORES,
                PetrolparkTags.commonItemTag("ores/fluorite"), PetrolparkTags.commonItemTag("ores_in_ground/deepslate"))
            .build()
        .register();

    public static final BlockEntry<Block> END_FLUORITE_ORE = REGISTRATE.block("end_fluorite_ore", Block::new)
        .initialProperties(() -> Blocks.END_STONE)
        .properties(p -> p
            .mapColor(MapColor.COLOR_PURPLE)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .strength(4f, 9f))
        .transform(TagGen.pickaxeOnly())
        .loot(oreLootFortune(() -> DestroyItems.FLUORITE.get()))
        .tag(BlockTags.NEEDS_IRON_TOOL, Tags.Blocks.ORES,
            PetrolparkTags.commonBlockTag("ores/fluorite"), PetrolparkTags.commonBlockTag("ores_in_ground/end_stone"))
        .item()
            .tag(Tags.Items.ORES,
                PetrolparkTags.commonItemTag("ores/fluorite"), PetrolparkTags.commonItemTag("ores_in_ground/end_stone"))
            .build()
        .register();

    public static final BlockEntry<Block> NICKEL_ORE = REGISTRATE.block("nickel_ore", Block::new)
        .initialProperties(() -> Blocks.GOLD_ORE)
        .properties(p -> p
            .mapColor(MapColor.SAND)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .strength(3f, 3f))
        .transform(TagGen.pickaxeOnly())
        .loot(oreLootFortune(() -> DestroyItems.RAW_NICKEL.get()))
        .tag(BlockTags.NEEDS_IRON_TOOL, Tags.Blocks.ORES,
            PetrolparkTags.commonBlockTag("ores/nickel"), PetrolparkTags.commonBlockTag("ores_in_ground/stone"))
        .item()
            .tag(Tags.Items.ORES,
                PetrolparkTags.commonItemTag("ores/nickel"), PetrolparkTags.commonItemTag("ores_in_ground/stone"))
            .build()
        .register();

    public static final BlockEntry<Block> DEEPSLATE_NICKEL_ORE = REGISTRATE.block("deepslate_nickel_ore", Block::new)
        .initialProperties(() -> Blocks.DEEPSLATE_GOLD_ORE)
        .properties(p -> p
            .mapColor(MapColor.SAND)
            .instrument(NoteBlockInstrument.BASEDRUM)
            .requiresCorrectToolForDrops()
            .sound(SoundType.DEEPSLATE)
            .strength(4.5f, 3f))
        .transform(TagGen.pickaxeOnly())
        .loot(oreLootFortune(() -> DestroyItems.RAW_NICKEL.get()))
        .tag(BlockTags.NEEDS_IRON_TOOL, Tags.Blocks.ORES,
            PetrolparkTags.commonBlockTag("ores/nickel"), PetrolparkTags.commonBlockTag("ores_in_ground/deepslate"))
        .item()
            .tag(Tags.Items.ORES,
                PetrolparkTags.commonItemTag("ores/nickel"), PetrolparkTags.commonItemTag("ores_in_ground/deepslate"))
            .build()
        .register();

    // PHYTOMINING CROPS
    // 1.21.1 notes:
    // • BushBlock is abstract; HeftyBeetrootBlock / FullyGrownCropBlock / MagicBeetrootShootsBlock each
    // provide a MapCodec stub for Block deserialization.
    // • PlantType/IPlantable were removed from NeoForge 21.1 — farmland placement is handled by the
    // block's explicit mayPlaceOn override.
    // • GOLDEN_CARROTS custom createCropDrops loot and MAGIC_BEETROOT_SHOOTS seed drop are deferred to
    // the loot batch; defaults will apply for now. The BlockItem is suppressed on
    // MAGIC_BEETROOT_SHOOTS (DestroyItems.MAGIC_BEETROOT_SEEDS serves as its placer — vanilla
    // beetroot pattern).

    public static final BlockEntry<MagicBeetrootShootsBlock>

    MAGIC_BEETROOT_SHOOTS = REGISTRATE.block("magic_beetroot_shoots", MagicBeetrootShootsBlock::new)
        .addLayer(() -> RenderType::cutout)
        .initialProperties(() -> Blocks.BEETROOTS)
        .register();

    public static final BlockEntry<FullyGrownCropBlock>

    GOLDEN_CARROTS = REGISTRATE.block("golden_carrots", p -> new FullyGrownCropBlock(p, () -> Items.GOLDEN_CARROT))
        .initialProperties(() -> Blocks.CARROTS)
        .tag(BlockTags.CROPS)
        .register();

    public static final BlockEntry<HeftyBeetrootBlock>

    HEFTY_BEETROOT = REGISTRATE.block("hefty_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.HEFTY_BEETROOT))
        .initialProperties(() -> Blocks.BEETROOTS)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    COAL_INFUSED_BEETROOT = REGISTRATE.block("coal_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.COAL_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    COPPER_INFUSED_BEETROOT = REGISTRATE.block("copper_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.COPPER_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    DIAMOND_INFUSED_BEETROOT = REGISTRATE.block("diamond_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.DIAMOND_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    EMERALD_INFUSED_BEETROOT = REGISTRATE.block("emerald_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.EMERALD_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    FLUORITE_INFUSED_BEETROOT = REGISTRATE.block("fluorite_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.FLUORITE_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    GOLD_INFUSED_BEETROOT = REGISTRATE.block("gold_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.GOLD_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    IRON_INFUSED_BEETROOT = REGISTRATE.block("iron_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.IRON_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    LAPIS_INFUSED_BEETROOT = REGISTRATE.block("lapis_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.LAPIS_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    NICKEL_INFUSED_BEETROOT = REGISTRATE.block("nickel_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.NICKEL_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    NETHER_CROCOITE_INFUSED_BEETROOT = REGISTRATE.block("nether_crocoite_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.NETHER_CROCOITE_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    QUARTZ_INFUSED_BEETROOT = REGISTRATE.block("quartz_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.QUARTZ_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    REDSTONE_INFUSED_BEETROOT = REGISTRATE.block("redstone_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.REDSTONE_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register(),

    ZINC_INFUSED_BEETROOT = REGISTRATE.block("zinc_infused_beetroot", p -> new HeftyBeetrootBlock(p, DestroyItems.ZINC_INFUSED_BEETROOT))
        .initialProperties(HEFTY_BEETROOT)
        .tag(DestroyTags.Blocks.BEETROOTS.tag)
        .register();

    // FOOD BLOCKS
    // 1.21.1 notes:
    // • RAW_FRIES_BLOCK uses silkTouchDispatchFixed (drops 5 RAW_FRIES, silk touch drops the block)
    // — same pattern as EXTRUDED_CORDITE_BLOCK.
    // • MASHED_POTATO_BLOCK: plain 1×1 drop-self, Registrate default loot is fine.

    public static final BlockEntry<Block> MASHED_POTATO_BLOCK = REGISTRATE.block("mashed_potato_block", Block::new)
        .initialProperties(() -> Blocks.CLAY)
        .properties(p -> p
            .mapColor(MapColor.COLOR_YELLOW)
            .sound(SoundType.SLIME_BLOCK)
            .strength(0.2f)
        ).tag(BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE)
        .simpleItem()
        .register();

    public static final BlockEntry<RotatedPillarBlock> RAW_FRIES_BLOCK = REGISTRATE.block("raw_fries_block", RotatedPillarBlock::new)
        .initialProperties(() -> Blocks.CLAY)
        .properties(p -> p
            .mapColor(MapColor.COLOR_YELLOW)
            .sound(SoundType.SLIME_BLOCK)
            .strength(0.2f)
        ).loot(silkTouchDispatchFixed(() -> DestroyItems.RAW_FRIES.get(), 5f))
        .tag(BlockTags.MINEABLE_WITH_SHOVEL, BlockTags.MINEABLE_WITH_HOE)
        .simpleItem()
        .register();

    // UNCATEGORISED
    // 1.21.1 notes:
    // • FIBERGLASS_BLOCK — plain glass-strength block tagged c:glass_blocks.

    public static final BlockEntry<Block> FIBERGLASS_BLOCK = REGISTRATE.block("fiberglass_block", Block::new)
        .initialProperties(() -> Blocks.GLASS)
        .properties(p -> p
            .strength(6f)
            .mapColor(MapColor.SNOW)
        ).tag(Tags.Blocks.GLASS_BLOCKS, BlockTags.MINEABLE_WITH_PICKAXE)
        .item()
            .tag(Tags.Items.GLASS_BLOCKS)
            .build()
        .register();

    // PLYWOOD — FlippableRotatedPillarBlock-based planks.
    // UNVARNISHED_PLYWOOD uses CombustibleBlockItem with .setBurnTime(2000) via .onRegister;
    // NeoForge 21.1 still honors IItemExtension#getBurnTime overrides at runtime. A future data-map batch may migrate to neoforge:furnace_fuels JSON but isn't required
    // for function.

    public static final BlockEntry<FlippableRotatedPillarBlock>

    PLYWOOD = REGISTRATE.block("plywood", FlippableRotatedPillarBlock::new)
        .properties(p -> p
            .mapColor(MapColor.WOOD)
            .sound(SoundType.WOOD)
            .instrument(NoteBlockInstrument.BASS)
            .strength(4.0f, 6.0f)
        ).tag(BlockTags.MINEABLE_WITH_AXE, BlockTags.PLANKS)
        .item()
            .tag(ItemTags.PLANKS)
            .build()
        .register(),

    UNVARNISHED_PLYWOOD = REGISTRATE.block("unvarnished_plywood", FlippableRotatedPillarBlock::new)
        .properties(p -> p
            .mapColor(MapColor.WOOD)
            .sound(SoundType.WOOD)
            .instrument(NoteBlockInstrument.BASS)
            .strength(3.0f, 5.0f)
            .ignitedByLava()
        ).tag(BlockTags.MINEABLE_WITH_AXE, BlockTags.PLANKS)
        .item(CombustibleBlockItem::new)
            .tag(ItemTags.PLANKS)
            .onRegister(i -> i.setBurnTime(2000))
            .build()
        .register();

    // AGING BARREL
    // 1.21.1 notes:
    // • BE/item cap registration done on AgeingBarrelBlockEntity.registerCapabilities (static),
    // hooked in Destroy.java via modEventBus.addListener.
    // • No simpleItem() call — BlockItem is suppressed because the barrel is placed via a recipe.
    // A plain BlockItem is attached so creative pickup still works.

    // PRIMED BOMB BLOCKS — placeable TNT variants that spawn their specific
    // PrimedBombEntity on ignition/RS pulse. Each block uses a different SmartExplosion subtype.
    // DYNAMITE_BLOCK + ExcavationExplosion deferred (depends on the bettervaluesettings subsystem).

    public static final BlockEntry<petrolpark.mc.destroy.core.explosion.PrimeableBombBlock<petrolpark.mc.destroy.core.explosion.PrimedBombEntity.Anfo>> ANFO_BLOCK =
        REGISTRATE.block("anfo_block",
            p -> new petrolpark.mc.destroy.core.explosion.PrimeableBombBlock<>(p, petrolpark.mc.destroy.core.explosion.PrimedBombEntity.Anfo::new))
            .initialProperties(() -> Blocks.TNT)
            .properties(p -> p.mapColor(MapColor.SAND))
            .simpleItem()
            .register();

    public static final BlockEntry<petrolpark.mc.destroy.core.explosion.PrimeableBombBlock<petrolpark.mc.destroy.core.explosion.PrimedBombEntity.Cordite>> CORDITE =
        REGISTRATE.block("cordite",
            p -> new petrolpark.mc.destroy.core.explosion.PrimeableBombBlock<>(p, petrolpark.mc.destroy.core.explosion.PrimedBombEntity.Cordite::new))
            .initialProperties(() -> Blocks.TNT)
            .properties(p -> p.mapColor(MapColor.COLOR_ORANGE))
            .simpleItem()
            .register();

    public static final BlockEntry<petrolpark.mc.destroy.core.explosion.PrimeableBombBlock<petrolpark.mc.destroy.core.explosion.PrimedBombEntity.Nitrocellulose>> NITROCELLULOSE_BLOCK =
        REGISTRATE.block("nitrocellulose_block",
            p -> new petrolpark.mc.destroy.core.explosion.PrimeableBombBlock<>(p, petrolpark.mc.destroy.core.explosion.PrimedBombEntity.Nitrocellulose::new))
            .initialProperties(() -> Blocks.TNT)
            .properties(p -> p.mapColor(MapColor.TERRACOTTA_WHITE))
            .simpleItem()
            .register();

    public static final BlockEntry<petrolpark.mc.destroy.core.explosion.PrimeableBombBlock<petrolpark.mc.destroy.core.explosion.PrimedBombEntity.PicricAcid>> PICRIC_ACID_BLOCK =
        REGISTRATE.block("picric_acid_block",
            p -> new petrolpark.mc.destroy.core.explosion.PrimeableBombBlock<>(p, petrolpark.mc.destroy.core.explosion.PrimedBombEntity.PicricAcid::new))
            .initialProperties(() -> Blocks.TNT)
            .properties(p -> p.mapColor(MapColor.COLOR_YELLOW))
            .simpleItem()
            .register();

    // DYNAMITE_BLOCK — ExcavationExplosion-driven area excavator. Simplified:
    // fixed radius from server config (no per-block UI). See DynamiteBlock javadoc for the upgrade path.
    public static final BlockEntry<petrolpark.mc.destroy.core.explosion.DynamiteBlock> DYNAMITE_BLOCK =
        REGISTRATE.block("dynamite_block", petrolpark.mc.destroy.core.explosion.DynamiteBlock::new)
            .initialProperties(() -> Blocks.TNT)
            .properties(p -> p.mapColor(MapColor.COLOR_MAGENTA))
            .simpleItem()
            .register();

    public static final BlockEntry<AgingBarrelBlock> AGING_BARREL =
        REGISTRATE.block("aging_barrel", AgingBarrelBlock::new)
            .initialProperties(() -> Blocks.OAK_PLANKS)
            .properties(p -> p
                .mapColor(MapColor.WOOD)
                .sound(SoundType.WOOD)
                .instrument(NoteBlockInstrument.BASS)
                .strength(2f, 3f)
                .noOcclusion()
                .dynamicShape())
            .tag(BlockTags.MINEABLE_WITH_AXE)
            // until
            // then Registrate's default simple-block stateblock is fine for runtime registration.
            .simpleItem()
            .register();

    // CREATIVE_PUMP — infinite-source pump with player-settable speed. Extends
    // Create's PumpBlock; BlockEntity uses ScrollValueBehaviour for speed setting.
    public static final BlockEntry<CreativePumpBlock> CREATIVE_PUMP =
        REGISTRATE.block("creative_pump", CreativePumpBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.COLOR_PURPLE).noOcclusion())
            .simpleItem()
            .register();

    // MECHANICAL_SIEVE — KineticBlock that sifts ItemEntities dropped onto it via
    // SievingRecipe outputs. X blockstate property tracks shaft axis (X vs Z).
    public static final BlockEntry<MechanicalSieveBlock> MECHANICAL_SIEVE =
        REGISTRATE.block("mechanical_sieve", MechanicalSieveBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .simpleItem()
            .register();

    // EXTRUSION_DIE — RotatedPillarBlock that extrudes blocks pushed through its axis
    // by a contraption. ExtrudableMovementBehaviour (registered via BlockExtrusion.register) handles
    // the block-substitution logic; entities caught inside take damage (SweetBerryBush-style).
    // noCollission: the contraption has to move the block being extruded into the die's own
    // position, and entityInside only fires for entities that can enter the block.
    public static final BlockEntry<ExtrusionDieBlock> EXTRUSION_DIE =
        REGISTRATE.block("extrusion_die", ExtrusionDieBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion().noCollission())
            .simpleItem()
            .register();

    // TEST_TUBE_RACK: 4-slot rack (holds TEST_TUBE items)
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeRackBlock> TEST_TUBE_RACK =
        REGISTRATE.block("test_tube_rack", petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeRackBlock::new)
            .initialProperties(() -> Blocks.OAK_PLANKS)
            .properties(p -> p.strength(0.5f).sound(SoundType.WOOD).noOcclusion())
            .simpleItem()
            .register();

    // BEAKER: mixture tank — capacity 500 mB. fix:
    // was hardcoded `() -> 250` which differed from upstream.
    // Previously used full
    // glass 1x1x1 collision (entity-block-sized hitbox) and (5,1,5)-(11,10,11) fluid box (water
    // overflowed the rim + textures z-fought with cup walls).
    // (5.5, 0.5, 5.5)-(10.5, 7, 10.5) — 0.5 inset on each face + height capped at 7/16 (rim is
    // around 8/16 in the model). Collision uses {@link DestroyVoxelShapes#BEAKER} = tight box.
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlock> BEAKER =
        REGISTRATE.block("beaker",
                petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlock.of(
                    () -> petrolpark.mc.destroy.config.DestroyConfigs.safeInt(
                        petrolpark.mc.destroy.config.DestroyConfigs.server().blocks.beakerCapacity::get, 500),
                    5.5f, 0.5f, 5.5f, 10.5f, 7f, 10.5f,
                    DestroyVoxelShapes.BEAKER))
            .initialProperties(() -> Blocks.GLASS)
            .properties(p -> p.strength(0.3f).sound(SoundType.GLASS).noOcclusion().dynamicShape())
            .item(petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlockItem::new)
            .build()
            .register();

    // FLASK: 500 mB mixture tank (medium)
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlock> FLASK =
        REGISTRATE.block("flask",
                petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlock.of(
                    () -> 500, 4f, 1f, 4f, 12f, 12f, 12f,
                    net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState().getShape(null, null)))
            .initialProperties(() -> Blocks.GLASS)
            .properties(p -> p.strength(0.3f).sound(SoundType.GLASS).noOcclusion().dynamicShape())
            .item(petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlockItem::new)
            .build()
            .register();

    // JAR: 1000 mB mixture tank (large)
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlock> JAR =
        REGISTRATE.block("jar",
                petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlock.of(
                    () -> 1000, 3f, 1f, 3f, 13f, 15f, 13f,
                    net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState().getShape(null, null)))
            .initialProperties(() -> Blocks.GLASS)
            .properties(p -> p.strength(0.3f).sound(SoundType.GLASS).noOcclusion().dynamicShape())
            .item(petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlockItem::new)
            .build()
            .register();

    // MEASURING_CYLINDER: precision tank with GUI fluid transfer (uses config capacity)
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.storage.measuringcylinder.MeasuringCylinderBlock> MEASURING_CYLINDER =
        REGISTRATE.block("measuring_cylinder", petrolpark.mc.destroy.core.chemistry.storage.measuringcylinder.MeasuringCylinderBlock::new)
            .initialProperties(() -> Blocks.GLASS)
            .properties(p -> p.strength(0.3f).sound(SoundType.GLASS).noOcclusion().dynamicShape())
            .item(petrolpark.mc.destroy.core.chemistry.storage.measuringcylinder.MeasuringCylinderBlockItem::new)
            .build()
            .register();

    // Uses
    // Config-driven capacity via DestroyBlocksConfigs.roundBottomedFlaskCapacity (default 500 mB).
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlock> ROUND_BOTTOMED_FLASK =
        REGISTRATE.block("round_bottomed_flask",
                petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlock.of(
                    () -> petrolpark.mc.destroy.config.DestroyConfigs.safeInt(
                        petrolpark.mc.destroy.config.DestroyConfigs.server().blocks.roundBottomedFlaskCapacity::get, 500),
                    5.5f, 0.5f, 5.5f, 10.5f, 4.5f, 10.5f,
                    DestroyVoxelShapes.ROUND_BOTTOMED_FLASK))
            .initialProperties(() -> Blocks.GLASS)
            .properties(p -> p.strength(0.3f).sound(SoundType.GLASS).noOcclusion().dynamicShape())
            .item(petrolpark.mc.destroy.core.chemistry.storage.SimplePlaceableMixtureTankBlockItem::new)
            .build()
            .register();

    // ELEMENT_TANK — HorizontalDirectionalBlock fluid tank that converts itself into
    // a result block when the held fluid matches an ELEMENT_TANK_FILLING recipe. Used for periodic-
    // table element-sample displays. Lambdas are inlined here
    // to avoid adding an unused helper class-level method.
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.storage.ElementTankBlock> ELEMENT_TANK =
        REGISTRATE.block("element_tank", petrolpark.mc.destroy.core.chemistry.storage.ElementTankBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p
                .strength(4f)
                .sound(SoundType.GLASS)
                .noOcclusion()
                .isValidSpawn((s, l, pos, entity) -> false)
                .isRedstoneConductor((s, l, pos) -> false)
                .isSuffocating((s, l, pos) -> false)
                .isViewBlocking((s, l, pos) -> false))
            .transform(TagGen.pickaxeOnly())
            .simpleItem()
            .register();

    // TREE_TAP — HorizontalKineticBlock that taps wood blocks on its facing side
    // and fills its internal tank with the tapping's fluid (latex for stripped jungle wood).
    public static final BlockEntry<TreeTapBlock> TREE_TAP =
        REGISTRATE.block("tree_tap", TreeTapBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .simpleItem()
            .register();

    // KEYPUNCH — HorizontalKineticBlock + ICogWheel for circuit mask punching on
    // belts. The Y-axis kinetic cog accepts rotation from above/below while the block faces
    // horizontally (HORIZONTAL_FACING). Item placement auto-registered via simpleItem().
    public static final BlockEntry<KeypunchBlock> KEYPUNCH =
        REGISTRATE.block("keypunch", KeypunchBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .simpleItem()
            .register();

    // CENTRIFUGE — KineticBlock + ICogWheel; Y-axis cog spinning in a 4-voxel slab.
    // DENSE_OUTPUT_FACE horizontal blockstate determines which neighboring direction receives the
    // dense fluid output (light goes to opposite face). Block + BER + Visual all live.
    public static final BlockEntry<CentrifugeBlock> CENTRIFUGE =
        REGISTRATE.block("centrifuge", CentrifugeBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .simpleItem()
            .register();

    // PUMPJACK + structural cells — multi-block oil pump. Controller
    // (HorizontalDirectionalBlock) + FRONT drilling-head (invisible structural) + TOP beam
    // (invisible structural) + BACK cam (kinetic, rotation input from shaft). PumpjackBlockItem
    // reserves the 4-cell footprint on place.
    public static final BlockEntry<PumpjackBlock> PUMPJACK =
        REGISTRATE.block("pumpjack", PumpjackBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .item(PumpjackBlockItem::new)
            .build()
            .register();

    public static final BlockEntry<PumpjackStructuralBlock> PUMPJACK_STRUCTURAL =
        REGISTRATE.block("pumpjack_structural", PumpjackStructuralBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .register();

    public static final BlockEntry<PumpjackCamBlock> PUMPJACK_CAM =
        REGISTRATE.block("pumpjack_cam", PumpjackCamBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .register();

    // BUBBLE_CAP — vertically-stackable Distillation Tower segment. TOP/BOTTOM
    // blockstate flags auto-compute via neighbor-aware BubbleCapBlock.stateForPositionInTower.
    public static final BlockEntry<BubbleCapBlock> BUBBLE_CAP =
        REGISTRATE.block("bubble_cap", BubbleCapBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .simpleItem()
            .register();

    // BLOWPIPE
    // — glassblowing pipe. DirectionalBlock with FACING; holds molten glass in its FluidTank;
    // advances blowing progress via Create fan AirCurrent. Uses `.item(BlowpipeItem::new)`;
    // BlowpipeItem carries stacksTo(1) + TIME_TO_MOVE_TO_MOUTH constant plus the full
    // use/useOn/inventoryTick behavior + capability + DataComponent wiring.
    public static final BlockEntry<BlowpipeBlock> BLOWPIPE =
        REGISTRATE.block("blowpipe", BlowpipeBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.NONE).noOcclusion())
            .item(BlowpipeItem::new)
            .build()
            .register();

    // DYNAMO — kinetic "charger" above a Basin or
    // Belt/Depot. Morphs into an Arc Furnace when placed atop an Arc Furnace Lid (CARBON_FIBER_BLOCK
    // tag-transformable). KineticBlock with AXIS + ARC_FURNACE blockstate. BE exposes
    // arcFurnaceBlock Lazy + getRedstoneSignal + ChargingBehaviour + recipe pipeline.
    public static final BlockEntry<DynamoBlock> DYNAMO =
        REGISTRATE.block("dynamo", DynamoBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .simpleItem()
            .register();

    // ARC_FURNACE_LID — the "lower half" of an Arc Furnace; paired with a Dynamo
    // on top. Horizontal AXIS synced with the Dynamo. Not independently craftable — appears only
    // when a Dynamo is placed on a CARBON_FIBER_BLOCK (or similar tagged block).
    public static final BlockEntry<ArcFurnaceLidBlock> ARC_FURNACE_LID =
        REGISTRATE.block("arc_furnace_lid", ArcFurnaceLidBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .simpleItem()
            .register();

    // COOLER — Refrigerstrayter: inverse of Create's BlazeBurner. Accepts refrigerant
    // Mixtures or COOLANT-tagged fluids, converts to cooling ticks that apply FROSTING to Basins above.
    // NOTE: Actual FROSTING HeatLevel injection deferred to HeatLevelMixin;
    // for now, cooling ticks are tracked but no heat-level blockstate changes occur on Basin side.
    public static final BlockEntry<CoolerBlock> COOLER =
        REGISTRATE.block("cooler", CoolerBlock::new)
            .initialProperties(() -> Blocks.NETHER_BRICKS)
            .properties(p -> p.mapColor(MapColor.COLOR_LIGHT_BLUE).noOcclusion().lightLevel(state -> 8))
            .simpleItem()
            .register();

    // SAND_CASTLE — decorative fragile block built by bucket-and-spade or baby
    // villagers. Three material variants via EnumProperty<Material>. Sentimental behaviour makes
    // the original builder cry if player trample/break it.
    public static final BlockEntry<SandCastleBlock> SAND_CASTLE =
        REGISTRATE.block("sand_castle", SandCastleBlock::new)
            .initialProperties(() -> Blocks.POPPY)
            .properties(p -> p
                .mapColor(MapColor.SAND)
                .noOcclusion()
                .noLootTable()
                .instabreak()
                .sound(SoundType.SAND))
            .register();

    // SIPHON — redstone-pulsed fluid metering block. Per-pulse "N mB" counter limits
    // how much fluid downstream pumps can drain through it. Uses GeniusFluidTank + ScrollValue UI.
    public static final BlockEntry<SiphonBlock> SIPHON =
        REGISTRATE.block("siphon", SiphonBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.COLOR_CYAN).noOcclusion())
            .simpleItem()
            .register();

    // CATALYTIC_CONVERTER — directional fluid-consuming block with GeniusFluidTank
    // behaviour. Every 10 ticks dumps polluted fluid into the environment with a reduction multiplier.
    public static final BlockEntry<CatalyticConverterBlock> CATALYTIC_CONVERTER =
        REGISTRATE.block("catalytic_converter", CatalyticConverterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY).noOcclusion())
            .simpleItem()
            .register();

    // URINE_CAULDRON — Destroy's "full" cauldron variant holding urine fluid. Glass
    // bottle → URINE_BOTTLE via DestroyCauldronInteractions.URINE. No BlockItem — the cauldron
    // appears naturally when a player urinates on a regular cauldron (server-only spawn).
    public static final BlockEntry<UrineCauldronBlock> URINE_CAULDRON =
        REGISTRATE.block("urine_cauldron", p -> new UrineCauldronBlock(p, DestroyCauldronInteractions.URINE))
            .initialProperties(() -> Blocks.WATER_CAULDRON)
            .tag(BlockTags.CAULDRONS)
            .register();

    // REDSTONE_PROGRAMMER — Block + BlockItem for the channel sequencer.
    // Opens a Menu on right-click. Wrench-cycleable; horizontal
    // facing; waterloggable. Item form is "pocket programmer" — uses same BlockItem to tick + hold
    // program state via PROGRAMMER_UUID / PROGRAMMER_PROGRAM DataComponents.
    public static final BlockEntry<petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerBlock> REDSTONE_PROGRAMMER =
        REGISTRATE.block("redstone_programmer",
            petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerBlock::new)
            .properties(p -> p
                .noOcclusion()
                .destroyTime(1.0F)
                .sound(net.minecraft.world.level.block.SoundType.WOOD))
            .item(petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerBlockItem::new)
                .build()
            .register();

    // POLLUTOMETER — wrenchable directional pollution-readout block. Hosts a
    // ScrollOptionBehaviour over PollutometerSelector so the player can cycle between the 5
    // pollution types (greenhouse / ozone / acid_rain / smog / radioactivity). Top-face anemometer
    // + weathervane spin via PollutometerRenderer; Display Link reads via PollutometerDisplaySource.
    public static final BlockEntry<petrolpark.mc.destroy.core.pollution.PollutometerBlock> POLLUTOMETER =
        REGISTRATE.block("pollutometer", petrolpark.mc.destroy.core.pollution.PollutometerBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.noOcclusion().destroyTime(1.0F))
            .simpleItem()
            .register();

    // PERIODIC_TABLE — decorative HorizontalDirectionalBlock registered as an
    // element on a 2D periodic-table grid. Placed adjacent to other elements at correct relative
    // offsets (per ELEMENTS data-pack JSON), the table auto-completes and awards the
    // PERIODIC_TABLE advancement. PeriodicTableBlockItem uses catnip IPlacementHelper to snap
    // placement next to existing element blocks.
    public static final BlockEntry<petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlock> PERIODIC_TABLE =
        REGISTRATE.block("periodic_table_block",
            petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL))
            .item(petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlockItem::new)
                .build()
            .register();

    // 17 element-specific periodic-table blocks. Split into two families:
    // • Opaque `PeriodicTableBlock` instances (metals / carbon): initialProperties → a metal block
    // for mining level + tool inheritance.
    // • Transparent `TankPeriodicTableBlock` instances (gases / liquids): initialProperties →
    // GLASS, properties → strength 2f / SoundType.GLASS / noOcclusion / no spawn / etc.
    // Client-side color tints wired in DestroyClient block-color events; model/blockstate assets
    // already present in resources/assets/destroy/{blockstates,models/block/periodic_table}.

    // ignored the alpha byte.
    // 1.21 they dim the tint to 12-25% opacity making everything wash to gray. Setting alpha=0xFF
    // gives the original RGB tint at full strength; transparency continues to come from the
    // translucent render_type + the texture's own alpha channel (gas.png is RGBA).
    public static final BlockEntry<petrolpark.mc.destroy.content.product.periodictable.TankPeriodicTableBlock>
    HYDROGEN_PERIODIC_TABLE_BLOCK = tankPeriodicTableBlock("hydrogen_periodic_table_block", 0xFFFFFFFF),
    NITROGEN_PERIODIC_TABLE_BLOCK = tankPeriodicTableBlock("nitrogen_periodic_table_block", 0xFFFFFFFF),
    OXYGEN_PERIODIC_TABLE_BLOCK   = tankPeriodicTableBlock("oxygen_periodic_table_block",   0xFFFFFFFF),
    FLUORINE_PERIODIC_TABLE_BLOCK = tankPeriodicTableBlock("fluorine_periodic_table_block", 0xFFF8F9A7),
    CHLORINE_PERIODIC_TABLE_BLOCK = tankPeriodicTableBlock("chlorine_periodic_table_block", 0xFFC0F9A7),
    MERCURY_PERIODIC_TABLE_BLOCK  = tankPeriodicTableBlock("mercury_periodic_table_block",  0xFFB3B3B3);

    public static final BlockEntry<petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlock>
    CARBON_PERIODIC_TABLE_BLOCK    = solidPeriodicTableBlock("carbon_periodic_table_block"),
    CHROMIUM_PERIODIC_TABLE_BLOCK  = solidPeriodicTableBlock("chromium_periodic_table_block"),
    IRON_PERIODIC_TABLE_BLOCK      = solidPeriodicTableBlock("iron_periodic_table_block"),
    NICKEL_PERIODIC_TABLE_BLOCK    = solidPeriodicTableBlock("nickel_periodic_table_block"),
    COPPER_PERIODIC_TABLE_BLOCK    = solidPeriodicTableBlock("copper_periodic_table_block"),
    ZINC_PERIODIC_TABLE_BLOCK      = solidPeriodicTableBlock("zinc_periodic_table_block"),
    RHODIUM_PERIODIC_TABLE_BLOCK   = solidPeriodicTableBlock("rhodium_periodic_table_block"),
    PALLADIUM_PERIODIC_TABLE_BLOCK = solidPeriodicTableBlock("palladium_periodic_table_block"),
    IODINE_PERIODIC_TABLE_BLOCK    = solidPeriodicTableBlock("iodine_periodic_table_block"),
    PLATINUM_PERIODIC_TABLE_BLOCK  = solidPeriodicTableBlock("platinum_periodic_table_block"),
    GOLD_PERIODIC_TABLE_BLOCK      = solidPeriodicTableBlock("gold_periodic_table_block"),
    LEAD_PERIODIC_TABLE_BLOCK      = solidPeriodicTableBlock("lead_periodic_table_block");

    private static BlockEntry<petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlock>
            solidPeriodicTableBlock(String name) {
        return REGISTRATE.block(name, petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL))
            .transform(com.simibubi.create.foundation.data.TagGen.pickaxeOnly())
            .item(petrolpark.mc.destroy.content.product.periodictable.PeriodicTableBlockItem::new)
                .build()
            .register();
    }

    private static BlockEntry<petrolpark.mc.destroy.content.product.periodictable.TankPeriodicTableBlock>
            tankPeriodicTableBlock(String name, int color) {
        return REGISTRATE.block(name,
                p -> new petrolpark.mc.destroy.content.product.periodictable.TankPeriodicTableBlock(p, color))
            .initialProperties(() -> Blocks.GLASS)
            .properties(p -> p
                .strength(2f)
                .sound(net.minecraft.world.level.block.SoundType.GLASS)
                .noOcclusion()
                .isValidSpawn((state, level, pos, ent) -> false)
                .isRedstoneConductor((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false))
            .transform(com.simibubi.create.foundation.data.TagGen.pickaxeOnly())
            .item(petrolpark.mc.destroy.content.product.periodictable.TankPeriodicTableBlockItem::new)
                .build()
            .register();
    }

    // VAT_CONTROLLER — Vat multi-block chemistry reactor controller.
    // HorizontalDirectionalBlock + IBE + IWrenchable; use()/display/open-screen
    // interaction via VatScreen + ISpecialMixtureContainerBlock. Registration
    // unlocks VatMaterial.registerDestroyVatMaterials() + Vat.tryConstruct() usage in Vat.java.
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlock> VAT_CONTROLLER =
        REGISTRATE.block("vat_controller",
            petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .simpleItem()
            .register();

    // VAT_SIDE — side-cell block of a Vat multi-block.
    // The CopycatFullBlockModel custom baked model
    // is what makes vat walls render with the wrapped material's full block geometry
    // (e.g. iron-block-textured walls). Without it Create's default copycat model surfaces (the
    // panel/X outline). Solid/Cutout/CutoutMipped/Translucent render
    // layers are added so the wrapped model's own render type passes through (mirrors BuilderTransformers
    // .copycat() in Create 1.21).
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.vat.VatSideBlock> VAT_SIDE =
        REGISTRATE.block("vat_side",
            petrolpark.mc.destroy.core.chemistry.vat.VatSideBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .addLayer(() -> RenderType::solid)
            .addLayer(() -> RenderType::cutout)
            .addLayer(() -> RenderType::cutoutMipped)
            .addLayer(() -> RenderType::translucent)
            .onRegister(com.simibubi.create.foundation.data.CreateRegistrate.blockModel(
                () -> petrolpark.mc.destroy.core.block.copycat.CopycatFullBlockModel::new))
            .simpleItem()
            .register();

    // BLACKLIGHT — UV lamp block supplying 100W UV light out its opposite-SIDE
    // face. Wrench-flip toggles 2-axis FLIPPED orientation. Waterloggable + contraption-movable.
    // Unlocks vatUV* Chemistry Ponder scenes (vatUVWithoutBlackLight / vatUVWithBlackLight / vatUV
    // helper) via IUVLampBlock interface + DestroyVoxelShapes.BLACKLIGHT shape entries.
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.vat.uv.BlacklightBlock> BLACKLIGHT =
        REGISTRATE.block("blacklight",
            petrolpark.mc.destroy.core.chemistry.vat.uv.BlacklightBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion().lightLevel(s -> 3))
            .simpleItem()
            .register();

    // COLORIMETER — Vat observation block that detects specific molecules via
    // redstone output. HorizontalDirectionalBlock + FACING/POWERED/BLUSHING
    // properties + IBE + BLUSHING-aware neighbor detection (AllBlocks.SMART_OBSERVER) +
    // redstone-monitor / GUI / mixture-observation pipeline. Registration unlocks the
    // `colorimeter` Chemistry Ponder scene.
    public static final BlockEntry<petrolpark.mc.destroy.core.chemistry.vat.observation.colorimeter.ColorimeterBlock> COLORIMETER =
        REGISTRATE.block("colorimeter",
            petrolpark.mc.destroy.core.chemistry.vat.observation.colorimeter.ColorimeterBlock::new)
            .initialProperties(() -> Blocks.IRON_BLOCK)
            .properties(p -> p.mapColor(MapColor.METAL).noOcclusion())
            .simpleItem()
            .register();

    // custom explosive mix · place-in-world variant paired with
    // MixedExplosiveBlockEntity · uses MixedExplosiveBlockItem
    // (IMixedExplosiveItem impl with DYED_COLOR DataComponent + EXPLOSIVE_MIX inventory payload).
    public static final BlockEntry<petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveBlock> CUSTOM_EXPLOSIVE_MIX =
        REGISTRATE.block("custom_explosive_mix",
            petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveBlock::new)
            .initialProperties(() -> Blocks.TNT)
            .properties(p -> p.mapColor(MapColor.COLOR_GRAY))
            .item(petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveBlockItem::new)
                .build()
            .register();

    public static void register() {
        // class-load trigger; REGISTRATE handles actual bus registration
    }
}
