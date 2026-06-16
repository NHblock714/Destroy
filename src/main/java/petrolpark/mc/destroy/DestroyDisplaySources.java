package petrolpark.mc.destroy;

import java.util.function.Supplier;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugeBlockEntity;
import petrolpark.mc.destroy.content.processing.distillation.BubbleCapBlockEntity;
import petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity;

/**
 * Destroy's {@link DisplaySource} registrations.
*/
public class DestroyDisplaySources {

    /**
 * NeoForge DeferredRegister against Create's DISPLAY_SOURCE registry. Wires through the
 * mod-bus {@code RegisterEvent} during init phase (before registry freeze).
*/
    public static final DeferredRegister<DisplaySource> DISPLAY_SOURCES =
        DeferredRegister.create(
            (net.minecraft.resources.ResourceKey<? extends Registry<DisplaySource>>) CreateBuiltInRegistries.DISPLAY_SOURCE.key(),
            Destroy.MOD_ID);

    // ---- Source instances (held both in the registry AND in BY_BLOCK_ENTITY map; same instance) ----

    public static final java.util.function.Supplier<BubbleCapBlockEntity.BubbleCapDisplaySource> BUBBLE_CAP =
        DISPLAY_SOURCES.register("bubble_cap", BubbleCapBlockEntity.BubbleCapDisplaySource::new);

    public static final java.util.function.Supplier<CentrifugeBlockEntity.CentrifugeDisplaySource> CENTRIFUGE_INPUT =
        DISPLAY_SOURCES.register("centrifuge_input", CentrifugeBlockEntity.CentrifugeDisplaySource::createInput);
    public static final java.util.function.Supplier<CentrifugeBlockEntity.CentrifugeDisplaySource> CENTRIFUGE_DENSE_OUTPUT =
        DISPLAY_SOURCES.register("centrifuge_dense_output", CentrifugeBlockEntity.CentrifugeDisplaySource::createDenseOutput);
    public static final java.util.function.Supplier<CentrifugeBlockEntity.CentrifugeDisplaySource> CENTRIFUGE_LIGHT_OUTPUT =
        DISPLAY_SOURCES.register("centrifuge_light_output", CentrifugeBlockEntity.CentrifugeDisplaySource::createLightOutput);

    public static final java.util.function.Supplier<petrolpark.mc.destroy.core.pollution.pollutometer.PollutometerDisplaySource> POLLUTOMETER =
        DISPLAY_SOURCES.register("pollutometer", petrolpark.mc.destroy.core.pollution.pollutometer.PollutometerDisplaySource::new);

    // Vat content display sources (3 variants picked on the Display Link GUI).
    // These are inner-class factories on VatControllerBlockEntity, wired so a Display Link can
    // read a Vat's contents.
    public static final java.util.function.Supplier<VatControllerBlockEntity.VatDisplaySource> VAT_ALL =
        DISPLAY_SOURCES.register("vat_all", VatControllerBlockEntity.VatDisplaySource::createAllSource);
    public static final java.util.function.Supplier<VatControllerBlockEntity.VatDisplaySource> VAT_SOLUTION =
        DISPLAY_SOURCES.register("vat_solution", VatControllerBlockEntity.VatDisplaySource::createSolutionSource);
    public static final java.util.function.Supplier<VatControllerBlockEntity.VatDisplaySource> VAT_GAS =
        DISPLAY_SOURCES.register("vat_gas", VatControllerBlockEntity.VatDisplaySource::createGasSource);

    // Colorimeter display source.
    public static final java.util.function.Supplier<petrolpark.mc.destroy.core.chemistry.vat.observation.colorimeter.ColorimeterBlockEntity.ColorimeterDisplaySource> COLORIMETER =
        DISPLAY_SOURCES.register("colorimeter",
            petrolpark.mc.destroy.core.chemistry.vat.observation.colorimeter.ColorimeterBlockEntity.ColorimeterDisplaySource::new);

    /**
 * Wire the DeferredRegister to the mod event bus + populate BY_BLOCK_ENTITY associations.
 * Must be called from the mod constructor (before {@link net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent}).
*/
    public static void register(IEventBus modEventBus) {
        DISPLAY_SOURCES.register(modEventBus);
    }

    /**
 * Populate {@link DisplaySource#BY_BLOCK_ENTITY} associations using the SAME instances that
 * were registered to the DISPLAY_SOURCE registry. Called from
 * {@link Destroy#init(net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent)} after registry
 * registration has completed (FMLCommonSetupEvent fires after all RegisterEvents).
*/
    public static void registerAssociations() {
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.BUBBLE_CAP.get(), BUBBLE_CAP.get());

        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.CENTRIFUGE.get(), CENTRIFUGE_INPUT.get());
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.CENTRIFUGE.get(), CENTRIFUGE_DENSE_OUTPUT.get());
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.CENTRIFUGE.get(), CENTRIFUGE_LIGHT_OUTPUT.get());

        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.POLLUTOMETER.get(), POLLUTOMETER.get());

        // VAT controller + side both expose the 3 vat sources (display link can be
        // attached to either; VatDisplaySource.getFluidStack auto-resolves controller through
        // VatSide.getController if needed).
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.VAT_CONTROLLER.get(), VAT_ALL.get());
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.VAT_CONTROLLER.get(), VAT_SOLUTION.get());
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.VAT_CONTROLLER.get(), VAT_GAS.get());
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.VAT_SIDE.get(), VAT_ALL.get());
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.VAT_SIDE.get(), VAT_SOLUTION.get());
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.VAT_SIDE.get(), VAT_GAS.get());

        // Colorimeter
        DisplaySource.BY_BLOCK_ENTITY.add(DestroyBlockEntityTypes.COLORIMETER.get(), COLORIMETER.get());
    }
}
