package petrolpark.mc.destroy.content.redstone.programmer;

import java.util.function.BooleanSupplier;

import com.simibubi.create.content.equipment.clipboard.ClipboardCloneable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * {@link BlockEntityBehaviour} housing the {@link RedstoneProgram} inside a
 * {@code RedstoneProgrammerBlockEntity}. Ties the program's abstract methods
 * ({@code hasPower/getBlockPos/shouldTransmit/getWorld}) to the BE's runtime state via
 * {@link BehaviourRedstoneProgram} inner class.
*/
public class RedstoneProgrammerBehaviour extends BlockEntityBehaviour implements ClipboardCloneable, MenuProvider {

    public static BehaviourType<RedstoneProgrammerBehaviour> TYPE = new BehaviourType<>();

    protected BooleanSupplier powerChecker;
    public BehaviourRedstoneProgram program;

    public RedstoneProgrammerBehaviour(SmartBlockEntity be, BooleanSupplier powerChecker) {
        super(be);
        this.powerChecker = powerChecker;
        program = new BehaviourRedstoneProgram();
    }

    @Override
    public void initialize() {
        super.initialize();
        if (getWorld().isClientSide()) return;
        program.load();
    }

    @Override
    public void unload() {
        super.unload();
        if (getWorld().isClientSide()) return;
        program.unload();
    }

    @Override
    public void tick() {
        program.tick();
        super.tick();
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        setProgram(nbt.getCompound("Program"), registries);
    }

    public void setProgram(CompoundTag tag, HolderLookup.Provider registries) {
        program = RedstoneProgram.read(BehaviourRedstoneProgram::new, tag, registries);
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.put("Program", program.write(registries));
    }

    public class BehaviourRedstoneProgram extends RedstoneProgram {

        @Override
        public boolean hasPower() {
            return powerChecker.getAsBoolean();
        }

        @Override
        public BlockPos getBlockPos() {
            return getPos();
        }

        /**
 * Copied from {@link com.simibubi.create.content.redstone.link.LinkBehaviour Create source}.
*/
        @Override
        public boolean shouldTransmit() {
            Level level = RedstoneProgrammerBehaviour.super.getWorld();
            BlockPos pos = getPos();
            if (blockEntity.isChunkUnloaded()) return false;
            if (blockEntity.isRemoved()) return false;
            if (!level.isLoaded(pos)) return false;
            return level.getBlockEntity(pos) == blockEntity;
        }

        @Override
        public LevelAccessor getWorld() {
            return RedstoneProgrammerBehaviour.super.getWorld();
        }

        @Override
        public void whenChanged() {
            blockEntity.notifyUpdate();
            program.load();
        }

        @Override
        public void tick() {
            super.tick();
        }
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public String getClipboardKey() {
        return "Frequencies";
    }

    
    @Override
    public boolean writeToClipboard(HolderLookup.Provider registries, CompoundTag tag, Direction side) {
        tag.put("RedstoneProgram", program.write(registries));
        return true;
    }

    @Override
    public boolean readFromClipboard(HolderLookup.Provider registries, CompoundTag tag, Player player, Direction side, boolean simulate) {
        if (tag.contains("RedstoneProgram")) {
            setProgram(tag.getCompound("RedstoneProgram"), registries);
            return true;
        }
        if (!tag.contains("First") || !tag.contains("Last")) return false;
        Couple<Frequency> frequencies = Couple.create(
            Frequency.of(ItemStack.parseOptional(registries, tag.getCompound("First"))),
            Frequency.of(ItemStack.parseOptional(registries, tag.getCompound("Last"))));
        if (program.getChannels().stream().anyMatch(channel -> channel.networkKey.equals(frequencies))) return false;
        if (!simulate) program.addBlankChannel(frequencies);
        return true;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // Wire createMenu to the RedstoneProgrammerMenu.create factory
        // (`public static RedstoneProgrammerMenu create(int id, Inventory inv, RedstoneProgram
        // program)`). Returning null here would prevent the menu from opening.
        return RedstoneProgrammerMenu.create(containerId, playerInventory, program);
    }

    @Override
    public Component getDisplayName() {
        return Component.empty();
    }
}
