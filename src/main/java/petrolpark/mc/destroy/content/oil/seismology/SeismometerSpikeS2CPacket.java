package petrolpark.mc.destroy.content.oil.seismology;

import io.netty.buffer.ByteBuf;

import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.codec.StreamCodec;

import petrolpark.mc.destroy.DestroyPackets;

/** Payload is
 * empty; the mere arrival is the signal.
*/
public final class SeismometerSpikeS2CPacket implements ClientboundPacketPayload {

    public static final SeismometerSpikeS2CPacket INSTANCE = new SeismometerSpikeS2CPacket();
    public static final StreamCodec<ByteBuf, SeismometerSpikeS2CPacket> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    private SeismometerSpikeS2CPacket() {}

    @Override
    public PacketTypeProvider getTypeProvider() {
        return DestroyPackets.SEISMOMETER_SPIKE;
    }

    @Override
    public void handle(LocalPlayer player) {
        // Trigger the spike animation: this flips SeismometerItemRenderer.spikeNextPage to
        // 32 ticks, causing the renderer to show PAGE_SPIKE on its next 32-tick animation cycle
        // and the needle to execute the 4-phase jitter pattern (-30° → 30° → 10° → -10°).
        SeismometerItemRenderer.spike();
    }
}
