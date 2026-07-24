package petrolpark.mc.destroy.compat.curios;

import petrolpark.mc.library.compat.curios.PetrolparkCurios;

import net.neoforged.bus.api.IEventBus;
import petrolpark.mc.destroy.core.chemistry.hazard.ChemistryHazardHelper.Protection;

/**
 * Destroy's Curios compatibility wiring. Loaded only when Curios is installed (gated at the call
 * site in {@code Destroy} via {@code Mods.CURIOS.executeIfInstalled(...)}).
 *
 * <p>The goggles wearing-predicate is attached by the library's {@link PetrolparkCurios}, and slot
 * opt-in is data-driven — items are tagged for their slots in {@link DestroyItems} via Curios
 * Registrate transforms ({@code data/curios/tags/item/<slot>.json}) — so nothing needs registering
 * here.</p>
*/
public class DestroyCurios {

    public static void init(IEventBus modEventBus, IEventBus forgeEventBus) {
        // Protective equipment → matching body-part protection predicate (all go in the "head" slot
        // except nothing currently — BODY/LEGS/FEET are covered by vanilla armor slots, which the
        // Protection enum's default test already handles).
        registerCuriosTest(Protection.HEAD,          "head");
        registerCuriosTest(Protection.EYES,          "head");
        registerCuriosTest(Protection.NOSE,          "head");
        registerCuriosTest(Protection.MOUTH,         "head");
        registerCuriosTest(Protection.MOUTH_COVERED, "head");
    }

    private static void registerCuriosTest(Protection protectionType, String slotId) {
        protectionType.registerTest(PetrolparkCurios.wearingCurioPredicate(protectionType.defaultTag::matches, slotId));
    }
}
