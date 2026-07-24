package petrolpark.mc.destroy.content.product;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import petrolpark.mc.library.registry.PetrolparkDataComponentTypes;
import petrolpark.mc.library.core.world.item.decay.DecayTime;
import petrolpark.mc.library.core.world.item.decay.ItemDecay;
import petrolpark.mc.library.core.world.item.decay.product.ChangeItemDecayProduct;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 吸收空气中 CO₂ 缓慢碳化 —— 用于 QUICKLIME（生石灰）→ CHALK_DUST（石灰粉）的默认衰变。与
 * {@link OxidizingItem} 共享懒初始化 + petrolpark {@code ItemStackMixin.copy} 驱动的 checkDecay 架构，
 * 唯一区别是没有 water 瞬变分支（quicklime 遇水反应是另一条产出路径，不属本 item 的衰变语义）。
*/
public class CarboxylatingItem extends Item {

    private final Supplier<ItemStack> decayProduct;
    private final IntSupplier lifetimeTicks;
    private final String timeTranslationKey;

    public CarboxylatingItem(Properties properties, Supplier<ItemStack> decayProduct,
                             IntSupplier lifetimeTicks, String timeTranslationKey) {
        super(properties);
        this.decayProduct = decayProduct;
        this.lifetimeTicks = lifetimeTicks;
        this.timeTranslationKey = timeTranslationKey;
    }

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
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        ensureDecayComponents(stack);
        return super.onEntityItemUpdate(stack, entity);
    }
}
