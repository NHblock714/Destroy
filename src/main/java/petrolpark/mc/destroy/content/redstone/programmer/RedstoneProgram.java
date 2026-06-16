package petrolpark.mc.destroy.content.redstone.programmer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.google.common.collect.ImmutableList;
import com.simibubi.create.Create;
import com.simibubi.create.content.redstone.link.IRedstoneLinkable;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler.Frequency;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.config.DestroyAllConfigs;

/**
 * Abstract program-state holder for a Redstone Programmer — a channel sequencer that broadcasts
 * timed redstone signals to {@link com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler}
 * on programmable frequencies. 7 {@link PlayMode}s govern how the program responds to power + pulse
 * inputs. Concrete implementation is {@code RedstoneProgrammerBlockEntity} (still deferred; this
 * class is usable independently as a data structure).
*/
public abstract class RedstoneProgram {

    public PlayMode mode;
    protected int length;
    protected int playtime;
    protected int ticksToNextBeat;
    public boolean paused;
    protected boolean pausedLastTick;
    protected boolean poweredLastTick;
    protected List<Channel> channels;

    protected int ticksPerBeat;
    public int beatsPerLine;
    public int linesPerBar;

    protected boolean notifiedChange;

    public RedstoneProgram() {
        mode = PlayMode.MANUAL;
        ticksPerBeat = DestroyAllConfigs.SERVER.blocks.redstoneProgrammerMinTicksPerBeat.get();
        length = 20;
        playtime = 0;
        paused = true;
        pausedLastTick = false;
        poweredLastTick = false;
        channels = new ArrayList<>();
        beatsPerLine = 2;
        linesPerBar = 4;
        notifiedChange = false;
    }

    public int getLength() {
        return length;
    }

    public int getTicksPerBeat() {
        return ticksPerBeat;
    }

    public int getAbsolutePlaytime() {
        return ticksPerBeat * playtime + (ticksPerBeat - ticksToNextBeat);
    }

    public void setTicksPerBeat(int value) {
        ticksPerBeat = Math.max(DestroyAllConfigs.SERVER.blocks.redstoneProgrammerMinTicksPerBeat.get(), value);
    }

    public void tick() {
        boolean powered = hasPower();

        if (mode.powerRequired) paused = !powered;

        if (powered && !poweredLastTick) {
            if (mode == PlayMode.SWITCH_ON_PULSE) {
                paused = !paused;
            } else if (mode == PlayMode.RESTART_ON_PULSE) {
                paused = false;
                playtime = 0;
            }
        }

        if (!powered && mode == PlayMode.LOOP_WITH_POWER) playtime = 0;

        if (!paused) {
            notifiedChange = false;
            if (ticksPerBeat == 1) {
                playtime++;
            } else {
                ticksToNextBeat--;
                if (ticksToNextBeat <= 0) {
                    ticksToNextBeat = ticksPerBeat;
                    playtime++;
                } else {
                    notifiedChange = true;
                }
            }
        }

        if (paused != pausedLastTick) notifiedChange = false;

        if (playtime >= length) {
            playtime = 0;
            if (mode.pausesWhenFinished) paused = true;
        }

        if (!notifiedChange) {
            channels.forEach(Channel::updateNetwork);
            if (paused) notifiedChange = true;
        }

        poweredLastTick = powered;
        pausedLastTick = paused;
    }

    public void restart() {
        playtime = 0;
        ticksToNextBeat = ticksPerBeat;
    }

    public abstract boolean hasPower();

    public abstract BlockPos getBlockPos();

    public abstract boolean shouldTransmit();

    public abstract LevelAccessor getWorld();

    public void whenChanged() {}

    public ImmutableList<Channel> getChannels() {
        return ImmutableList.copyOf(channels);
    }

    public void addBlankChannel(Couple<Frequency> frequencies) {
        Channel channel = new Channel(frequencies, new int[length]);
        channels.add(channel);
        if (!isValidWorld(getWorld())) return;
        getHandler().addToNetwork(getWorld(), channel);
    }

    public boolean remove(Channel channel) {
        boolean removed = channels.remove(channel);
        if (removed && isValidWorld(getWorld())) getHandler().removeFromNetwork(getWorld(), channel);
        return removed;
    }

    public void swap(Channel channel1, Channel channel2) {
        if (channels.contains(channel1) && channels.contains(channel2)) {
            Collections.swap(channels, channels.indexOf(channel1), channels.indexOf(channel2));
        }
    }

    public void load() {
        if (!isValidWorld(getWorld()) || getBlockPos() == null) return;
        channels.forEach(channel -> getHandler().addToNetwork(getWorld(), channel));
        notifiedChange = false;
    }

    public void unload() {
        if (!isValidWorld(getWorld())) return;
        channels.forEach(channel -> getHandler().removeFromNetwork(getWorld(), channel));
    }

    public void setDuration(int duration) {
        length = duration;
        for (Channel channel : channels) {
            channel.sequence = Arrays.copyOf(channel.sequence, duration);
        }
    }

    /**
 * Serialize the program state to NBT.
*/
    public CompoundTag write(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Mode", mode.ordinal());
        tag.putInt("TicksPerBeat", ticksPerBeat);
        tag.putInt("Length", length);
        tag.putInt("Playtime", playtime);
        if (ticksPerBeat != 1) tag.putInt("TicksToNextBeat", ticksToNextBeat);
        tag.putBoolean("Paused", paused);
        tag.putBoolean("PoweredLastTick", poweredLastTick);
        tag.putInt("BeatsPerLine", beatsPerLine);
        tag.putInt("LinesPerBar", linesPerBar);

        ListTag sequencesTag = new ListTag();

        for (Channel channel : channels) {
            CompoundTag sequenceTag = new CompoundTag();
            // Use saveOptional, NOT save. 1.21 vanilla ItemStack.save throws
            // {@code IllegalStateException: Cannot encode empty ItemStack} for empty stacks
            // .
            // Channels with one frequency dropped (intermediate JEI ghost-drag state) carry
            // ItemStack.EMPTY in one of the two slots → save() crashes the player tick →
            // server crash. saveOptional is symmetric with the read-side parseOptional already
            // used at line 266-267 below — empty stack ↔ empty tag round-trips cleanly.
            sequenceTag.put("FrequencyFirst", channel.networkKey.getFirst().getStack().saveOptional(provider));
            sequenceTag.put("FrequencyLast", channel.networkKey.getSecond().getStack().saveOptional(provider));
            sequenceTag.putIntArray("Sequence", getEncodedSequence(channel));
            sequencesTag.add(sequenceTag);
        }

        tag.put("Sequences", sequencesTag);

        return tag;
    }

    /**
 * Read program state from NBT.
*/
    public static <T extends RedstoneProgram> T read(Supplier<T> newProgram, CompoundTag tag, HolderLookup.Provider provider) {
        T program = newProgram.get();
        program.mode = PlayMode.values()[tag.getInt("Mode")];
        program.ticksPerBeat = tag.getInt("TicksPerBeat");
        program.length = tag.getInt("Length");
        program.playtime = tag.getInt("Playtime");
        if (program.ticksPerBeat != 1) program.ticksToNextBeat = tag.getInt("TicksToNextBeat");
        program.paused = tag.getBoolean("Paused");
        program.poweredLastTick = tag.getBoolean("PoweredLastTick");
        program.beatsPerLine = tag.getInt("BeatsPerLine");
        program.linesPerBar = tag.getInt("LinesPerBar");

        tag.getList("Sequences", Tag.TAG_COMPOUND).forEach(t -> {
            CompoundTag sequenceTag = (CompoundTag) t;
            int[] sequence = decodeSequence(program.length, sequenceTag.getIntArray("Sequence"));

            program.channels.add(
                program.new Channel(
                    Couple.create(
                        Frequency.of(ItemStack.parseOptional(provider, sequenceTag.getCompound("FrequencyFirst"))),
                        Frequency.of(ItemStack.parseOptional(provider, sequenceTag.getCompound("FrequencyLast")))
                    ),
                    sequence
                )
            );
        });

        return program;
    }

    /**
 * Wire-serialize the program. Buffer type upgrades to {@link RegistryFriendlyByteBuf}
 * for registry context needed by ItemStack STREAM_CODEC.
*/
    public final void write(RegistryFriendlyByteBuf buf) {
        buf.writeVarInt(mode.ordinal());
        buf.writeVarInt(ticksPerBeat);
        buf.writeVarInt(length);
        buf.writeVarInt(playtime);
        buf.writeVarInt(ticksToNextBeat);
        buf.writeBoolean(paused);
        buf.writeBoolean(poweredLastTick);
        buf.writeVarInt(beatsPerLine);
        buf.writeVarInt(linesPerBar);
        buf.writeVarInt(channels.size());
        for (Channel channel : channels) {
            // Use OPTIONAL_STREAM_CODEC, NOT STREAM_CODEC. 1.21 vanilla
            // ItemStack.STREAM_CODEC explicitly rejects empty stacks ("Empty ItemStack not
            // allowed"); the empty-allowing variant is OPTIONAL_STREAM_CODEC. RedstoneProgrammer
            // channels can have one empty Frequency slot (e.g. just-added channel where only
            // one ItemStack was dropped — second frequency is still ItemStack.EMPTY) —
            // that's a valid intermediate state during JEI ghost-drag. Using
            // STREAM_CODEC here crashed the network with EncoderException → world disconnect
            // (symptom: the world crashed the instant the programmer was placed).
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, channel.networkKey.getFirst().getStack());
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, channel.networkKey.getSecond().getStack());
            buf.writeVarIntArray(getEncodedSequence(channel));
        }
    }

    public void read(RegistryFriendlyByteBuf buf) {
        mode = PlayMode.values()[buf.readVarInt()];
        ticksPerBeat = buf.readVarInt();
        length = buf.readVarInt();
        playtime = buf.readVarInt();
        ticksToNextBeat = buf.readVarInt();
        paused = buf.readBoolean();
        poweredLastTick = buf.readBoolean();
        beatsPerLine = buf.readVarInt();
        linesPerBar = buf.readVarInt();
        int channels = buf.readVarInt();
        for (int i = 0; i < channels; i++) {
            // Match the encoder side: OPTIONAL_STREAM_CODEC handles empty stacks.
            // Asymmetric codec (encode optional / decode strict) would break network sync the
            // moment a freshly-added channel had only one of its two frequency slots filled.
            this.channels.add(new Channel(
                Couple.create(
                    Frequency.of(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)),
                    Frequency.of(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf))),
                decodeSequence(length, buf.readVarIntArray())
            ));
        }
    }

    public void copyFrom(RedstoneProgram otherProgram) {
        mode = otherProgram.mode;
        ticksPerBeat = otherProgram.ticksPerBeat;
        length = otherProgram.length;
        playtime = otherProgram.playtime;
        ticksToNextBeat = otherProgram.ticksToNextBeat;
        paused = otherProgram.paused;
        poweredLastTick = otherProgram.poweredLastTick;
        channels = new ArrayList<>(otherProgram.channels.stream()
            .map(channel -> new Channel(channel.networkKey, Arrays.copyOf(channel.sequence, length))).toList());
        beatsPerLine = otherProgram.beatsPerLine;
        linesPerBar = otherProgram.linesPerBar;
        notifiedChange = false;
    }

    public boolean hasPowerChanged() {
        return hasPower() != poweredLastTick;
    }

    // Redstone powers go up to 16, so we fit 7 per int (avoid sign bit).
    private int[] getEncodedSequence(Channel channel) {
        int[] encodedStrengths = new int[(length / 7) + 1];
        int i = 0;
        while (i < length) {
            int encodedStrength = 0;
            for (int j = 6; j >= 0; j--) {
                int strength = i < length ? channel.sequence[i] : 0;
                encodedStrength += strength << 4 * j;
                i++;
            }
            encodedStrengths[(i - 1) / 7] = encodedStrength;
        }
        return encodedStrengths;
    }

    protected static int[] decodeSequence(int length, int[] encodedSequence) {
        int[] sequence = new int[length];
        int i = 0;
        for (int encodedStrength : encodedSequence) {
            decodeStrengths: for (int j = 6; j >= 0; j--) {
                if (i >= length) break decodeStrengths;
                int strength = encodedStrength >> 4 * j;
                encodedStrength -= strength << 4 * j;
                sequence[i] = strength;
                i++;
            }
        }
        return sequence;
    }

    protected static RedstoneLinkNetworkHandler getHandler() {
        return Create.REDSTONE_LINK_NETWORK_HANDLER;
    }

    protected static boolean isValidWorld(LevelAccessor level) {
        return level != null && !level.isClientSide();
    }

    public class Channel implements IRedstoneLinkable {

        public final Couple<Frequency> networkKey;
        protected int[] sequence;

        protected Channel(Couple<Frequency> networkKey, int[] sequence) {
            this.networkKey = networkKey;
            this.sequence = sequence;
        }

        protected void updateNetwork() {
            if (!isValidWorld(getWorld())) return;
            if (playtime == 0 || sequence[playtime] != sequence[playtime - 1]) {
                getHandler().updateNetworkOf(getWorld(), this);
            }
        }

        public int getStrength(int position) {
            if (position >= sequence.length || position < 0) return 0;
            return sequence[position];
        }

        public void setStrength(int position, int strength) {
            if (position < length) {
                if (strength >= 16 || strength < 0) strength = 0;
                sequence[position] = strength;
            }
        }

        public void clear() {
            sequence = new int[length];
        }

        @Override
        public int getTransmittedStrength() {
            if (playtime >= length) return 0;
            return sequence[playtime];
        }

        @Override
        public void setReceivedStrength(int power) {
            // No-op — programmer transmits only
        }

        @Override
        public boolean isListening() {
            return false;
        }

        @Override
        public boolean isAlive() {
            return shouldTransmit();
        }

        @Override
        public Couple<Frequency> getNetworkKey() {
            return networkKey;
        }

        @Override
        public BlockPos getLocation() {
            return getBlockPos();
        }
    }

    public enum PlayMode {
        MANUAL(true, false, DestroyLang.translate("tooltip.redstone_programmer.mode.manual").component()),
        SWITCH_ON_PULSE(false, false, DestroyLang.translate("tooltip.redstone_programmer.mode.switch_on_pulse").component()),
        RESTART_ON_PULSE(true, false, DestroyLang.translate("tooltip.redstone_programmer.mode.restart_on_pulse").component()),
        RESUME_WITH_POWER(false, true, DestroyLang.translate("tooltip.redstone_programmer.mode.resume_with_power").component()),
        RESTART_WITH_POWER(true, true, DestroyLang.translate("tooltip.redstone_programmer.mode.restart_with_power").component()),
        LOOP_WITH_POWER(false, true, DestroyLang.translate("tooltip.redstone_programmer.mode.loop_with_power").component()),
        LOOP(false, false, DestroyLang.translate("tooltip.redstone_programmer.mode.loop").component());

        PlayMode(boolean pausesWhenFinished, boolean powerRequired, Component description) {
            this.pausesWhenFinished = pausesWhenFinished;
            this.powerRequired = powerRequired;
            this.description = description;
        }

        public final boolean pausesWhenFinished;
        public final boolean powerRequired;

        public final Component description;
    }
}
