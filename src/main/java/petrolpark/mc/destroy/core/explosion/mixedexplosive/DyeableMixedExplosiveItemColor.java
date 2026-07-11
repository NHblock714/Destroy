package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import net.minecraft.client.color.item.ItemColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Client-side {@link ItemColor} for dyeable mixed-explosive item stacks — returns the stack's
 * {@link net.minecraft.core.component.DataComponents#DYED_COLOR} for {@code tintIndex == 0}, else
 * {@code -1} (no tint).
 *
 * <p><b>1.21 migration</b>: {@code ((DyeableLeatherItem)stack.getItem()).getColor(stack)} →
 * {@link DyedItemColor#getOrDefault DyedItemColor.getOrDefault(stack, 0xFFFFFF)}. Removes the
 * instanceof-dispatch entirely — any stack can now carry DYED_COLOR DataComponent.</p>
*/
public class DyeableMixedExplosiveItemColor implements ItemColor {

    public static final DyeableMixedExplosiveItemColor INSTANCE = new DyeableMixedExplosiveItemColor();

    @Override
    public int getColor(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) return -1;
        // Opaque white default (0xFF alpha). An undyed stack has no DYED_COLOR, and getOrDefault
        // returns this default verbatim — a bare 0xFFFFFF (alpha 0) tints the layer fully
        // transparent, so a freshly-crafted / creative-tab mix rendered see-through.
        return DyedItemColor.getOrDefault(stack, 0xFFFFFFFF);
    }
}
