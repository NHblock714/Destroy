package petrolpark.mc.destroy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import petrolpark.mc.destroy.DestroyItems;

/**
 * Lets the Cartography Table turn a Map and a Seismometer into a Seismograph. The vanilla Menu
 * accepts only paper, a glass pane or a map in the second slot, so without this the Seismometer is
 * rejected and no Seismograph can be made.
 */
@Mixin(CartographyTableMenu.class)
public abstract class CartographyTableMenuMixin extends AbstractContainerMenu {

    @Shadow
    private final ContainerLevelAccess access;

    @Shadow
    long lastSoundTime;

    @Shadow
    public final Container container;

    @Shadow
    private final ResultContainer resultContainer;

    protected CartographyTableMenuMixin(MenuType<?> pMenuType, int pContainerId) {
        super(pMenuType, pContainerId);
        throw new AssertionError(); // Should never be called
    }

    @Inject(
        method = "<init>*",
        at = @At("RETURN")
    )
    public void inInit(CallbackInfo ci) {

        // Remove the old input slot (slot 1), which cannot accept Seismometers
        slots.remove(1);

        // Create a new input slot which can accept Seismometers
        Slot inputSlot = new Slot(container, 1, 15, 52) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(Items.PAPER) || stack.is(Items.MAP) || stack.is(Items.GLASS_PANE)
                    || DestroyItems.SEISMOMETER.isIn(stack);
            }
        };
        inputSlot.index = 1;
        slots.add(1, inputSlot);

        // Remove the old output slot (slot 2)
        slots.remove(2);

        // Create a new output slot which will not consume the Seismometer
        Slot outputSlot = new Slot(resultContainer, 2, 145, 39) {

            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            /**
 * Copied from {@link net.minecraft.world.inventory.CartographyTableMenu Minecraft source code}.
*/
            @Override
            public void onTake(Player player, ItemStack stack) {
                slots.get(0).remove(1);
                if (!DestroyItems.SEISMOMETER.isIn(slots.get(1).getItem())) slots.get(1).remove(1);
                stack.getItem().onCraftedBy(stack, player.level(), player); // Scale or lock the Map if necessary
                access.execute((level, pos) -> {
                    long l = level.getGameTime();
                    if (lastSoundTime != l) {
                        level.playSound((Player) null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT,
                            SoundSource.BLOCKS, 1f, 1f);
                        lastSoundTime = l;
                    }
                });
                super.onTake(player, stack);
            }
        };
        outputSlot.index = 2;
        slots.add(2, outputSlot);
    }

    @Inject(
        method = "Lnet/minecraft/world/inventory/CartographyTableMenu;lambda$setupResultSlot$0(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;is(Lnet/minecraft/world/item/Item;)Z",
            ordinal = 0
        ),
        cancellable = true,
        locals = LocalCapture.CAPTURE_FAILSOFT
    )
    public void inSetupResultSlot(ItemStack map, ItemStack firstSlotStack, ItemStack resultItem,
                                  Level level, BlockPos pos, CallbackInfo ci, MapItemSavedData mapData) {
        if (DestroyItems.SEISMOMETER.isIn(firstSlotStack)) {
            ItemStack stack = DestroyItems.SEISMOGRAPH.asStack();
            // Copy the source Map's MAP_ID onto the Seismograph so vanilla map rendering draws the
            // right map underneath it.
            MapId mapId = map.get(DataComponents.MAP_ID);
            if (mapId != null) {
                stack.set(DataComponents.MAP_ID, mapId);
            }
            if (!ItemStack.matches(resultItem, stack)) {
                resultContainer.setItem(2, stack);
                broadcastChanges();
                ci.cancel();
            }
        }
    }

    @Inject(
        method = "quickMoveStack",
        at = @At("HEAD"),
        cancellable = true
    )
    public void inQuickMoveStack(Player player, int index, CallbackInfoReturnable<ItemStack> cir) {
        Slot slot = slots.get(index);
        if (slot == null) return;
        ItemStack stack = slot.getItem();
        if (DestroyItems.SEISMOMETER.isIn(stack)) {
            ItemStack copy = stack.copy();
            if (index > 2 && moveItemStackTo(stack, 1, 2, false)) {
                if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
                slot.setChanged();
                if (stack.getCount() == copy.getCount()) {
                    cir.setReturnValue(ItemStack.EMPTY);
                    return;
                }
                slot.onTake(player, stack);
                broadcastChanges();
                cir.setReturnValue(copy);
            }
        }
    }
}
