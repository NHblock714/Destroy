package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import com.simibubi.create.foundation.gui.menu.MenuBase;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.SlotItemHandler;

import petrolpark.mc.destroy.client.DestroyMenuTypes;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosivePropertyCondition;

/**
 * ContainerMenu for {@link MixedExplosiveBlock}'s crafting-style UI — exposes the BE's
 * {@link MixedExplosiveInventory} slots (explosive ingredients) + player inventory below. On
 * quick-move: shift-clicking in explosive slots → player inventory; in player inventory →
 * explosive slots.
*/
public class MixedExplosiveMenu extends MenuBase<IMixedExplosiveBlockEntity> {

    private int explosiveSlots = 0;

    public MixedExplosiveMenu(MenuType<?> type, int id, Inventory playerInv, RegistryFriendlyByteBuf extraData) {
        super(type, id, playerInv, extraData);
    }

    protected MixedExplosiveMenu(MenuType<?> type, int id, Inventory playerInv, IMixedExplosiveBlockEntity contentHolder) {
        super(type, id, playerInv, contentHolder);
        explosiveSlots = contentHolder.getExplosiveInventory().getSlots();
    }

    public static MixedExplosiveMenu create(int id, Inventory playerInv, IMixedExplosiveBlockEntity be) {
        return new MixedExplosiveMenu(DestroyMenuTypes.CUSTOM_EXPLOSIVE.get(), id, playerInv, be);
    }

    @Override
    protected IMixedExplosiveBlockEntity createOnClient(RegistryFriendlyByteBuf extraData) {
        return new DummyCustomExplosiveMixBlockEntity(extraData);
    }

    @Override
    protected void initAndReadInventory(IMixedExplosiveBlockEntity contentHolder) {
        // post-init inventory read deferred — inventory already comes via writeToBuffer
        // + DummyCustomExplosiveMixBlockEntity client-side deserialization.
    }

    @Override
    protected void addSlots() {
        MixedExplosiveInventory inv = contentHolder.getExplosiveInventory();
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            addSlot(new SlotItemHandler(inv, slot, 94 + 18 * (slot % 4), 25 + 18 * (slot / 4)));
        }
        addPlayerSlots(8, 157);
    }

    @Override
    protected void saveData(IMixedExplosiveBlockEntity contentHolder) {
        // inventory writes go through the BE's own NBT save path on setChanged().
        // Menu-close saveData is a no-op — BE is already server-authoritative.
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot clickedSlot = getSlot(index);
        if (!clickedSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = clickedSlot.getItem();
        if (index < explosiveSlots) {
            moveItemStackTo(stack, explosiveSlots, slots.size(), false);
        } else {
            while (!stack.isEmpty()) {
                if (!moveItemStackTo(stack, 0, explosiveSlots, false)) break;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
 * Client-side stand-in for the server's {@link IMixedExplosiveBlockEntity}. Deserializes the
 * displayName + inventory + applicable-conditions from the Menu's spawn-packet buffer (format
 * matches {@link IMixedExplosiveBlockEntity#writeToBuffer}).
*/
    protected static class DummyCustomExplosiveMixBlockEntity implements IMixedExplosiveBlockEntity {

        private final Component name;
        private MixedExplosiveInventory inv;
        private final ExplosivePropertyCondition[] conditions; // May contain null entries if server registers conditions the client doesn't know.

        protected DummyCustomExplosiveMixBlockEntity(RegistryFriendlyByteBuf buffer) {
            this.name = ComponentSerialization.STREAM_CODEC.decode(buffer);
            this.inv = new MixedExplosiveInventory(buffer.readVarInt());
            CompoundTag tag = buffer.readNbt();
            if (tag != null) {
                inv.deserializeNBT(buffer.registryAccess(), tag);
            }
            int conditionCount = buffer.readVarInt();
            conditions = new ExplosivePropertyCondition[conditionCount];
            for (int i = 0; i < conditionCount; i++) {
                conditions[i] = ExplosiveProperties.EXPLOSIVE_PROPERTY_CONDITIONS.get(buffer.readResourceLocation());
            }
        }

        @Override
        public Component getDisplayName() {
            return name;
        }

        @Override
        public MixedExplosiveInventory getExplosiveInventory() {
            return inv;
        }

        @Override
        public void setExplosiveInventory(MixedExplosiveInventory inv) {
            this.inv = inv;
        }

        @Override
        public ExplosivePropertyCondition[] getApplicableExplosionConditions() {
            return conditions;
        }

        // ClipboardCloneable — dummy stub (no real clipboard persistence on client-side dummy)
        @Override
        public boolean readFromClipboard(HolderLookup.Provider provider, CompoundTag tag, Player player, Direction side, boolean simulate) {
            return false;
        }

        @Override
        public boolean writeToClipboard(HolderLookup.Provider provider, CompoundTag tag, Direction side) {
            return false;
        }

        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return null; // Client-side dummy never opens a nested menu
        }
    }
}
