package petrolpark.mc.destroy.core.pollution;

import java.util.Collections;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import petrolpark.mc.library.util.codec.ContextualCodec;
import petrolpark.mc.library.util.codec.RecordContextualCodecBuilder;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;

public abstract class Pollution<HOLDER extends IAttachmentHolder> {

    protected final HOLDER holder;

    protected final Object2IntMap<PollutionType<HOLDER>> values = new Object2IntOpenHashMap<>();
    protected final Object2IntMap<PollutionType<HOLDER>> lastSyncedValues = new Object2IntOpenHashMap<>();
    
    public Pollution(HOLDER holder, Map<PollutionType<HOLDER>, Integer> values) {
        this.holder = holder;

        this.values.defaultReturnValue(0);
        lastSyncedValues.defaultReturnValue(0);

        this.values.putAll(values);
    };

    protected Map<PollutionType<HOLDER>, Integer> getValues() {
        return values;
    };

    protected Object2IntMap<PollutionType<HOLDER>> setValues(Map<PollutionType<HOLDER>, Integer> values) {
        this.values.putAll(values);
        this.lastSyncedValues.putAll(values);
        setChanged();
        return this.values;
    };

    public int changePollution(PollutionType<HOLDER> pollutionType, int change) {
        return setPollution(pollutionType, values.getInt(pollutionType) + change);
    };

    /**
 * @param pollutionType
 * @param change
 * @return Whether to sync to client
*/
    protected boolean changePollutionUnchecked(PollutionType<HOLDER> pollutionType, int change) {
        return setPollutionUnchecked(pollutionType, values.getInt(pollutionType) + change);
    };

    public int setPollution(PollutionType<HOLDER> pollutionType, int value) {
        final int newValue = Mth.clamp(value, 0, getProperties(pollutionType).max());
        if (setPollutionUnchecked(pollutionType, newValue)) sync();
        setChanged();
        return newValue;
    };

    /**
 * @param pollutionType
 * @param newValue
 * @return Whether to sync to client
*/
    protected boolean setPollutionUnchecked(PollutionType<HOLDER> pollutionType, int newValue) {
        final int oldValue = values.getInt(pollutionType);
        values.put(pollutionType, newValue);
        setChanged();
        return Mth.abs(oldValue - newValue) >= getProperties(pollutionType).syncThreshold();
    };

    public int getPollution(PollutionType<HOLDER> pollutionType) {
        return values.getInt(pollutionType);
    };

    public void tick(RandomSource random) {
        boolean sync = false;
        for (PollutionType<HOLDER> pollutionType : values.keySet()) sync |= tickPollutionTypeUnsynced(random, pollutionType);
        if (sync) sync();
    };

    /**
 * @param random
 * @param pollutionType
 * @return Whether we need to sync
*/
    public boolean tickPollutionTypeUnsynced(RandomSource random, PollutionType<HOLDER> pollutionType) {
        if (getPollution(pollutionType) <= 0) return false;
        final PollutionType.Properties properties = getProperties(pollutionType);
        final int flatDecrease = (int)properties.decayChancePerTick();
        final float decreaseChance = properties.decayChancePerTick() - flatDecrease;
        return changePollutionUnchecked(pollutionType, - (flatDecrease + (random.nextFloat() < decreaseChance ? 1 : 0)));
    };

    public void setChanged() {};

    public final void sync() {
        syncInternal();
        lastSyncedValues.putAll(values);
    };

    protected abstract void syncInternal();

    public abstract PollutionType.Properties getProperties(PollutionType<HOLDER> pollutionType);

    public static abstract class Serializer<HOLDER extends IAttachmentHolder, POLLUTION extends Pollution<HOLDER>> implements IAttachmentSerializer<Tag, POLLUTION> {

        protected final Registry<PollutionType<HOLDER>> pollutionTypeRegistry;
        protected final ContextualCodec<HOLDER, POLLUTION> codec;

        public Serializer(Registry<PollutionType<HOLDER>> pollutionTypeRegistry) {
            this.pollutionTypeRegistry = pollutionTypeRegistry;
            this.codec = RecordContextualCodecBuilder.create(instance -> instance.group(
                instance.context(),
                ContextualCodec.<HOLDER, Map<PollutionType<HOLDER>, Integer>>of(Codec.unboundedMap(pollutionTypeRegistry.byNameCodec(), Codec.INT)).fieldOf("values").forGetter(Pollution::getValues)
            ).apply(instance, this::create));
        };

        protected abstract POLLUTION create(HOLDER holder, Map<PollutionType<HOLDER>, Integer> values);

        public abstract HOLDER castHolder(IAttachmentHolder holder);

        @Override
        public final POLLUTION read(IAttachmentHolder holder, Tag tag, HolderLookup.Provider provider) {
            final HOLDER trueHolder = castHolder(holder);
            return codec.parse(RegistryOps.create(NbtOps.INSTANCE, provider), trueHolder, tag).resultOrPartial().orElse(create(trueHolder, Collections.emptyMap()));
        };

        @Override
        public final @Nullable Tag write(POLLUTION attachment, HolderLookup.Provider provider) {
            return codec.encodeStart(RegistryOps.create(NbtOps.INSTANCE, provider), attachment.holder, attachment).resultOrPartial().orElse(null);
        };

    };
};
