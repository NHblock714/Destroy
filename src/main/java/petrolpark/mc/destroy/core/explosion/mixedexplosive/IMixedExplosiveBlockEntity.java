package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.foundation.item.ItemHelper;
import com.simibubi.create.foundation.item.ItemHelper.ExtractionCountMode;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;

import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosivePropertyCondition;

/**
 * BlockEntity contract for mixed-explosive blocks — holds a {@link MixedExplosiveInventory} +
 * exposes it for {@link MixedExplosiveMenu} + ItemStack roundtrip ({@link #onPlace} writes stack's
 * inventory into BE on placement, {@link #getFilledItemStack} writes BE inventory back for
 * pick-block / drop). Extends {@link MenuProvider} so Block.use can
 * {@code player.openMenu(be, buffer-writer)} + {@link ClipboardCloneable} so Create's Clipboard
 * can copy inventory between BEs.
*/
public interface IMixedExplosiveBlockEntity extends MenuProvider, ClipboardCloneable {

    // ---------- Abstract contract ----------

    MixedExplosiveInventory getExplosiveInventory();

    void setExplosiveInventory(MixedExplosiveInventory inv);

    ExplosivePropertyCondition[] getApplicableExplosionConditions();

    default String getExplosivePropertyDescriptionTranslationKeySuffix() {
        return "";
    }

    // ---------- ItemStack ↔ BE inventory roundtrip ----------

    /**
 * Called from {@code MixedExplosiveBlock.setPlacedBy} — reads the placing stack's
 * {@link DestroyDataComponents#EXPLOSIVE_MIX EXPLOSIVE_MIX} payload into this BE's inventory.
*/
    default void onPlace(ItemStack blockItemStack, HolderLookup.Provider provider) {
        if (blockItemStack.getItem() instanceof IMixedExplosiveItem customMixItem) {
            setExplosiveInventory(customMixItem.getExplosiveInventory(blockItemStack, provider));
        }
    }

    /**
 * Called from {@code MixedExplosiveBlock.getCloneItemStack} / {@code getDrops} — writes this
 * BE's inventory into the provided empty stack's EXPLOSIVE_MIX payload + returns it.
*/
    default ItemStack getFilledItemStack(ItemStack emptyItemStack, HolderLookup.Provider provider) {
        if (emptyItemStack.getItem() instanceof IMixedExplosiveItem customMixItem) {
            customMixItem.setExplosiveInventory(emptyItemStack, getExplosiveInventory(), provider);
        }
        return emptyItemStack;
    }

    // ---------- MenuProvider ----------

    /**
 * MixedExplosiveMenu.create constructs the container-menu for this BE.
*/
    @Override
    default AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return MixedExplosiveMenu.create(containerId, playerInventory, this);
    }

    // ---------- Menu spawn-packet writer ----------

    /**
 * Serialize the BE's state into a menu spawn-packet buffer. Called by Block.use via
 * {@code player.openMenu(this, this::writeToBuffer)}. 1.21 {@link RegistryFriendlyByteBuf}
 * required for Component encoding.
*/
    default void writeToBuffer(RegistryFriendlyByteBuf buffer) {
        // ComponentSerialization.STREAM_CODEC encodes Component directly on RegistryFriendlyByteBuf.
        net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(buffer, getDisplayName());
        MixedExplosiveInventory inv = getExplosiveInventory();
        buffer.writeVarInt(inv.getSlots());
        buffer.writeNbt(inv.serializeNBT(buffer.registryAccess()));
        ExplosivePropertyCondition[] conditions = getApplicableExplosionConditions();
        buffer.writeVarInt(conditions.length);
        for (ExplosivePropertyCondition c : conditions) {
            buffer.writeResourceLocation(c.rl);
        }
    }

    // ---------- ClipboardCloneable ----------

    @Override
    default String getClipboardKey() {
        return "CustomExplosiveMix";
    }

    /** Any leftover goes back to the player. {@link ItemHandlerHelper}/{@link ItemStackHandler}/{@link PlayerMainInvWrapper}
 * all use NeoForge 1.21 paths.
*/
    @Override
    default boolean readFromClipboard(HolderLookup.Provider provider, CompoundTag tag, Player player, Direction side, boolean simulate) {
        if (!tag.contains("TargetInventory")) return false;
        if (simulate) return true;

        // TODO check if player is creative

        MixedExplosiveInventory inv = getExplosiveInventory();
        int invSize = inv.getSlots();

        List<ItemStack> oldItems = new ArrayList<>(invSize);
        for (int slot = 0; slot < invSize; slot++) {
            oldItems.add(inv.getStackInSlot(slot));
            inv.setStackInSlot(slot, ItemStack.EMPTY); // Clear the Inventory
        }

        ItemStackHandler targetInventory = new ItemStackHandler();
        targetInventory.deserializeNBT(provider, tag.getCompound("TargetInventory"));

        List<ItemStack> leftoverItems = new ArrayList<>();

        tryAddEachItem: for (int slot = 0; slot < targetInventory.getSlots(); slot++) {
            ItemStack targetStack = targetInventory.getStackInSlot(slot);
            if (targetStack.isEmpty()) continue tryAddEachItem;

            // Adding from existing Stacks
            for (ItemStack availableStack : oldItems) {
                if (ItemStack.isSameItemSameComponents(availableStack, targetStack)) {
                    int inserted = Math.max(availableStack.getCount(), targetStack.getCount());
                    ItemStack insertedStack = availableStack.copy();
                    insertedStack.setCount(inserted);

                    ItemStack leftoverStack = ItemHandlerHelper.insertItem(inv, insertedStack, false);
                    if (!leftoverStack.isEmpty()) {
                        leftoverItems.add(leftoverStack);
                        continue tryAddEachItem;
                    }

                    availableStack.shrink(inserted);
                    targetStack.shrink(inserted);
                    if (targetStack.isEmpty()) continue tryAddEachItem;
                }
            }

            // Adding from the Player's inventory
            ItemStack extractedStack = ItemHelper.extract(
                new PlayerMainInvWrapper(player.getInventory()),
                s -> ItemStack.isSameItemSameComponents(s, targetStack),
                ExtractionCountMode.UPTO,
                targetStack.getCount(),
                false);
            ItemStack leftoverStack = ItemHandlerHelper.insertItem(inv, extractedStack, false);
            if (!leftoverStack.isEmpty()) {
                leftoverItems.add(leftoverStack);
                continue tryAddEachItem;
            }
        }

        // Give any remaining Items back to the Player
        for (ItemStack remainingStack : oldItems) if (!remainingStack.isEmpty()) player.getInventory().placeItemBackInInventory(remainingStack, true);
        for (ItemStack leftoverStack : leftoverItems) player.getInventory().placeItemBackInInventory(leftoverStack, true);

        return true;
    }

    @Override
    default boolean writeToClipboard(HolderLookup.Provider provider, CompoundTag tag, @Nullable Direction side) {
        tag.put("TargetInventory", getExplosiveInventory().serializeNBT(provider));
        return true;
    }
}
