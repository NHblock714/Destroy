package petrolpark.mc.destroy.core.pollution;

import java.util.HashMap;
import java.util.Map;

import petrolpark.mc.library.util.codec.CodecHelper;

import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import petrolpark.mc.destroy.DestroyAttachmentTypes;
import petrolpark.mc.destroy.DestroyPackets;
import petrolpark.mc.destroy.DestroyRegistries;

public record ChunkPollutionPacket(ChunkPos pos, Map<PollutionType<ChunkAccess>, Integer> values) implements ClientboundPacketPayload {
    
    public static final StreamCodec<RegistryFriendlyByteBuf, ChunkPollutionPacket> STREAM_CODEC = StreamCodec.composite(
        CodecHelper.CHUNK_POS_STREAM, ChunkPollutionPacket::pos,
        ByteBufCodecs.map(HashMap::new, ByteBufCodecs.registry(DestroyRegistries.Keys.CHUNK_POLLUTION_TYPE), ByteBufCodecs.VAR_INT), ChunkPollutionPacket::values,
        ChunkPollutionPacket::new
    );

    @Override
    public PacketTypeProvider getTypeProvider() {
        return DestroyPackets.CHUNK_POLLUTION;
    };

    @Override
    public void handle(LocalPlayer player) {
        player.level().getChunk(pos().x, pos().z).getData(DestroyAttachmentTypes.CHUNK_POLLUTION).setValues(values());
        ClientPollutionEvents.refreshSmog(pos());
    };
};
