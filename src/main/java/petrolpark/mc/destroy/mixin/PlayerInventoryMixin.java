package petrolpark.mc.destroy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.core.extendedinventory.ExtendedInventory;

/**
 * Replaces vanilla {@link Player#getInventory()} with the Destroy {@link ExtendedInventory}
 * subclass at Player {@code <init>} → RETURN, so every Player (server-side ServerPlayer + client
 * LocalPlayer + RemotePlayer) holds an ExtendedInventory in its {@code inventory} field for the
 * lifetime of the entity. This is what makes the Creatine consumable actually expand the inventory
 * — the {@link petrolpark.mc.destroy.DestroyAttributes#EXTRA_INVENTORY_SIZE EXTRA_INVENTORY_SIZE}
 * attribute modifier added by eating Creatine has no effect unless the Player's inventory is the
 * subclass that reads that attribute.
 *
 * <p><b>Why two injectors?</b></p>
 * <ol>
 * <li>{@code <init>} → RETURN: swaps the {@code inventory} field. Without this, every Player
 * gets a vanilla {@link Inventory} that ignores Destroy's extra-slot attributes.</li>
 * <li>{@code setItemSlot} → HEAD (cancellable): vanilla's setItemSlot for MAINHAND writes
 * directly to {@code inventory.items.set(selected, stack)}, bypassing
 * {@link ExtendedInventory#setItem} which is needed for the extra-slot redirection logic.
 * This intercepts, routes through {@code ExtendedInventory.setItem}, fires the equipment-change
 * hook ({@code onEquipItem}), and cancels the vanilla path. NON-MAINHAND slots
 * (offhand, armor) are left to vanilla — those paths don't touch the inventory items list.
 * </li>
 * </ol>
 *
 * <p>Registered via {@code destroy.mixins.json} {@code "mixins"} array.</p>
*/
@Mixin(Player.class)
public abstract class PlayerInventoryMixin extends LivingEntity {

    @Shadow public Inventory inventory;

    /** Stub constructor — LivingEntity is abstract so the mixin shape needs a matching ctor; the
 * mixin processor never actually invokes this (only the bytecode shape matters).*/
    protected PlayerInventoryMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    public void inInit(Level level, BlockPos pos, float yRot, GameProfile gameProfile, CallbackInfo ci) {
        ExtendedInventory extendedInv = new ExtendedInventory((Player) (Object) this);
        this.inventory = extendedInv;
        // CRITICAL FIX: rebuild player.inventoryMenu so its Slot objects reference
        // the NEW ExtendedInventory rather than the now-orphaned vanilla Inventory. Without
        // this, vanilla Player constructor's `this.inventoryMenu = new InventoryMenu(this.inventory, ...)`
        // captured the OLD inventory; subsequent click/place/break operations modify the
        // orphan inventory while player.getInventory() returns ExtendedInventory →
        // server-broadcast slot updates desync from actual player state → rubber-band on
        // every block place/break and missing model swaps for held items.
        // Default zero-coords overload — server doesn't render; client refreshes with proper
        // coords later via ExtendedInventoryClientHandler when a screen opens.
        ExtendedInventory.refreshPlayerInventoryMenu((Player) (Object) this);
    }

    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    public void inSetItemSlot(EquipmentSlot slot, ItemStack stack, CallbackInfo ci) {
        verifyEquippedItem(stack);
        if (slot == EquipmentSlot.MAINHAND) {
            ExtendedInventory inv = ExtendedInventory.get((Player) (Object) this);
            ItemStack oldStack = inv.getItem(inv.selected);
            inv.setItem(inv.selected, stack);
            onEquipItem(slot, oldStack, stack);
            ci.cancel();
        }
    }
}
