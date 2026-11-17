package petrolpark.mc.destroy.core.extendedinventory;

import io.netty.buffer.ByteBuf;

import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ServerboundPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;

import petrolpark.mc.destroy.DestroyPackets;

/**
 * Client → server: "I just opened my inventory screen — please re-broadcast the full state of my
 * {@code inventoryMenu} to me." Used to defeat the desync that arises when the client's
 * inventory screen opens before its {@link ExtendedInventory} has been told about its
 * server-authoritative size.
*/
public final class RequestInventoryFullStateC2SPacket implements ServerboundPacketPayload {

    public static final RequestInventoryFullStateC2SPacket INSTANCE = new RequestInventoryFullStateC2SPacket();
    public static final StreamCodec<ByteBuf, RequestInventoryFullStateC2SPacket> STREAM_CODEC =
        StreamCodec.unit(INSTANCE);

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return DestroyPackets.REQUEST_INVENTORY_FULL_STATE;
    }

    @Override
    public void handle(ServerPlayer player) {
        // Creative cursor stacks are created locally; the server only owns the inventory slots.
        if (player.isCreative()) {
            for (var slot : player.inventoryMenu.slots) {
                if (slot.container == player.getInventory()) {
                    player.connection.send(new ClientboundContainerSetSlotPacket(-2,
                        player.inventoryMenu.getStateId(), slot.getSlotIndex(), slot.getItem()));
                }
            }
            return;
        }
        player.inventoryMenu.broadcastFullState();
        player.inventoryMenu.sendAllDataToRemote();
    }
}
