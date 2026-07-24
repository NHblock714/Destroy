package petrolpark.mc.destroy.mixin.client;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import petrolpark.mc.destroy.client.ExtendedInventoryClientHandler;
import petrolpark.mc.destroy.core.extendedinventory.ExtendedInventory;

/**
 * Creative-mode inventory screen integration for {@link ExtendedInventory}'s extra slots. The
 * survival inventory tab of the Creative menu is the only Creative tab that should show extra
 * slots — when the player switches to it, this mixin:
 *
 * <ol>
 * <li><b>{@code addLimitedSlots} (`@WrapOperation`)</b>: caps the iteration in {@code selectTab}
 * at 46 (vanilla survival-tab slot count: 1 destroyItemSlot + 45 inventory grid). Without
 * this, vanilla's tab-switch loop would try to manage every menu slot — including extras —
 * through its quick-move logic, which doesn't know about extended-inventory slot indexes.</li>
 * <li><b>{@code inSelectTab} (`@Inject` at FIELD destroyItemSlot)</b>: when the survival tab
 * activates, walks the player's {@code inventoryMenu.slots} to find the extra-slot
 * {@link Slot} objects (which were added via
 * {@link ExtendedInventory#refreshPlayerInventoryMenu refreshPlayerInventoryMenu}), then
 * wraps each in a {@code CreativeModeInventoryScreen.SlotWrapper} (Creative menu's
 * interaction-bridging shim) and adds them to the Creative menu via
 * {@link ExtendedInventoryClientHandler#addSlotsToClientMenu}.</li>
 * <li><b>{@code inSlotClicked} (`@Inject` at HEAD)</b>: when the player quick-moves the
 * destroyItemSlot (vanilla "trash all" gesture), also clear all extra slots via
 * {@code gameMode.handleCreativeModeItemAdd(EMPTY, slotIndex)}.</li>
 * </ol>
 *
 * <p>Registered via {@code destroy.mixins.json} {@code "mixins"} array (client-side only via
 * {@code "client"} sub-array if needed; for now in main mixins array since the class is
 * {@code @OnlyIn(Dist.CLIENT)}-implicit by mixing into a client-only target).</p>
*/
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin extends EffectRenderingInventoryScreen<ItemPickerMenu> {

    @Shadow
    private Slot destroyItemSlot;

    /** Stub constructor — never invoked by Mixin AP, just satisfies the abstract-class
 * inheritance shape.*/
    public CreativeModeInventoryScreenMixin(ItemPickerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        throw new AssertionError();
    }

    /** Cap the slot-management iteration at 46 (vanilla survival-tab count) so vanilla's
 * tab-switch logic doesn't try to manage extra-inventory slots through its quick-move
 * path. Extra slots are added separately via {@link #destroy$addExtendedInventorySlots}.*/
    @WrapOperation(
        method = "selectTab",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/NonNullList;size()I"
        )
    )
    public int destroy$capSlotIteration(NonNullList<Slot> slots, Operation<Integer> original) {
        return 46;
    }

    /** When the survival tab activates: pull extra-slot Slot objects from the player's
 * inventoryMenu (added via refreshPlayerInventoryMenu), wrap each in a
 * CreativeModeInventoryScreen.SlotWrapper, and register them with the Creative menu so
 * they receive click events through the same path as vanilla survival slots.*/
    @Inject(
        method = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;selectTab(Lnet/minecraft/world/item/CreativeModeTab;)V",
        at = @At(
            value = "FIELD",
            target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;destroyItemSlot:Lnet/minecraft/world/inventory/Slot;",
            ordinal = 0
        )
    )
    public void destroy$addExtendedInventorySlots(CreativeModeTab tab, CallbackInfo ci) {
        Minecraft mc = getMinecraft();
        if (mc == null) return;
        LocalPlayer player = mc.player;
        if (player == null) return;
        ExtendedInventory inv;
        try {
            inv = ExtendedInventory.get(player);
        } catch (ClassCastException ex) { return; }
        // Collect the extra-slot Slot objects from the player's vanilla inventoryMenu (where they
        // live thanks to refreshPlayerInventoryMenu). Index by container slot index since
        // the menu-slot index won't match the Inventory's container index in general.
        Int2ObjectMap<Slot> extendedInventorySlots = new Int2ObjectArrayMap<>();
        for (Slot slot : player.inventoryMenu.slots) {
            if (slot.getSlotIndex() >= inv.getExtraInventoryStartSlotIndex()) {
                extendedInventorySlots.put(slot.getSlotIndex(), slot);
            }
        }
        // Mark this Creative screen as the current extended-inventory-aware screen + add slots.
        // SlotWrapper signature in 1.21: SlotWrapper(Slot original, int newIndex, int x, int y).
        ExtendedInventoryClientHandler.setCurrentScreen(this);
        ExtendedInventoryClientHandler.refreshExtraInventoryAreas(inv);
        ExtendedInventoryClientHandler.addSlotsToClientMenu(inv, menu::addSlot,
            (container, index, x, y) -> {
                Slot target = extendedInventorySlots.get(index);
                // SlotWrapper reads the wrapped Slot's container in its super() call, so it can't be
                // handed a null. The inventoryMenu it comes from is rebuilt whenever the Player is,
                // and this Screen can't rebuild it here — the Creative menu already claimed
                // containerMenu. Back the wrapper with an equivalent Slot over the same container.
                if (target == null) target = new Slot(container, index, x, y);
                return new CreativeModeInventoryScreen.SlotWrapper(target, index, x, y);
            });
    }

    /** Quick-move on destroyItemSlot (the trash icon) → also clear all extra-inventory slots.*/
    @Inject(
        method = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen;slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ClickType;)V",
        at = @At("HEAD")
    )
    protected void destroy$clearExtendedInventoryOnTrash(@Nullable Slot slot, int slotId,
                                                         int mouseButton, ClickType type, CallbackInfo ci) {
        if (slot != destroyItemSlot || type != ClickType.QUICK_MOVE) return;
        Minecraft mc = getMinecraft();
        if (mc == null) return;
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) return;
        ExtendedInventory inv;
        try {
            inv = ExtendedInventory.get(player);
        } catch (ClassCastException ex) { return; }
        for (Slot inventorySlot : menu.slots) {
            if (inventorySlot.getSlotIndex() >= inv.getExtraInventoryStartSlotIndex()) {
                gameMode.handleCreativeModeItemAdd(ItemStack.EMPTY, inventorySlot.index);
            }
        }
    }
}
