package petrolpark.mc.destroy.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.ItemPickerMenu;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Keeps the creative cursor attached to the current inventory menu after a slot-count change. */
@Mixin(ItemPickerMenu.class)
public abstract class CreativeItemPickerMenuMixin {

    @ModifyExpressionValue(method = {"getCarried", "setCarried"}, at = @At(value = "FIELD",
        target = "Lnet/minecraft/client/gui/screens/inventory/CreativeModeInventoryScreen$ItemPickerMenu;inventoryMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;"))
    private AbstractContainerMenu destroy$currentInventoryMenu(AbstractContainerMenu original) {
        var player = Minecraft.getInstance().player;
        return player == null ? original : player.inventoryMenu;
    }
}
