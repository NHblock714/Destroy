package petrolpark.mc.destroy.compat.createbigcannons;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import petrolpark.mc.destroy.compat.createbigcannons.block.CreateBigCannonsBlocks;
import petrolpark.mc.destroy.compat.createbigcannons.block.entity.CreateBigCannonBlockEntityTypes;
import petrolpark.mc.destroy.compat.createbigcannons.entity.CreateBigCannonsEntityTypes;

/**
 * Top-level init for the CBC compat module. Called from {@link petrolpark.mc.destroy.Destroy}
 * constructor under a {@code Mods.BIG_CANNONS.executeIfInstalled} guard (so the class is never
 * classloaded when CBC is absent — avoids NoClassDefFoundError on CBC types).
*/
public class CreateBigCannons {

    public static void init(IEventBus modEventBus) {
        DestroyMunitionPropertiesHandlers.init();
        CreateBigCannonsBlocks.register();
        CreateBigCannonBlockEntityTypes.register();
        CreateBigCannonsEntityTypes.register();

        modEventBus.addListener(CreateBigCannons::onCommonSetup);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            modEventBus.addListener(Client::onClientSetup);
        }
    }

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(DestroyBlobEffects::registerBlobEffects);
    }

    /**
     * Client-only wiring, nested so dedicated-server class loading of {@link CreateBigCannons}
     * never resolves Flywheel visual classes (same RuntimeDistCleaner pattern as the rest of
     * the port).
     */
    public static final class Client {

        private Client() {}

        /**
         * Register the Flywheel visual for the custom shell's installed fuze. CBC's
         * {@code FuzedBlockEntityRenderer} (registered as the BER) early-returns whenever
         * {@code VisualizationManager.supportsVisualization} — always true with Flywheel
         * active in 1.21 — and expects a {@code FuzedBlockVisual} to draw the fuze via the
         * instancing path instead. CBC registers that visual for its own shells through
         * Registrate's {@code .visual(...)} chain; this compat module's Registrate wrapper
         * doesn't expose it, so mirror the registration here. Without this, an installed
         * fuze is invisible on the placed shell even though the data is present (goggle
         * tooltip shows it).
         */
        public static void onClientSetup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event) {
            dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer
                .builder(CreateBigCannonBlockEntityTypes.CUSTOM_EXPLOSIVE_MIX_SHELL.get())
                .factory(rbasamoyai.createbigcannons.munitions.big_cannon.FuzedBlockVisual::new)
                .apply();

            // Render the installed fuze on the shell ITEM model (inventory + in-hand), matching
            // CBC's own shells. CBC drives that via the model override property
            // createbigcannons:fuze_state (0 = none, 1 = head, 2 = base), but its generic function
            // gates on `stack.getItem() instanceof FuzedProjectileBlockItem` — and the shell item is
            // a MixedExplosiveBlockItem, so the generic returned 0 and the override never fired.
            // Register the same property id PER-ITEM (per-item registration wins over the generic),
            // minus the item-type gate; the block is still a FuzedProjectileBlock so isBaseFuze works.
            event.enqueueWork(() -> {
                final float fuzedState = CreateBigCannonsBlocks.CUSTOM_EXPLOSIVE_MIX_SHELL.get().isBaseFuze() ? 2.0F : 1.0F;
                net.minecraft.client.renderer.item.ItemProperties.register(
                    CreateBigCannonsBlocks.CUSTOM_EXPLOSIVE_MIX_SHELL.asItem(),
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("createbigcannons", "fuze_state"),
                    (stack, level, entity, seed) ->
                        stack.getOrDefault(rbasamoyai.createbigcannons.index.CBCDataComponents.FUZE,
                            net.minecraft.world.item.component.ItemContainerContents.EMPTY).copyOne().isEmpty()
                            ? 0.0F : fuzedState);
            });
        }
    }
}
