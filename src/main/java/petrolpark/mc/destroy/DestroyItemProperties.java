package petrolpark.mc.destroy;

import net.minecraft.client.renderer.item.ItemProperties;

import petrolpark.mc.destroy.content.tool.swissarmyknife.SwissArmyKnifeItemRenderer;

/**
 * Registers custom {@code ItemPropertyFunction}s on Destroy items so the resource-pack model
 * selector can branch on per-stack float values. Called from the client setup event
 * ({@link petrolpark.mc.destroy.core.event.DestroyClientModEvents}) so the registration happens
 * on the correct thread (main thread, client-only).
 *
 * <p>Current registrations:</p>
 * <ul>
 * <li>{@code destroy:component} on {@link DestroyItems#SWISS_ARMY_KNIFE} —
 * {@link SwissArmyKnifeItemRenderer.RenderedTool#getItemProperty} reads
 * {@code DestroyDataComponents.RENDERED_TOOL} and returns the tool-ordinal / 8f so
 * the JSON model selector picks the right blade variant.</li>
 * </ul>
*/
public class DestroyItemProperties {

    public static void register() {
        ItemProperties.register(
            DestroyItems.SWISS_ARMY_KNIFE.get(),
            Destroy.asResource("component"),
            SwissArmyKnifeItemRenderer.RenderedTool::getItemProperty);
    }
}
