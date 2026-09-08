package petrolpark.mc.destroy.content.processing.cooler;

import java.util.List;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.fluid.SmartFluidTank;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.math.AngleHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.DestroyTags;
import petrolpark.mc.destroy.chemistry.api.util.Constants;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.config.DestroyConfigs;
import petrolpark.mc.destroy.core.pollution.PollutionHelper;

/**
 * Refrigerstrayter BE — consumes refrigerant-Molecule Mixture fluids or tagged COOLANT fluids
 * and converts them into "cooling ticks". While coolingTicks > 0 the Block is in FROSTING mode,
 * providing a cold heat-source effect to the Basin above (analogous to Create's BlazeBurner
 * providing heat). Visual: animated head (StrayPartial) that follows the player + snowflake
 * particles when FROSTING.
*/
public class CoolerBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final int TANK_CAPACITY = 1000;

    private SmartFluidTankBehaviour tank;

    public int coolingTicks;
    protected LerpedFloat headAnimation;
    protected LerpedFloat headAngle;

    public CoolerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        coolingTicks = 0;
        headAnimation = LerpedFloat.linear();
        headAngle = LerpedFloat.angular();
        headAngle.startWithValue((AngleHelper.horizontalAngle(Direction.NORTH) + 180) % 360);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 1, TANK_CAPACITY, true)
            .whenFluidUpdates(this::consumeFluid)
            .forbidExtraction();
        behaviours.add(tank);
    }

    private void consumeFluid() {
        if (!hasLevel()) return;
        // A virtual BE (one inside a Mechanical Bearing contraption) must not run any of the
        // fluid consumption side effects: setColdnessOfBlock, PollutionHelper.pollute and
        // setBlockAndUpdate all mutate the world at the pre-assembly worldPosition, placing a
        // phantom Cooler, leaking pollution, and convincing the Vat below that the Cooler is
        // still there, which condenses liquid air out of nowhere.
        if (isVirtual()) return;

        float coolingPower = 0f;

        FluidStack fluidStack = tank.getPrimaryHandler().getFluid();
        if (DestroyFluids.isMixture(fluidStack)) {
            int amount = fluidStack.getAmount();
            ReadOnlyMixture mixture = ReadOnlyMixture.readNBT(ReadOnlyMixture::new,
                fluidStack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));

            float totalMolesPerBucket = 0f;
            float totalRefrigerantMolesPerBucket = 0f;
            for (LegacySpecies molecule : mixture.getContents(true)) {
                float concentration = mixture.getConcentrationOf(molecule);
                totalMolesPerBucket += concentration;
                if (molecule.hasTag(DestroyMolecules.Tags.REFRIGERANT)) {
                    totalRefrigerantMolesPerBucket += concentration;
                    coolingPower += DestroyConfigs.server().blocks.coolerEfficiency.getF()
                        * concentration * amount * molecule.getMolarHeatCapacity() * 10
                        / Constants.MILLIBUCKETS_PER_LITER;
                }
            }

            if (DestroyConfigs.server().blocks.coolerEnhancedByPurity.get()) {
                coolingPower *= totalRefrigerantMolesPerBucket / totalMolesPerBucket;
            }
        } else if (fluidStack.getFluid().is(DestroyTags.Fluids.COOLANT.tag)) {
            coolingPower += fluidStack.getAmount(); // 1 bucket of coolant = 50s of cooling
        }

        if (coolingPower > 0f) {
            setColdnessOfBlock(ColdnessLevel.FROSTING);
            coolingTicks += coolingPower;
            if (coolingTicks >= getMaxCoolingTicks()) {
                tank.forbidInsertion();
            }
        }

        tank.getPrimaryHandler().drain(TANK_CAPACITY, FluidAction.EXECUTE);
        PollutionHelper.pollute(getLevel(), getBlockPos(), fluidStack);

        notifyUpdate();
    }

    @Override
    public void tick() {
        super.tick();
        if (!hasLevel()) return;

        if (getLevel().isClientSide()) {
            tickAnimation();
            if (!isVirtual()) spawnParticles(getColdnessFromBlock());
            return;
        }

        // same guard as consumeFluid. In contraption-virtual mode, decrementing
        // coolingTicks is harmless in itself, but if it reaches 0 we'd call
        // setColdnessOfBlock → setBlockAndUpdate at the BE's worldPosition (= original
        // anchor pos), placing a phantom cooler block in the world during contraption time.
        // Skip the entire body when virtual.
        if (isVirtual()) return;

        if (coolingTicks > 0) {
            coolingTicks--;
            if (coolingTicks < getMaxCoolingTicks()) tank.allowInsertion();
            if (coolingTicks <= 0) setColdnessOfBlock(ColdnessLevel.IDLE);
            sendData();
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        coolingTicks = tag.getInt("Timer");
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Timer", coolingTicks);
    }

    public SmartFluidTank getInputTank() {
        return tank.getPrimaryHandler();
    }

    public LerpedFloat getHeadAnimation() {
        return headAnimation;
    }

    public LerpedFloat getHeadAngle() {
        return headAngle;
    }

    /**
 * Trick the game into thinking this is a Blaze Burner with FROSTING heat level. The FROSTING
 * enum value is injected into Create's HeatLevel via {@link petrolpark.mc.destroy.mixin.compat.create.HeatLevelMixin}
 *. {@code HeatLevel.valueOf("FROSTING")} resolves at
 * runtime after the mixin applies at class-load; using {@code valueOf} rather than a direct
 * field reference keeps the code compilable without a bytecode-produced enum constant.
*/
    public void updateHeatLevel(ColdnessLevel coldnessLevel) {
        if (!hasLevel()) return;
        // A virtual (contraption) BE must not setBlockAndUpdate at its worldPosition: that pos is
        // the original anchor, left as air once the Bearing captured this BE, and writing there
        // resurrects a phantom Cooler with the FROSTING heat level. The Vat below reads that as
        // the Cooler still being present, keeps cooling, and condenses liquid air.
        if (isVirtual()) return;
        HeatLevel targetHeat = coldnessLevel == ColdnessLevel.FROSTING
            ? HeatLevel.valueOf("FROSTING")
            : HeatLevel.NONE;
        BlockState newState = getBlockState().setValue(BlazeBurnerBlock.HEAT_LEVEL, targetHeat);
        if (!newState.equals(getBlockState())) {
            getLevel().setBlockAndUpdate(getBlockPos(), newState);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void tickAnimation() {
        float target = 0;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !player.isInvisible()) {
            double x;
            double z;
            if (isVirtual()) {
                x = -4;
                z = -10;
            } else {
                x = player.getX();
                z = player.getZ();
            }
            double dx = x - (getBlockPos().getX() + 0.5);
            double dz = z - (getBlockPos().getZ() + 0.5);
            target = AngleHelper.deg(-Mth.atan2(dz, dx)) - 90;
        }
        target = headAngle.getValue() + AngleHelper.getShortestAngleDiff(headAngle.getValue(), target);
        headAngle.chase(target, 0.25f, LerpedFloat.Chaser.exp(5));
        headAngle.tickChaser();

        headAnimation.chase(validBlockAbove() ? 1 : 0, 0.25f, LerpedFloat.Chaser.exp(0.25f));
        headAnimation.tickChaser();
    }

    protected void spawnParticles(ColdnessLevel coldnessLevel) {
        if (!hasLevel()) return;
        if (coldnessLevel == ColdnessLevel.NONE) return;

        RandomSource r = getLevel().getRandom();

        Vec3 c = VecHelper.getCenterOf(getBlockPos());
        Vec3 v = c.add(VecHelper.offsetRandomly(Vec3.ZERO, r, 0.125f).multiply(1, 0, 1));

        if (r.nextInt(coldnessLevel == ColdnessLevel.IDLE ? 32 : 2) != 0) return;

        boolean empty = level.getBlockState(getBlockPos().above())
            .getCollisionShape(getLevel(), getBlockPos().above())
            .isEmpty();

        if (empty || r.nextInt(8) == 0) getLevel().addParticle(ParticleTypes.SNOWFLAKE, v.x, v.y, v.z, 0, 0.07d, 0);
    }

    private boolean validBlockAbove() {
        if (!hasLevel()) return false;
        BlockState blockState = getLevel().getBlockState(worldPosition.above());
        return AllBlocks.BASIN.has(blockState) || blockState.getBlock() instanceof FluidTankBlock;
    }

    /**
 * Capability registration: fluid handler only accessible from the DOWN face.
*/
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            DestroyBlockEntityTypes.COOLER.get(),
            (be, context) -> context == Direction.DOWN ? be.tank.getCapability() : null);
    }

    public ColdnessLevel getColdnessFromBlock() {
        return CoolerBlock.getColdnessLevelOf(getBlockState());
    }

    public void setColdnessOfBlock(ColdnessLevel coldnessLevel) {
        if (!hasLevel()) return;
        // same virtual-mode guard as updateHeatLevel.
        if (isVirtual()) return;
        getLevel().setBlockAndUpdate(getBlockPos(),
            getBlockState().setValue(CoolerBlock.COLD_LEVEL, coldnessLevel));
        updateHeatLevel(coldnessLevel);
    }

    public static enum ColdnessLevel implements StringRepresentable {
        NONE,
        IDLE,
        FROSTING;

        @Override
        public String getSerializedName() {
            return Lang.asId(name());
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (coolingTicks <= 0) return false;
        final String timeRemaining;
        int seconds = (coolingTicks % 1200) / 20;
        if (coolingTicks < 72000) {
            timeRemaining = "" + coolingTicks / 1200 + ":" + (seconds < 10 ? "0" : "") + seconds;
        } else {
            timeRemaining = DestroyLang.translate("tooltip.cooler.long_time_remaining").string();
        }
        DestroyLang.translate("tooltip.cooler.time_remaining", timeRemaining).forGoggles(tooltip);
        return true;
    }

    /** How much the Cooler will fill before stopping any excess Fluid. Default: 10 minutes.*/
    public int getMaxCoolingTicks() {
        return DestroyConfigs.server().blocks.maximumCoolingTicks.get();
    }
}
