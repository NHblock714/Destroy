package petrolpark.mc.destroy.content.oil.pumpjack;

import java.lang.ref.WeakReference;
import java.util.List;

import petrolpark.mc.library.compat.create.core.world.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.DestroyAttachmentTypes;
import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.DestroySoundEvents;
import petrolpark.mc.destroy.config.DestroyAllConfigs;
import petrolpark.mc.destroy.content.oil.ChunkCrudeOil;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;
import petrolpark.mc.destroy.core.pollution.PollutingBehaviour;

/**
 * Full BlockEntity for the Pumpjack — owns the fluid output tank, ticks the cam to drive the
 * pumping animation, and pulls oil from the facing chunk's {@link ChunkCrudeOil} attachment on
 * each tick when kinetically powered. Sound effects (pumpjack creaks) play on the client side in
 * sync with the cam's rotation angle.
*/
public class PumpjackBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    public SmartFluidTankBehaviour tank;
    protected PollutingBehaviour pollutionBehaviour;
    protected RememberPlacerForOilDiscoveryBehaviour prospectingBehaviour;
    protected DestroyAdvancementBehaviour advancementBehaviour;

    public WeakReference<PumpjackCamBlockEntity> source;

    private boolean upsqueak; // Client-only

    public PumpjackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        source = new WeakReference<>(null);
        upsqueak = false;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.OUTPUT, this, 1, getTankCapacity(), false)
            .forbidInsertion();
        behaviours.add(tank);

        advancementBehaviour = new DestroyAdvancementBehaviour(this, DestroyAdvancementTrigger.USE_PUMPJACK);
        behaviours.add(advancementBehaviour);

        pollutionBehaviour = new PollutingBehaviour(this);
        behaviours.add(pollutionBehaviour);

        prospectingBehaviour = new RememberPlacerForOilDiscoveryBehaviour(this);
        behaviours.add(prospectingBehaviour);
    }

    
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            DestroyBlockEntityTypes.PUMPJACK.get(),
            (be, side) -> {
                Direction facing = PumpjackBlock.getFacing(be.getBlockState());
                if (side == null || (facing.getAxis() != side.getAxis() && side.getAxis() != Axis.Y)) {
                    return be.tank.getCapability();
                }
                return null;
            });
    }

    @Override
    public void tick() {
        super.tick();
        PumpjackCamBlockEntity cam = getCam();

        // Updating cam
        if (!hasLevel()) return;
        if (cam == null) return;
        if (!cam.getBlockPos()
            .subtract(getBlockPos())
            .equals(cam.pumpjackPos))
        {
            cam.update(getBlockPos());
            sendData();
            return;
        }
        Direction facing = PumpjackBlock.getFacing(getBlockState());
        if (getLevel().isLoaded(getBlockPos().relative(facing.getOpposite()))) cam.update(getBlockPos());

        // Sounds
        if (getLevel().isClientSide()) {
            playClientSound();
            return;
        }

        // Pumping oil
        if (cam.getSpeed() == 0) return;
        BlockPos pipePos = getBlockPos().relative(facing);
        LevelChunk chunk = level.getChunkAt(pipePos);
        ChunkCrudeOil crudeOil = chunk.getData(DestroyAttachmentTypes.CHUNK_CRUDE_OIL);
        crudeOil.generate(chunk, prospectingBehaviour.getPlayer());
        int oilAmount = crudeOil.getAmount();
        if (oilAmount == 0) return;
        advancementBehaviour.awardDestroyAdvancement(DestroyAdvancementTrigger.USE_PUMPJACK);
        // Add the oil to the Pumpjack's internal tank
        tank.allowInsertion();
        // 上游 0.1.3-i+1 修复 "Fixed pumpjacks outputting flowing crude oil instead of the
        // correct fluid (this broke a few recipes, notably ANFO)"。Registrate FluidEntry.get() 返回
        // FLOWING 变体，必须用 .getSource() 拿 SOURCE 变体才能与 ANFO 等 recipe 的 fluid 输入匹配。
        // 显式 cast 到 Fluid 消歧义 — getSource() 是 <S extends BaseFlowingFluid> S，否则编译器在
        // FluidStack(Fluid, int) 与 FluidStack(Holder<Fluid>, int) 两个 ctor 之间不能选。
        net.minecraft.world.level.material.Fluid sourceFluid = DestroyFluids.CRUDE_OIL.getSource();
        int amountPumped = tank.getPrimaryHandler().fill(
            new FluidStack(sourceFluid,
                (int) Math.min(oilAmount,
                    DestroyAllConfigs.SERVER.blocks.pumpjackExtractionSpeed.getF() * Math.abs(cam.getSpeed() / 16f))),
            FluidAction.EXECUTE);
        tank.forbidInsertion();
        crudeOil.decreaseAmount(amountPumped);
    }

    public Float getTargetAngle() {
        float angle = 0;
        BlockState blockState = getBlockState();
        if (!DestroyBlocks.PUMPJACK.has(blockState)) return null;

        PumpjackCamBlockEntity cam = getCam();

        if (cam == null) return null;

        Direction facing = PumpjackBlock.getFacing(blockState);
        Axis axis = KineticBlockEntityRenderer.getRotationAxisOf(cam);
        angle = KineticBlockEntityRenderer.getAngleForBe(cam, cam.getBlockPos(), axis);

        if (axis.isHorizontal() && (facing.getAxis() == Axis.X ^ facing.getAxisDirection() == AxisDirection.NEGATIVE))
            angle *= -1;

        return angle;
    }

    public float getRenderAngle() {
        Float angle = getTargetAngle();
        if (angle == null) {
            BlockState blockState = getBlockState();
            Direction facing = PumpjackBlock.getFacing(blockState);
            Direction.Axis axis = facing.getCounterClockWise().getAxis();
            BlockPos pos = getCamPos();
            double d = (((axis == Direction.Axis.X) ? 0 : pos.getX()) + ((axis == Direction.Axis.Y) ? 0 : pos.getY())
                + ((axis == Direction.Axis.Z) ? 0 : pos.getZ())) % 2;
            angle = d == 0 ? 22.5f * Mth.PI / 180.f : 0f;

            if (axis.isHorizontal() && (facing.getAxis() == Axis.X ^ facing.getAxisDirection() == AxisDirection.NEGATIVE))
                angle *= -1;
        }
        return angle;
    }

    @OnlyIn(Dist.CLIENT)
    public void playClientSound() {
        if (getTargetAngle() == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        // Sound is synchronised to Pumpjack movement
        if (Math.abs(getTargetAngle()) <= 0.1f && upsqueak) {
            DestroySoundEvents.PUMPJACK_CREAK_1.play(getLevel(), minecraft.player, getBlockPos());
            upsqueak = false;
        }
        if (Math.abs(Math.abs(getTargetAngle()) - Mth.PI) < 0.1 && !upsqueak) {
            DestroySoundEvents.PUMPJACK_CREAK_2.play(getLevel(), minecraft.player, getBlockPos());
            upsqueak = true;
        }
    }

    public BlockPos getCamPos() {
        Direction facing = PumpjackBlock.getFacing(getBlockState());
        return getBlockPos().relative(facing, 1);
    }

    public PumpjackCamBlockEntity getCam() {
        PumpjackCamBlockEntity cam = source.get();
        if (cam == null || cam.isRemoved() || !cam.canPower(getBlockPos())) {
            if (cam != null) source = new WeakReference<>(null);
            BlockEntity anyCamAt = getLevel().getBlockEntity(getCamPos());
            if (anyCamAt instanceof PumpjackCamBlockEntity newCam && newCam.canPower(getBlockPos())) {
                cam = newCam;
                source = new WeakReference<>(cam);
            }
        }
        return cam;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        return containedFluidTooltip(tooltip, isPlayerSneaking, tank.getCapability());
    }

    public class RememberPlacerForOilDiscoveryBehaviour extends AbstractRememberPlacerBehaviour {

        public RememberPlacerForOilDiscoveryBehaviour(SmartBlockEntity be) {
            super(be);
        }

        public static BehaviourType<?> TYPE = new BehaviourType<>();

        @Override
        public boolean shouldRememberPlacer(Player placer) {
            LevelChunk chunk = getLevel().getChunkAt(getBlockPos());
            if (chunk == null) return true;
            return chunk.getData(DestroyAttachmentTypes.CHUNK_CRUDE_OIL).isGenerated();
        }

        @Override
        public BehaviourType<?> getType() {
            return TYPE;
        }

    }

    public int getTankCapacity() {
        return DestroyAllConfigs.SERVER.blocks.pumpjackCapacity.get();
    }

}
