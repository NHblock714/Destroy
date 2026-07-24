package petrolpark.mc.destroy.core.chemistry.vat.material;

import java.util.HashMap;
import java.util.Map;

import petrolpark.mc.library.core.data.recipe.ingredient.BlockIngredient;

import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import petrolpark.mc.destroy.DestroyPackets;

/**
 * Server → client packet: sync the {@code BlockIngredient → VatMaterial} datapack map so clients
 * can evaluate whether a placed Vat-side block is a valid Vat shell material + know its
 * pressure/conductivity/transparency properties for rendering.
*/
public record SyncVatMaterialsS2CPacket(Map<BlockIngredient<?>, VatMaterial> materials) implements ClientboundPacketPayload {

    public static final StreamCodec<RegistryFriendlyByteBuf, SyncVatMaterialsS2CPacket> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public SyncVatMaterialsS2CPacket decode(RegistryFriendlyByteBuf buffer) {
                int count = buffer.readVarInt();
                Map<BlockIngredient<?>, VatMaterial> materials = new HashMap<>(count);
                for (int i = 0; i < count; i++) {
                    BlockIngredient<?> ingredient = BlockIngredient.read(buffer);
                    materials.put(ingredient,
                        new VatMaterial(buffer.readFloat(), buffer.readFloat(), buffer.readBoolean(), false));
                }
                return new SyncVatMaterialsS2CPacket(materials);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, SyncVatMaterialsS2CPacket packet) {
                buffer.writeVarInt(packet.materials.size());
                packet.materials.forEach((blockIngredient, material) -> {
                    BlockIngredient.write(blockIngredient, buffer);
                    buffer.writeFloat(material.maxPressure());
                    buffer.writeFloat(material.thermalConductivity());
                    buffer.writeBoolean(material.transparent());
                });
            }
        };

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return DestroyPackets.SYNC_VAT_MATERIALS;
    }

    @Override
    public void handle(LocalPlayer player) {
        VatMaterial.clearDatapackMaterials();
        VatMaterial.BLOCK_MATERIALS.putAll(materials);
        // TODO(T2a closer): DestroyPonderTags.refreshVatMaterialsTag() — rebuild Ponder vat_materials
        // tag from updated BLOCK_MATERIALS map. Not yet ported to 1.21 DestroyPonderTags; cosmetic
        // only (affects Ponder index listing, not gameplay).
    }
}
