package petrolpark.mc.destroy.core.fluid.gasparticle;

import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.net.base.BasePacketPayload;
import net.createmod.catnip.net.base.ClientboundPacketPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.DestroyPackets;
import petrolpark.mc.destroy.client.DestroyParticleTypes;

/**
 * Server → client packet summoning evaporating fluid particles at a given BlockPos. 5 particles
 * spawned at the block's center with upward initial velocity. Fired by
 * {@link petrolpark.mc.destroy.core.pollution.PollutingOpenEndedPipeEffectHandler} (~5% chance
 * per pollution-fluid spray) to sync the visual effect across clients.
 *
 * <p>Registration: add to {@link DestroyPackets} enum.</p>
*/
public record EvaporatingFluidS2CPacket(BlockPos blockPos, FluidStack fluidStack) implements ClientboundPacketPayload {

    /** Uses the OPTIONAL stream codec so empty FluidStacks are allowed (e.g. a catalytic
 * converter that fully consumes the gas it pulls). NeoForge 1.21's default
 * {@code FluidStack.STREAM_CODEC} throws {@code EncoderException("Empty FluidStack not allowed")}
 * on encode of empty stacks, which crashes the server connection (symptom: extracting gas from
 * the vat to a catalytic converter disconnected the player from the save). The OPTIONAL variant
 * accepts empty as a valid sentinel.
*/
    public static final StreamCodec<RegistryFriendlyByteBuf, EvaporatingFluidS2CPacket> STREAM_CODEC =
        StreamCodec.composite(
            BlockPos.STREAM_CODEC,            EvaporatingFluidS2CPacket::blockPos,
            FluidStack.OPTIONAL_STREAM_CODEC, EvaporatingFluidS2CPacket::fluidStack,
            EvaporatingFluidS2CPacket::new);

    @Override
    public BasePacketPayload.PacketTypeProvider getTypeProvider() {
        return DestroyPackets.EVAPORATING_FLUID;
    }

    @Override
    @SuppressWarnings("resource")
    public void handle(LocalPlayer player) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || fluidStack.isEmpty()) return;
        Vec3 center = VecHelper.getCenterOf(blockPos);
        GasParticleData particleData = new GasParticleData(DestroyParticleTypes.EVAPORATION.get(), fluidStack);
        for (int i = 0; i < 5; i++) {
            level.addParticle(particleData, center.x, center.y, center.z, 0d, 0.07D, 0d);
        }
    }
}
