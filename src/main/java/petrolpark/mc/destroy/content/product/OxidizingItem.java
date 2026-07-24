package petrolpark.mc.destroy.content.product;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import petrolpark.mc.library.registry.PetrolparkDataComponentTypes;
import petrolpark.mc.library.core.world.item.decay.DecayTime;
import petrolpark.mc.library.core.world.item.decay.ItemDecay;
import petrolpark.mc.library.core.world.item.decay.product.ChangeItemDecayProduct;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class OxidizingItem extends Item {

    private final Supplier<ItemStack> decayProduct;
    private final IntSupplier lifetimeTicks;
    private final String timeTranslationKey;

    public OxidizingItem(Properties properties, Supplier<ItemStack> decayProduct,
                         IntSupplier lifetimeTicks, String timeTranslationKey) {
        super(properties);
        this.decayProduct = decayProduct;
        this.lifetimeTicks = lifetimeTicks;
        this.timeTranslationKey = timeTranslationKey;
    }

    /**
 * Lazy-init the decay data components on the stack if they haven't been set yet. Called at
 * the top of each {@link #inventoryTick} / {@link #onEntityItemUpdate} so the stack reaches
 * tick-active state the first time it's handled. Also starts the decay clock.
*/
    private void ensureDecayComponents(ItemStack stack) {
        if (!stack.has(PetrolparkDataComponentTypes.DECAY_TIME)) {
            stack.set(PetrolparkDataComponentTypes.DECAY_TIME,
                new DecayTime(timeTranslationKey, lifetimeTicks.getAsInt()));
        }
        if (!stack.has(PetrolparkDataComponentTypes.DECAY_PRODUCT)) {
            stack.set(PetrolparkDataComponentTypes.DECAY_PRODUCT,
                new ChangeItemDecayProduct(decayProduct.get()));
        }
        if (!stack.has(PetrolparkDataComponentTypes.DECAY_START_TIME)) {
            ItemDecay.startDecay(stack);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        ensureDecayComponents(stack);
        checkForWater(stack, entity, isSelected);
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        ensureDecayComponents(stack);
        checkForWater(stack, entity, true);
        return super.onEntityItemUpdate(stack, entity);
    }

    /**
 * If the carrier is in water (or in rain while holding the stack in offhand / as an item entity),
 * collapse the remaining lifetime to 0 so the next {@code ItemStack.copy()} converts to the decay
 * product.
*/
    protected void checkForWater(ItemStack stack, Entity entity, boolean rainSensitive) {
        boolean submerged = entity.isInWaterOrBubble();
        boolean rainHit = entity.isInWaterRainOrBubble()
            && (rainSensitive || (entity instanceof LivingEntity le && le.getOffhandItem() == stack));
        if (!submerged && !rainHit) return;

        Long start = stack.get(PetrolparkDataComponentTypes.DECAY_START_TIME);
        if (start == null) return;
        long remaining = ItemDecay.getRemainingTime(stack, start);
        if (remaining <= 0) return;
        ItemDecay.extendLifetime(stack, (int) -remaining);
    }
}
