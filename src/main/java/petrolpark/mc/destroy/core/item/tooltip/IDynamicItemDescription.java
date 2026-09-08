package petrolpark.mc.destroy.core.item.tooltip;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Implemented by Items whose description is built at runtime instead of coming from fixed lang
 * keys like an ordinary Create Item's. The Modifier calls {@link #getItemDescription()} again
 * whenever the locale changes, so config values such as
 * {@code DestroyAllConfigs.SERVER.substances.*.get()} can be written into the tooltip.
 */
public interface IDynamicItemDescription {

    /** Returns a TooltipModifier that wraps this dynamic description, or null if item doesn't implement.*/
    static Modifier create(Item item) {
        if (item instanceof IDynamicItemDescription dynamic) return new Modifier(item, dynamic);
        return null;
    }

    ItemDescription getItemDescription();

    Palette getPalette();

    class Modifier extends ItemDescription.Modifier {

        private final IDynamicItemDescription itemWithDescription;

        public Modifier(Item item, IDynamicItemDescription itemWithDescription) {
            super(item, itemWithDescription.getPalette());
            this.itemWithDescription = itemWithDescription;
        }

        @Override
        public void modify(ItemTooltipEvent context) {
            if (checkLocale()) {
                description = itemWithDescription.getItemDescription();
            }
            if (description == null) return;
            context.getToolTip().addAll(1, description.getCurrentLines());
        }
    }
}
