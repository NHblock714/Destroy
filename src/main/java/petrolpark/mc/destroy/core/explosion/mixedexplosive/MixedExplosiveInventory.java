package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import net.minecraft.util.Mth;
import net.minecraft.world.item.FireworkStarItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosiveProperty;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosivePropertyCondition;

/**
 * {@link ItemStackHandler} subclass that stores the ingredient stacks of a Mixed Explosive and
 * computes the aggregate {@link ExplosiveProperties} from them.
 *
 * <p><b>Behavior summary</b>:</p>
 * <ul>
 * <li>Slot-limited to 1 item per slot ({@link #getSlotLimit} returns 1).</li>
 * <li>{@link #isItemValid} restricts insertion to items with registered {@link
 * ExplosiveProperties#ITEM_EXPLOSIVE_PROPERTIES explosive properties} (loaded by
 * {@link ExplosiveProperties.Listener} on data-pack reload).</li>
 * <li>{@link #getExplosiveProperties} sums per-item property values (clamped to [-10, 10]) +
 * applies the ctor-supplied {@link ExplosivePropertyCondition conditions}.</li>
 * <li>{@link #getSpecialItems} filters for stacks whose item implements {@link
 * ISpecialEffectExplosiveItem} or vanilla {@code FireworkStarItem} — driven by
 * future T2b {@code CustomExplosiveMixExplosion}.</li>
 * </ul>
*/
public class MixedExplosiveInventory extends ItemStackHandler {

    protected ExplosivePropertyCondition[] conditions;

    /** Fired whenever a slot changes (menu edits). The owning BlockEntity wires this to
     * mark itself dirty + re-sync, so the client copy stays current for pick-block and the
     * contents are saved. Defaults to a no-op for item-side / detached inventories.*/
    protected Runnable onChanged = () -> {};

    public MixedExplosiveInventory(int size, ExplosivePropertyCondition... conditions) {
        super(size);
        this.conditions = conditions;
    }

    public MixedExplosiveInventory withChangeCallback(Runnable onChanged) {
        this.onChanged = onChanged;
        return this;
    }

    @Override
    protected void onContentsChanged(int slot) {
        super.onContentsChanged(slot);
        onChanged.run();
    }

    public static boolean canBeAdded(ItemStack stack) {
        return ExplosiveProperties.ITEM_EXPLOSIVE_PROPERTIES.get(stack.getItem()) != null; // Must have explosive properties
    }

    public ExplosiveProperties getExplosiveProperties() {
        ExplosiveProperties properties = new ExplosiveProperties();
        for (int slot = 0; slot < getSlots(); slot++) {
            ItemStack stack = getStackInSlot(slot);
            ExplosiveProperties itemProperties = ExplosiveProperties.ITEM_EXPLOSIVE_PROPERTIES.getOrDefault(stack.getItem(), new ExplosiveProperties());
            for (ExplosiveProperty property : ExplosiveProperty.values()) properties.merge(property, itemProperties.get(property), (e1, e2) -> {
                e1.value += e2.value;
                return e1;
            });
        }
        properties.forEach((ep, e) -> e.value = Mth.clamp(e.value, -10f, 10f));
        return properties.withConditions(conditions);
    }

    public boolean isEmpty() {
        return stacks.isEmpty() || stacks.stream().allMatch(ItemStack::isEmpty);
    }

    /**
 * Items which have special behaviour when exploded.
 *
 * @return list of stacks whose item is either vanilla {@code FireworkStarItem} or implements
 * {@link ISpecialEffectExplosiveItem}
*/
    public List<ItemStack> getSpecialItems() {
        return stacks.stream().filter(s -> s.getItem() instanceof FireworkStarItem || s.getItem() instanceof ISpecialEffectExplosiveItem).toList();
    }

    @Override
    public final boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return canBeAdded(stack);
    }

    @Override
    public final int getSlotLimit(int slot) {
        return 1;
    }
}
