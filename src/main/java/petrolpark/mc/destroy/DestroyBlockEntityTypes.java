package petrolpark.mc.destroy;

import static petrolpark.mc.destroy.Destroy.REGISTRATE;

import com.tterrag.registrate.util.entry.BlockEntityEntry;

import petrolpark.mc.destroy.content.logistics.creativepump.CreativePumpBlockEntity;
import petrolpark.mc.destroy.content.logistics.siphon.SiphonBlockEntity;
import petrolpark.mc.destroy.content.logistics.siphon.SiphonRenderer;
import petrolpark.mc.destroy.content.processing.ageing.AgeingBarrelBlockEntity;
import petrolpark.mc.destroy.content.processing.ageing.AgeingBarrelRenderer;
import petrolpark.mc.destroy.content.processing.cooler.CoolerBlockEntity;
import petrolpark.mc.destroy.content.processing.cooler.CoolerRenderer;
import petrolpark.mc.destroy.content.processing.extrusion.ExtrusionDieBlockEntity;
import petrolpark.mc.destroy.content.processing.sieve.MechanicalSieveBlockEntity;
import petrolpark.mc.destroy.content.processing.sieve.MechanicalSieveRenderer;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugeBlockEntity;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugeRenderer;
import petrolpark.mc.destroy.content.oil.pumpjack.PumpjackBlockEntity;
import petrolpark.mc.destroy.content.oil.pumpjack.PumpjackCamBlockEntity;
import petrolpark.mc.destroy.content.oil.pumpjack.PumpjackRenderer;
import petrolpark.mc.destroy.content.processing.distillation.BubbleCapBlockEntity;
import petrolpark.mc.destroy.content.processing.distillation.BubbleCapRenderer;
import petrolpark.mc.destroy.content.processing.dynamo.DynamoBlockEntity;
import petrolpark.mc.destroy.content.processing.dynamo.DynamoRenderer;
import petrolpark.mc.destroy.content.processing.glassblowing.BlowpipeBlockEntity;
import petrolpark.mc.destroy.content.processing.glassblowing.BlowpipeBlockEntityRenderer;
import petrolpark.mc.destroy.content.processing.treetap.TreeTapBlockEntity;
import petrolpark.mc.destroy.content.processing.treetap.TreeTapRenderer;
import petrolpark.mc.destroy.content.processing.trypolithography.keypunch.KeypunchBlockEntity;
import petrolpark.mc.destroy.content.processing.trypolithography.keypunch.KeypunchRenderer;
import petrolpark.mc.destroy.content.sandcastle.SandCastleBlockEntity;
import petrolpark.mc.destroy.core.pollution.catalyticconverter.CatalyticConverterBlockEntity;

/**
 * Destroy 的 BlockEntityType 清单。
 *
 * <p>1.21.1 要点：
 * <ul>
 * <li>Registrate {@code .blockEntity(name, factory).validBlocks(...).renderer(() -> R::new).register()} 与
 * Create 的 {@code AllBlockEntityTypes} 同款流水线。</li>
 * <li>Capability 在 BE 类上声明静态 {@code registerCapabilities(RegisterCapabilitiesEvent)}，由
 * {@link Destroy#Destroy} 里 {@code modEventBus.addListener} 挂。</li>
 * </ul>
*/
public class DestroyBlockEntityTypes {

    public static final BlockEntityEntry<AgeingBarrelBlockEntity> AGING_BARREL =
        REGISTRATE.blockEntity("aging_barrel", AgeingBarrelBlockEntity::new)
            .validBlocks(DestroyBlocks.AGING_BARREL)
            .renderer(() -> AgeingBarrelRenderer::new)
            .register();

    public static final BlockEntityEntry<CreativePumpBlockEntity> CREATIVE_PUMP =
        REGISTRATE.blockEntity("creative_pump", CreativePumpBlockEntity::new)
            .validBlocks(DestroyBlocks.CREATIVE_PUMP)
            .register();

    public static final BlockEntityEntry<CatalyticConverterBlockEntity> CATALYTIC_CONVERTER =
        REGISTRATE.blockEntity("catalytic_converter", CatalyticConverterBlockEntity::new)
            .validBlocks(DestroyBlocks.CATALYTIC_CONVERTER)
            .register();

    public static final BlockEntityEntry<SiphonBlockEntity> SIPHON =
        REGISTRATE.blockEntity("siphon", SiphonBlockEntity::new)
            .validBlocks(DestroyBlocks.SIPHON)
            .renderer(() -> SiphonRenderer::new)
            .register();

    public static final BlockEntityEntry<SandCastleBlockEntity> SAND_CASTLE =
        REGISTRATE.blockEntity("sand_castle", SandCastleBlockEntity::new)
            .validBlocks(DestroyBlocks.SAND_CASTLE)
            .register();

    public static final BlockEntityEntry<CoolerBlockEntity> COOLER =
        REGISTRATE.blockEntity("cooler", CoolerBlockEntity::new)
            .validBlocks(DestroyBlocks.COOLER)
            .renderer(() -> CoolerRenderer::new)
            .register();

    public static final BlockEntityEntry<TreeTapBlockEntity> TREE_TAP =
        REGISTRATE.blockEntity("tree_tap", TreeTapBlockEntity::new)
            .validBlocks(DestroyBlocks.TREE_TAP)
            .renderer(() -> TreeTapRenderer::new)
            .register();

    // KEYPUNCH — HorizontalKineticBlock + ICogWheel hosting
    // CircuitPunchingBehaviour + DestroyAdvancementBehaviour + NamingBehaviour. BER renders piston
    // + SHAFTLESS cog; Visual provides Flywheel-accelerated instance rendering.
    public static final BlockEntityEntry<KeypunchBlockEntity> KEYPUNCH =
        REGISTRATE.blockEntity("keypunch", KeypunchBlockEntity::new)
            .validBlocks(DestroyBlocks.KEYPUNCH)
            .renderer(() -> KeypunchRenderer::new)
            .register();

    // CENTRIFUGE — KineticBlock + ICogWheel; Y-axis cog spinning in a 4-voxel slab
    // that separates mixture fluids into dense/light outputs. Cog renders via BER + Flywheel Visual.
    public static final BlockEntityEntry<CentrifugeBlockEntity> CENTRIFUGE =
        REGISTRATE.blockEntity("centrifuge", CentrifugeBlockEntity::new)
            .validBlocks(DestroyBlocks.CENTRIFUGE)
            .renderer(() -> CentrifugeRenderer::new)
            .register();

    // PUMPJACK — multi-block oil
    // drilling controller. Fluid tank + ChunkCrudeOil AttachmentType drilling + cam rotation
    // driven by Create shaft + animated beam rocking rendered via PumpjackRenderer + Flywheel
    // PumpjackVisual (instanced path preferred).
    public static final BlockEntityEntry<PumpjackBlockEntity> PUMPJACK =
        REGISTRATE.blockEntity("pumpjack", PumpjackBlockEntity::new)
            .validBlocks(DestroyBlocks.PUMPJACK)
            .renderer(() -> PumpjackRenderer::new)
            .register();

    // PUMPJACK_CAM — kinetic counter-weight cell driving the cam rotation→drill timer sync.
    public static final BlockEntityEntry<PumpjackCamBlockEntity> PUMPJACK_CAM =
        REGISTRATE.blockEntity("pumpjack_cam", PumpjackCamBlockEntity::new)
            .validBlocks(DestroyBlocks.PUMPJACK_CAM)
            .register();

    // BUBBLE_CAP —
    // vertically stackable Distillation Tower segment. Full JSON distillation gameplay:
    // BubbleCapBE auto-joins DistillationTower singleton on placement, controller (bottom) ticks
    // recipe matching + process(), BubbleCapRenderer shows fluid fill as 3-section (bottom/center/top).
    public static final BlockEntityEntry<BubbleCapBlockEntity> BUBBLE_CAP =
        REGISTRATE.blockEntity("bubble_cap", BubbleCapBlockEntity::new)
            .validBlocks(DestroyBlocks.BUBBLE_CAP)
            .renderer(() -> BubbleCapRenderer::new)
            .register();

    // BLOWPIPE — glassblowing pipe BE with BER wired. BlowpipeItemRenderer +
    // BlowpipeItemRenderLayer (for handheld / first-person display) depend on BlowpipeItem +
    // DataComponent stack NBT migration.
    public static final BlockEntityEntry<BlowpipeBlockEntity> BLOWPIPE =
        REGISTRATE.blockEntity("blowpipe", BlowpipeBlockEntity::new)
            .validBlocks(DestroyBlocks.BLOWPIPE)
            .renderer(() -> BlowpipeBlockEntityRenderer::new)
            .register();

    // DYNAMO —
    // kinetic charger.
    // DynamoCogVisual (Flywheel SingleAxisRotatingVisual) **must** be registered as a
    // SimpleBlockEntityVisualizer; without it, the inner shaft model is invisible. Create 6.x's
    // KineticBlockEntityRenderer.renderSafe early-returns when Flywheel visualization is supported
    // (always-on in 1.21), so the BER becomes a fallback-only path. The visualizer registration
    // for Destroy uses an FMLClientSetupEvent hook (DestroyClientModEvents) since DestroyRegistrate
    // doesn't expose Create's `.visual()` chain method.
    public static final BlockEntityEntry<DynamoBlockEntity> DYNAMO =
        REGISTRATE.blockEntity("dynamo", DynamoBlockEntity::new)
            .validBlocks(DestroyBlocks.DYNAMO)
            .renderer(() -> DynamoRenderer::new)
            .register();

    public static final BlockEntityEntry<ExtrusionDieBlockEntity> EXTRUSION_DIE =
        REGISTRATE.blockEntity("extrusion_die", ExtrusionDieBlockEntity::new)
            .validBlocks(DestroyBlocks.EXTRUSION_DIE)
            .register();

    // ELEMENT_TANK — Mixture-aware 1-bucket fluid tank that converts to a result
    // block when the held fluid matches an ELEMENT_TANK_FILLING recipe. BER renders the held
    // fluid as a proportional inner box.
    // TEST_TUBE_RACK: 4-slot rack for TestTube / Syringe items. SmartBlockEntityRenderer.
    public static final BlockEntityEntry<petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeRackBlockEntity> TEST_TUBE_RACK =
        REGISTRATE.blockEntity("test_tube_rack",
            petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeRackBlockEntity::new)
            .validBlocks(DestroyBlocks.TEST_TUBE_RACK)
            .renderer(() -> petrolpark.mc.destroy.core.chemistry.storage.testtube.TestTubeRackRenderer::new)
            .register();

    // SIMPLE_MIXTURE_TANK: generic small mixture container (beaker / flask / jar · multi-block-instance).
    public static final BlockEntityEntry<petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankBlockEntity.SimplePlaceableMixtureTankBlockEntity> SIMPLE_MIXTURE_TANK =
        REGISTRATE.blockEntity("simple_mixture_tank",
            petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankBlockEntity.SimplePlaceableMixtureTankBlockEntity::new)
            .validBlocks(DestroyBlocks.BEAKER, DestroyBlocks.FLASK, DestroyBlocks.JAR, DestroyBlocks.ROUND_BOTTOMED_FLASK)
            .renderer(() -> petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankRenderer::new)
            .register();

    // MEASURING_CYLINDER: precision mixture measurement tank (with GUI transfer).
    public static final BlockEntityEntry<petrolpark.mc.destroy.core.chemistry.storage.measuringcylinder.MeasuringCylinderBlockEntity> MEASURING_CYLINDER =
        REGISTRATE.blockEntity("measuring_cylinder",
            petrolpark.mc.destroy.core.chemistry.storage.measuringcylinder.MeasuringCylinderBlockEntity::new)
            .validBlocks(DestroyBlocks.MEASURING_CYLINDER)
            .renderer(() -> petrolpark.mc.destroy.core.chemistry.storage.SimpleMixtureTankRenderer::new)
            .register();

    public static final BlockEntityEntry<petrolpark.mc.destroy.core.chemistry.storage.ElementTankBlockEntity> ELEMENT_TANK =
        REGISTRATE.blockEntity("element_tank",
            petrolpark.mc.destroy.core.chemistry.storage.ElementTankBlockEntity::new)
            .validBlocks(DestroyBlocks.ELEMENT_TANK)
            .renderer(() -> petrolpark.mc.destroy.core.chemistry.storage.ElementTankRenderer::new)
            .register();

    public static final BlockEntityEntry<MechanicalSieveBlockEntity> MECHANICAL_SIEVE =
        REGISTRATE.blockEntity("mechanical_sieve", MechanicalSieveBlockEntity::new)
            .validBlocks(DestroyBlocks.MECHANICAL_SIEVE)
            .renderer(() -> MechanicalSieveRenderer::new)
            .register();

    // REDSTONE_PROGRAMMER BE. Hosts RedstoneProgrammerBehaviour, which
    // contains the RedstoneProgram channel sequencer. BER renderer wired.
    public static final BlockEntityEntry<petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerBlockEntity> REDSTONE_PROGRAMMER =
        REGISTRATE.blockEntity("redstone_programmer",
            petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerBlockEntity::new)
            .validBlocks(DestroyBlocks.REDSTONE_PROGRAMMER)
            .renderer(() -> petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerBlockEntityRenderer::new)
            .register();

    // DYNAMITE — per-side excavation-radius BE. SidedScrollValueBehaviour stores
    // 6 ints (one per face); DynamiteBlock reads the BE's excavationAreaUpper/LowerCorner to
    // build the asymmetric explosion AABB.
    public static final BlockEntityEntry<petrolpark.mc.destroy.core.explosion.DynamiteBlockEntity> DYNAMITE =
        REGISTRATE.blockEntity("dynamite",
            petrolpark.mc.destroy.core.explosion.DynamiteBlockEntity::new)
            .validBlocks(DestroyBlocks.DYNAMITE_BLOCK)
            .register();

    // POLLUTOMETER — pollution-readout BE. Hosts ScrollOptionBehaviour over
    // PollutometerSelector; renderer animates anemometer + weathervane. Display Link source
    // wired in DestroyDisplaySources.register.
    public static final BlockEntityEntry<petrolpark.mc.destroy.core.pollution.pollutometer.PollutometerBlockEntity> POLLUTOMETER =
        REGISTRATE.blockEntity("pollutometer",
            petrolpark.mc.destroy.core.pollution.pollutometer.PollutometerBlockEntity::new)
            .validBlocks(DestroyBlocks.POLLUTOMETER)
            .renderer(() -> petrolpark.mc.destroy.core.pollution.pollutometer.PollutometerRenderer::new)
            .register();

    // VAT_CONTROLLER — Vat multi-block controller. Full Vat subsystem: VatSideBlockEntity /
    // VatFluidTankBehaviour / VatScreen / VatRenderer / observation screens / UV lamp blocks.
    // Registration enables VatMaterial.registerDestroyVatMaterials() to actually reference
    // the block + Vat.tryConstruct() to compile.
    public static final BlockEntityEntry<petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity> VAT_CONTROLLER =
        REGISTRATE.blockEntity("vat_controller",
            petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity::new)
            .validBlocks(DestroyBlocks.VAT_CONTROLLER)
            .renderer(() -> petrolpark.mc.destroy.core.chemistry.vat.VatRenderer::new)
            .register();

    // VAT_SIDE — side-cell BE of a Vat multi-block. Stores DisplayType
    // (NORMAL / BAROMETER / THERMOMETER / PIPE / vents). Stub BE: field storage only; full
    // fluid I/O + redstone-monitor + copycat-rendering behaviour deferred.
    public static final BlockEntityEntry<petrolpark.mc.destroy.core.chemistry.vat.VatSideBlockEntity> VAT_SIDE =
        REGISTRATE.blockEntity("vat_side",
            petrolpark.mc.destroy.core.chemistry.vat.VatSideBlockEntity::new)
            .validBlocks(DestroyBlocks.VAT_SIDE)
            .renderer(() -> petrolpark.mc.destroy.core.chemistry.vat.VatSideRenderer::new)
            .register();

    // COLORIMETER — Vat observation BE. Stub BE: SmartBlockEntity base with
    // empty addBehaviours. Full redstone-monitor + molecule-concentration observation pipeline
    // + ColorimeterScreen GUI deferred.
    public static final BlockEntityEntry<petrolpark.mc.destroy.core.chemistry.vat.observation.colorimeter.ColorimeterBlockEntity> COLORIMETER =
        REGISTRATE.blockEntity("colorimeter",
            petrolpark.mc.destroy.core.chemistry.vat.observation.colorimeter.ColorimeterBlockEntity::new)
            .validBlocks(DestroyBlocks.COLORIMETER)
            .register();

    // CUSTOM_EXPLOSIVE_MIX — pairs with the MixedExplosiveBlock
    // registration. Enables in-world placement + entity spawn pipeline.
    // BER wire renders the 4-direction custom-name label when block has setCustomName.
    public static final BlockEntityEntry<petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveBlockEntity> CUSTOM_EXPLOSIVE_MIX =
        REGISTRATE.blockEntity("custom_explosive_mix",
            petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveBlockEntity::new)
            .validBlocks(DestroyBlocks.CUSTOM_EXPLOSIVE_MIX)
            .renderer(() -> petrolpark.mc.destroy.core.explosion.mixedexplosive.MixedExplosiveBlockEntityRenderer::new)
            .register();

    public static void register() {
        // class-load trigger; REGISTRATE handles actual bus registration
    }
}
