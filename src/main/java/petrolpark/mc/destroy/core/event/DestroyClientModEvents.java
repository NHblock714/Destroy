package petrolpark.mc.destroy.core.event;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.chemistry.naming.SaltNameOverrides;
import petrolpark.mc.destroy.client.DestroyPonderPlugin;
import petrolpark.mc.destroy.content.oil.seismology.SeismographItemRenderer;
import petrolpark.mc.destroy.content.oil.seismology.SeismometerItemRenderer;
import petrolpark.mc.destroy.content.processing.glassblowing.BlowpipeItemRenderLayer;
import petrolpark.mc.destroy.content.processing.glassblowing.BlowpipeItemRenderer;
import petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternItemModel;
import petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternItemRenderer;
import petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerItemRenderer;
import petrolpark.mc.destroy.content.oil.pumpjack.PumpjackVisual;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugeCogVisual;
import petrolpark.mc.destroy.content.processing.sieve.MechanicalSieveVisual;
import petrolpark.mc.destroy.content.processing.treetap.TreeTapVisual;
import petrolpark.mc.destroy.content.processing.trypolithography.keypunch.KeypunchVisual;
import petrolpark.mc.destroy.content.tool.swissarmyknife.SwissArmyKnifeItemRenderer;
import petrolpark.mc.destroy.content.tool.syringe.SyringeItemRenderer;
import petrolpark.mc.destroy.core.pollution.SmogAffectedBlockColor;
import petrolpark.mc.destroy.util.NameLists;

/**
 * Central hub for Destroy's client-side mod-bus event subscriptions.
*/
@EventBusSubscriber(modid = Destroy.MOD_ID, value = Dist.CLIENT)
public class DestroyClientModEvents {

    @SubscribeEvent
    public static final void registerClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(SaltNameOverrides.RELOAD_LISTENER);
        event.registerReloadListener(NameLists.RELOAD_LISTENER);
    }

    /**
     * Scan every active resource pack for atom JSON models under
     * {@code assets/&lt;ns&gt;/models/chemistry/atom/*.json} and add each to the standalone-model
     * pipeline. Without this, datapack-defined elements that ship their atom model in a
     * resource pack would fail texture stitching at atlas build time — the atlas only includes
     * textures referenced by registered models, and datapack-element {@code PartialModel.of}
     * calls happen during {@code SyncElementsS2CPacket.handle()} which fires AFTER the atlas
     * is already built.
     *
     * <p>Pre-registering every {@code chemistry/atom} model regardless of whether a datapack
     * actually defines a matching element is wasteful by a few KB but harmless — unused
     * standalone models are just baked once and never referenced.</p>
     */
    @SubscribeEvent
    public static final void registerAtomStandaloneModels(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional event) {
        net.minecraft.server.packs.resources.ResourceManager rm =
            net.minecraft.client.Minecraft.getInstance().getResourceManager();
        java.util.Map<net.minecraft.resources.ResourceLocation, net.minecraft.server.packs.resources.Resource> models =
            rm.listResources("models/chemistry/atom",
                rl -> rl.getPath().endsWith(".json"));
        for (net.minecraft.resources.ResourceLocation modelFile : models.keySet()) {
            // Convert "<ns>:models/chemistry/atom/foo.json" → ModelResourceLocation
            // "<ns>:chemistry/atom/foo" (no "models/" prefix, no ".json").
            String trimmed = modelFile.getPath();
            if (trimmed.startsWith("models/")) trimmed = trimmed.substring("models/".length());
            if (trimmed.endsWith(".json")) trimmed = trimmed.substring(0, trimmed.length() - ".json".length());
            net.minecraft.resources.ResourceLocation modelId =
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(modelFile.getNamespace(), trimmed);
            event.register(net.minecraft.client.resources.model.ModelResourceLocation.standalone(modelId));
        }
    }

    /**
 * Register {@link SmogAffectedBlockColor} variants on vanilla blocks so their tint darkens with
 * chunk SMOG level.
*/
    /**
 * Register {@link SyringeItemRenderer} as a custom item renderer for every syringe-family item
 * via {@link RegisterClientExtensionsEvent}.
 *
 * <p>Shared {@code SyringeItemRenderer} + {@code IClientItemExtensions} instances are reused
 * across all 3 subclass items — the animation only reads {@code stack.has(INJECTING)} so it's
 * per-stack stateful.</p>
*/
    @SubscribeEvent
    public static final void registerItemExtensions(RegisterClientExtensionsEvent event) {
        // switched all CustomRenderedItemModelRenderer subclasses to register via Create's
        // SimpleCustomRenderer.create(item, renderer) helper (instead of bare IClientItemExtensions).
        // Reason: Create's ModelSwapper only wraps an item's BakedModel in CustomRenderedItemModel
        // if the item was registered in CustomRenderedItems via SimpleCustomRenderer.create() (or
        // CustomRenderedItems.register() directly). Without the wrap, the
        // CustomRenderedItemModelRenderer cast in Create's render path silently no-ops, so renderers
        // never fire. Symptom: circuit_board/circuit_mask middle empty (no overlay); seismograph
        // map overlay missing; redstone_programmer animation missing; etc.
        final SyringeItemRenderer syringeRenderer = new SyringeItemRenderer();
        event.registerItem(SimpleCustomRenderer.create(DestroyItems.ASPIRIN_SYRINGE.get(), syringeRenderer),
            DestroyItems.ASPIRIN_SYRINGE.get());
        event.registerItem(SimpleCustomRenderer.create(DestroyItems.CISPLATIN_SYRINGE.get(), syringeRenderer),
            DestroyItems.CISPLATIN_SYRINGE.get());
        event.registerItem(SimpleCustomRenderer.create(DestroyItems.BABY_BLUE_SYRINGE.get(), syringeRenderer),
            DestroyItems.BABY_BLUE_SYRINGE.get());

        // SwissArmyKnifeItem custom renderer (animated per-tool sub-model render).
        final SwissArmyKnifeItemRenderer swissArmyKnifeRenderer = new SwissArmyKnifeItemRenderer();
        event.registerItem(SimpleCustomRenderer.create(DestroyItems.SWISS_ARMY_KNIFE.get(), swissArmyKnifeRenderer),
            DestroyItems.SWISS_ARMY_KNIFE.get());

        // SeismographItemRenderer draws the first-person held-map nonogram overlay on top
        // of the vanilla map tile. SeismometerItemRenderer draws the animated analog
        // needle + spike-page animation driven by SeismometerSpike
        final SeismographItemRenderer seismographRenderer = new SeismographItemRenderer();
        event.registerItem(SimpleCustomRenderer.create(DestroyItems.SEISMOGRAPH.get(), seismographRenderer),
            DestroyItems.SEISMOGRAPH.get());

        final SeismometerItemRenderer seismometerRenderer = new SeismometerItemRenderer();
        event.registerItem(SimpleCustomRenderer.create(DestroyItems.SEISMOMETER.get(), seismometerRenderer),
            DestroyItems.SEISMOMETER.get());

        // CircuitBoardItem + CircuitMaskItem custom renderers (trypolithography batch).
        // Each renderer has a different fragment texture folder (circuit_board vs circuit_mask), so
        // they instantiate as separate renderers pointing at different ResourceLocations.
        final CircuitPatternItemRenderer circuitBoardRenderer =
            new CircuitPatternItemRenderer(Destroy.asResource("item/circuit_pattern/circuit_board"));
        event.registerItem(SimpleCustomRenderer.create(DestroyItems.CIRCUIT_BOARD.get(), circuitBoardRenderer),
            DestroyItems.CIRCUIT_BOARD.get());

        final CircuitPatternItemRenderer circuitMaskRenderer =
            new CircuitPatternItemRenderer(Destroy.asResource("item/circuit_pattern/circuit_mask"));
        event.registerItem(SimpleCustomRenderer.create(DestroyItems.CIRCUIT_MASK.get(), circuitMaskRenderer),
            DestroyItems.CIRCUIT_MASK.get());

        // RedstoneProgrammer BlockItem custom renderer.
        final RedstoneProgrammerItemRenderer redstoneProgrammerRenderer = new RedstoneProgrammerItemRenderer();
        event.registerItem(SimpleCustomRenderer.create(DestroyBlocks.REDSTONE_PROGRAMMER.asItem(), redstoneProgrammerRenderer),
            DestroyBlocks.REDSTONE_PROGRAMMER.asItem());

        // BlowpipeItem custom first-person / GUI renderer.
        // Third-person hand display is handled separately by BlowpipeItemRenderLayer (onAddLayers).
        final BlowpipeItemRenderer blowpipeRenderer = new BlowpipeItemRenderer();
        event.registerItem(SimpleCustomRenderer.create(DestroyBlocks.BLOWPIPE.asItem(), blowpipeRenderer),
            DestroyBlocks.BLOWPIPE.asItem());

        // TestTubeItem custom renderer (mixture-color translucent fluid overlay via
        // TransparentItemRenderer.transformAndRenderModel). Same SimpleCustomRenderer.create pattern
        // as Circuit / Seismograph etc.
        final petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeItemRenderer testTubeRenderer =
            new petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeItemRenderer();
        event.registerItem(SimpleCustomRenderer.create(DestroyItems.TEST_TUBE.get(), testTubeRenderer),
            DestroyItems.TEST_TUBE.get());

        // MixedExplosiveBlockItem custom renderer.
        // Renders vanilla item model + 4-direction truncated label via
        // MixedExplosiveBlockEntityRenderer.renderTruncated when stack has CUSTOM_NAME DataComponent.
        final petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveBlockItemRenderer customExplosiveMixRenderer =
            new petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveBlockItemRenderer();
        event.registerItem(SimpleCustomRenderer.create(DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.asItem(), customExplosiveMixRenderer),
            DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.asItem());

        // SimplePlaceableMixtureTank item renderers.
        // Each of BEAKER/FLASK/JAR gets an instance of SimpleMixtureTankItemRenderer bound to the
        // underlying item's render-info interface so the held item shows a dynamic fluid fill
        // scaled to the block's FluidBoxDimensions (the earlier placeholder showed no fluid fill).
        registerTankItemRenderer(event, DestroyBlocks.BEAKER);
        registerTankItemRenderer(event, DestroyBlocks.FLASK);
        registerTankItemRenderer(event, DestroyBlocks.JAR);
        registerTankItemRenderer(event, DestroyBlocks.MEASURING_CYLINDER);
        // ROUND_BOTTOMED_FLASK was missing from this list — the block + item were added
        // but its custom renderer was not wired here. Without registration, the item lacks Create's
        // CustomRenderedItem ModelSwapper wrap, which can corrupt the GuiGraphics buffer-source
        // state when rendered as a recipe-result icon in BlowpipeScreen — symptom: hover over the
        // round-bottomed-flask recipe row caused all fluid+item icons across all rows in the
        // recipe-selection window to vanish (PoseStack/buffer-source leak from un-wrapped 3D model
        // render through GuiGraphics.renderItem).
        registerTankItemRenderer(event, DestroyBlocks.ROUND_BOTTOMED_FLASK);
    }

    /** Helper · wires a SimpleMixtureTankItemRenderer for the given tank block's item.*/
    @SuppressWarnings("unchecked")
    private static void registerTankItemRenderer(RegisterClientExtensionsEvent event,
                                                 com.tterrag.registrate.util.entry.BlockEntry<?> blockEntry) {
        if (!(blockEntry.asItem() instanceof petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankRenderer.ISimpleMixtureTankRenderInformation<?>)) return;
        petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankRenderer.ISimpleMixtureTankRenderInformation<net.minecraft.world.item.ItemStack> renderInfo =
            (petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankRenderer.ISimpleMixtureTankRenderInformation<net.minecraft.world.item.ItemStack>) blockEntry.asItem();
        final petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankItemRenderer tankRenderer =
            new petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankItemRenderer(renderInfo);
        // was bare {@code new IClientItemExtensions(){...}} which does NOT register the
        // item with Create's CustomRenderedItems → ModelSwapper doesn't wrap the BakedModel in
        // CustomRenderedItemModel → renderer's {@code model.getOriginalModel()} returns the raw
        // 2D parent model instead of the block's 3D model → inventory shows flat icon + a tiny
        // misplaced fluid box. Using SimpleCustomRenderer.create here makes the inventory show the
        // full 3D beaker/flask/cylinder model with fluid inside, matching the placed-on-ground
        // appearance.
        event.registerItem(
            com.simibubi.create.foundation.item.render.SimpleCustomRenderer.create(blockEntry.asItem(), tankRenderer),
            blockEntry.asItem());
    }

    /**
 * Attach {@link BlowpipeItemRenderLayer} to every player-skin and humanoid-model entity
 * renderer.
*/
    @SubscribeEvent
    public static final void onAddLayers(EntityRenderersEvent.AddLayers event) {
        BlowpipeItemRenderLayer.onAddLayers(event);
    }

    /**
 * Wire Flywheel {@link dev.engine_room.flywheel.api.visualization.BlockEntityVisualizer}s
 * for BEs that have Visual classes — Flywheel-enabled clients get the instanced rendering
 * path; the BER is skipped when a Visual is active (default {@code skipVanillaRender = true}
 * in {@link SimpleBlockEntityVisualizer.Builder#apply}, avoiding double-draw).
 *
 * <p>Uses {@code SimpleBlockEntityVisualizer.builder(type).factory(Visual::new).apply()}
 * pattern — identical to Create's {@code CreateBlockEntityBuilder.registerVisualizer} path
 * (verified via javap on {@code flywheel-neoforge-api-1.21.1-1.0.5.jar} and Create
 * {@code AllBlockEntityTypes} usage). The {@code .apply()} call registers into
 * {@code VisualizerRegistry.setVisualizer(type, visualizer)} as a side effect.</p>
*/
    @SubscribeEvent
    public static final void onClientSetup(FMLClientSetupEvent event) {
        // DestroyItemProperties wiring (Swiss Army Knife component property).
        // Must run inside FMLClientSetupEvent.enqueueWork because ItemProperties.register is
        // main-thread-only (1.21 NeoForge).
        event.enqueueWork(petrolpark.mc.destroy.DestroyItemProperties::register);

        // Ponder plugin registration. Create 1.21 pattern (javap verified on
        // CreateClient): PonderIndex.addPlugin(new CreatePonderPlugin()). No SPI / META-INF
        // registration. Invoked client-side only during mod-init.
        event.enqueueWork(() -> net.createmod.ponder.foundation.PonderIndex.addPlugin(new DestroyPonderPlugin()));

        // Mysterious Item Conversion JEI hints (Empty Blaze Burner ↔ Cooler etc.).
        // Must run client-side only because MysteriousItemConversionCategory.RECIPES is a
        // client-side static list consumed by JEI plugin reg.
        event.enqueueWork(petrolpark.mc.destroy.DestroyMysteriousItemConversions::addAll);

        SimpleBlockEntityVisualizer.builder(DestroyBlockEntityTypes.TREE_TAP.get())
            .factory(TreeTapVisual::new)
            .apply();

        SimpleBlockEntityVisualizer.builder(DestroyBlockEntityTypes.MECHANICAL_SIEVE.get())
            .factory(MechanicalSieveVisual::new)
            .apply();

        // DYNAMO inner shaft visualizer. Without this, the DynamoRenderer (BER) is the
        // only render path, but Create 6.x KineticBlockEntityRenderer.renderSafe early-returns
        // when Flywheel visualization is supported (always-on in 1.21), so BER never runs and
        // the rotating inner shaft model is completely invisible.
        // skipVanillaRender(true) so BER is skipped (the dedicated DynamoRenderer for arc
        // lightning effects IS still wanted; renderSafe still calls super early-return,
        // but the overridden renderSafe runs the arc-line code AFTER super returns when arcs
        // should fire — that path is independent of the inner-shaft path).
        SimpleBlockEntityVisualizer.builder(DestroyBlockEntityTypes.DYNAMO.get())
            .factory(petrolpark.mc.destroy.content.processing.dynamo.DynamoCogVisual::new)
            .apply();

        // KEYPUNCH — adds piston instance on top of EncasedCogVisual's cogwheel.
        SimpleBlockEntityVisualizer.builder(DestroyBlockEntityTypes.KEYPUNCH.get())
            .factory(KeypunchVisual::new)
            .apply();

        // CENTRIFUGE — SingleAxisRotatingVisual for the Y-axis inner cog. Stub BE
        // means no "live" centrifugation rendering yet; cog spin alone for now.
        SimpleBlockEntityVisualizer.builder(DestroyBlockEntityTypes.CENTRIFUGE.get())
            .factory(CentrifugeCogVisual::new)
            .apply();

        // PUMPJACK — 4 TransformedInstances (cam + linkage + beam + pump) animated
        // per frame via PumpjackBlockEntity.getRenderAngle(). Replaces PumpjackRenderer path
        // when Flywheel is active.
        SimpleBlockEntityVisualizer.builder(DestroyBlockEntityTypes.PUMPJACK.get())
            .factory(PumpjackVisual::new)
            .apply();
    }

    /**
 * Per-client-tick callback driving {@link SeismometerItemRenderer#tick()} — advances the
 * {@link net.createmod.catnip.animation.LerpedFloat} needle chaser and decrements the spike
 * countdown. Without this, the Seismometer renderer's needle wouldn't smoothly animate
 * between target angles.
*/
    @SubscribeEvent
    public static final void onClientTick(ClientTickEvent.Post event) {
        SeismometerItemRenderer.tick();
        tickHoverHook();
        // Draw the on-face value box for sided scroll-value blocks (Dynamite excavation radius).
        // Create's ScrollValueRenderer only renders concrete ScrollValueBehaviour subclasses, so
        // SidedScrollValueBehaviour needs its own hover dispatcher.
        petrolpark.mc.destroy.core.bettervaluesettings.SidedScrollValueRenderer.tick();
        // Swiss Army Knife auto-tool-selection. Without this, the tool selection logic
        // (which reads mc.hitResult to detect what block the player is aiming at, sets the
        // Tool client-side, and sends a C2SPacket to update server-side ACTIVE_TOOL DataComponent)
        // never runs → the rendered tool stays at default (PICKAXE) → ItemPropertyFunction
        // returns 0 → JSON model selector picks the default model → no visual swap when aiming
        // at logs/leaves/etc.
        // 1.21 NeoForge equivalent is the static clientPlayerTick called from ClientTickEvent.Post.
        petrolpark.mc.destroy.content.tool.swissarmyknife.SwissArmyKnifeItem.clientPlayerTick();
    }

    /** Player joining dedicated server triggers RecipesUpdatedEvent — use it to refresh the
 * Periodic Table Ponder scene cache (first JEI access no longer blocks the main thread for
 * several seconds generating scenes).
 *
 * <p>Also compacts the JEI molecule reverse-index ({@code MOLECULES_INPUT/OUTPUT}) by filtering
 * out {@code Recipe<?>} instances that are no longer in the client's RecipeManager. The
 * underlying issue is {@code JeiProcessingRecipeMixin} appending to those maps in
 * {@code ProcessingRecipe.<init>} with no clearing mechanism — every datapack reload (and
 * every single-player world re-entry) reconstructs every recipe → mixin appends fresh
 * {@code Recipe<?>} instances on top of the previous reload's. Additionally, in single
 * player the mixin fires on BOTH the integrated server's RecipeManager.apply() AND the
 * client's RecipeManager.apply() (after the recipe-sync packet arrives), so each recipe
 * shows up twice even on first world entry. Filtering against the client's current
 * recipe set drops both kinds of stale duplicates.</p>
*/
    @SubscribeEvent
    public static final void onRecipesUpdated(net.neoforged.neoforge.client.event.RecipesUpdatedEvent event) {
        if (net.minecraft.client.Minecraft.getInstance().level != null) {
            petrolpark.mc.destroy.client.DestroyPonderScenes.refreshPeriodicTableBlockScenes();
        }
        // JEI optional-dependency guard.
        if (!com.petrolpark.compat.Mods.JEI.isLoading()) return;
        net.minecraft.world.item.crafting.RecipeManager rm = event.getRecipeManager();
        java.util.Set<net.minecraft.world.item.crafting.Recipe<?>> live = new java.util.HashSet<>();
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder : rm.getRecipes()) {
            live.add(holder.value());
        }
        java.util.function.BiConsumer<
            petrolpark.mc.destroy.chemistry.legacy.LegacySpecies,
            java.util.List<net.minecraft.world.item.crafting.Recipe<?>>> compact = (species, list) -> {
            java.util.LinkedHashSet<net.minecraft.world.item.crafting.Recipe<?>> kept = new java.util.LinkedHashSet<>();
            for (net.minecraft.world.item.crafting.Recipe<?> r : list) {
                if (live.contains(r)) kept.add(r);
            }
            list.clear();
            list.addAll(kept);
        };
        petrolpark.mc.destroy.compat.jei.DestroyJEI.MOLECULES_INPUT.forEach(compact);
        petrolpark.mc.destroy.compat.jei.DestroyJEI.MOLECULES_OUTPUT.forEach(compact);
    }

    /** Inlines just the hover-dispatch
 * piece here. Without it TestTubeRack's per-slot outline / VatController's multi-block outline
 * never draw even though the BE implements the interface.
*/
    private static void tickHoverHook() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (player == null) return;
        net.minecraft.world.phys.HitResult target = mc.hitResult;
        if (!(target instanceof net.minecraft.world.phys.BlockHitResult result)) return;
        net.minecraft.client.multiplayer.ClientLevel level = mc.level;
        if (level == null) return;
        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(result.getBlockPos());
        if (be instanceof petrolpark.mc.destroy.core.block.entity.ISpecialWhenHoveredBlockEntity hover) {
            hover.whenLookedAt(player, result);
        }
    }

    /**
 * Register Destroy's custom {@link net.neoforged.neoforge.client.model.geometry.IGeometryLoader}s
 * —— loaders that models can opt into via {@code "loader": "destroy:X"} in JSON.
*/
    @SubscribeEvent
    public static final void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(Destroy.asResource("circuit_pattern"), CircuitPatternItemModel.Loader.INSTANCE);
    }

    /** The source impl slices circuit-pattern
 * textures into 16 tile sprites (powering {@link petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternItemRenderer})
 * and emits universal armor-trim palette permutations.
*/
    @SubscribeEvent
    public static final void registerSpriteSourceTypes(net.neoforged.neoforge.client.event.RegisterSpriteSourceTypesEvent event) {
        petrolpark.mc.destroy.client.DestroySpriteSource.registerTypes(event);
    }

    @SubscribeEvent
    public static final void changeBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(SmogAffectedBlockColor.GRASS, Blocks.GRASS_BLOCK, Blocks.FERN, Blocks.TALL_GRASS);
        event.register(SmogAffectedBlockColor.DOUBLE_TALL_GRASS, Blocks.TALL_GRASS, Blocks.LARGE_FERN);
        event.register(SmogAffectedBlockColor.PINK_PETALS, Blocks.PINK_PETALS);
        event.register(SmogAffectedBlockColor.FOLIAGE, Blocks.OAK_LEAVES, Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES, Blocks.VINE, Blocks.MANGROVE_LEAVES);
        event.register(SmogAffectedBlockColor.BIRCH, Blocks.BIRCH_LEAVES);
        event.register(SmogAffectedBlockColor.SPRUCE, Blocks.SPRUCE_LEAVES);
        event.register(SmogAffectedBlockColor.WATER, Blocks.WATER, Blocks.BUBBLE_COLUMN, Blocks.WATER_CAULDRON);
        event.register(SmogAffectedBlockColor.SUGAR_CANE, Blocks.SUGAR_CANE);

        // Dyeable mixed-explosive block tint (T2b batch).
        event.register(
            petrolpark.mc.destroy.core.explosion.mixedexplosive.DyeableMixedExplosiveBlockColor.INSTANCE,
            petrolpark.mc.destroy.DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.get());

        // TankPeriodicTableBlock tint (reads TankPeriodicTableBlock.color for the fluid inside
        // the glass shell). Covers 6 elements: hydrogen, nitrogen, oxygen, fluorine, chlorine, mercury.
        event.register(
            petrolpark.mc.destroy.content.product.periodictable.TankPeriodicTableBlockColor.INSTANCE,
            petrolpark.mc.destroy.DestroyBlocks.HYDROGEN_PERIODIC_TABLE_BLOCK.get(),
            petrolpark.mc.destroy.DestroyBlocks.NITROGEN_PERIODIC_TABLE_BLOCK.get(),
            petrolpark.mc.destroy.DestroyBlocks.OXYGEN_PERIODIC_TABLE_BLOCK.get(),
            petrolpark.mc.destroy.DestroyBlocks.FLUORINE_PERIODIC_TABLE_BLOCK.get(),
            petrolpark.mc.destroy.DestroyBlocks.CHLORINE_PERIODIC_TABLE_BLOCK.get(),
            petrolpark.mc.destroy.DestroyBlocks.MERCURY_PERIODIC_TABLE_BLOCK.get());
    }

    
    @SubscribeEvent
    public static final void changeItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
            petrolpark.mc.destroy.core.explosion.mixedexplosive.DyeableMixedExplosiveItemColor.INSTANCE,
            petrolpark.mc.destroy.DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.asItem());

        // TankPeriodicTableBlockItem tint (reads item.getColor() -> block.color).
        event.register(
            petrolpark.mc.destroy.content.product.periodictable.TankPeriodicTableBlockItemColor.INSTANCE,
            petrolpark.mc.destroy.DestroyBlocks.HYDROGEN_PERIODIC_TABLE_BLOCK.asItem(),
            petrolpark.mc.destroy.DestroyBlocks.NITROGEN_PERIODIC_TABLE_BLOCK.asItem(),
            petrolpark.mc.destroy.DestroyBlocks.OXYGEN_PERIODIC_TABLE_BLOCK.asItem(),
            petrolpark.mc.destroy.DestroyBlocks.FLUORINE_PERIODIC_TABLE_BLOCK.asItem(),
            petrolpark.mc.destroy.DestroyBlocks.CHLORINE_PERIODIC_TABLE_BLOCK.asItem(),
            petrolpark.mc.destroy.DestroyBlocks.MERCURY_PERIODIC_TABLE_BLOCK.asItem());

        // Per-syringe-variant overlay tint. layer0 = syringe_overlay (the colored fluid
        // inside), layer1 = syringe (glass body). SyringeItem.getTintColor(layer) returns the
        // ARGB color per layer; subclasses override to differentiate aspirin/cisplatin/baby_blue.
        // The base SYRINGE returns 0xFFFFFFFF (no tint = empty syringe just shows raw overlay).
        net.minecraft.client.color.item.ItemColor syringeColor = (stack, tintIndex) -> {
            if (stack.getItem() instanceof petrolpark.mc.destroy.content.tool.syringe.SyringeItem syringe) {
                return syringe.getTintColor(tintIndex);
            }
            return 0xFFFFFFFF;
        };
        event.register(syringeColor,
            petrolpark.mc.destroy.DestroyItems.SYRINGE.get(),
            petrolpark.mc.destroy.DestroyItems.ASPIRIN_SYRINGE.get(),
            petrolpark.mc.destroy.DestroyItems.CISPLATIN_SYRINGE.get(),
            petrolpark.mc.destroy.DestroyItems.BABY_BLUE_SYRINGE.get());
    }
}
