package petrolpark.mc.destroy.content.processing.trypolithography.keypunch;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.petrolpark.compat.create.core.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import com.petrolpark.compat.create.core.item.directional.DirectionalTransportedItemStack;
import com.petrolpark.compat.create.core.item.directional.IDirectionalOnBelt;
import com.petrolpark.util.BinaryMatrix4x4;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.content.processing.trypolithography.CircuitMaskItem;
import petrolpark.mc.destroy.content.processing.trypolithography.CircuitPatternItem;
import petrolpark.mc.destroy.content.processing.trypolithography.CircuitPuncherHandler;
import petrolpark.mc.destroy.content.processing.trypolithography.keypunch.CircuitPunchingBehaviour.CircuitPunchingSpecifics;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;

/**
 * Punches circuit patterns onto {@link CircuitMaskItem}s passing underneath on a belt. Host for a
 * {@link CircuitPunchingBehaviour} (animation + processing state) plus a
 * {@link DestroyAdvancementBehaviour} (USE_KEYPUNCH / KEYPUNCH_FIVE triggers) plus an inner
 * {@link NamingBehaviour}.
 *
 * <p>Implements {@link ICircuitPuncher} so {@link CircuitPuncherHandler} can resolve a mask's
 * {@code PUNCHED_BY} UUID list back into puncher names for the mask tooltip.</p>
*/
public class KeypunchBlockEntity extends KineticBlockEntity implements ICircuitPuncher, CircuitPunchingSpecifics {

    private int pistonPosition;

    protected boolean namedYet;
    public String name;
    protected UUID uuid;

    public CircuitPunchingBehaviour punchingBehaviour;
    public DestroyAdvancementBehaviour advancementBehaviour;
    public NamingBehaviour namingBehaviour;

    // This is kept for the Advancement
    public int differentPositionsPunched;
    public int previouslyPunched;

    public KeypunchBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        pistonPosition = 0;
        uuid = UUID.randomUUID();
        name = "";
        namedYet = false;
    }

    @Override
    public void initialize() {
        super.initialize();
        Destroy.CIRCUIT_PUNCHER_HANDLER.addPuncher(getLevel(), this);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        punchingBehaviour = new CircuitPunchingBehaviour(this);
        behaviours.add(punchingBehaviour);

        advancementBehaviour = new DestroyAdvancementBehaviour(this, DestroyAdvancementTrigger.USE_KEYPUNCH, DestroyAdvancementTrigger.KEYPUNCH_FIVE);
        behaviours.add(advancementBehaviour);

        namingBehaviour = new NamingBehaviour();
        behaviours.add(namingBehaviour);
    }

    @Override
    public boolean tryProcessOnBelt(DirectionalTransportedItemStack input, AtomicReference<TransportedItemStack> output, boolean simulate) {
        ItemStack stack = input.stack;
        if (!(stack.getItem() instanceof CircuitMaskItem)) return false;
        int pattern = CircuitPatternItem.getPattern(stack);

        int positionToPunch = BinaryMatrix4x4.rotate(getActualPosition(), input.getRotation());
        if (stack.has(DestroyDataComponents.FLIPPED)) positionToPunch = BinaryMatrix4x4.flip(positionToPunch);

        if (BinaryMatrix4x4.is1(pattern, positionToPunch)) return false;

        if (!simulate) {
            ItemStack resultStack = CircuitMaskItem.contaminate(input.stack, uuid);
            if (!(resultStack.getItem() instanceof IDirectionalOnBelt)) {
                output.set(new TransportedItemStack(resultStack));
                return true;
            }
            CircuitPatternItem.putPattern(resultStack, BinaryMatrix4x4.set1(pattern, positionToPunch));
            DirectionalTransportedItemStack result = DirectionalTransportedItemStack.copy(input);
            result.stack = resultStack;
            output.set(result);
            punchingBehaviour.particleItem = resultStack;

            int newPattern = BinaryMatrix4x4.set1(previouslyPunched, positionToPunch); // Record this for the advancement
            if (previouslyPunched != newPattern) {
                differentPositionsPunched++;
                previouslyPunched = newPattern;
                if (differentPositionsPunched >= 5) {
                    advancementBehaviour.awardDestroyAdvancement(DestroyAdvancementTrigger.KEYPUNCH_FIVE);
                }
            }
        }
        return true;
    }

    public int getPistonPosition() {
        return pistonPosition;
    }

    public void setPistonPosition(int position) {
        if (position < 0 || position > 15) position = 0;
        pistonPosition = position;
        previouslyPunched = 0;
        differentPositionsPunched = 0;
        notifyUpdate();
    }

    public int getActualPosition() {
        Direction facing = getBlockState().getValue(KeypunchBlock.HORIZONTAL_FACING);
        int rot;
        switch (facing) {
            case WEST: {
                rot = 3;
                break;
            } case SOUTH: {
                rot = 2;
                break;
            } case EAST: {
                rot = 1;
                break;
            } default: rot = 0;
        }
        int pos = pistonPosition;
        for (int i = 0; i < rot; i++) {
            pos = BinaryMatrix4x4.ROTATED_90[pos];
        }
        return pos;
    }

    @Override
    public void tick() {
        super.tick();
        if (Destroy.CIRCUIT_PUNCHER_HANDLER.getPuncher(getLevel(), uuid) == CircuitPuncherHandler.UNKNOWN) Destroy.CIRCUIT_PUNCHER_HANDLER.addPuncher(level, this);
        if (!namedYet && !getLevel().isClientSide()) {
            Player player = namingBehaviour.getPlayer();
            if (player != null && player instanceof ServerPlayer serverPlayer) {
                // Client receives, picks random name from NameLists keypunch.json, echoes back via NameKeypunchC2SPacket.
                CatnipServices.NETWORK.sendToClient(serverPlayer, new RequestKeypunchNameS2CPacket(getBlockPos()));
            } else {
                name = "Unnamed";
            }
            namedYet = true;
        }
    }

    @Override
    public void onPunchingCompleted() {
        advancementBehaviour.awardDestroyAdvancement(DestroyAdvancementTrigger.USE_KEYPUNCH);
    }

    @Override
    public float getKineticSpeed() {
        return getSpeed();
    }

    @Override
    protected AABB createRenderBoundingBox() {
        return new AABB(worldPosition).expandTowards(0d, -1.5d, 0d);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        if (compound.contains("PatternIvePunched", Tag.TAG_INT)) {
            previouslyPunched = compound.getInt("PatternIvePunched");
            differentPositionsPunched = compound.getInt("CountPositionsIvePunched");
        }
        pistonPosition = compound.getInt("PunchPosition");
        uuid = compound.getUUID("UUID");
        name = compound.getString("Name");
        namedYet = !compound.contains("NamedYet");
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        if (advancementBehaviour.getPlayer() != null) {
            compound.putInt("PatternIvePunched", previouslyPunched);
            compound.putInt("CountPositionsIvePunched", differentPositionsPunched);
        }
        compound.putInt("PunchPosition", pistonPosition);
        compound.putUUID("UUID", uuid);
        if (name != null) compound.putString("Name", name);
        if (!namedYet) compound.putBoolean("NamedYet", false);
    }

    @Override
    public UUID getUUID() {
        return uuid;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void invalidate() {
        Destroy.CIRCUIT_PUNCHER_HANDLER.removePuncher(getLevel(), this);
        super.invalidate();
    }

    public class NamingBehaviour extends AbstractRememberPlacerBehaviour {

        public static BehaviourType<NamingBehaviour> TYPE = new BehaviourType<>();

        public NamingBehaviour() {
            super(KeypunchBlockEntity.this);
        }

        @Override
        public boolean shouldRememberPlacer(Player placer) {
            return !namedYet;
        }

        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

    }

}
