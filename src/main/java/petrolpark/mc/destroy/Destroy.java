package petrolpark.mc.destroy;

import java.util.function.Supplier;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import petrolpark.mc.library.compat.Mods;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import petrolpark.mc.destroy.config.DestroyConfigs;
import petrolpark.mc.destroy.core.registrate.DestroyRegistrate;
import petrolpark.mc.destroy.data.DestroyDatagen;

@Mod(Destroy.MOD_ID)
public class Destroy {

    public static final String MOD_ID = "destroy";

    public static final String NAME = "Destroy";

    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DestroyRegistrate REGISTRATE = new DestroyRegistrate(MOD_ID);

    // singleton registry of keypunch block entities per-level (maps puncher UUID to
    // ICircuitPuncher instance for mask tooltip resolution). Load/unload wiring deferred to
    // KeypunchBlockEntity port session (LevelEvent.Load / Unload NeoForge 1.21 events).
    public static final petrolpark.mc.destroy.content.processing.trypolithography.CircuitPuncherHandler CIRCUIT_PUNCHER_HANDLER =
        new petrolpark.mc.destroy.content.processing.trypolithography.CircuitPuncherHandler();

    // singleton SavedData + resource-reload listener for named circuit patterns.
    // Server loads JSON definitions from data/<ns>/destroy_compat/circuit_patterns/*.json, generates
    // or reads back patterns, persists to per-level "destroy_circuit_pattern" SavedData, and syncs
    // the packed map to all clients via CircuitPatterns
    public static final petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternHandler CIRCUIT_PATTERN_HANDLER =
        new petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternHandler();

    public static ResourceLocation asResource(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    };

    public Destroy(IEventBus modEventBus, ModContainer modContainer) {

        REGISTRATE.registerEventListeners(modEventBus);

        // Config
        DestroyConfigs.register(ModLoadingContext.get(), modContainer);

        // Registration
        DestroyAttachmentTypes.register(modEventBus);
        DestroyDataComponents.register(modEventBus);
        DestroyNumberProviderTypes.register();
        DestroyPackets.register();
        DestroyPollutionTypes.register();
        DestroyRegistries.init();
        DestroyItemAttributeTypes.register(modEventBus); // DeferredRegister on mod event bus (was static-init Registry.register which trips "Registry is already frozen" in 1.21)
        petrolpark.mc.destroy.client.DestroyMenuTypes.register(); // REDSTONE_PROGRAMMER MenuType + Screen binding
        DestroyStats.register(modEventBus);
        DestroyAttributes.register(modEventBus);
        // Attach EXTRA_INVENTORY_SIZE + EXTRA_HOTBAR_SLOTS to the Player entity's
        // attribute supplier. WITHOUT THIS, player.getAttributes().hasAttribute(...) returns
        // false → Creatine's `extraInventory.addPermanentModifier(...)` is skipped by the
        // `if (hasAttribute)` guard → attribute stays at 0 → no inventory expansion.
        // had this in a Mob.java mixin or via Forge's EntityAttributeModificationEvent;
        // 1.21 NeoForge keeps EntityAttributeModificationEvent (mod-bus, fires once at startup).
        modEventBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent event) -> {
            event.add(net.minecraft.world.entity.EntityType.PLAYER, DestroyAttributes.EXTRA_INVENTORY_SIZE);
            event.add(net.minecraft.world.entity.EntityType.PLAYER, DestroyAttributes.EXTRA_HOTBAR_SLOTS);
        });
        DestroySoundEvents.register(modEventBus);
        petrolpark.mc.destroy.client.DestroyParticleTypes.register(modEventBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(petrolpark.mc.destroy.client.DestroyParticleTypes::registerFactories);
            petrolpark.mc.destroy.client.DestroyPartials.init();
        }
        DestroyMobEffects.register(); // class-load trigger; REGISTRATE handles actual bus registration
        DestroyPotions.register(modEventBus);
        DestroyBlocks.register();
        DestroyItems.register();
        DestroyFluids.register();
        petrolpark.mc.destroy.chemistry.legacy.index.DestroyGroupFinder.register();
        petrolpark.mc.destroy.chemistry.legacy.index.DestroyTopologies.register();
        // fix missing DestroyMolecules.register() call. Molecule <clinit> creates functional
        // group instances which populate LegacyFunctionalGroup.groupTypesAndReactions Map. MUST
        // run BEFORE DestroyGenericReactions.register() because DoubleGroupGenericReaction.<init>
        // (line 37) does groupTypesAndReactions.get(firstType).add(this) — NPE if Map empty.
        petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules.register();
        petrolpark.mc.destroy.chemistry.legacy.index.DestroyGenericReactions.register();
        petrolpark.mc.destroy.chemistry.legacy.index.DestroyReactions.register();
        DestroyBlockEntityTypes.register();
        DestroyEntityTypes.register();
        DestroyRecipeTypes.register();
        DestroyRecipeTypes.registerDeferred(modEventBus);
        DestroyLoot.register(modEventBus);
        modEventBus.addListener(petrolpark.mc.destroy.content.processing.ageing.AgeingBarrelBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.core.pollution.catalyticconverter.CatalyticConverterBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.content.logistics.siphon.SiphonBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.content.processing.cooler.CoolerBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.content.processing.treetap.TreeTapBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.content.processing.centrifuge.CentrifugeBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.content.processing.distillation.BubbleCapBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.content.oil.pumpjack.PumpjackBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.core.chemistry.vat.VatSideBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.core.chemistry.storage.ElementTankBlockEntity::registerCapabilities);
        // TestTubeRack + SimpleMixtureTank + MeasuringCylinder cap registration
        modEventBus.addListener(petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeRackBlockEntity::registerCapabilities);
        modEventBus.addListener(petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankBlockEntity::registerCapabilities);
        // register Destroy's custom FluidIngredient types (mixture_with_molecule etc.)
        DestroyFluidIngredientTypes.register(modEventBus);
        // register DisplaySources to CreateBuiltInRegistries.DISPLAY_SOURCE registry
        // (mod-bus DeferredRegister). Must run BEFORE FMLCommonSetupEvent's
        // DestroyDisplaySources.registerAssociations() (which uses .get() on the entries).
        DestroyDisplaySources.register(modEventBus);
        // register Destroy's custom item Ingredient types (circuit_pattern_item).
        // Without this, recipes using "type": "destroy:circuit_pattern_item" (Colorimeter,
        // Pollutometer, Redstone Programmer) silently fail to load.
        DestroyIngredientTypes.register(modEventBus);
        // register IFluidHandlerItem capability for TEST_TUBE + BALLOON.
        // also register for BEAKER / FLASK / JAR / MEASURING_CYLINDER / ROUND_BOTTOMED_FLASK
        // placeable-tank items.
        // to return `new ItemMixtureTank(stack, ...)` for ALL its subclasses; the 1.21 port migrated
        // initCapabilities → RegisterCapabilitiesEvent but only TEST_TUBE + BALLOON were
        // re-registered, dropping cap on the 5 placeable container items. Without ITEM-cap, Create's
        // Spout (注液器) cannot fill these items on a depot/belt — the user's reported symptom.
        // Capacity is taken at runtime from the BlockItem's `getCapacity(stack)` (which delegates
        // to the underlying block's getMixtureCapacity()).
        modEventBus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) -> {
            event.registerItem(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
                (stack, ctx) -> new petrolpark.mc.destroy.core.chemistry.storage.ItemMixtureTank(stack,
                    petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeItem.CAPACITY),
                DestroyItems.TEST_TUBE.get());
            event.registerItem(
                net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
                (stack, ctx) -> new petrolpark.mc.destroy.core.chemistry.storage.ItemMixtureTank(stack,
                    petrolpark.mc.destroy.core.chemistry.storage.BalloonItem.CAPACITY),
                DestroyItems.BALLOON.get());
            // placeable-tank items: capacity from item.getCapacity(stack) per-instance.
            // Item resolves via `(BlockItem) stack.getItem()` cast; getCapacity is on the
            // PlaceableMixtureTankItem / IMixtureStorageItem interface chain.
            net.minecraft.world.level.ItemLike[] placeableTanks = {
                DestroyBlocks.BEAKER.asItem(),
                DestroyBlocks.FLASK.asItem(),
                DestroyBlocks.JAR.asItem(),
                DestroyBlocks.MEASURING_CYLINDER.asItem(),
                DestroyBlocks.ROUND_BOTTOMED_FLASK.asItem(),
            };
            for (net.minecraft.world.level.ItemLike itemLike : placeableTanks) {
                event.registerItem(
                    net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
                    (stack, ctx) -> {
                        if (stack.getItem() instanceof petrolpark.mc.destroy.core.chemistry.storage.IMixtureStorageItem msi) {
                            return new petrolpark.mc.destroy.core.chemistry.storage.ItemMixtureTank(stack, msi.getCapacity(stack));
                        }
                        return null;
                    },
                    itemLike.asItem());
            }
        });
        DestroyCreativeModeTabs.register(modEventBus);
        DestroyArmorMaterials.register(modEventBus);
        DestroyArmorMaterials.register();
        DestroyAdvancementTrigger.register();
        // DeferredRegister on mod event bus (was static-init Registry.register which trips "Registry is already frozen" in 1.21)
        DestroyPotatoProjectileBlockHitActions.register(modEventBus);
        DestroyPotatoProjectileEntityHitActions.register(modEventBus);
        // DestroyOpenEndedPipeEffectHandlers.register() moved into FMLCommonSetupEvent.enqueueWork (see init())
        // because FluidEntry.getSource() requires bound DeferredHolder, which only resolves AFTER
        // the fluid RegisterEvent fires (mod constructor is TOO EARLY).
        // DestroyDisplaySources.register() same issue · calls DestroyBlockEntityTypes.BUBBLE_CAP.get()
        // which is unbound in mod constructor · moved to FMLCommonSetupEvent.enqueueWork.
        petrolpark.mc.destroy.client.DestroyItemDisplayContexts.register(); // BLOWPIPE ItemDisplayContext for BlowpipeBlockEntityRenderer
        petrolpark.mc.destroy.content.processing.dynamo.DynamoBlock.registerMovementChecks(); // Dynamo/ArcFurnaceLid contraption-move refusal
        DestroyVillagers.register(modEventBus); // Innkeeper profession + Aging Barrel POI

        // Events
        modEventBus.addListener(this::init);
        modEventBus.addListener(EventPriority.HIGHEST, DestroyDatagen::gatherDataHighPriority);
        modEventBus.addListener(EventPriority.LOWEST, DestroyDatagen::gatherData);

        // Compat
        // if (Mods.JEI.isLoading()) NeoForge.EVENT_BUS.register(ITickableCategory.ClientEvents.class);
        // Mods.CREATE.executeIfInstalled(() -> () -> Create.ctor(modEventBus, NeoForge.EVENT_BUS));
        Mods.CURIOS.executeIfInstalled(() -> () -> petrolpark.mc.destroy.compat.curios.DestroyCurios.init(modEventBus, net.neoforged.neoforge.common.NeoForge.EVENT_BUS));
        // Create: Big Cannons integration: two blocks + shell projectile + propellant handler.
        Mods.BIG_CANNONS.executeIfInstalled(() -> () -> petrolpark.mc.destroy.compat.createbigcannons.CreateBigCannons.init(modEventBus));
    };

    private void init(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            DestroyStats.registerFormatters();
            DestroyCompostables.register();
            DestroyCropMutations.register();
            // OpenEndedPipeEffectHandler registration · must run AFTER fluid RegisterEvent
            // (FluidEntry.getSource calls DeferredHolder.get which needs bound registry value).
            // FMLCommonSetupEvent fires after all mod-bus RegisterEvents so DeferredHolders are ready.
            DestroyOpenEndedPipeEffectHandlers.register();
            // DisplaySources BY_BLOCK_ENTITY associations (uses BlockEntityType DeferredHolders).
            // Must run after BE registry is bound. Registry registration itself is done in mod
            // constructor via DestroyDisplaySources.register(modEventBus).
            DestroyDisplaySources.registerAssociations();
            // Block Extrusion mappings for Extrusion Die. Must run after block registry
            // (BlockExtrusion.EXTRUSIONS keyed by Block instance from .get()).
            DestroyBlockExtrusions.register();
            // Vat attached-check + Dynamo movement-allowed-check. Both register into
            // Create's BlockMovementChecks (mutable registry; safe to call any time after Create's
            // own registrations finish, which they have by FMLCommonSetupEvent).
            DestroyMovementChecks.register();
            // Force VatMaterial type registration eagerly at mod setup, NOT lazily via
            // Vat.<clinit>. Without this, players who never place a Vat (so Vat class never loads)
            // get a CRASH on /reload: "Block Ingredient Type destroy:single_block is not registered"
            // serializes BlockIngredient keys via type-id dispatch, and SingleBlockIngredient.TYPE
            // was never registered. The static block in Vat.java still calls this redundantly,
            // which is fine — petrolpark's registerType is idempotent (check-and-add map).
            petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial.registerDestroyVatMaterials();
        });
    };

    public static final <T> T runForDist(Supplier<Supplier<T>> clientSupplier, Supplier<Supplier<T>> serverSupplier) {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            return clientSupplier.get().get();
        } else {
            return serverSupplier.get().get();
        }
    };

    public static final <T> T unsafeCallClient(Supplier<Supplier<T>> supplier) {
        try {
            if (FMLEnvironment.dist == Dist.CLIENT) supplier.get().get();
        } catch (Exception e) {
            throw new RuntimeException();
        };
        return null;
    };

    public static final void unsafeRunClient(Supplier<Runnable> supplier) {
        try {
            if (FMLEnvironment.dist == Dist.CLIENT) supplier.get().run();
        } catch (Exception e) {
            throw new RuntimeException();
        };
    };

};

