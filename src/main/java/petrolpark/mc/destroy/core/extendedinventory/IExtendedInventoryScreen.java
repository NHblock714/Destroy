package petrolpark.mc.destroy.core.extendedinventory;

import java.util.List;

import net.minecraft.client.renderer.Rect2i;

import petrolpark.mc.destroy.config.DestroyClientConfigs;

/**
 * Marker + render-config interface for {@link net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
 * Screens} that have special support for rendering the {@link ExtendedInventory}. The Menu
 * associated with this Screen should implement {@link IExtendedInventoryMenu}.
 *
 * <p><b>Usage</b>: any custom Screen that wants to control where the Extended-Inventory hotbar /
 * inventory "windows" render relative to its own GUI implements this interface. The Screen returns
 * 4 {@link Rect2i} regions describing where each window should sit; the
 * {@code ExtendedInventoryClientHandler} consumes those regions during
 * {@code ScreenEvent.Render.Pre}. Returning {@code null} from any method skips that window.
 * Returning {@code true} from {@link #customExtendedInventoryRendering()} suppresses the default
 * background + slot rendering entirely (Screen renders everything itself).</p>
*/
public interface IExtendedInventoryScreen {

    /**
 * Whether the rendering of the {@link ExtendedInventory} is entirely overridden. If this
 * returns {@code false}, the backgrounds and Slot backgrounds will be rendered as usual
 * (e.g. as in vanilla Chests). This method should be effectively static.
 * @return {@code true} if you handle rendering all Extended-Inventory things in the
 * appropriate render method of this Screen.
*/
    public default boolean customExtendedInventoryRendering() {
        return false;
    }

    /**
 * Get the location of the "window" which shows the left-hand-side hotbar Slots of the
 * Extended Inventory, including the border around the Slots. Called when
 * {@link #customExtendedInventoryRendering()} returns {@code false}. Should match the spec
 * of the static {@code ExtendedInventoryClientHandler.getLeftHotbarLocation} method.
 * Re-evaluated on screen open + whenever {@code refreshClientInventoryMenu} fires (so call
 * that whenever the location should change; you may also need to update the Slots in the
 * Menu attached to this Screen).
 *
 * @param extendedInventory The Player's Extended Inventory.
 * @param leftHotbarSlots Number of hotbar Slots to render to the left of the vanilla
 * hotbar, per {@link DestroyClientConfigs}.
 * @param renderMainInventoryLeft Whether the non-hotbar Inventory Slots render to the left
 * of the vanilla Inventory.
 * @return Area in which to render the background of this section of the Extended Inventory,
 * or {@code null} if it should not be rendered.
*/
    public Rect2i getLeftHotbarLocation(ExtendedInventory extendedInventory, int leftHotbarSlots, boolean renderMainInventoryLeft);

    /**
 * Get the location of the "window" which shows the right-hand-side hotbar Slots of the
 * Extended Inventory, including the border around the Slots. Symmetric counterpart of
 * {@link #getLeftHotbarLocation}.
 *
 * @return Area in which to render the background, or {@code null} if it should not be rendered.
*/
    public Rect2i getRightHotbarLocation(ExtendedInventory extendedInventory, int leftHotbarSlots, boolean renderMainInventoryLeft);

    /**
 * Get the location of the "window" which shows the Slots of the Extended Inventory not on
 * the hotbar, including the border around the Slots.
 *
 * @return Area in which to render the background, or {@code null} if it should not be rendered.
*/
    public Rect2i getInventoryLocation(ExtendedInventory extendedInventory, int leftHotbarSlots, boolean renderMainInventoryLeft);

    /**
 * Get the location of the combined "window" containing both the appropriate-side hotbar
 * Slots and the non-hotbar Slots of the Extended Inventory, when they merge into one panel
 * (vertical layout where the inventory has enough rows to butt up against the hotbar).
 *
 * @return Area in which to render the background, or {@code null} if separate windows should
 * render instead.
*/
    public Rect2i getCombinedInventoryHotbarLocation(ExtendedInventory extendedInventory, int leftHotbarSlots, boolean renderMainInventoryLeft);

    /**
 * Get any portion of the screen obscured by the Extended Inventory being rendered, so JEI
 * will not render anything there. May be empty if JEI extra GUI areas are handled elsewhere.
*/
    public List<Rect2i> getExtendedInventoryGuiAreas(ExtendedInventory extendedInventory);
}
