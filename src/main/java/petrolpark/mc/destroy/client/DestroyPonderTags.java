package petrolpark.mc.destroy.client;

import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyItems;

/**
 * Registrar for Destroy's Ponder tags. Ponder "tags" are category groupings visible in the
 * Ponder UI tag index (e.g. the "Chemistry" tab shows all Chemistry-tagged blocks). Currently
 * registers only the 3 top-level tags; block-to-tag additions are deferred since they
 * reference blocks/items spread across several subdirs.
*/
public class DestroyPonderTags {

    public static final ResourceLocation
        CHEMISTRY       = Destroy.asResource("chemistry"),
        DESTROY         = Destroy.asResource("destroy"),
        VAT_SIDE_BLOCKS = Destroy.asResource("vat_side_blocks");

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        // Top-level tag registrations — 3 tabs in the Ponder index. TEST_TUBE + VAT_CONTROLLER
        // items not yet available; LOGO is used as the icon for all 3 in the meantime
        // (cosmetic-only, shown in Ponder tag index).
        helper.registerTag(CHEMISTRY)
            .addToIndex()
            .item(DestroyItems.LOGO)  // TODO: TEST_TUBE when available
            .register();

        helper.registerTag(DESTROY)
            .addToIndex()
            .item(DestroyItems.LOGO)
            .register();

        helper.registerTag(VAT_SIDE_BLOCKS)
            .addToIndex()
            .item(DestroyItems.LOGO)  // TODO: VAT_CONTROLLER (block) when the Vat subdir is available
            .register();

        // Later: HELPER.addToTag(CHEMISTRY).add(AllBlocks.BASIN).add(BUBBLE_CAP).etc
        // Added one by one once the relevant block/item registrations are in place.
    }
}
