package petrolpark.mc.destroy.core.chemistry.storage.testtube;

import static petrolpark.mc.library.compat.create.PetrolparkCreateClient.OUTLINER;

import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Pair;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyTags;
import petrolpark.mc.destroy.core.block.entity.ISpecialWhenHoveredBlockEntity;

/**
 * Block-entity backing TestTubeRack · holds 4-slot ItemStackHandler (each slot accepts TEST_TUBE-
 * tagged items only).
*/
public class TestTubeRackBlockEntity extends SmartBlockEntity implements ISpecialWhenHoveredBlockEntity {

    public TestTubeRackInventory inv;

    public TestTubeRackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inv = new TestTubeRackInventory();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // no-op · capabilities wired in registerCapabilities
    }

    
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            DestroyBlockEntityTypes.TEST_TUBE_RACK.get(),
            (be, context) -> be.inv);
    }

    @Override
    public void tick() {
        super.tick();
        sendData();
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        inv = new TestTubeRackInventory();
        inv.deserializeNBT(registries, tag.getCompound("Inventory"));
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.put("Inventory", inv.serializeNBT(registries));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void whenLookedAt(LocalPlayer player, BlockHitResult result) {
        int tube = TestTubeRackBlock.getTargetedTube(getBlockState(), getBlockPos(), player);
        if (tube == -1) return;
        if (inv.isItemValid(tube, player.getItemInHand(InteractionHand.MAIN_HAND)) || !inv.getStackInSlot(tube).isEmpty()) {
            OUTLINER.showAABB(Pair.of("test_tube_rack_" + tube, getBlockPos()),
                TestTubeRackBlock.getTubeBox(getBlockState(), getBlockPos(), tube), 1)
                .lineWidth(1 / 64f)
                .colored(0xFF7F7F7F);
        }
    }

    public class TestTubeRackInventory extends ItemStackHandler implements IItemHandler {

        public TestTubeRackInventory() {
            super(4);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return stack.is(DestroyTags.Items.TEST_TUBE_RACK_STORABLE.tag);
        }

        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            notifyUpdate();
        }
    }
}
