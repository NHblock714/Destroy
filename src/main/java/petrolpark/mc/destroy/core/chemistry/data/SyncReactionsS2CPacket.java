package petrolpark.mc.destroy.core.chemistry.data;

import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;

import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyPackets;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;

/**
 * Server → client packet: sync the datapack-defined reaction set so multiplayer clients can
 * see them in JEI and have a complete {@link LegacyReaction#REACTIONS} map for tooltip / lookup
 * code that reads the registry client-side.
 *
 * <p>Sent from {@link ReactionDataReloadListener#apply} (broadcast to all online players when a
 * datapack reload completes) and from the {@code OnDatapackSyncEvent} per-player handler in
 * {@code DestroyCommonEvents} (so late-joining players also receive the current set).</p>
 *
 * <p>In single player the same JVM hosts both sides and the server-side apply already populates
 * {@code REACTIONS}; the client-side handler is therefore a no-op except for the JEI runtime
 * recipe-manager update (the {@code addRecipes} lambdas have already executed by that time).</p>
 */
public record SyncReactionsS2CPacket(Map<ResourceLocation, ReactionDefinition> reactions)
    implements ClientboundPacketPayload {

    /** Codec for a single {@code (id, definition)} entry. Used to build the stream codec. */
    private static final Codec<Map.Entry<ResourceLocation, ReactionDefinition>> ENTRY_CODEC =
        com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Map.Entry::getKey),
            ReactionDefinition.CODEC.fieldOf("def").forGetter(Map.Entry::getValue)
        ).apply(i, Map::entry));

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncReactionsS2CPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncReactionsS2CPacket decode(RegistryFriendlyByteBuf buffer) {
                int count = buffer.readVarInt();
                Map<ResourceLocation, ReactionDefinition> map = new LinkedHashMap<>(count);
                for (int i = 0; i < count; i++) {
                    ResourceLocation id = ResourceLocation.STREAM_CODEC.decode(buffer);
                    ReactionDefinition def = ByteBufCodecs.fromCodec(ReactionDefinition.CODEC).decode(buffer);
                    map.put(id, def);
                }
                return new SyncReactionsS2CPacket(map);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, SyncReactionsS2CPacket packet) {
                buffer.writeVarInt(packet.reactions.size());
                for (Map.Entry<ResourceLocation, ReactionDefinition> e : packet.reactions.entrySet()) {
                    ResourceLocation.STREAM_CODEC.encode(buffer, e.getKey());
                    ByteBufCodecs.fromCodec(ReactionDefinition.CODEC).encode(buffer, e.getValue());
                }
            }
        };

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return DestroyPackets.SYNC_REACTIONS;
    }

    @Override
    public void handle(LocalPlayer player) {
        // In single player both sides share REACTIONS and species indexes; clearing and
        // reapplying is redundant work but harmless (the clear method only removes entries
        // marked as datapack, then the apply rebuilds them through the same ReactionBuilder
        // pipeline the server already ran). In dedicated-server play this is essential.
        LegacyReaction.clearDatapackReactions();
        int ok = 0;
        int skipped = 0;
        for (Map.Entry<ResourceLocation, ReactionDefinition> entry : reactions.entrySet()) {
            try {
                if (entry.getValue().apply(entry.getKey())) ok++;
                else skipped++;
            } catch (Throwable t) {
                Destroy.LOGGER.warn("Failed to apply synced datapack reaction {}: {}",
                    entry.getKey(), t.getMessage());
                skipped++;
            }
        }
        Destroy.LOGGER.info("Received {} datapack reaction(s) from server; {} applied, {} skipped.",
            reactions.size(), ok, skipped);

        // Refresh JEI's ReactionCategory recipe map + push new entries to JEI runtime so the
        // category page picks them up without a /jei reload.
        if (petrolpark.mc.library.compat.Mods.JEI.isLoading()) {
            petrolpark.mc.destroy.compat.jei.DestroyJEI.refreshDatapackReactionsClientSide();
        }
    }
}
