package petrolpark.mc.destroy.content.product;

import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyAttributes;
import petrolpark.mc.destroy.config.DestroyAllConfigs;

/**
 * CREATINE — 吃下后永久增加 EXTRA_INVENTORY_SIZE / EXTRA_HOTBAR_SLOTS 属性（值由 server 配置读）。
 *
 * <ul>
 * <li><b>{@link AttributeModifier} 构造签名改</b>：{@code (UUID, String, double, Operation)} →
 * {@code (ResourceLocation, double, Operation)}。name 字段并入 ResourceLocation。两个
 * modifier 的 UUID 改为 {@code destroy:extra_inventory_size_modifier} /
 * {@code destroy:extra_hotbar_slots_modifier}——人读、稳定、与 modifier 所修改的 attribute 一致命名。</li>
 * <li><b>{@code Operation.ADDITION} → {@code Operation.ADD_VALUE}</b>（枚举名 rename）。</li>
 * <li><b>{@link AttributeInstance#removeModifier removeModifier(UUID)} → {@code removeModifier(ResourceLocation)}</b>：
 * key 从 UUID 换到 ResourceLocation；两个方法签名都存在，只是 UUID 版已废弃。</li>
 * <li><b>{@code addPermanentModifier(AttributeModifier)}</b> 仍可用，sync 逻辑 1:1 保留
 * （{@link ClientboundUpdateAttributesPacket} + {@code AttributeMap.getSyncableAttributes()}）。</li>
 * <li>{@code use()} / {@code finishUsingItem()} 签名未变。</li>
 * </ul>
*/
public class CreatineItem extends Item {

    public static final ResourceLocation EXTRA_INVENTORY_MODIFIER_ID = Destroy.asResource("extra_inventory_size_modifier");
    public static final ResourceLocation EXTRA_HOTBAR_MODIFIER_ID = Destroy.asResource("extra_hotbar_slots_modifier");

    public CreatineItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!player.isCreative() && player.getFoodData().getFoodLevel() > 6) {
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }
        return super.use(level, player, hand);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        if (!level.isClientSide) {
            if (livingEntity.getAttributes().hasAttribute(DestroyAttributes.EXTRA_INVENTORY_SIZE)) {
                AttributeInstance extraInventory = livingEntity.getAttribute(DestroyAttributes.EXTRA_INVENTORY_SIZE);
                if (extraInventory != null) {
                    extraInventory.removeModifier(EXTRA_INVENTORY_MODIFIER_ID);
                    extraInventory.addPermanentModifier(new AttributeModifier(
                        EXTRA_INVENTORY_MODIFIER_ID,
                        DestroyAllConfigs.SERVER.substances.creatineExtraInventorySize.get(),
                        AttributeModifier.Operation.ADD_VALUE));
                }
            }
            if (livingEntity.getAttributes().hasAttribute(DestroyAttributes.EXTRA_HOTBAR_SLOTS)) {
                AttributeInstance extraHotbar = livingEntity.getAttribute(DestroyAttributes.EXTRA_HOTBAR_SLOTS);
                if (extraHotbar != null) {
                    extraHotbar.removeModifier(EXTRA_HOTBAR_MODIFIER_ID);
                    extraHotbar.addPermanentModifier(new AttributeModifier(
                        EXTRA_HOTBAR_MODIFIER_ID,
                        DestroyAllConfigs.SERVER.substances.creatineExtraHotbarSlots.get(),
                        AttributeModifier.Operation.ADD_VALUE));
                }
            }
            if (livingEntity instanceof ServerPlayer serverPlayer) {
                serverPlayer.connection.send(new ClientboundUpdateAttributesPacket(
                    serverPlayer.getId(),
                    serverPlayer.getAttributes().getSyncableAttributes()));
                // Apply the new attribute values to the ExtendedInventory data layer.
                // addPermanentModifier just changes the AttributeMap; it doesn't notify the
                // ExtendedInventory subclass, so extraItems list still has the OLD size. Without
                // this updateSize call, the player's inventory data structure stays at 0 extras
                // even though the attribute now reads e.g. 9. updateSize(true) re-reads the
                // attribute, resizes extraItems, rebuilds inventoryMenu, and
                // broadcasts the size-change packet to the client.
                petrolpark.mc.destroy.core.extendedinventory.ExtendedInventory.get(serverPlayer)
                    .updateSize(true);
            }
        }
        return super.finishUsingItem(stack, level, livingEntity);
    }
}
