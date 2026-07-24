package petrolpark.mc.destroy.core.pollution;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import petrolpark.mc.library.core.world.ChunkTickEvent;
import petrolpark.mc.library.util.RandomHelper;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import petrolpark.mc.destroy.DestroyAttachmentTypes;
import petrolpark.mc.destroy.DestroyDataMapTypes;
import petrolpark.mc.destroy.DestroyRegistries;

@EventBusSubscriber
public class ChunkPollution extends Pollution<ChunkAccess> {

    public static final Serializer SERIALIZER = new Serializer();

    public ChunkPollution(ChunkAccess holder, Map<PollutionType<ChunkAccess>, Integer> values) {
        super(holder, values);
    };

    public static final ChunkPollution create(IAttachmentHolder holder) {
        if (holder instanceof ChunkAccess chunk) return new ChunkPollution(chunk, DestroyRegistries.CHUNK_POLLUTION_TYPES.stream().collect(Collectors.toMap(Function.identity(), t -> 0)));
        throw new IllegalArgumentException();
    };

    @Override
    public void setChanged() {
        holder.setUnsaved(true);
    }

    @Override
    protected void syncInternal() {
        if (holder.getLevel() instanceof ServerLevel level) CatnipServices.NETWORK.sendToClientsTrackingChunk(level, holder.getPos(), new ChunkPollutionPacket(holder.getPos(), getValues()));
    };

    public void syncTo(Player entity) {
        if (entity instanceof ServerPlayer player) CatnipServices.NETWORK.sendToClient(player, new ChunkPollutionPacket(holder.getPos(), getValues()));
    };

    @Override
    public PollutionType.Properties getProperties(PollutionType<ChunkAccess> pollutionType) {
        return PollutionHelper.getChunkPollutionTypeProperties(pollutionType);
    };

    public PollutionType.SpreadingProperties getSpreadingProperties(PollutionType<ChunkAccess> pollutionType) {
        return Optional.ofNullable(DestroyRegistries.CHUNK_POLLUTION_TYPES.getData(DestroyDataMapTypes.CHUNK_POLLUTION_SPREADING_PROPERTIES, DestroyRegistries.CHUNK_POLLUTION_TYPES.wrapAsHolder(pollutionType).getKey())).orElse(PollutionType.SpreadingProperties.DEFAULT);
    };

    @Override
    public boolean tickPollutionTypeUnsynced(RandomSource random, PollutionType<ChunkAccess> pollutionType) {
        boolean sync = super.tickPollutionTypeUnsynced(random, pollutionType);
        final Level level = holder.getLevel();
        final PollutionType.SpreadingProperties spreadingProperties = getSpreadingProperties(pollutionType);
        
        if (holder.getPos().x % 2 == holder.getPos().z % 2) { // Only spread to/from chunks in a checkerboard fashion, as it is really the Chunk boundaries we want to tick
            final List<ChunkPos> adjacentPositions = new ArrayList<>(List.of(new ChunkPos(holder.getPos().x - 1, holder.getPos().z), new ChunkPos(holder.getPos().x + 1, holder.getPos().z), new ChunkPos(holder.getPos().x, holder.getPos().z - 1), new ChunkPos(holder.getPos().x, holder.getPos().z + 1)));
            RandomHelper.shuffle(adjacentPositions, random);
            for (ChunkPos otherPos : adjacentPositions) {
                final ChunkAccess otherChunk = level.getChunk(otherPos.x, otherPos.z, ChunkStatus.FULL, false);
                if (otherChunk == null) continue;
                final ChunkPollution otherPollution = otherChunk.getData(DestroyAttachmentTypes.CHUNK_POLLUTION);
                if (random.nextFloat() > spreadingProperties.chance()) continue;
                final int transfer = (int)((getPollution(pollutionType) - otherPollution.getPollution(pollutionType)) * spreadingProperties.rate());
                if (transfer == 0) continue;
                sync |= changePollutionUnchecked(pollutionType, -transfer);
                otherPollution.changePollution(pollutionType, transfer);
            };
        };

        return sync;
    };

    public static class Serializer extends Pollution.Serializer<ChunkAccess, ChunkPollution> {

        public Serializer() {
            super(DestroyRegistries.CHUNK_POLLUTION_TYPES);
        };

        @Override
        protected ChunkPollution create(ChunkAccess holder, Map<PollutionType<ChunkAccess>, Integer> values) {
            return new ChunkPollution(holder, values);
        };

        @Override
        public ChunkAccess castHolder(IAttachmentHolder holder) {
            if (holder instanceof ChunkAccess chunk) return chunk;
            throw new IllegalArgumentException();
        };

    };

    @SubscribeEvent
    public static final void onWatchChunk(ChunkWatchEvent.Watch event) {
        event.getChunk().getData(DestroyAttachmentTypes.CHUNK_POLLUTION).syncTo(event.getPlayer());
    };

    @SubscribeEvent
    public static final void onTickChunk(ChunkTickEvent.Post event) {
        event.getChunk().getData(DestroyAttachmentTypes.CHUNK_POLLUTION).tick(event.getChunk().getLevel().getRandom());
    };
    
};
