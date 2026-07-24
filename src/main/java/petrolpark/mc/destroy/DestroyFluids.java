package petrolpark.mc.destroy;

import static petrolpark.mc.destroy.Destroy.REGISTRATE;

import petrolpark.mc.destroy.core.registrate.DestroyRegistrate;
import petrolpark.mc.library.PetrolparkTags;
import petrolpark.mc.library.core.world.fluid.ColoredFluidType;
import com.simibubi.create.Create;
import com.simibubi.create.content.fluids.VirtualFluid;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.simibubi.create.foundation.data.VirtualFluidBuilder;
import com.tterrag.registrate.builders.FluidBuilder;
import com.tterrag.registrate.util.entry.FluidEntry;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid.MixtureFluidType;
import petrolpark.mc.destroy.content.processing.moltenblock.MoltenBorosilicateGlassFluid;
import petrolpark.mc.destroy.content.processing.moltenblock.MoltenStainlessSteelFluid;

/**
 * Destroy's fluid registry.
 *
 * <p>1.21.1 notes:</p>
 *
 * <p><b>Hold (deferred until their underlying subsystems are ported):</b></p>
 * <ul>
 * <li>{@code MIXTURE} / {@code GAS_MIXTURE} — require the chemistry engine (MixtureFluid + Mixture
 * serialization) which has not been migrated yet.</li>
 * <li>{@code DestroyFluids.air(...)} / {@code isMixture(...)} helpers — return when MIXTURE lands.</li>
 * </ul>
*/
public class DestroyFluids {

    /** Air mixture molar density in mol/L — re-exposed unchanged for ported consumers.*/
    public static final double AIR_MOLAR_DENSITY = 0.0420352380152d;

    /**
 * Convenience helper: synthesize a Fluid Stack of "air" — an {@code N₂ + O₂} Mixture at the given
 * temperature, wrapped on {@link #MIXTURE} with translation key {@code fluid.destroy.air}.
*/
    public static FluidStack air(int amount, float temperature) {
        return MixtureFluid.of(amount, MixtureFluid.airMixture(temperature), "fluid.destroy.air");
    }

    public static final FluidEntry<MixtureFluid> MIXTURE = mixtureFluid("mixture",
        Destroy.asResource("fluid/mixture_still"),
        Destroy.asResource("fluid/mixture_flow")
    ).register();

    public static final FluidEntry<MixtureFluid> GAS_MIXTURE = mixtureFluid("gas",
        Destroy.asResource("fluid/gas"),
        Destroy.asResource("fluid/gas")
    ).register();

    public static final FluidEntry<MoltenStainlessSteelFluid> MOLTEN_STAINLESS_STEEL =
        customFluid("molten_stainless_steel",
            MoltenStainlessSteelFluid::createSource, MoltenStainlessSteelFluid::createFlowing)
            .register();

    public static final FluidEntry<MoltenBorosilicateGlassFluid> MOLTEN_BOROSILICATE_GLASS =
        customFluid("molten_borosilicate_glass",
            MoltenBorosilicateGlassFluid::createSource, MoltenBorosilicateGlassFluid::createFlowing)
            .register();

    public static final FluidEntry<VirtualFluid>

    URINE = virtualFluid("urine")
        .tag(PetrolparkTags.commonFluidTag("urine"))
        .register(),

    APPLE_JUICE = coloredWaterFluid("apple_juice", 0xC0F2DB46)
        .tag(PetrolparkTags.commonFluidTag("apple_juice"))
        .register(),

    CHORUS_WINE = coloredSwirlingFluid("chorus_wine", 0x808000C0)
        .register(),

    CREAM = virtualFluid("cream")
        .register(),

    CRUDE_OIL = virtualFluid("crude_oil")
        .tag(PetrolparkTags.commonFluidTag("crude_oil"), DestroyTags.Fluids.AMPLIFIES_SMOG.tag)
        .bucket()
            .tag(PetrolparkTags.commonItemTag("buckets/crude_oil"))
            .build()
        .register(),

    MOLTEN_CINNABAR = virtualFluid("molten_cinnabar")
        .properties(p -> p.lightLevel(10))
        .register(),

    NAPALM_SUNDAE = virtualFluid("napalm_sundae")
        .tag(DestroyTags.Fluids.AMPLIFIES_SMOG.tag)
        .register(),

    PERFUME = coloredSwirlingFluid("perfume", 0x80ffcff7)
        .register(),

    SKIMMED_MILK = coloredWaterFluid("skimmed_milk", 0xFF000000)
        .register(),

    MOONSHINE = coloredWaterFluid("moonshine", 0xC0A18666)
        .register(),

    UNDISTILLED_MOONSHINE = coloredWaterFluid("undistilled_moonshine", 0xF053330D)
        .register(),

    // POTIONS (display-only; the actual potion-fluid is still Create's PotionFluid)

    LONG_POTION       = coloredPotionFluid("long_potion",       0xffff0000).register(),
    STRONG_POTION     = coloredPotionFluid("strong_potion",     0xffffff00).register(),
    SPLASH_POTION     = coloredPotionFluid("splash_potion",     0xffd00000).register(),
    LINGERING_POTION  = coloredPotionFluid("lingering_potion",  0xffd000d0).register(),
    CORRUPTING_POTION = coloredPotionFluid("corrupting_potion", 0xff00d000).register();

    // --- builder helpers ---

    /**
 * Simple virtual fluid that uses {@code textures/fluid/<name>.png} for both still and flowing
 * sprites and Create's default fluid type.
*/
    private static FluidBuilder<VirtualFluid, DestroyRegistrate> virtualFluid(String name) {
        ResourceLocation tex = Destroy.asResource("fluid/" + name);
        return REGISTRATE.entry(name, c -> new VirtualFluidBuilder<>(REGISTRATE, REGISTRATE, name, c,
            tex, tex,
            CreateRegistrate::defaultFluidType,
            VirtualFluid::createSource,
            VirtualFluid::createFlowing));
    }

    /**
 * Custom-fluid-subclass builder (MoltenStainlessSteel / MoltenBorosilicateGlass). Texture lives at
 * {@code block/<name>.png}.
*/
    /**
 * Custom-fluid-subclass builder (MoltenStainlessSteel / MoltenBorosilicateGlass). Texture lives at
 * {@code block/<name>.png}.
*/
    private static <F extends VirtualFluid> FluidBuilder<F, DestroyRegistrate> customFluid(
            String name,
            com.tterrag.registrate.util.nullness.NonNullFunction<net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties, F> sourceFactory,
            com.tterrag.registrate.util.nullness.NonNullFunction<net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties, F> flowingFactory) {
        ResourceLocation tex = Destroy.asResource("block/" + name);
        return REGISTRATE.entry(name, c -> new VirtualFluidBuilder<>(REGISTRATE, REGISTRATE, name, c,
            tex, tex,
            CreateRegistrate::defaultFluidType,
            sourceFactory,
            flowingFactory));
    }

    private static FluidBuilder<VirtualFluid, DestroyRegistrate> coloredWaterFluid(String name, int color) {
        return coloredFluid(name, color,
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_still"),
            ResourceLocation.fromNamespaceAndPath("minecraft", "block/water_flow"));
    }

    private static FluidBuilder<VirtualFluid, DestroyRegistrate> coloredSwirlingFluid(String name, int color) {
        ResourceLocation swirl = Destroy.asResource("fluid/swirling");
        return coloredFluid(name, color, swirl, swirl);
    }

    private static FluidBuilder<VirtualFluid, DestroyRegistrate> coloredPotionFluid(String name, int color) {
        return coloredFluid(name, color, Create.asResource("fluid/potion_still"), Create.asResource("fluid/potion_flow"));
    }

    private static FluidBuilder<VirtualFluid, DestroyRegistrate> coloredFluid(String name, int color, ResourceLocation stillTexture, ResourceLocation flowingTexture) {
        return REGISTRATE.entry(name, c -> new VirtualFluidBuilder<>(REGISTRATE, REGISTRATE, name, c,
            stillTexture, flowingTexture,
            (properties, st, ft) -> new ColoredFluidType(properties, st, ft, color),
            VirtualFluid::createSource,
            VirtualFluid::createFlowing));
    }

    /**
 * Builder helper for {@link #MIXTURE} and {@link #GAS_MIXTURE} — custom texture paths + the
 * {@link MixtureFluidType} that reads the Mixture DataComponent for colour and description.
*/
    private static FluidBuilder<MixtureFluid, DestroyRegistrate> mixtureFluid(
            String name, ResourceLocation stillTexture, ResourceLocation flowingTexture) {
        return REGISTRATE.entry(name, c -> new VirtualFluidBuilder<>(REGISTRATE, REGISTRATE, name, c,
            stillTexture, flowingTexture,
            MixtureFluidType::new,
            MixtureFluid::createSource,
            MixtureFluid::createFlowing));
    }

    /**
 * Whether the given FluidStack is a {@link #MIXTURE} (non-empty + carries a Mixture payload
 * via the {@link DestroyDataComponents#MIXTURE} DataComponent).
*/
    public static boolean isMixture(FluidStack stack) {
        return stack != null && !stack.isEmpty() && isMixture(stack.getFluid())
            && stack.has(DestroyDataComponents.MIXTURE);
    }

    /** Whether the given Fluid is the Mixture source fluid. GAS_MIXTURE is separate.*/
    public static boolean isMixture(Fluid fluid) {
        return fluid.isSame(MIXTURE.get());
    }

    public static void register() {
        // class-load trigger; REGISTRATE handles actual bus registration
    }
}
