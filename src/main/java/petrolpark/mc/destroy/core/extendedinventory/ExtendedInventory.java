package petrolpark.mc.destroy.core.extendedinventory;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import petrolpark.mc.library.PetrolparkTags;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyAttributes;
import petrolpark.mc.destroy.config.DestroyConfigs;

/**
 * Extension of vanilla {@link Inventory} that adds N "extra" slots whose count is driven by the
 * Player's {@link DestroyAttributes#EXTRA_INVENTORY_SIZE} + {@link DestroyAttributes#EXTRA_HOTBAR_SLOTS}
 * attributes. The Creatine consumable item adds modifiers to these attributes → eating Creatine
 * permanently expands the inventory.
*/
@EventBusSubscriber(modid = Destroy.MOD_ID)
public class ExtendedInventory extends Inventory {

    public NonNullList<ItemStack> extraItems = NonNullList.of(ItemStack.EMPTY);
    private int extraHotbarSlots = 0;

    public ExtendedInventory(Player player) {
        super(player);
        updateSize();
    }

    /** @return The Player's Extended Inventory (cast from vanilla {@code player.getInventory()}).*/
    public static ExtendedInventory get(Player player) {
        return (ExtendedInventory) player.getInventory();
    }

    public void updateSize() {
        updateSize(false);
    }

    /**
 * Re-read the Player's {@link DestroyAttributes#EXTRA_INVENTORY_SIZE} +
 * {@link DestroyAttributes#EXTRA_HOTBAR_SLOTS} attributes; resize {@link #extraItems} +
 * {@link #extraHotbarSlots} accordingly.
*/
    public void updateSize(boolean forceSync) {
        int sizeBefore = extraItems.size();
        int hotbarBefore = extraHotbarSlots;
        // 1.21: hasAttribute / getAttributeValue take Holder<Attribute>. DeferredHolder IS a Holder<Attribute> — pass it directly, no .get() needed.
        if (player.getAttributes().hasAttribute(DestroyAttributes.EXTRA_HOTBAR_SLOTS)) {
            setExtraHotbarSlots((int) player.getAttributeValue(DestroyAttributes.EXTRA_HOTBAR_SLOTS));
        }
        if (player.getAttributes().hasAttribute(DestroyAttributes.EXTRA_INVENTORY_SIZE)) {
            setExtraInventorySize((int) player.getAttributeValue(DestroyAttributes.EXTRA_INVENTORY_SIZE));
        }
        boolean changed = sizeBefore != extraItems.size() || hotbarBefore != extraHotbarSlots;
        if ((forceSync || changed)
            && !player.level().isClientSide()
            && player instanceof net.minecraft.server.level.ServerPlayer sp
            && sp.connection != null) {
            // rebuild the server-side inventoryMenu so its slot count matches the new
            // extras count. This makes broadcastFullState send extra-slot contents on the next
            // open. Default zero coords (server doesn't render).
            if (changed) refreshPlayerInventoryMenu(player);
            net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClient(sp,
                new ExtraInventorySizeChangeS2CPacket(extraItems.size(), extraHotbarSlots, false));
        }
    }

    public void setExtraInventorySize(int size) {
        size = Math.max(size, 0);
        if (size == extraItems.size()) return;
        if (size < extraItems.size()) {
            for (int stack = size; stack < extraItems.size(); stack++) {
                player.drop(extraItems.get(stack), false);
            }
        }
        NonNullList<ItemStack> newExtraItems = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int i = 0; i < size && i < extraItems.size(); i++) newExtraItems.set(i, extraItems.get(i));
        extraItems = newExtraItems;
        setChanged();
    }

    public void setExtraHotbarSlots(int extraSlots) {
        extraSlots = Math.max(extraSlots, 0);
        if (extraSlots == extraHotbarSlots) return;
        extraHotbarSlots = extraSlots;
        setChanged();
    }

    public int getExtraHotbarSlots() {
        return Math.min(extraItems.size(), extraHotbarSlots);
    }

    public int getExtraInventoryStartSlotIndex() {
        return super.getContainerSize();
    }

    public boolean isExtendedHotbarSlot(int index) {
        int extraInventoryStart = getExtraInventoryStartSlotIndex();
        return isHotbarSlot(index) || (index >= extraInventoryStart && index - extraInventoryStart < getExtraHotbarSlots());
    }

    public int getHotbarSize() {
        return getSelectionSize() + getExtraHotbarSlots();
    }

    /** Get the actual slot index of the given visual hotbar position.*/
    public int getSlotIndex(int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex >= getHotbarSize()) return -1;
        if (hotbarIndex < 9) return hotbarIndex;
        return getExtraInventoryStartSlotIndex() + hotbarIndex - getSelectionSize();
    }

    public int getSelectedHotbarIndex() {
        if (isHotbarSlot(selected)) return selected;
        return selected - getExtraInventoryStartSlotIndex() + getSelectionSize();
    }

    @SubscribeEvent
    public static void onPlayerJoinsWorld(PlayerEvent.PlayerLoggedInEvent event) {
        restoreExtraInventorySlots(event.getEntity());
    }

    /** Respawning hands out a brand new Player whose InventoryMenu holds only the vanilla Slots,
 * even though {@link #replaceWith} has already restored the extra ItemStacks.*/
    @SubscribeEvent
    public static void onPlayerRespawns(PlayerEvent.PlayerRespawnEvent event) {
        restoreExtraInventorySlots(event.getEntity());
    }

    /** Changing dimension keeps the ServerPlayer, but the client rebuilds its LocalPlayer from
 * scratch — so the client's menu loses the extra Slots and has to be told to re-add them.*/
    @SubscribeEvent
    public static void onPlayerChangesDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        restoreExtraInventorySlots(event.getEntity());
    }

    /**
 * Re-attach the extra Slots to the Player's inventoryMenu and re-sync the client. Server uses
 * default zero coords (it doesn't render); the client rebuilds with proper coords when it
 * handles the packet.
 *
 * <p>{@code requestFullState} makes the client round-trip a
 * {@link RequestInventoryFullStateC2SPacket} back, forcing a full inventory re-broadcast so the
 * re-attached slot indexes get their contents.</p>
*/
    private static void restoreExtraInventorySlots(Player player) {
        ExtendedInventory inv = get(player);
        inv.updateSize();
        // Rebuilding also reassigns containerMenu, so only do it when the menu is actually stale.
        if (!inv.hasExtraInventorySlots(player.inventoryMenu)) refreshPlayerInventoryMenu(player);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.createmod.catnip.platform.CatnipServices.NETWORK.sendToClient(sp,
                new ExtraInventorySizeChangeS2CPacket(inv.extraItems.size(), inv.extraHotbarSlots, true));
        }
    }

    /** @return whether the Menu already holds one Slot per extra inventory slot. No vanilla Menu
 * Slot has a container index this high, so counting them is enough to tell.*/
    public boolean hasExtraInventorySlots(AbstractContainerMenu menu) {
        int extraInventoryStart = getExtraInventoryStartSlotIndex();
        int found = 0;
        for (Slot slot : menu.slots) {
            if (slot.getSlotIndex() >= extraInventoryStart) found++;
        }
        return found == extraItems.size();
    }

    @SubscribeEvent
    public static void onOpenContainer(PlayerContainerEvent.Open event) {
        // no-op until UI client handler lands. The server-side data is correct (extra
        // slots exist + items overflow into them), but there's no menu integration to expose
        // the slots in the GUI. Re-enable when ExtendedInventoryClientHandler ports.
        AbstractContainerMenu menu = event.getContainer();
        if (!supportsExtraInventory(menu)) return;
        // get(event.getEntity()).addExtraInventorySlotsToMenu(menu, 5, 0, 0, 0, 0, 0, 0, 0);
    }

    public static boolean supportsExtraInventory(AbstractContainerMenu menu) {
        if (menu instanceof IExtendedInventoryMenu) return true;
        try {
            MenuType<?> menuType = menu.getType();
            if (menuType == null) return false;
            if (DestroyConfigs.server().extendedInventorySafeMode.get()) {
                return PetrolparkTags.MenuTypes.ALWAYS_SHOWS_EXTENDED_INVENTORY.matches(menuType);
            } else {
                return !PetrolparkTags.MenuTypes.NEVER_SHOWS_EXTENDED_INVENTORY.matches(menuType);
            }
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    public void addExtraInventorySlotsToMenu(AbstractContainerMenu menu, int columns, int invX, int invY,
                                             int leftHotbarSlots, int leftHotbarX, int leftHotbarY,
                                             int rightHotbarX, int rightHotbarY) {
        addExtraInventorySlotsToMenu(menu::addSlot, Slot::new, columns, invX, invY,
            leftHotbarSlots, leftHotbarX, leftHotbarY, rightHotbarX, rightHotbarY);
    }

    /** The server doesn't render so coords are 0;
 * it just needs the slots to EXIST in the menu so {@code broadcastFullState} sends their
 * contents to the client. The client then rebuilds with proper coords via
 * {@link petrolpark.mc.destroy.client.ExtendedInventoryClientHandler#refreshClientInventoryMenu}.*/
    public static void refreshPlayerInventoryMenu(Player player) {
        refreshPlayerInventoryMenu(player, 1, 0, 0, 0, 0, 0, 0, 0);
    }

    /** Called from:
 * <ul>
 * <li>Server side — {@link #onPlayerJoinsWorld} + {@link #updateSize} after a size change,
 * with default zero coords (server doesn't render).</li>
 * <li>Client side —
 * {@link petrolpark.mc.destroy.client.ExtendedInventoryClientHandler#refreshClientInventoryMenu}
 * with screen-geometry coords from the player's
 * {@link petrolpark.mc.destroy.config.DestroyClientConfigs} settings.</li>
 * </ul>
 * Requires {@code public-f} AT on {@code Player.inventoryMenu}.
 *
 * <p>For ServerPlayer with an active connection, also calls {@code initInventoryMenu} so the
 * container synchronizer rebinds. Without this the client would see a phantom old menu until
 * the next inventory open.</p>
*/
    public static void refreshPlayerInventoryMenu(Player player, int columns, int invX, int invY,
                                                  int leftHotbarSlots, int leftHotbarX, int leftHotbarY,
                                                  int rightHotbarX, int rightHotbarY) {
        // Direct field assignment — works because the AT strips `final` from inventoryMenu.
        player.inventoryMenu = new net.minecraft.world.inventory.InventoryMenu(player.getInventory(),
            !player.level().isClientSide(), player);
        get(player).addExtraInventorySlotsToMenu(player.inventoryMenu, columns, invX, invY,
            leftHotbarSlots, leftHotbarX, leftHotbarY, rightHotbarX, rightHotbarY);
        player.containerMenu = player.inventoryMenu;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp
            && sp.containerSynchronizer != null && sp.containerListener != null) {
            sp.initInventoryMenu();
        }
    }

    public void addExtraInventorySlotsToMenu(Consumer<Slot> slotAdder, SlotFactory slotFactory, int columns,
                                             int invX, int invY, int leftHotbarSlots, int leftHotbarX,
                                             int leftHotbarY, int rightHotbarX, int rightHotbarY) {
        int extraItemsStart = getExtraInventoryStartSlotIndex();
        for (int i = 0; i < getExtraHotbarSlots() - leftHotbarSlots; i++) {
            slotAdder.accept(slotFactory.create(this, extraItemsStart + i,
                rightHotbarX + i * 18, rightHotbarY));
        }
        int j = 0;
        for (int i = getExtraHotbarSlots() - leftHotbarSlots; i < getExtraHotbarSlots(); i++) {
            slotAdder.accept(slotFactory.create(this, extraItemsStart + i,
                leftHotbarX + j * 18, leftHotbarY));
            j++;
        }
        j = 0;
        for (int i = getExtraHotbarSlots(); i < extraItems.size(); i++) {
            slotAdder.accept(slotFactory.create(this, extraItemsStart + i,
                invX + 18 * (j % columns), invY + 18 * (j / columns)));
            j++;
        }
    }

    @FunctionalInterface
    public interface SlotFactory {
        Slot create(Container container, int slotIndex, int x, int y);
    }

    public void forEach(Consumer<? super ItemStack> action) {
        items.forEach(action);
        armor.forEach(action);
        offhand.forEach(action);
        extraItems.forEach(action);
    }

    public Stream<ItemStack> stream() {
        return Stream.concat(Stream.concat(items.stream(), armor.stream()),
            Stream.concat(offhand.stream(), extraItems.stream()));
    }

    @Override
    public ItemStack getSelected() {
        int extraInventoryStart = getExtraInventoryStartSlotIndex();
        if (selected >= extraInventoryStart) {
            int selectedExtra = selected - extraInventoryStart;
            if (selectedExtra < getExtraHotbarSlots()) return extraItems.get(selectedExtra);
        }
        return super.getSelected();
    }

    @Override
    public int getFreeSlot() {
        int freeSlot = super.getFreeSlot();
        if (freeSlot == -1) {
            for (int i = 0; i < extraItems.size(); i++) {
                if (extraItems.get(i).isEmpty()) return getExtraInventoryStartSlotIndex() + i;
            }
            return -1;
        }
        return freeSlot;
    }

    @Override
    public void setPickedItem(ItemStack stack) {
        int matchingSlot = findSlotMatchingItem(stack);
        if (isExtendedHotbarSlot(matchingSlot)) {
            selected = matchingSlot;
        } else if (matchingSlot != -1) {
            pickSlot(matchingSlot);
        } else {
            selected = getSuitableHotbarSlot();
            if (!getItem(selected).isEmpty()) {
                int freeSlot = getFreeSlot();
                if (freeSlot != -1) setItem(freeSlot, stack);
            }
            setItem(selected, stack);
        }
    }

    @Override
    public void pickSlot(int index) {
        selected = getSuitableHotbarSlot();
        ItemStack oldSelectedStack = getItem(selected);
        setItem(selected, getItem(index));
        setItem(index, oldSelectedStack);
    }

    @Override
    public int findSlotMatchingItem(ItemStack stack) {
        return findSlot(s -> !s.isEmpty() && ItemStack.isSameItemSameComponents(s, stack));
    }

    @Override
    public int findSlotMatchingUnusedItem(ItemStack stack) {
        return findSlot(s -> !s.isEmpty()
            && ItemStack.isSameItemSameComponents(s, stack)
            && !s.isDamaged()
            && !s.isEnchanted()
            && !s.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME));
    }

    /** Search vanilla items + extraItems (excludes armor/offhand).*/
    public int findSlot(Predicate<ItemStack> stackPredicate) {
        for (int i = 0; i < items.size(); i++) {
            if (stackPredicate.test(items.get(i))) return i;
        }
        for (int i = 0; i < extraItems.size(); i++) {
            if (stackPredicate.test(extraItems.get(i))) return getExtraInventoryStartSlotIndex() + i;
        }
        return -1;
    }

    @Override
    public int getSuitableHotbarSlot() {
        int selectedHotbarSlot = getSelectedHotbarIndex();
        for (int i = 0; i < getHotbarSize(); i++) {
            int nextSlot = getSlotIndex((selectedHotbarSlot + i) % getHotbarSize());
            if (getItem(nextSlot).isEmpty()) return nextSlot;
        }
        // Fallback: prefer non-damaged stacks; if all are precious, just return the current.
        for (int i = 0; i < getHotbarSize(); i++) {
            int nextSlot = getSlotIndex((selectedHotbarSlot + i) % getHotbarSize());
            if (!getItem(nextSlot).isEnchanted() && !getItem(nextSlot).isDamaged()) return nextSlot;
        }
        return -1;
    }

    @Override
    public void swapPaint(double scroll) {
        int d = (int) Math.signum(scroll);
        int selectedHotbarSlot = getSelectedHotbarIndex();
        for (selectedHotbarSlot -= d; selectedHotbarSlot < 0; selectedHotbarSlot += getHotbarSize()) ;
        while (selectedHotbarSlot >= getHotbarSize()) selectedHotbarSlot -= getHotbarSize();
        selected = getSlotIndex(selectedHotbarSlot);
    }

    /** Inlined from Inventory's private hasRemainingSpaceForItem.*/
    private static boolean hasRemainingSpaceForItemInline(ItemStack existing, ItemStack stack) {
        return !existing.isEmpty()
            && ItemStack.isSameItemSameComponents(existing, stack)
            && existing.isStackable()
            && existing.getCount() < existing.getMaxStackSize();
    }

    @Override
    public int getSlotWithRemainingSpace(ItemStack stack) {
        int slot = super.getSlotWithRemainingSpace(stack);
        if (slot == -1) {
            for (int i = 0; i < extraItems.size(); i++) {
                if (hasRemainingSpaceForItemInline(extraItems.get(i), stack)) {
                    return getExtraInventoryStartSlotIndex() + i;
                }
            }
        }
        return slot;
    }

    @Override
    public void tick() {
        updateSize();
        super.tick();
        for (int i = 0; i < extraItems.size(); i++) {
            int slot = getExtraInventoryStartSlotIndex() + i;
            extraItems.get(i).inventoryTick(player.level(), player, slot, selected == slot);
        }
    }

    /** Copied from {@link Inventory#add(int, ItemStack)} with extra-slot redirection.*/
    @Override
    public boolean add(int slot, ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            if (stack.isDamaged()) {
                if (slot == -1) slot = getFreeSlot();
                if (slot >= getExtraInventoryStartSlotIndex()) {
                    int extraItemsIndex = slot - getExtraInventoryStartSlotIndex();
                    extraItems.set(extraItemsIndex, stack.copyAndClear());
                    extraItems.get(extraItemsIndex).setPopTime(5);
                    return true;
                } else if (slot >= 0) {
                    items.set(slot, stack.copyAndClear());
                    items.get(slot).setPopTime(5);
                    return true;
                } else if (player.getAbilities().instabuild) {
                    stack.setCount(0);
                    return true;
                } else {
                    return false;
                }
            } else {
                // Vanilla addResource path delegates correctly via setItem overrides.
                return super.add(slot, stack);
            }
        } catch (Throwable throwable) {
            CrashReport crashreport = CrashReport.forThrowable(throwable, "Adding item to inventory");
            CrashReportCategory crashreportcategory = crashreport.addCategory("Item being added");
            final ItemStack finalStack = stack;
            crashreportcategory.setDetail("Registry Name",
                () -> String.valueOf(BuiltInRegistries.ITEM.getKey(finalStack.getItem())));
            crashreportcategory.setDetail("Item Class", () -> finalStack.getItem().getClass().getName());
            crashreportcategory.setDetail("Item ID", Item.getId(stack.getItem()));
            crashreportcategory.setDetail("Item data", stack.getDamageValue());
            crashreportcategory.setDetail("Item name", () -> finalStack.getHoverName().getString());
            throw new ReportedException(crashreport);
        }
    }

    @Override
    public ItemStack removeItem(int slotIndex, int count) {
        if (slotIndex >= getExtraInventoryStartSlotIndex()) {
            slotIndex -= getExtraInventoryStartSlotIndex();
            if (slotIndex < extraItems.size()) {
                if (extraItems.get(slotIndex).isEmpty()) return ItemStack.EMPTY;
                return ContainerHelper.removeItem(extraItems, slotIndex, count);
            }
        }
        return super.removeItem(slotIndex, count);
    }

    @Override
    public void removeItem(ItemStack stack) {
        if (!extraItems.removeIf(s -> s == stack)) super.removeItem(stack);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slotIndex) {
        if (slotIndex >= getExtraInventoryStartSlotIndex()) {
            slotIndex -= getExtraInventoryStartSlotIndex();
            if (slotIndex < extraItems.size()) {
                ItemStack stack = extraItems.get(slotIndex);
                if (stack.isEmpty()) return ItemStack.EMPTY;
                extraItems.set(slotIndex, ItemStack.EMPTY);
                return stack;
            }
        }
        return super.removeItemNoUpdate(slotIndex);
    }

    @Override
    public void setItem(int slotIndex, ItemStack stack) {
        if (slotIndex >= getExtraInventoryStartSlotIndex()) {
            slotIndex -= getExtraInventoryStartSlotIndex();
            if (slotIndex < extraItems.size()) {
                extraItems.set(slotIndex, stack);
                return;
            }
        }
        super.setItem(slotIndex, stack);
    }

    @Override
    public float getDestroySpeed(BlockState state) {
        return getItem(selected).getDestroySpeed(state);
    }

    @Override
    public ListTag save(ListTag listTag) {
        listTag = super.save(listTag);
        var registries = player.level().registryAccess();
        int extraInventoryStart = getExtraInventoryStartSlotIndex();
        for (int i = 0; i < extraItems.size(); i++) {
            ItemStack stack = extraItems.get(i);
            if (!stack.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                tag.putInt("Slot", i + extraInventoryStart);
                // 1.21 ItemStack.save now requires HolderLookup.Provider.
                stack.save(registries, tag);
                listTag.add(tag);
            }
        }
        return listTag;
    }

    @Override
    public void load(ListTag listTag) {
        updateSize();
        super.load(listTag);
        var registries = player.level().registryAccess();
        int extraInventoryStart = getExtraInventoryStartSlotIndex();
        for (int i = 0; i < listTag.size(); i++) {
            CompoundTag tag = listTag.getCompound(i);
            if (tag.contains("Slot", Tag.TAG_INT)) {
                int slotIndex = tag.getInt("Slot");
                if (slotIndex >= extraInventoryStart) {
                    slotIndex -= extraInventoryStart;
                    if (slotIndex < extraItems.size()) {
                        // 1.21 ItemStack.parseOptional returns ItemStack.EMPTY on parse failure.
                        extraItems.set(slotIndex, ItemStack.parseOptional(registries, tag));
                    }
                }
            }
        }
    }

    @Override
    public int getContainerSize() {
        return super.getContainerSize() + extraItems.size();
    }

    @Override
    public boolean isEmpty() {
        return super.isEmpty() && extraItems.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slotIndex) {
        int extraInventoryStart = getExtraInventoryStartSlotIndex();
        if (slotIndex >= extraInventoryStart) {
            slotIndex -= extraInventoryStart;
            if (slotIndex < extraItems.size()) return extraItems.get(slotIndex);
        }
        return super.getItem(slotIndex);
    }

    @Override
    public void dropAll() {
        super.dropAll();
        for (int i = 0; i < extraItems.size(); i++) {
            ItemStack stack = extraItems.get(i);
            if (stack.isEmpty()) continue;
            player.drop(stack, true, false);
            extraItems.set(i, ItemStack.EMPTY);
        }
    }

    @Override
    public boolean contains(ItemStack stack) {
        return findSlot(s -> ItemStack.isSameItemSameComponents(s, stack)) != -1;
    }

    @Override
    public boolean contains(TagKey<Item> tag) {
        return findSlot(s -> s.is(tag)) != -1;
    }

    @Override
    public void replaceWith(Inventory playerInventory) {
        if (playerInventory instanceof ExtendedInventory extendedInv) {
            setExtraHotbarSlots(extendedInv.extraHotbarSlots);
            setExtraInventorySize(extendedInv.extraItems.size());
        }
        super.replaceWith(playerInventory);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        extraItems.clear();
    }

    @Override
    public void fillStackedContents(StackedContents stackedContents) {
        super.fillStackedContents(stackedContents);
        extraItems.forEach(stackedContents::accountSimpleStack);
    }

    public interface DelayedSlotPopulation {
        void populateDelayedSlots();
    }
}
