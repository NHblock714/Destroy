package petrolpark.mc.destroy.content.processing.centrifuge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllFluids;
import com.simibubi.create.content.fluids.FluidFX;
import com.simibubi.create.content.fluids.FluidTransportBehaviour;
import com.simibubi.create.content.fluids.pipes.FluidPipeBlock;
import com.simibubi.create.content.fluids.pipes.GlassFluidPipeBlock;
import com.simibubi.create.content.fluids.potion.PotionFluid.BottleType;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.foundation.fluid.CombinedTankWrapper;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import com.simibubi.create.infrastructure.config.AllConfigs;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.config.DestroyAllConfigs;

import com.mojang.datafixers.util.Pair;
import net.createmod.catnip.data.Couple;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.IntStream;
import petrolpark.mc.destroy.content.processing.centrifuge.potion.PotionSeparationRecipes;
import petrolpark.mc.destroy.core.block.entity.IHaveLabGoggleInformation;
import petrolpark.mc.destroy.core.chemistry.MixtureContentsDisplaySource;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;
import petrolpark.mc.destroy.core.fluid.GeniusFluidTankBehaviour;
import petrolpark.mc.destroy.core.pollution.PollutingBehaviour;

/**
 * The Centrifuge's Block Entity — hosts 3 fluid tanks (input / dense output / light output) and
 * ticks {@link CentrifugationRecipe}s that separate a single input fluid into 2 output fluids
 * (ratio 1:1 in tank amounts). Dense output is routed to the {@link CentrifugeBlock#DENSE_OUTPUT_FACE}
 * face; light output goes DOWN. Input is UP.
*/
public class CentrifugeBlockEntity extends KineticBlockEntity implements IHaveLabGoggleInformation {

    private static final Object centrifugationRecipeKey = new Object();

    /**
     * Distinct {@link BehaviourType} for the dense-output tank so it doesn't map-collide
     * with the light-output tank. Both tanks are {@link SmartFluidTankBehaviour}, and
     * {@link com.simibubi.create.foundation.blockEntity.SmartBlockEntity}'s internal
     * {@code behaviours} map is keyed by {@code getType()}: two behaviours sharing
     * {@code SmartFluidTankBehaviour.OUTPUT} would silently overwrite each other on
     * {@code attachBehaviourLate} → {@code put()}, removing the dense tank from the
     * lifecycle (no NBT write, no client sync, no tick). Symptom: the dense-output tank
     * physically held fluid on the server but the goggle tooltip showed only the empty
     * capacity line because client-side {@code getDenseOutputTank().getFluid()} returned
     * empty.
     *
     * <p>The light-output tank is kept on the stock {@code SmartFluidTankBehaviour.OUTPUT}
     * type so that pre-fix worlds — which serialised their only-surviving tank under
     * NBT key {@code "OUTPUT"} — load their light-output contents into the light tank
     * after the fix without a data-fixer. Their dense-output contents are unrecoverable
     * (the data was never written), but at least the light side persists.</p>
     */
    public static final BehaviourType<SmartFluidTankBehaviour> DENSE_OUTPUT =
        new BehaviourType<>("dense_output");

    private SmartFluidTankBehaviour inputTank, denseOutputTank, lightOutputTank;

    protected DestroyAdvancementBehaviour advancementBehaviour;
    protected PollutingBehaviour pollutingBehaviour;

    private Direction denseOutputTankFace;

    public int timer;
    private CentrifugationRecipe lastRecipe;

    private boolean pondering; // Whether this Centrifuge is in a Ponder Scene

    public CentrifugeBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        denseOutputTankFace = state.getValue(CentrifugeBlock.DENSE_OUTPUT_FACE);
        pondering = false;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        inputTank = new GeniusFluidTankBehaviour(SmartFluidTankBehaviour.INPUT, this, 1, getEachTankCapacity(), true)
            .whenFluidUpdates(this::onFluidStackChanged);
        denseOutputTank = new GeniusFluidTankBehaviour(DENSE_OUTPUT, this, 1, getEachTankCapacity(), true)
            .whenFluidUpdates(this::onFluidStackChanged)
            .forbidInsertion();
        lightOutputTank = new GeniusFluidTankBehaviour(SmartFluidTankBehaviour.OUTPUT, this, 1, getEachTankCapacity(), true)
            .whenFluidUpdates(this::onFluidStackChanged)
            .forbidInsertion();
        behaviours.addAll(List.of(inputTank, denseOutputTank, lightOutputTank));

        advancementBehaviour = new DestroyAdvancementBehaviour(this, DestroyAdvancementTrigger.USE_CENTRIFUGE);
        behaviours.add(advancementBehaviour);

        pollutingBehaviour = new PollutingBehaviour(this);
        behaviours.add(pollutingBehaviour);
    }

    
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            DestroyBlockEntityTypes.CENTRIFUGE.get(),
            (be, side) -> {
                if (side == null) {
                    // null side → combined wrapper (used by PollutingBehaviour to scan all tanks)
                    return new CombinedTankWrapper(
                        be.inputTank.getCapability(),
                        be.denseOutputTank.getCapability(),
                        be.lightOutputTank.getCapability());
                }
                if (side == Direction.UP) return be.inputTank.getCapability();
                if (side == Direction.DOWN) return be.lightOutputTank.getCapability();
                if (side == be.denseOutputTankFace || be.pondering) return be.denseOutputTank.getCapability();
                return null;
            });
    }

    /**
 * Attempts to rotate the Centrifuge so that its DENSE_OUTPUT_FACE points to a new face which
 * also has a Pipe. If no Pipe is available, just rotates it anyway.
 *
 * @param shouldSwitch Whether the rotation should prioritise switching faces or staying on the current face
 * @return Whether the Centrifuge was rotated
*/
    public boolean attemptRotation(boolean shouldSwitch) {
        if (!hasLevel()) return false;
        Direction newFace = refreshDirection(
            this,
            shouldSwitch ? denseOutputTankFace.getClockWise() : denseOutputTankFace,
            getDenseOutputTank(),
            true);
        if (getLevel().setBlock(getBlockPos(), getBlockState().setValue(CentrifugeBlock.DENSE_OUTPUT_FACE, newFace), 6)) {
            denseOutputTankFace = getBlockState().getValue(CentrifugeBlock.DENSE_OUTPUT_FACE);
            notifyUpdate();
            return true;
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!hasLevel()) return;
        if (getSpeed() == 0) return;
        if (isTankFull(getDenseOutputTank()) || isTankFull(getLightOutputTank())) return;
        if (timer > 0) {
            timer -= getProcessingSpeed();
            if (getLevel().isClientSide()) {
                spawnParticles();
                return;
            }
            if (timer <= 0) {
                process();
            }
            sendData();
            return;
        }
        if (inputTank.getPrimaryHandler().isEmpty()) return;

        if (lastRecipe == null || !lastRecipe.getRequiredFluid().ingredient().test(getInputTank().getFluid())) {
            FluidStack inputFluidStack = getInputTank().getFluid();

            // Standard JSON recipes)
            List<CentrifugationRecipe> possibleRecipes =
                RecipeFinder.get(centrifugationRecipeKey, getLevel(), rh -> rh.value().getType() == DestroyRecipeTypes.CENTRIFUGATION.getType())
                    .stream()
                    .map(rh -> (CentrifugationRecipe) rh.value())
                    .filter(recipe -> {
                        if (!recipe.isValidAt(getLevel(), getBlockPos())) return false; // Biome-specific recipes
                        if (!recipe.getRequiredFluid().ingredient().test(inputFluidStack)) return false;
                        if (!canFitFluidInTank(recipe.getDenseOutputFluid(), getDenseOutputTank())
                            || !canFitFluidInTank(recipe.getLightOutputFluid(), getLightOutputTank())) return false;
                        return true;
                    })
                    .collect(Collectors.toList());

            // Potion separation path. 1.21 migrations:
            // • inputFluidStack.getOrCreateTag().getString("Potion") + ForgeRegistries.POTIONS.getValue
            // → fluidStack.get(DataComponents.POTION_CONTENTS).potion() returns Holder<Potion> directly
            // (no registry lookup needed; Create's PotionFluid.of() puts the Holder right in the
            // PotionContents component).
            // • NBTHelper.readEnum(tag, "BottleType") → fluidStack.getOrDefault(
            // AllDataComponents.POTION_FLUID_BOTTLE_TYPE, BottleType.REGULAR) — Create 1.21 stores
            // BottleType as a separate AllDataComponent (see PotionFluid.of source).
            // • PotionSeparationRecipes.ALL static → createSeparationRecipes(level) lazy-init —
            // 1.21 PotionBrewing is per-level so the recipe map can't be built at class-load.
            // • Map value type RecipeHolder<CentrifugationRecipe> — unwrap via .value()
            // to add to the List<CentrifugationRecipe> already populated above.
            // • Pair is net.createmod.catnip.data.Pair (matches PotionSeparationRecipes side) —
            // use FQN here because this file already imports com.mojang.datafixers.util.Pair
            // for chemistry-engine-side phased-molecule pairs (different concept).
            if (AllConfigs.server().recipes.allowBrewingInMixer.get()
                && inputFluidStack.getFluid().isSame(AllFluids.POTION.get())) {
                PotionContents contents = inputFluidStack.getOrDefault(
                    DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                java.util.Optional<Holder<Potion>> potionOpt = contents.potion();
                if (potionOpt.isPresent()) {
                    BottleType bottleType = inputFluidStack.getOrDefault(
                        AllDataComponents.POTION_FLUID_BOTTLE_TYPE, BottleType.REGULAR);
                    java.util.Map<net.createmod.catnip.data.Pair<Holder<Potion>, BottleType>, RecipeHolder<CentrifugationRecipe>> separationMap =
                        PotionSeparationRecipes.createSeparationRecipes(getLevel());
                    RecipeHolder<CentrifugationRecipe> separationHolder = separationMap.get(
                        net.createmod.catnip.data.Pair.of(potionOpt.get(), bottleType));
                    if (separationHolder != null
                        && canFitFluidInTank(separationHolder.value().getDenseOutputFluid(), getDenseOutputTank())
                        && canFitFluidInTank(separationHolder.value().getLightOutputFluid(), getLightOutputTank())) {
                        possibleRecipes.add(separationHolder.value());
                    }
                }
            }

            if (!possibleRecipes.isEmpty()) {
                lastRecipe = possibleRecipes.get(0);
            } else {
                lastRecipe = null;
            }
        }

        if (lastRecipe == null) {
            timer = 100;
        } else {
            timer = lastRecipe.getProcessingDuration();
        }

        sendData();
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        timer = compound.getInt("Timer");
        // GeniusFluidTankBehaviour auto-handles tank persistence via super.read (behaviours chain)
        super.read(compound, registries, clientPacket);
        denseOutputTankFace = getBlockState().getValue(CentrifugeBlock.DENSE_OUTPUT_FACE);
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putInt("Timer", timer);
        super.write(compound, registries, clientPacket);
    }

    public int getProcessingSpeed() {
        return Mth.clamp((int) Math.abs(getSpeed() / 16f), 1, 512);
    }

    public SmartFluidTank getInputTank() {
        return inputTank.getPrimaryHandler();
    }

    public SmartFluidTank getDenseOutputTank() {
        return denseOutputTank.getPrimaryHandler();
    }

    public SmartFluidTank getLightOutputTank() {
        return lightOutputTank.getPrimaryHandler();
    }

    public void process() {
        if (lastRecipe == null) {
            // Mixture-fluid centrifugation: mass-weighted phase-separation algorithm.
            // Splits gas/liquid phases
            // via LegacyMixture.separatePhases, then sorts molecules by density + splits them
            // into dense/light halves keeping ion-pairs balanced (cations + anions must move
            // together to preserve charge neutrality in each output mixture).
            // stack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()).
            if (!DestroyFluids.isMixture(getInputTank().getFluid())) return;

            LegacyMixture mixture = LegacyMixture.readNBT(
                getInputTank().getFluid().getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
            if (mixture == null) return;
            // Output tanks must be empty OR already holding Mixture (can't mix non-Mixture fluids)
            if (!(DestroyFluids.isMixture(getDenseOutputTank().getFluid()) || getDenseOutputTank().isEmpty())
                || !(DestroyFluids.isMixture(getLightOutputTank().getFluid()) || getLightOutputTank().isEmpty())) return;

            int amount = IntStream.of(new int[]{
                getInputTank().getFluidAmount(),
                getDenseOutputTank().getSpace() * 2,
                getLightOutputTank().getSpace() * 2
            }).min().getAsInt();
            if (amount == 0) return;
            float totalVolume = amount / 1000f;

            Map<Pair<LegacySpecies, Boolean>, Float> phasedMoleculesRemainingMoles = new HashMap<>();
            Map<Pair<LegacySpecies, Boolean>, Float> phasedMoleculesRemainingVolumes = new HashMap<>();
            Map<Pair<LegacySpecies, Boolean>, Float> phasedMoleculesMolesInLightMixture = new HashMap<>();
            Map<Pair<LegacySpecies, Boolean>, Float> phasedMoleculesMolesInDenseMixture = new HashMap<>();

            LegacyMixture.Phases phases = mixture.separatePhases(totalVolume);
            float liquidVolume = (float)(double) phases.liquidVolume();
            float gasVolume = totalVolume - liquidVolume;
            LegacyMixture gasMixture = phases.gasMixture();
            gasMixture.scale(gasVolume);
            LegacyMixture liquidMixture = phases.liquidMixture();

            for (LegacySpecies molecule : liquidMixture.getContents(false)) {
                Pair<LegacySpecies, Boolean> phasedMolecule = Pair.of(molecule, false);
                float moles = liquidMixture.getConcentrationOf(molecule) * liquidVolume;
                phasedMoleculesRemainingVolumes.put(phasedMolecule, moles / molecule.getPureConcentration());
                phasedMoleculesRemainingMoles.put(phasedMolecule, moles);
            }

            float totalGasConcentration = gasMixture.getTotalConcentration();
            for (LegacySpecies molecule : gasMixture.getContents(false)) {
                Pair<LegacySpecies, Boolean> phasedMolecule = Pair.of(molecule, true);
                float moles = gasMixture.getConcentrationOf(molecule) * gasVolume;
                phasedMoleculesRemainingVolumes.put(phasedMolecule, totalGasConcentration == 0f ? 0f : moles / totalGasConcentration);
                phasedMoleculesRemainingMoles.put(phasedMolecule, moles);
            }

            // Sort molecules by phase density (heaviest sinks first to the dense output tank).
            //
            // The naive {@code mixture.getConcentrationOf(m) * m.getMass() / remainingVolume}
            // key is wrong for molecules that exist in both phases: the conc factor is the
            // mixture-wide value (large) while {@code remainingVolume} for the gas pair is
            // the tiny separated gas volume → the ratio explodes and a trace gas fraction
            // outranks every liquid as "densest", landing in the dense output tank. Fix by
            // ranking by actual per-phase density:
            //   - liquid: pure species density (= mass × pureConcentration), e.g. 1141 g/L for O₂
            //   - gas:    post-separation gas-mixture density of that species (per-litre)
            // For a 77 K O₂/N₂ mix, this puts liquid O₂ (1141) > liquid N₂ (808) >>
            // gas N₂ (~7 g/L) — gas drops into the light tank where it belongs.
            List<Pair<LegacySpecies, Boolean>> orderedPhasedMolecules = new ArrayList<>(phasedMoleculesRemainingVolumes.keySet());
            final LegacyMixture gasMixtureForSort = gasMixture;
            orderedPhasedMolecules.sort((p1, p2) -> Float.compare(
                phaseDensity(p2, gasMixtureForSort), phaseDensity(p1, gasMixtureForSort)));

            float volumeOfDenseMixture = 0f;

            splitEachMolecule: for (Pair<LegacySpecies, Boolean> phasedMolecule : orderedPhasedMolecules) {
                LegacySpecies molecule = phasedMolecule.getFirst();
                if (!phasedMoleculesRemainingMoles.containsKey(phasedMolecule)) continue splitEachMolecule;
                float moles = phasedMoleculesRemainingMoles.get(phasedMolecule);

                if (molecule.getCharge() == 0) {
                    // Neutral molecule: just split by available dense-mixture volume
                    float volume = phasedMoleculesRemainingVolumes.get(phasedMolecule);
                    float volumeInDenseMixture = Math.min(volume, (totalVolume / 2f) - volumeOfDenseMixture);
                    float proportionInDenseMixture = volume == 0f ? 0f : volumeInDenseMixture / volume;

                    phasedMoleculesMolesInDenseMixture.put(phasedMolecule, moles * proportionInDenseMixture);
                    phasedMoleculesMolesInLightMixture.put(phasedMolecule, moles * (1 - proportionInDenseMixture));
                    volumeOfDenseMixture += volumeInDenseMixture;
                    phasedMoleculesRemainingVolumes.replace(phasedMolecule, 0f);
                    phasedMoleculesRemainingMoles.replace(phasedMolecule, 0f);
                } else {
                    // Ion: must pair with counter-ions to preserve charge balance
                    findCounterions: while (Optional.ofNullable(phasedMoleculesRemainingVolumes.get(phasedMolecule)).orElse(0f) > 0f) {
                        // Re-read the remaining moles each pass: the molecule is consumed across
                        // successive counter-ion pairings, so the loop-entry value goes stale.
                        Float remainingMoleculeMoles = phasedMoleculesRemainingMoles.get(phasedMolecule);
                        if (remainingMoleculeMoles == null) break findCounterions;
                        moles = remainingMoleculeMoles;

                        Pair<LegacySpecies, Boolean> phasedCounterion = getNextIon(orderedPhasedMolecules, phasedMoleculesRemainingVolumes, molecule.getCharge() < 0);
                        LegacySpecies counterion = phasedCounterion.getFirst();
                        if (counterion == null) {
                            petrolpark.mc.destroy.Destroy.LOGGER.error("Tried to centrifuge charge-imbalanced Mixture");
                            break findCounterions;
                        }
                        // Skip a counter-ion whose moles are already used up even if getNextIon still
                        // offered it by volume.
                        if (phasedMoleculesRemainingMoles.get(phasedCounterion) == null) break findCounterions;

                        // Charge balance: n_primary * c_primary + n_counter * c_counter = 0
                        //   →  n_counter = - n_primary * c_primary / c_counter
                        // i.e. counterionMolesRequired = -moles * molecule.charge / counterion.charge.
                        // Upstream Destroy 1.20.1 wrote the two charges swapped (numerator =
                        // counterion.charge, denominator = molecule.charge), which inverts the
                        // pairing ratio every time the primary and counter-ion have different
                        // charge magnitudes — centrifuging a 1:3 Fe³⁺/Cl⁻ mixture produced
                        // output halves with Fe³⁺:Cl⁻ = 3:1. 1:1 salts (NaCl, KCl) accidentally
                        // looked correct because the swap is a no-op when |c_primary|=|c_counter|.
                        float counterionMolesRequired = -moles * (float) molecule.getCharge() / (float) counterion.getCharge();
                        float proportionAvailable = phasedMoleculesRemainingMoles.get(phasedCounterion) / counterionMolesRequired;
                        if (proportionAvailable <= 0f) break findCounterions;

                        float volumeOfMoleculeUsed;
                        float molesOfMoleculeUsed;
                        float volumeOfCounterionUsed;
                        float molesOfCounterionUsed;

                        if (proportionAvailable > 1f) {
                            volumeOfMoleculeUsed = phasedMoleculesRemainingVolumes.get(phasedMolecule);
                            molesOfMoleculeUsed = moles;
                            volumeOfCounterionUsed = phasedMoleculesRemainingVolumes.get(phasedCounterion) / proportionAvailable;
                            molesOfCounterionUsed = phasedMoleculesRemainingMoles.get(phasedCounterion) / proportionAvailable;
                        } else {
                            volumeOfMoleculeUsed = phasedMoleculesRemainingVolumes.get(phasedMolecule) * proportionAvailable;
                            molesOfMoleculeUsed = moles * proportionAvailable;
                            volumeOfCounterionUsed = phasedMoleculesRemainingVolumes.get(phasedCounterion);
                            molesOfCounterionUsed = phasedMoleculesRemainingMoles.get(phasedCounterion);
                        }

                        float combinationVolume = volumeOfMoleculeUsed + volumeOfCounterionUsed;
                        float combinedVolumeInDenseMixture = Math.min(combinationVolume, (totalVolume / 2f) - volumeOfDenseMixture);
                        float proportionInDenseMixture = combinationVolume == 0f ? 0f : combinedVolumeInDenseMixture / combinationVolume;

                        phasedMoleculesMolesInDenseMixture.merge(phasedMolecule, molesOfMoleculeUsed * proportionInDenseMixture, Float::sum);
                        phasedMoleculesMolesInDenseMixture.merge(phasedCounterion, molesOfCounterionUsed * proportionInDenseMixture, Float::sum);
                        phasedMoleculesMolesInLightMixture.merge(phasedMolecule, molesOfMoleculeUsed * (1 - proportionInDenseMixture), Float::sum);
                        phasedMoleculesMolesInLightMixture.merge(phasedCounterion, molesOfCounterionUsed * (1 - proportionInDenseMixture), Float::sum);

                        phasedMoleculesRemainingVolumes.compute(phasedMolecule, (pm, volume) -> {
                            float newVolume = volume - volumeOfMoleculeUsed;
                            return newVolume <= 1 / 256f / 256f ? null : newVolume;
                        });
                        phasedMoleculesRemainingVolumes.compute(phasedCounterion, (pm, volume) -> {
                            float newVolume = volume - volumeOfCounterionUsed;
                            return newVolume <= 1 / 256f / 256f ? null : newVolume;
                        });
                        phasedMoleculesRemainingMoles.compute(phasedMolecule, (pm, mol) -> {
                            float newMol = (mol == null ? 0f : mol) - molesOfMoleculeUsed;
                            return newMol <= 1 / 256f / 256f ? null : newMol;
                        });
                        phasedMoleculesRemainingMoles.compute(phasedCounterion, (pm, mol) -> {
                            float newMol = (mol == null ? 0f : mol) - molesOfCounterionUsed;
                            return newMol <= 1 / 256f / 256f ? null : newMol;
                        });

                        volumeOfDenseMixture += combinedVolumeInDenseMixture;
                    }
                }
            }

            // Build output mixtures
            LegacyMixture denseMixture = new LegacyMixture();
            denseMixture.setTemperature(mixture.getTemperature());
            LegacyMixture lightMixture = new LegacyMixture();
            lightMixture.setTemperature(mixture.getTemperature());

            for (Pair<LegacyMixture, Map<Pair<LegacySpecies, Boolean>, Float>> mixtureAndMap :
                 List.of(Pair.of(denseMixture, phasedMoleculesMolesInDenseMixture),
                         Pair.of(lightMixture, phasedMoleculesMolesInLightMixture))) {
                LegacyMixture resultMixture = mixtureAndMap.getFirst();
                Map<Pair<LegacySpecies, Boolean>, Float> map = mixtureAndMap.getSecond();
                Map<LegacySpecies, Couple<Float>> moleculeStates = new HashMap<>(map.size());

                for (Entry<Pair<LegacySpecies, Boolean>, Float> entry : map.entrySet()) {
                    Pair<LegacySpecies, Boolean> phasedMolecule = entry.getKey();
                    moleculeStates.compute(phasedMolecule.getFirst(), (molec, couple) -> {
                        if (couple == null) couple = Couple.create(0f, 0f);
                        couple.set(phasedMolecule.getSecond(), entry.getValue());
                        return couple;
                    });
                }

                for (Entry<LegacySpecies, Couple<Float>> entry : moleculeStates.entrySet()) {
                    Couple<Float> molesCouple = entry.getValue();
                    LegacySpecies molec = entry.getKey();
                    float totalMoles = molesCouple.getFirst() + molesCouple.getSecond();
                    if (totalMoles <= 0f) continue;
                    resultMixture.addMolecule(molec, 2f * totalMoles / totalVolume);
                    resultMixture.setState(molec, molesCouple.getFirst() / totalMoles);
                }
            }

            // Commit: drain input + fill outputs
            getInputTank().drain(amount, FluidAction.EXECUTE);
            getDenseOutputTank().fill(MixtureFluid.of(amount / 2, denseMixture), FluidAction.EXECUTE);
            getLightOutputTank().fill(MixtureFluid.of(amount / 2, lightMixture), FluidAction.EXECUTE);

            advancementBehaviour.awardDestroyAdvancement(DestroyAdvancementTrigger.USE_CENTRIFUGE);
            notifyUpdate();
            return;
        }
        if (!canFitFluidInTank(lastRecipe.getDenseOutputFluid(), getDenseOutputTank())
            || !canFitFluidInTank(lastRecipe.getLightOutputFluid(), getLightOutputTank())
            || hasFluidInTank(lastRecipe.getRequiredFluid(), getLightOutputTank())) return;
        SizedFluidIngredient required = lastRecipe.getRequiredFluid();
        getInputTank().drain(required.amount(), FluidAction.EXECUTE);
        getDenseOutputTank().fill(lastRecipe.getDenseOutputFluid(), FluidAction.EXECUTE);
        getLightOutputTank().fill(lastRecipe.getLightOutputFluid(), FluidAction.EXECUTE);
        advancementBehaviour.awardDestroyAdvancement(DestroyAdvancementTrigger.USE_CENTRIFUGE);
        notifyUpdate();
    }

    /** Helper: find the next ion of the opposite charge that still has remaining volume.*/
    private static Pair<LegacySpecies, Boolean> getNextIon(List<Pair<LegacySpecies, Boolean>> list,
                                                          Map<Pair<LegacySpecies, Boolean>, Float> map,
                                                          boolean cation) {
        for (Pair<LegacySpecies, Boolean> pair : list) {
            int charge = pair.getFirst().getCharge();
            if (charge != 0 && (charge > 0) == cation && map.containsKey(pair) && map.get(pair) > 0f) return pair;
        }
        return Pair.of(null, null);
    }

    /** Phase-aware density (g/L) for the centrifuge sort key — see comment on the sort call. */
    private static float phaseDensity(Pair<LegacySpecies, Boolean> phasedMolecule, LegacyMixture gasMixture) {
        LegacySpecies m = phasedMolecule.getFirst();
        boolean isGas = phasedMolecule.getSecond();
        if (isGas) {
            // Per-litre gas density after phase separation. gasMixture's concentrations
            // have already been rescaled to the actual gas volume by the {@code gasMixture.scale}
            // call up in process(), so this is the real cold-gas mass density.
            return gasMixture.getConcentrationOf(m) * m.getMass();
        }
        // Pure-liquid density (mass × pureConcentration) — independent of how much of the
        // species is present, which is what the original sort key would have collapsed to
        // after the algebra for a single-phase liquid. Stable and physically meaningful.
        return m.getDensity();
    }

    public void spawnParticles() {
        FluidStack fluidStack = inputTank.getPrimaryHandler().getFluid();
        if (fluidStack.isEmpty() || !hasLevel()) return;

        RandomSource random = getLevel().getRandom();

        ParticleOptions data = FluidFX.getFluidParticle(fluidStack);
        float angle = random.nextFloat() * 360;
        Vec3 offset = new Vec3(0, 0, 0.7f);
        offset = VecHelper.rotate(offset, angle, Axis.Y);
        Vec3 target = VecHelper.rotate(offset, getSpeed() > 0 ? 25 : -25, Axis.Y);

        Vec3 center = offset.add(VecHelper.getCenterOf(worldPosition));
        target = VecHelper.offsetRandomly(target.subtract(offset), random, 1 / 128f);
        getLevel().addParticle(data, center.x, center.y, center.z, target.x, target.y, target.z);
    }

    public int getEachTankCapacity() {
        return DestroyAllConfigs.SERVER.blocks.centrifugeCapacity.get();
    }

    private void onFluidStackChanged() {
        notifyUpdate();
    }

    /**
 * Marks this Centrifuge as being in a Ponder scene. This makes it so the dense Fluid can be pulled
 * from any side (otherwise Ponder scenes would need to match the DENSE_OUTPUT_FACE blockstate
 * exactly — impractical for pre-recorded scenes).
*/
    public void setPondering() {
        pondering = true;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);
        DestroyLang.fluidContainerInfoHeader(tooltip);
        DestroyLang.tankInfoTooltip(tooltip, DestroyLang.translate("tooltip.centrifuge.input_tank"), getInputTank());
        DestroyLang.tankInfoTooltip(tooltip, DestroyLang.translate("tooltip.centrifuge.dense_output_tank"), getDenseOutputTank());
        DestroyLang.tankInfoTooltip(tooltip, DestroyLang.translate("tooltip.centrifuge.light_output_tank"), getLightOutputTank());
        return true;
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════
    // IDirectionalOutputFluidBlockEntity inlined helpers
    // ═════════════════════════════════════════════════════════════════════════════════════════

    private static boolean isTankFull(FluidTank tank) {
        return tank.getFluidAmount() == tank.getCapacity();
    }

    private static boolean canFitFluidInTank(FluidStack stack, FluidTank tank) {
        return (FluidStack.isSameFluidSameComponents(stack, tank.getFluid()) || tank.isEmpty())
            && stack.getAmount() <= tank.getSpace();
    }

    /** Checks (1) the tank has at least the required amount + (2) one of the
 * ingredient's accepted fluids matches the tank's current fluid.
*/
    private static boolean hasFluidInTank(SizedFluidIngredient ingredient, FluidTank tank) {
        if (tank.drain(ingredient.amount(), FluidAction.SIMULATE).getAmount() != ingredient.amount()) return false;
        // FluidIngredient.getMatchingFluidStacks() (List<FluidStack>).
        for (FluidStack stack : ingredient.getFluids()) {
            if (FluidStack.isSameFluidSameComponents(stack, tank.getFluid())) return true;
        }
        return false;
    }

    /**

 * Loops through horizontal directions starting from {@code currentDirection}, prioritizing it,
 * to find a direction that can pull/push to an adjacent pipe/tank.
*/
    private static Direction refreshDirection(CentrifugeBlockEntity be, Direction currentDirection, FluidTank tank, boolean output) {
        if (!be.hasLevel() || currentDirection.getAxis() == Axis.Y) {
            return Direction.NORTH;
        }
        Direction direction = currentDirection;
        for (int i = 0; i < 4; i++) {
            BlockEntity adjacentBE = be.getLevel().getBlockEntity(be.getBlockPos().relative(direction));
            BlockState adjacentState = be.getLevel().getBlockState(be.getBlockPos().relative(direction));
            if (adjacentBE != null) {
                FluidTransportBehaviour transport = BlockEntityBehaviour.get(adjacentBE, FluidTransportBehaviour.TYPE);
                if (transport != null) {
                    if (output && transport.canPullFluidFrom(tank.getFluid(), adjacentState, direction.getOpposite())) {
                        return direction;
                    } else if (!output && transport.canHaveFlowToward(adjacentState, direction.getOpposite())) {
                        return direction;
                    } else if (FluidPipeBlock.isPipe(adjacentState)
                        || (adjacentState.getBlock() instanceof GlassFluidPipeBlock
                            && direction.getAxis() != adjacentState.getValue(RotatedPillarBlock.AXIS))) {
                        return direction;
                    }
                }
            }
            direction = direction.getClockWise();
        }
        return currentDirection;
    }

    // Keep unused variable alive for future PotionContents integration (silences IDE warning
    // without deleting the import tree for when the TODO is tackled)
    @SuppressWarnings("unused")
    private static final Object UNUSED_SENTINEL_FOR_POTION_SEPARATION = new ArrayList<>();

    /**
 * DisplayLink source — reads one of the Centrifuge's 3 tanks (input, dense output, light
 * output).
 * 3 variants via static factories: {@link #createInput}, {@link #createDenseOutput},
 * {@link #createLightOutput}.
 *
 * <p>Registration: {@link petrolpark.mc.destroy.DestroyDisplaySources#register}.</p>
*/
    public static class CentrifugeDisplaySource extends MixtureContentsDisplaySource {

        private final Function<CentrifugeBlockEntity, SmartFluidTank> tankGetter;
        private final String tankId;

        private CentrifugeDisplaySource(String tankId, Function<CentrifugeBlockEntity, SmartFluidTank> tankGetter) {
            super(false);
            this.tankId = tankId;
            this.tankGetter = tankGetter;
        }

        public static CentrifugeDisplaySource createInput() {
            return new CentrifugeDisplaySource("input", CentrifugeBlockEntity::getInputTank);
        }

        public static CentrifugeDisplaySource createDenseOutput() {
            return new CentrifugeDisplaySource("dense_output", CentrifugeBlockEntity::getDenseOutputTank);
        }

        public static CentrifugeDisplaySource createLightOutput() {
            return new CentrifugeDisplaySource("light_output", CentrifugeBlockEntity::getLightOutputTank);
        }

        @Override
        public FluidStack getFluidStack(DisplayLinkContext context) {
            if (context.getSourceBlockEntity() instanceof CentrifugeBlockEntity centrifuge) {
                return tankGetter.apply(centrifuge).getFluid();
            }
            return FluidStack.EMPTY;
        }

        @Override
        public Component getName() {
            return DestroyLang.translate("display_source.centrifuge." + tankId).component();
        }
    }
}
