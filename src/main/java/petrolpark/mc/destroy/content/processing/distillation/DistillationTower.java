package petrolpark.mc.destroy.content.processing.distillation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.recipe.RecipeFinder;

import net.createmod.catnip.data.Iterate;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.config.DestroyAllConfigs;
import petrolpark.mc.destroy.core.pollution.PollutionHelper;

/**
 * Non-BlockEntity "tower" object — owned by the controller {@link BubbleCapBlockEntity} (the
 * bottom Bubble Cap). Tracks the ordered list of BubbleCaps making up the tower, finds + runs
 * {@link DistillationRecipe}s against the controller's input tank, and distributes fluid
 * fractions up the tower's internal tanks.
*/
public class DistillationTower {

    private static final Object distillationRecipeKey = new Object();

    private BlockPos position; // The bottom of the Distillation Tower
    private List<BubbleCapBlockEntity> bubbleCaps;
    private DistillationRecipe lastRecipe;
    private int tick;

    public DistillationTower(Level level, BlockPos controllerPos) {
        position = controllerPos;
        bubbleCaps = new ArrayList<>();
        tick = getProcessingTime();
        int i = 0;
        while (true) {
            BlockEntity be = level.getBlockEntity(controllerPos.above(i));
            if (be == null || !(be instanceof BubbleCapBlockEntity bubbleCap)) {
                break;
            } else {
                addBubbleCap(bubbleCap);
            }
            i++;
        }
    }

    public int getProcessingTime() {
        return DestroyAllConfigs.SERVER.blocks.bubbleCapRecipeFrequency.get();
    }

    /** Reconstruct a tower from persisted NBT — scan upward from {@code pos} for Bubble Caps.*/
    public DistillationTower(CompoundTag compound, Level level, BlockPos pos) {
        position = pos;
        tick = compound.getInt("Tick");
        int height = compound.getInt("Height");
        bubbleCaps = new ArrayList<>();
        for (int i = 0; i < height; i++) {
            BlockEntity be = level.getBlockEntity(position.above(i));
            if (be == null || !(be instanceof BubbleCapBlockEntity bubbleCap)) {
                if (!level.isClientSide()) {
                    // Silent — a partial tower on chunk-load can happen normally during world gen
                }
                break;
            } else {
                addBubbleCap(bubbleCap);
            }
        }
    }

    public BlockPos getControllerPos() {
        return position;
    }

    public int getHeight() {
        return bubbleCaps.size();
    }

    public BubbleCapBlockEntity getControllerBubbleCap() {
        return getHeight() == 0 ? null : bubbleCaps.get(0);
    }

    /**
 * Adds a Bubble Cap to the top of the Distillation Tower. The Bubble Cap will only be added if
 * it is not already in this Distillation Tower.
*/
    public void addBubbleCap(BubbleCapBlockEntity bubbleCap) {
        if (bubbleCaps.contains(bubbleCap)) return;
        bubbleCap.addToDistillationTower(this);
        bubbleCaps.add(bubbleCap);
        BubbleCapBlockEntity controller = getControllerBubbleCap();
        if (controller != null) controller.sendData();
    }

    /** Removes this Bubble Cap and all Bubble Caps above it from the Distillation Tower.*/
    public void removeBubbleCap(BubbleCapBlockEntity bubbleCapToRemove) {
        List<BubbleCapBlockEntity> newBubbleCaps = new ArrayList<>();
        for (BubbleCapBlockEntity bubbleCap : bubbleCaps) {
            if (bubbleCap == bubbleCapToRemove) {
                break;
            }
            newBubbleCaps.add(bubbleCap);
        }
        bubbleCaps = newBubbleCaps;
    }

    public void tick(Level level) {
        tick--;
        if (tick <= 0) {
            findRecipe(level);
            process();
            tick = getProcessingTime();
        }
    }

    public void findRecipe(Level level) {
        if (getControllerBubbleCap() == null || level.isClientSide()) return;
        SmartFluidTank inputTank = getControllerBubbleCap().getTank();
        if (lastRecipe == null
            || !lastRecipe.getRequiredFluid().ingredient().test(inputTank.getFluid())
            || !lastRecipe.isValidAt(level, getControllerPos())) {
            List<DistillationRecipe> possibleRecipes =
                RecipeFinder.get(distillationRecipeKey, level, rh -> rh.value().getType() == DestroyRecipeTypes.DISTILLATION.getType())
                    .stream()
                    .map(rh -> (DistillationRecipe) rh.value())
                    .filter(recipe ->
                        recipe.getRequiredFluid().ingredient().test(inputTank.getFluid())
                        && recipe.isValidAt(level, getControllerPos()))
                    .collect(Collectors.toList());
            if (!possibleRecipes.isEmpty()) {
                lastRecipe = possibleRecipes.get(0);
            } else {
                lastRecipe = null;
            }
        }
    }

    /**
 * Applies the current Recipe.
 * @return Whether the Recipe was successfully processed
*/
    public boolean process() {
        BubbleCapBlockEntity controller = getControllerBubbleCap();
        if (controller == null) return false;
        Level level = controller.getLevel();
        if (level == null) return false;

        FluidStack fluidStack = controller.getTank().getFluid();
        if (fluidStack.isEmpty()) return false;

        // Mixture distillation (chemistry phase-separation via boiling-point buckets).
        // Triggers if the controller
        // holds a Mixture fluid with a payload DataComponent; JSON-recipe path below runs otherwise.
        if (petrolpark.mc.destroy.DestroyFluids.isMixture(fluidStack.getFluid()) && fluidStack.has(DestroyDataComponents.MIXTURE)) {
            ReadOnlyMixture mixture = ReadOnlyMixture.readNBT(ReadOnlyMixture::new,
                fluidStack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
            List<FluidStack> fractions = getFractionsOfMixture(mixture, fluidStack.getAmount(), getHeight() - 1);
            if (fractions.size() <= 1) return false;  // only the residue → no distillation

            for (boolean simulate : Iterate.trueAndFalse) {
                // Check if resultant Fluids can fit (skip index 0 = residue, goes back in the reboiler)
                int i = 0;
                for (FluidStack fraction : fractions) {
                    if (i == 0) { i++; continue; }
                    BubbleCapBlockEntity bubbleCap = bubbleCaps.get(i);
                    SmartFluidTank tankToTryFill = simulate ? bubbleCap.getTank() : bubbleCap.getInternalTank();
                    if (simulate && !bubbleCap.getInternalTank().isEmpty()) return false;
                    if (tankToTryFill.fill(fraction, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE) < fraction.getAmount()) return false;
                    if (!simulate) bubbleCap.setTicksToFill(i * BubbleCapBlockEntity.getTankCapacity() / BubbleCapBlockEntity.getTransferRate());
                    i++;
                }
            }

            // Mixture distillation successful: drain full controller tank, refill with residue
            FluidStack fluidDrainedMx = controller.getTank().drain(BubbleCapBlockEntity.getTankCapacity(), FluidAction.EXECUTE);
            controller.getTank().fill(fractions.get(0), FluidAction.EXECUTE);
            controller.particleFluid = fluidDrainedMx.copy();
            controller.onDistill();
            return true;
        }

        // Recipes
        if (lastRecipe == null) return false;
        if (lastRecipe.getFractions() > getHeight() - 1) return false;

        FluidStack fluidDrained = FluidStack.EMPTY;
        for (boolean simulate : Iterate.trueAndFalse) {
            // Check if heat requirement is fulfilled
            HeatLevel heat = BasinBlockEntity.getHeatLevelOf(level.getBlockState(controller.getBlockPos().below(1)));
            if (!lastRecipe.getRequiredHeat().testBlazeBurner(heat)) return false;

            // Check if required Fluid is present
            int requiredFluidAmount = lastRecipe.getRequiredFluid().amount();
            fluidDrained = controller.getTank().drain(requiredFluidAmount, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE);
            if (fluidDrained.getAmount() < requiredFluidAmount) return false;

            // Check if resultant Fluids can fit
            for (int i = 0; i < lastRecipe.getFractions(); i++) {
                FluidStack distillate = lastRecipe.getFluidResults().get(i);
                BubbleCapBlockEntity bubbleCap = bubbleCaps.get(i + 1);

                SmartFluidTank tankToTryFill = simulate ? bubbleCap.getTank() : bubbleCap.getInternalTank();
                if (simulate && !bubbleCap.getInternalTank().isEmpty()) return false;

                if (tankToTryFill.fill(distillate, simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE) < distillate.getAmount()) return false;
                if (!simulate) bubbleCap.setTicksToFill(i * BubbleCapBlockEntity.getTankCapacity() / BubbleCapBlockEntity.getTransferRate());
            }
        }

        // Recipe successfully processed
        controller.particleFluid = fluidDrained.copy();
        controller.onDistill();
        if (controller.advancementBehaviour.getPlayer() instanceof ServerPlayer player) {
            // Synthesize a holder with placeholder ID (ID not used by the criterion's ingredient
            // traversal); bail silently if no matching holder is found.
            RecipeFinder.get(distillationRecipeKey, level, rh -> rh.value() == lastRecipe)
                .stream().findFirst()
                .ifPresent(rh -> player.triggerRecipeCrafted(rh, List.of()));
        }
        return true;
    }

    public CompoundTag serializeNBT() {
        CompoundTag compound = new CompoundTag();
        compound.putInt("Height", getHeight());
        compound.putInt("Tick", tick);
        return compound;
    }

    /** room temperature → gas fraction (single combined FluidStack at index N)</li>
 * <li>Molecules with boiling point &gt; tower maxTemperature → residue fraction (index 0 · reboiler)</li>
 * <li>Remaining molecules → evenly-spaced liquid fraction buckets across
 * [lowestBoilingPoint, highestBoilingPoint] (indices 1..N-1)</li>
 * </ul>
*/
    private List<FluidStack> getFractionsOfMixture(ReadOnlyMixture mixture, int mixtureAmount, int numberOfFractions) {
        List<FluidStack> fractions = new ArrayList<>(numberOfFractions);

        // quantize roomTemperature to nearest 0.1K before using it as the distillate's
        // declared temperature. Rationale: PollutionHelper.getLocalTemperature returns
        // `outdoor + 10*biomeBase`, where `outdoor = 289 + (greenhouse/max)*20 + (ozone/max)*4`
        // drifts smoothly as pollution accumulates. Two consecutive distillations performed in the
        // same biome at the same pos but separated by even one pollution-tick produce mixtures
        // whose temperatures differ by ~10⁻⁴ K — tiny but enough that the encoded MIXTURE
        // DataComponent NBT differs byte-for-byte. Destroy's own GeniusFluidTank.fill recovers
        // by energy-averaging via LegacyMixture.mix, but other mods' tanks (vanilla/Mekanism/
        // Create-default) compare FluidStack tags strictly with FluidStack.isFluidEqual and
        // refuse to stack non-matching NBT. Quantizing to 0.1K is well below physical
        // measurement precision yet large enough to absorb sub-tick pollution drift, restoring
        // cross-mod stackability without affecting chemistry semantics.
        float roomTemperature = PollutionHelper.getLocalTemperature(getControllerBubbleCap().getLevel(), getControllerPos());
        roomTemperature = Math.round(roomTemperature * 10f) / 10f;
        float maxTemperature = Math.max(
            getTemperatureForDistillationTower(getControllerBubbleCap().getLevel(), getControllerPos()),
            mixture.getTemperature());

        if (numberOfFractions == 0) return fractions;
        if (numberOfFractions == 1) return List.of(MixtureFluid.of(mixtureAmount, mixture));

        // Gases (BP < room temp) never condense → group into one FluidStack
        LegacyMixture gasMixture = new LegacyMixture().setTemperature(roomTemperature);
        boolean thereAreGases = false;

        // Residue (BP > maxTemperature) never evaporates → stays in reboiler
        LegacyMixture residueMixture = new LegacyMixture();

        List<LegacySpecies> liquids = new ArrayList<>();

        // Seed each at the opposite extreme so Math.min/Math.max below converge on the true liquid BP range
        float lowestBoilingPoint = maxTemperature;
        float highestBoilingPoint = roomTemperature;

        for (LegacySpecies molecule : mixture.getContents(false)) {
            if (molecule.getBoilingPoint() < roomTemperature) {
                thereAreGases = true;
                gasMixture.addMolecule(molecule, mixture.getConcentrationOf(molecule));
                continue;
            }
            if (molecule.getBoilingPoint() > maxTemperature) {
                residueMixture.addMolecule(molecule, mixture.getConcentrationOf(molecule));
                continue;
            }
            liquids.add(molecule);
            lowestBoilingPoint = Math.min(lowestBoilingPoint, molecule.getBoilingPoint());
            highestBoilingPoint = Math.max(highestBoilingPoint, molecule.getBoilingPoint());
        }

        if (thereAreGases) numberOfFractions--;  // gas fraction takes one slot

        float interval = (highestBoilingPoint - lowestBoilingPoint) / numberOfFractions;
        List<LegacyMixture> liquidMixtures = new ArrayList<>(numberOfFractions);
        for (int i = 0; i < numberOfFractions; i++) liquidMixtures.add(new LegacyMixture().setTemperature(roomTemperature));

        for (LegacySpecies molecule : liquids) {
            checkEachFraction: for (int fraction = 0; fraction < numberOfFractions; fraction++) {
                if (molecule.getBoilingPoint() <= lowestBoilingPoint + ((fraction + 1) * interval)) {
                    liquidMixtures.get(fraction).addMolecule(molecule, mixture.getConcentrationOf(molecule));
                    break checkEachFraction;
                }
            }
        }

        int residueAmount = residueMixture.recalculateVolume(mixtureAmount);
        fractions.add(MixtureFluid.of(residueAmount, residueMixture));  // index 0 = residue

        for (int fraction = 0; fraction < numberOfFractions; fraction++) {
            LegacyMixture fractionMixture = liquidMixtures.get(fraction);
            int amount = fractionMixture.recalculateVolume(mixtureAmount);
            if (amount == 0) continue;
            fractions.add(MixtureFluid.of(amount, fractionMixture));
        }

        if (thereAreGases) {
            int amount = gasMixture.recalculateVolume(mixtureAmount);
            fractions.add(MixtureFluid.of(amount, gasMixture));
        }

        return fractions;
    }

    /**
 * Get the temperature (in kelvins) to which this Heat Level will heat the Distillation Tower. FROSTING HeatLevel check still deferred
 *.
*/
    public static float getTemperatureForDistillationTower(Level level, BlockPos pos) {
        float temperature = PollutionHelper.getLocalTemperature(level, pos);
        HeatLevel heatLevel = BasinBlockEntity.getHeatLevelOf(level.getBlockState(pos.below()));
        switch (heatLevel) {
            case FADING:
                temperature = 350f;
                break;
            case KINDLED:
                temperature = 400f;
                break;
            case SEETHING:
                temperature = 650f;
                break;
            default:
        }
        return temperature;
    }

}
