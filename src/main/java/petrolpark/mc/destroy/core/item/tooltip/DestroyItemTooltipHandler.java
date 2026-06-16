package petrolpark.mc.destroy.core.item.tooltip;

import java.util.IdentityHashMap;
import java.util.Map;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.KineticStats;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper.Palette;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Game-bus handler that applies Destroy's tooltip modifier chain to every item at tooltip-render
 * time, building a per-item {@link TooltipModifier} on first sighting and caching it.
 *
 * <p>The chain:
 * <ul>
 * <li>base {@link ItemDescription.Modifier}（Create 的 lang-key-driven tooltip 解析：shift = summary + behaviours, ctrl = actions）</li>
 * <li>{@link KineticStats} — Create kinetic stats for items that carry rotation metadata</li>
 * <li>{@link IDynamicItemDescription} — Destroy items that override tooltip based on live config</li>
 * <li>{@link TempramentalItemDescription} — red "subject to change" notice for LIABLE_TO_CHANGE tagged items</li>
 * </ul>
*/
@EventBusSubscriber
public class DestroyItemTooltipHandler {

    private static final Map<Item, TooltipModifier> CACHE = new IdentityHashMap<>();

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        // null-player guard. ItemTooltipEvent fires from search-tree-build paths
        // (SessionSearchTrees.updateCreativeTooltips, JEI indexing) where event.getEntity() is null.
        // Create's KineticStats.getKineticStats → GogglesItem.isWearingGoggles dereferences player
        // without a null check → NPE. Skip the entire chain when there's no player context;
        // search-tree text indexing doesn't need the extra tooltip lines anyway.
        if (event.getEntity() == null) return;
        Item item = event.getItemStack().getItem();
        // only apply the chain to Destroy-namespaced items. Without this filter the
        // event handler runs for EVERY mod's items including Create + Create-addons that already
        // register their own ItemDescription.Modifier chain via CreateRegistrate, producing a
        // duplicate "Hold [Shift]" expand line + duplicate body (symptom: items from other Create
        // mods rendered the expand hint twice).
        net.minecraft.resources.ResourceLocation id =
            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        if (id == null || !"destroy".equals(id.getNamespace())) return;
        TooltipModifier modifier = CACHE.computeIfAbsent(item, DestroyItemTooltipHandler::buildModifier);
        modifier.modify(event);
    }

    private static TooltipModifier buildModifier(Item item) {
        return new ItemDescription.Modifier(item, Palette.STANDARD_CREATE)
            .andThen(TooltipModifier.mapNull(KineticStats.create(item)))
            .andThen(TooltipModifier.mapNull(IDynamicItemDescription.create(item)))
            .andThen(new TempramentalItemDescription());
    }
}
