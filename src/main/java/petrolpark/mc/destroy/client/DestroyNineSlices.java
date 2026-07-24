package petrolpark.mc.destroy.client;

import petrolpark.mc.library.core.client.rendering.PetrolparkNineSlice;

/**
 * Catalog of {@link PetrolparkNineSlice 9-slice} render helpers built from {@link DestroyGuiTextures}
 * sprites. Used for Extended-Inventory panel + extended-hotbar borders that need to scale
 * dynamically with slot count.
 *
 * <p><b>Slice borders (left, right, top, bottom)</b>:</p>
 * <ul>
 * <li>{@link #INVENTORY_BACKGROUND} — 4/5/4/5 (the 9×9 sprite has 4-px-wide left/top borders +
 * 5-px-wide right/bottom borders, scales the 1×1 centre tile to fill).</li>
 * <li>{@link #HOTBAR} — 8/14/8/14 (the 22×22 sprite has 8-px borders on left/top + 14-px on
 * right/bottom, scales the centre to fill the variable-width hotbar background).</li>
 * </ul>
*/
public class DestroyNineSlices {

    public static final PetrolparkNineSlice
        INVENTORY_BACKGROUND = new PetrolparkNineSlice(DestroyGuiTextures.INVENTORY_BACKGROUND, 4, 5, 4, 5),
        HOTBAR = new PetrolparkNineSlice(DestroyGuiTextures.HOTBAR_BACKGROUND, 8, 14, 8, 14);
}
