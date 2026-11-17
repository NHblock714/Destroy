package petrolpark.mc.destroy.chemistry.legacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.ImmutableList;

import net.createmod.catnip.data.Pair;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.api.error.ChemistryException;
import petrolpark.mc.destroy.chemistry.api.util.Constants;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.DoubleGroupGenericReaction;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.GenericReactant;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.GenericReaction;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.SingleGroupGenericReaction;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.legacy.reactionresult.NovelCompoundSynthesizedReactionResult;
import petrolpark.mc.destroy.core.chemistry.basinreaction.ReactionInBasinRecipe.ReactionInBasinResult;

/**
 * A mutable, reaction-capable Mixture — tracks Molecule concentrations, temperature, phase states,
 * and drives the tick-by-tick Reaction simulation. Extends {@link ReadOnlyMixture} with Reaction
 * bookkeeping (possible reactions list, group→molecule index, equilibrium flag, phase transition
 * points, lazy Molecule removal TTL map).
*/
public class LegacyMixture extends ReadOnlyMixture {

    protected static final int TICKS_PER_SECOND = 20;

    /** A Map of all Reaction Results generated in this Mixture, mapped to their accumulated moles/bucket.*/
    protected Map<ReactionResult, Float> reactionResults;

    /** Molecules which do not have a name space or ID.*/
    protected List<LegacySpecies> novelMolecules;

    /** All Reactions + GenericReactions currently possible given Molecules in this Mixture.*/
    protected List<LegacyReaction> possibleReactions;

    /** Every Molecule in this Mixture with a functional Group, indexed by Group Type.*/
    protected Map<LegacyFunctionalGroupType<?>, List<GenericReactant<?>>> groupIDsAndMolecules;

    /** Whether this Mixture has reached equilibrium.*/
    protected boolean equilibrium;

    /** Molecule with boiling point closest to current temperature, but higher. Either field may be null.*/
    Pair<Float, LegacySpecies> nextHigherBoilingPoint;

    /** Molecule with boiling point closest to current temperature, but lower. Either field may be null.*/
    Pair<Float, LegacySpecies> nextLowerBoilingPoint;

    /** Molecules removed this tick that might come back — maps Molecule to ticks-to-live.*/
    Map<LegacySpecies, Integer> moleculesToRemove;

    public LegacyMixture() {
        super();
        reactionResults = new HashMap<>();
        novelMolecules = new ArrayList<>();
        possibleReactions = new ArrayList<>();
        groupIDsAndMolecules = new HashMap<>();
        nextHigherBoilingPoint = Pair.of(Float.MAX_VALUE, null);
        nextLowerBoilingPoint = Pair.of(0f, null);
        moleculesToRemove = new HashMap<>();
        equilibrium = false;
    }

    /**
 * Get a Mixture containing only the given Molecule, unless it is charged, in which case get a Mixture
 * containing the sodium salt or chloride of the ion.
*/
    public static LegacyMixture pure(LegacySpecies molecule) {
        LegacyMixture mixture = new LegacyMixture();
        if (molecule.getCharge() == 0) {
            mixture.addMolecule(molecule, molecule.getPureConcentration());
            return mixture;
        }
        LegacySpecies otherIon = molecule.getCharge() < 0 ? DestroyMolecules.SODIUM_ION : DestroyMolecules.CHLORIDE;
        int chargeMagnitude = Math.abs(molecule.getCharge());
        mixture.addMolecule(molecule, 1f);
        mixture.addMolecule(otherIon, chargeMagnitude);
        mixture.recalculateVolume(1000);
        return mixture;
    }

    public static LegacyMixture readNBT(CompoundTag compound) {
        return readNBT(compound, true);
    }

    /** Use this from hot paths
 * where the parsed mixture is only needed for **storage / merge / serialization** (e.g.
 * {@link petrolpark.mc.destroy.core.fluid.GeniusFluidTankBehaviour.GeniusFluidTank#fill}
 * fluid-tank merge). Skipping saves an O(reactions × species) scan per parse.
 *
 * <p>The reaction list / display name / color / next-boiling-point cache only matter when
 * the mixture is going to **react** (Vat tick / Basin reaction) or **be displayed** (JEI /
 * tooltip / display link). Pure tank-to-tank pump transfer doesn't touch any of those.</p>
 *
 * <p>Caller-side rule: if you later use this mixture for reaction / display, call
 * {@link #refreshPossibleReactions} / {@link #updateName} / {@link #updateColor}
 * explicitly. Default {@code readNBT(CompoundTag)} keeps {@code fullProcessing=true} so
 * existing call sites are unaffected.</p>
*/
    public static LegacyMixture readNBT(CompoundTag compound, boolean fullProcessing) {
        LegacyMixture mixture = new LegacyMixture();

        if (compound == null) {
            Destroy.LOGGER.warn("Null Mixture loaded");
            return mixture;
        }

        mixture.translationKey = compound.getString("TranslationKey");

        if (compound.contains("Temperature")) mixture.temperature = compound.getFloat("Temperature");

        ListTag contents = compound.getList("Contents", Tag.TAG_COMPOUND);
        contents.forEach(tag -> {
            CompoundTag moleculeTag = (CompoundTag) tag;
            String moleculeId = moleculeTag.getString("Molecule");
            LegacySpecies molecule = LegacySpecies.getMolecule(moleculeId);
            if (molecule == null) {
                // Datapack / addon-mod recipe references a molecule id the current chemistry
                // registry doesn't know about — typical when an addon datapack is uninstalled
                // partway, when NBT is carried over from an older save where the molecule was
                // removed, or when reload-listener order puts a datapack molecule registration
                // after Minecraft's {@code RecipeManager.apply} (which constructs every
                // {@link com.simibubi.create.content.processing.recipe.ProcessingRecipe} and
                // synchronously walks its fluid components through this codepath via the
                // mixin-injected {@code captureMixtureMolecules}).
                //
                // Without this guard, {@link #internalAddMolecule} dereferences {@code null}
                // on {@code molecule.isNovel()} and throws NPE, which bubbles up through Codec
                // decoding past every {@code DataResult$Success.map} call site (those don't
                // catch RuntimeException) and aborts the entire datapack reload — the world
                // never finishes loading. Skipping the unknown entry with a single WARN per
                // call leaves the rest of the mixture intact and lets the reload finish.
                Destroy.LOGGER.warn("Unknown molecule id '{}' in mixture NBT — skipped (datapack / addon may have changed since this NBT was written).", moleculeId);
                return;
            }
            mixture.internalAddMolecule(molecule, moleculeTag.getFloat("Concentration"), false);
            if (moleculeTag.contains("Gaseous", Tag.TAG_FLOAT)) {
                float state = moleculeTag.getFloat("Gaseous");
                mixture.states.put(molecule, state);
                if (state != 0f && state != 1f) mixture.boiling = true;
            } else {
                mixture.states.put(molecule, molecule.getBoilingPoint() < mixture.temperature ? 1f : 0f);
            }
        });

        mixture.equilibrium = compound.getBoolean("AtEquilibrium");

        if (compound.contains("Results", Tag.TAG_LIST)) {
            ListTag results = compound.getList("Results", Tag.TAG_COMPOUND);
            results.forEach(tag -> {
                CompoundTag resultTag = (CompoundTag) tag;
                LegacyReaction reaction = LegacyReaction.get(resultTag.getString("Result"));
                if (reaction == null) return;
                ReactionResult result = reaction.getResult();
                if (result == null) return;
                mixture.reactionResults.put(result, resultTag.getFloat("MolesPerBucket"));
            });
        }

        if (fullProcessing) {
            mixture.updateName();
            mixture.updateColor();
            mixture.refreshPossibleReactions();
            mixture.updateNextBoilingPoints();
        }

        return mixture;
    }

    @Override
    public CompoundTag writeNBT() {
        CompoundTag tag = super.writeNBT();
        tag.putBoolean("AtEquilibrium", equilibrium);

        if (!reactionResults.isEmpty()) {
            tag.put("Results", NBTHelper.writeCompoundList(
                reactionResults.entrySet().stream().filter(entry -> entry.getKey().getReaction().isPresent()).toList(),
                entry -> {
                    CompoundTag resultTag = new CompoundTag();
                    resultTag.putString("Result", entry.getKey().getReaction().get().getFullId());
                    resultTag.putFloat("MolesPerBucket", entry.getValue());
                    return resultTag;
                }));
        }
        return tag;
    }

    /**
 * Set the temperature (in kelvins) of this Mixture. Mutative.
*/
    public LegacyMixture setTemperature(float temperature) {
        this.temperature = temperature;
        for (LegacySpecies molecule : contents.keySet()) {
            if (molecule.getBoilingPoint() < temperature) {
                states.put(molecule, 1f);
            } else {
                states.put(molecule, 0f);
            }
        }
        return this;
    }

    /**
 * Set the state of a Molecule.
 * @param state A number from 0 (entirely liquid) to 1 (entirely gaseous).
*/
    public void setState(LegacySpecies molecule, float state) {
        if (state < 0f || state > 1f)
            throw new IllegalStateException("Molecules can range from entirely liquid (state = 0) to entirely gas (state = 1)");
        if (getConcentrationOf(molecule) > 0f) states.put(molecule, state);
    }

    /**
 * Adds a Molecule to this Mixture. If already present, increases concentration.
*/
    @Override
    public LegacyMixture addMolecule(LegacySpecies molecule, float concentration) {

        if (getConcentrationOf(molecule) > 0f) {
            changeConcentrationOf(molecule, concentration, true);
            updateName();
            updateColor();
            return this;
        }

        internalAddMolecule(molecule, concentration, true);
        equilibrium = false;
        return this;
    }

    @Override
    public List<LegacySpecies> getContents(boolean excludeNovel) {
        return contents.keySet().stream()
            .filter(molecule -> getConcentrationOf(molecule) > 0f && (!molecule.isNovel() || !excludeNovel))
            .toList();
    }

    /**
 * Creates a new Mixture by mixing together existing ones. Does not give the volume of the new Mixture.
*/
    public static LegacyMixture mix(Map<LegacyMixture, Double> mixtures) {
        return mix(mixtures, true);
    }

    /** Used by hot fluid-merge paths
 * (Create pump fill into mixture-aware tank) where the result is immediately serialized to
 * NBT and discarded — those caches don't influence NBT contents.
 *
 * <p>Performance impact (Create pump → mixture tank merge case): for a 10-species mixture,
 * full mix() is ~2-5 ms (dominated by refreshPossibleReactions scanning ~150 reactions ×
 * 10 species), lite mix() is ~50-200 µs. Pump issues 5-20 fills per tick → full mix
 * accumulates 10-100 ms/tick → visible periodic GC stutter. Lite mix saves the per-fill
 * cost so allocations stay below GC threshold.</p>
 *
 * <p>{@code fullProcessing=true} keeps the original behaviour for reactor / display callers
 * (Vat tick mix, Basin reaction mix, JEI display).</p>
*/
    public static LegacyMixture mix(Map<LegacyMixture, Double> mixtures, boolean fullProcessing) {
        if (mixtures.size() == 0) return new LegacyMixture();
        if (mixtures.size() == 1) return mixtures.keySet().iterator().next();
        LegacyMixture resultMixture = new LegacyMixture();
        Map<LegacySpecies, Double> moleculesAndMoles = new HashMap<>();
        Map<ReactionResult, Double> reactionResultsAndMoles = new HashMap<>();
        double totalAmount = 0d;
        float totalEnergy = 0f;

        for (Entry<LegacyMixture, Double> mixtureAndAmount : mixtures.entrySet()) {
            LegacyMixture mixture = mixtureAndAmount.getKey();
            double amount = mixtureAndAmount.getValue();
            totalAmount += amount;

            for (Entry<LegacySpecies, Float> entry : mixture.contents.entrySet()) {
                LegacySpecies molecule = entry.getKey();
                float concentration = entry.getValue();
                moleculesAndMoles.merge(molecule, concentration * amount, (m1, m2) -> m1 + m2);
                totalEnergy += molecule.getMolarHeatCapacity() * concentration * mixture.temperature * amount;
                totalEnergy += molecule.getLatentHeat() * concentration * mixture.states.get(molecule) * amount;
            }

            for (Entry<ReactionResult, Float> entry : mixture.reactionResults.entrySet()) {
                reactionResultsAndMoles.merge(entry.getKey(), entry.getValue() * amount, (r1, r2) -> r1 + r2);
            }
        }

        for (Entry<LegacySpecies, Double> moleculeAndMoles : moleculesAndMoles.entrySet()) {
            LegacySpecies molecule = moleculeAndMoles.getKey();
            resultMixture.internalAddMolecule(molecule, (float) (moleculeAndMoles.getValue() / totalAmount), false);
            resultMixture.states.put(molecule, 0f);
        }

        for (Entry<ReactionResult, Double> reactionResultAndMoles : reactionResultsAndMoles.entrySet()) {
            if (reactionResultAndMoles.getKey().getReaction().isPresent())
                resultMixture.incrementReactionResults(reactionResultAndMoles.getKey().getReaction().get(),
                    (float) (reactionResultAndMoles.getValue() / totalAmount));
        }

        resultMixture.temperature = 0f;
        resultMixture.updateNextBoilingPoints();
        resultMixture.heat(totalEnergy / (float) totalAmount);

        if (fullProcessing) {
            resultMixture.refreshPossibleReactions();
            resultMixture.updateName();
            resultMixture.updateColor();
            resultMixture.updateNextBoilingPoints();
        }

        return resultMixture;
    }

    @Override
    public float getConcentrationOf(LegacySpecies molecule) {
        return super.getConcentrationOf(molecule);
    }

    /** Whether this Mixture will react any further.*/
    public boolean isAtEquilibrium() {
        return equilibrium;
    }

    /** Let this Mixture know it should no longer be at equilibrium.*/
    public void disturbEquilibrium() {
        equilibrium = false;
    }

    /**
 * Reacts the contents of this Mixture for one tick, if not already at equilibrium.
 * @param cycles Number of times each tick the reactions should be enacted
*/
    public void reactForTick(ReactionContext context, int cycles) {

        boolean shouldUpdateDisplay = true;

        for (int cycle = 0; cycle < cycles; cycle++) {

            if (equilibrium) {
                shouldUpdateDisplay = false;
                break;
            }

            equilibrium = true;
            boolean shouldRefreshPossibleReactions = false;

            Map<LegacySpecies, Float> oldContents = new HashMap<>(contents);

            Map<LegacyReaction, Float> reactionRates = new HashMap<>();
            List<LegacyReaction> orderedReactions = new ArrayList<>();

            orderEachReaction: for (LegacyReaction possibleReaction : possibleReactions) {
                if (possibleReaction.consumesItem()) continue orderEachReaction;

                for (IItemReactant itemReactant : possibleReaction.getItemReactants()) {
                    boolean validStackFound = false;
                    checkAllItems: for (ItemStack stack : context.availableItemStacks) {
                        if (itemReactant.isItemValid(stack)) {
                            validStackFound = true;
                            break checkAllItems;
                        }
                    }
                    if (!validStackFound) continue orderEachReaction;
                }

                reactionRates.put(possibleReaction, calculateReactionRate(possibleReaction, context) / cycles);
                orderedReactions.add(possibleReaction);
            }

            orderedReactions.sort((r1, r2) -> reactionRates.get(r1).compareTo(reactionRates.get(r2)));

            doEachReaction: for (LegacyReaction reaction : orderedReactions) {

                Float molesOfReaction = reactionRates.get(reaction);

                for (LegacySpecies reactant : reaction.getReactants()) {
                    int reactantMolarRatio = reaction.getReactantMolarRatio(reactant);
                    float reactantConcentration = getConcentrationOf(reactant);
                    if (reactantConcentration < reactantMolarRatio * molesOfReaction) {
                        molesOfReaction = reactantConcentration / (float) reactantMolarRatio;
                    }
                }

                if (molesOfReaction <= 0f) continue doEachReaction;

                shouldRefreshPossibleReactions |= doReaction(reaction, molesOfReaction);
            }

            for (LegacySpecies molecule : oldContents.keySet()) {
                if (!areVeryClose(oldContents.get(molecule), getConcentrationOf(molecule))) {
                    equilibrium = false;
                }
            }

            if (shouldRefreshPossibleReactions) {
                refreshPossibleReactions();
            }
        }

        // Purge removed Molecules
        boolean shouldUpdateReactions = false;
        Iterator<Entry<LegacySpecies, Integer>> iterator = moleculesToRemove.entrySet().iterator();
        while (iterator.hasNext()) {
            Entry<LegacySpecies, Integer> entry = iterator.next();
            entry.setValue(entry.getValue() - 1);
            if (entry.getValue() <= 0) {
                removeMolecule(entry.getKey());
                iterator.remove();
                shouldUpdateReactions = true;
            }
        }
        if (shouldUpdateReactions) refreshPossibleReactions();

        if (shouldUpdateDisplay) {
            updateName();
            updateColor();
        }
    }

    /**
 * Add or take heat from this Mixture. Will boil/condense Molecules and change the temperature.
 * @param energyDensity In joules per bucket
*/
    public void heat(float energyDensity) {
        heatInternal(energyDensity);
        // refresh based on FINAL settled temperature (post-recursion). Restores
        // any molecule that was excluded by mid-recursion updateNextBoilingPoints(true)
        // calls so that future heat() calls correctly classify all species.
        updateNextBoilingPoints(false);
    }

    /**
 * Recursive heat implementation. <b>Do not call externally</b> — call {@link #heat(float)}
 * which adds the post-recursion {@link #updateNextBoilingPoints(false)} refresh required
 * for correctness across phase-transition cycles.
*/
    private void heatInternal(float energyDensity) {
        float volumetricHeatCapacity = getVolumetricHeatCapacity();
        if (volumetricHeatCapacity == 0f) return;

        float temperatureChange = energyDensity / volumetricHeatCapacity;

        if (temperatureChange == 0f) {
            return;
        } else if (temperatureChange > 0f) {
            if (nextHigherBoilingPoint.getSecond() != null && temperature + temperatureChange >= nextHigherBoilingPoint.getFirst()) {

                temperatureChange = nextHigherBoilingPoint.getFirst() - temperature;
                temperature += temperatureChange;
                energyDensity -= temperatureChange * getVolumetricHeatCapacity();

                LegacySpecies molecule = nextHigherBoilingPoint.getSecond();
                float liquidConcentration = getConcentrationOf(molecule) * (1f - states.get(molecule));
                float energyRequiredToFullyBoil = liquidConcentration * molecule.getLatentHeat();

                if (energyDensity > energyRequiredToFullyBoil) {
                    states.put(molecule, 1f);
                    updateNextBoilingPoints(true);
                    boiling = false;
                    heatInternal(energyDensity - energyRequiredToFullyBoil);
                } else {
                    float boiled = energyDensity / (molecule.getLatentHeat() * getConcentrationOf(molecule));
                    states.merge(molecule, boiled, (f1, f2) -> f1 + f2);
                    boiling = true;
                }

                equilibrium = false;

            } else {
                temperature += temperatureChange;
            }
        } else {
            if (nextLowerBoilingPoint.getSecond() != null && temperature + temperatureChange < nextLowerBoilingPoint.getFirst()) {

                temperatureChange = nextLowerBoilingPoint.getFirst() - temperature;
                temperature += temperatureChange;
                energyDensity -= temperatureChange * getVolumetricHeatCapacity();

                LegacySpecies molecule = nextLowerBoilingPoint.getSecond();
                float gasConcentration = getConcentrationOf(molecule) * states.get(molecule);
                float energyReleasedWhenFullyCondensed = gasConcentration * molecule.getLatentHeat();

                if (energyDensity < -energyReleasedWhenFullyCondensed) {
                    states.put(molecule, 0f);
                    updateNextBoilingPoints(true);
                    boiling = false;
                    heatInternal(energyDensity + energyReleasedWhenFullyCondensed);
                } else {
                    float condensed = -energyDensity / (molecule.getLatentHeat() * getConcentrationOf(molecule));
                    states.merge(molecule, 1f - condensed, (f1, f2) -> f1 + f2 - 1f);
                    boiling = true;
                }

                equilibrium = false;

            } else {
                temperature += temperatureChange;
            }
        }

        temperature = Math.max(temperature, 0.0001f);
    }

    /**
 * Enact all Reactions that involve Item Stacks (dissolutions + Item-catalyzed Reactions).
 * @return The resultant Item Stacks after dissolution has occurred
*/
    public List<ItemStack> dissolveItems(ReactionContext context, double volume) {
        List<ItemStack> availableStacks = List.copyOf(context.availableItemStacks);
        if (availableStacks.isEmpty()) return availableStacks;
        boolean shouldRefreshReactions = false;

        List<LegacyReaction> orderedReactions = new ArrayList<>();

        for (LegacyReaction possibleReaction : possibleReactions) {
            if (!possibleReaction.consumesItem()) continue;
            orderedReactions.add(possibleReaction);
        }

        if (orderedReactions.isEmpty()) return availableStacks;

        possibleReactions.sort((r1, r2) -> ((Float) calculateReactionRate(r1, context))
            .compareTo(calculateReactionRate(r2, context)));

        tryEachReaction: for (LegacyReaction reaction : orderedReactions) {

            Map<ItemStack, ItemStack> copiesAndStacks = new HashMap<>(availableStacks.size());
            Map<IItemReactant, ItemStack> reactantsAndStacks = new HashMap<>(reaction.getItemReactants().size());

            for (ItemStack stack : availableStacks) {
                if (!stack.isEmpty()) copiesAndStacks.put(stack.copy(), stack);
            }

            while (true) {

                for (LegacySpecies reactant : reaction.getReactants()) {
                    if (getConcentrationOf(reactant) < (float) reaction.getReactantMolarRatio(reactant)
                        * reaction.getMolesPerItem() / (float) volume) continue tryEachReaction;
                }

                for (IItemReactant itemReactant : reaction.getItemReactants()) {
                    boolean validItemFound = false;
                    for (ItemStack stackCopy : copiesAndStacks.keySet()) {
                        if (itemReactant.isItemValid(stackCopy)) {
                            validItemFound = true;
                            if (!itemReactant.isCatalyst()) {
                                itemReactant.consume(stackCopy);
                                reactantsAndStacks.put(itemReactant, copiesAndStacks.get(stackCopy));
                            }
                        }
                    }
                    if (!validItemFound) continue tryEachReaction;
                }

                for (IItemReactant itemReactant : reaction.getItemReactants()) {
                    if (!itemReactant.isCatalyst()) itemReactant.consume(reactantsAndStacks.get(itemReactant));
                }

                equilibrium = false;
                shouldRefreshReactions |= doReaction(reaction, reaction.getMolesPerItem() / (float) volume);
            }
        }

        updateName();
        updateColor();

        if (shouldRefreshReactions) refreshPossibleReactions();

        return availableStacks;
    }

    /**
 * Corrects the volume of this Mixture. Mutative.
 * @deprecated doesn't account for space occupied by gases
*/
    @Deprecated
    public int recalculateVolume(int initialVolume) {
        if (contents.isEmpty()) return 0;
        double initialVolumeInLiters = (double) initialVolume / Constants.MILLIBUCKETS_PER_LITER;
        double newVolumeInLiters = 0d;

        Map<LegacySpecies, Double> molesOfMolecules = new HashMap<>();
        for (Entry<LegacySpecies, Float> entry : contents.entrySet()) {
            LegacySpecies molecule = entry.getKey();
            double molesOfMolecule = entry.getValue() * initialVolumeInLiters;
            molesOfMolecules.put(molecule, molesOfMolecule);
            newVolumeInLiters += molesOfMolecule / molecule.getPureConcentration();
        }
        for (Entry<LegacySpecies, Double> entry : molesOfMolecules.entrySet()) {
            contents.replace(entry.getKey(), (float) (entry.getValue() / newVolumeInLiters));
        }

        Map<ReactionResult, Float> resultsCopy = new HashMap<>(reactionResults);
        for (Entry<ReactionResult, Float> entry : resultsCopy.entrySet()) {
            reactionResults.replace(entry.getKey(), (float) (entry.getValue() * initialVolumeInLiters / newVolumeInLiters));
        }

        return (int) ((newVolumeInLiters * Constants.MILLIBUCKETS_PER_LITER));
    }

    /** Adjust concentrations so the number of moles is conserved if the volume changes.*/
    public void scale(float volumeIncreaseFactor) {
        contents.replaceAll((molecule, concentration) -> concentration / volumeIncreaseFactor);
        reactionResults.replaceAll((reactionResult, molesPerBucket) -> molesPerBucket / volumeIncreaseFactor);
    }

    public static record Phases(LegacyMixture gasMixture, Double gasVolume, LegacyMixture liquidMixture, Double liquidVolume) {}

    /**
 * Get two new Mixtures from one — all gas, all liquid. Doesn't mutate this Mixture.
*/
    public Phases separatePhases(double initialVolume) {
        Map<LegacySpecies, Double> liquidMoles = new HashMap<>();
        Map<LegacySpecies, Double> gasMoles = new HashMap<>();

        double newLiquidVolume = 0d;
        double newGasVolume = 1d;

        LegacyMixture liquidMixture = new LegacyMixture();
        LegacyMixture gasMixture = new LegacyMixture();

        for (Entry<LegacySpecies, Float> entry : contents.entrySet()) {
            LegacySpecies molecule = entry.getKey();
            float concentration = entry.getValue();
            float proportionGaseous = states.get(molecule);

            double molesOfLiquidMolecule = concentration * (1f - proportionGaseous) * initialVolume;
            liquidMoles.put(molecule, molesOfLiquidMolecule);
            newLiquidVolume += molesOfLiquidMolecule / molecule.getPureConcentration();

            gasMoles.put(molecule, concentration * proportionGaseous * initialVolume);
        }

        for (Entry<LegacySpecies, Double> entry : liquidMoles.entrySet()) {
            double moles = entry.getValue();
            if (moles == 0d) continue;
            liquidMixture.internalAddMolecule(entry.getKey(), (float) (moles / newLiquidVolume), false);
            liquidMixture.states.put(entry.getKey(), 0f);
        }
        for (Entry<LegacySpecies, Double> entry : gasMoles.entrySet()) {
            double moles = entry.getValue();
            if (moles == 0d) continue;
            gasMixture.internalAddMolecule(entry.getKey(), (float) (moles / newGasVolume), false);
            gasMixture.states.put(entry.getKey(), 1f);
        }

        for (Entry<ReactionResult, Float> entry : reactionResults.entrySet()) {
            double resultMoles = entry.getValue() * initialVolume;
            double newTotalVolume = newLiquidVolume + newGasVolume;
            liquidMixture.reactionResults.put(entry.getKey(), (float) (resultMoles / newTotalVolume));
            gasMixture.reactionResults.put(entry.getKey(), (float) (resultMoles / newTotalVolume));
        }

        liquidMixture.temperature = temperature;
        gasMixture.temperature = temperature;
        liquidMixture.refreshPossibleReactions();
        gasMixture.refreshPossibleReactions();
        liquidMixture.equilibrium = equilibrium;
        gasMixture.equilibrium = equilibrium;

        return new Phases(gasMixture, newGasVolume, liquidMixture, newLiquidVolume);
    }

    /**
 * Increase the number of moles of Reaction which have occurred, add products, remove reactants.
 * @return Whether possible Reactions should be refreshed
*/
    protected boolean doReaction(LegacyReaction reaction, float molesPerLiter) {

        boolean shouldRefreshPossibleReactions = false;

        for (LegacySpecies reactant : reaction.getReactants()) {
            changeConcentrationOf(reactant, -(molesPerLiter * reaction.getReactantMolarRatio(reactant)), false);
        }

        addEachProduct: for (LegacySpecies product : reaction.getProducts()) {
            if (product.isNovel() && getConcentrationOf(product) == 0f) {
                if (internalAddMolecule(product, molesPerLiter * reaction.getProductMolarRatio(product), false)) {
                    shouldRefreshPossibleReactions = true;
                }
                continue addEachProduct;
            }

            if (!contents.containsKey(product)) {
                shouldRefreshPossibleReactions = true;
            }
            changeConcentrationOf(product, molesPerLiter * reaction.getProductMolarRatio(product), false);
        }

        heat(-reaction.getEnthalpyChange() * 1000 * molesPerLiter);
        incrementReactionResults(reaction, molesPerLiter);

        return shouldRefreshPossibleReactions;
    }

    protected void incrementReactionResults(LegacyReaction reaction, float molesPerBucket) {
        if (!reaction.hasResult()) return;
        ReactionResult result = reaction.getResult();
        reactionResults.merge(result, molesPerBucket, (f1, f2) -> f1 + f2);
    }

    /**
 * React this Mixture until it reaches equilibrium. Mutative.
*/
    public ReactionInBasinResult reactInBasin(int volume, List<ItemStack> availableStacks,
                                              float heatingPower, float outsideTemperature) {
        float volumeInLiters = (float) volume / Constants.MILLIBUCKETS_PER_LITER;
        int ticks = 0;

        ReactionContext context = new ReactionContext(availableStacks, 0f, false);
        dissolveItems(context, volumeInLiters);
        while (!equilibrium && ticks < 600) {
            float energyChange = heatingPower / TICKS_PER_SECOND;
            // Fourier's Law (sort-of); Basin has fixed conductance of 100, divide by 20 ticks per second
            energyChange += (outsideTemperature - temperature) * 100f / TICKS_PER_SECOND;
            if (Math.abs(energyChange) > 0.0001f) {
                heat(1000 * energyChange / volume);
            }
            reactForTick(context, 1);
            ticks++;
        }

        if (ticks == 0) return new ReactionInBasinResult(0, Map.of(), volume);

        int amount = recalculateVolume(volume);

        return new ReactionInBasinResult(ticks, getCompletedResults(amount), amount);
    }

    /**
 * If any Results have had enough moles to have occurred, remove them from the Mixture and return them.
*/
    public Map<ReactionResult, Integer> getCompletedResults(double volumeInLiters) {
        Map<ReactionResult, Integer> results = new HashMap<>();
        if (reactionResults.isEmpty()) return results;
        for (ReactionResult result : reactionResults.keySet()) {

            if (result.isOneOff()) {
                results.put(result, 1);
                continue;
            }

            float molesPerLiterOfReaction = reactionResults.get(result);
            int numberOfResult = (int) (volumeInLiters * molesPerLiterOfReaction / result.getRequiredMoles());
            if (numberOfResult == 0) continue;

            reactionResults.replace(result, molesPerLiterOfReaction
                - numberOfResult * result.getRequiredMoles() / (float) volumeInLiters);
            results.put(result, numberOfResult);
        }
        return results;
    }

    /** Get the heat capacity (in joules per bucket-kelvin) of this Mixture.*/
    public float getVolumetricHeatCapacity() {
        float totalHeatCapacity = 0f;
        for (Entry<LegacySpecies, Float> entry : contents.entrySet()) {
            totalHeatCapacity += entry.getKey().getMolarHeatCapacity() * entry.getValue();
        }
        return totalHeatCapacity;
    }

    /** Set the Molecules which will be next to condense or boil if the temperature changes.*/
    protected void updateNextBoilingPoints() { updateNextBoilingPoints(false); }

    protected void updateNextBoilingPoints(boolean ignoreCurrentTemperature) {
        nextHigherBoilingPoint = Pair.of(Float.MAX_VALUE, null);
        nextLowerBoilingPoint = Pair.of(0f, null);
        for (LegacySpecies molecule : contents.keySet()) {
            float bp = molecule.getBoilingPoint();
            if (bp < temperature || (bp == temperature && !ignoreCurrentTemperature)) {
                if (bp > nextLowerBoilingPoint.getFirst()) nextLowerBoilingPoint = Pair.of(bp, molecule);
            }
            if (bp > temperature || (bp == temperature && !ignoreCurrentTemperature)) {
                if (bp < nextHigherBoilingPoint.getFirst()) nextHigherBoilingPoint = Pair.of(bp, molecule);
            }
        }
    }

    /**
 * Adds a Molecule to this Mixture, checking for matching novel Molecules.
 * @return {@code true} if possible reactions need to be refreshed (a new Molecule was added)
*/
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean internalAddMolecule(LegacySpecies molecule, float concentration, boolean shouldRefreshReactions) {

        if (contents.containsKey(molecule)) {
            changeConcentrationOf(molecule, concentration, shouldRefreshReactions);
            return false;
        }

        // Generated products are distinct objects even when they describe the same structure.
        // Resolve them before indexing functional groups so each group belongs to a stored species.
        if (molecule.isNovel()) {
            for (LegacySpecies novelMolecule : novelMolecules) {
                if (novelMolecule.getFullID().equals(molecule.getFullID())) {
                    changeConcentrationOf(novelMolecule, concentration, shouldRefreshReactions);
                    equilibrium = false;
                    return false;
                }
            }
        }

        super.addMolecule(molecule, concentration);
        if (!contents.containsKey(molecule)) return false;

        List<LegacyFunctionalGroup<?>> functionalGroups = molecule.getFunctionalGroups();
        if (functionalGroups.size() != 0) {
            for (LegacyFunctionalGroup group : functionalGroups) {
                addGroupToMixture(molecule, group);
            }
        }

        if (molecule.isNovel()) {
            novelMolecules.add(molecule);
            reactionResults.put(new NovelCompoundSynthesizedReactionResult(0f, null, molecule), 1f);
        }

        if (shouldRefreshReactions) {
            refreshPossibleReactions();
        }

        equilibrium = false;

        return true;
    }

    private <G extends LegacyFunctionalGroup<G>> void addGroupToMixture(LegacySpecies molecule, G group) {
        LegacyFunctionalGroupType<? extends G> groupType = group.getType();
        if (!groupIDsAndMolecules.containsKey(groupType)) {
            groupIDsAndMolecules.put(groupType, new ArrayList<>());
        }
        groupIDsAndMolecules.get(groupType).add(new GenericReactant<>(molecule, group));
    }

    /**
 * Removes the given Molecule from this Mixture. Does not refresh possible Reactions.
*/
    private LegacyMixture removeMolecule(LegacySpecies molecule) {

        List<LegacyFunctionalGroup<?>> functionalGroups = molecule.getFunctionalGroups();
        if (functionalGroups.size() != 0) {
            for (LegacyFunctionalGroup<?> group : functionalGroups) {
                groupIDsAndMolecules.get(group.getType()).removeIf(reactant -> reactant.getMolecule() == molecule);
            }
        }
        if (molecule.isNovel()) novelMolecules.remove(molecule);

        contents.remove(molecule);
        equilibrium = false;
        updateNextBoilingPoints();

        return this;
    }

    /**
 * Alters the concentration of a Molecule in a Mixture.
*/
    private LegacyMixture changeConcentrationOf(LegacySpecies molecule, float change, boolean shouldRefreshReactions) {
        Float currentConcentration = getConcentrationOf(molecule);

        if (!contents.containsKey(molecule) && change > 0f) internalAddMolecule(molecule, change, shouldRefreshReactions);

        if (currentConcentration <= 0f && change < 0f)
            throw new IllegalArgumentException("Attempted to decrease concentration of Molecule '"
                + molecule.getFullID() + "', which was not in a Mixture. The Mixture contains " + getContentsString());

        float newConcentration = Math.max(currentConcentration + change, 0f);
        contents.replace(molecule, newConcentration);
        if (newConcentration <= 0f) moleculesToRemove.put(molecule, 10);
        if (newConcentration > 0f) moleculesToRemove.remove(molecule);
        return this;
    }

    /**
 * Get the rate of this Reaction in moles/Bucket/tick.
*/
    private float calculateReactionRate(LegacyReaction reaction, ReactionContext context) {
        float rate = reaction.getRateConstant(temperature) / (float) TICKS_PER_SECOND;
        for (LegacySpecies molecule : reaction.getOrders().keySet()) {
            rate *= (float) Math.pow(getConcentrationOf(molecule), reaction.getOrders().get(molecule));
        }
        if (reaction.needsUV()) rate *= context.UVPower;
        return rate;
    }

    /**
 * Determine all Reactions + GenericReactions possible with the Molecules in this Mixture.
*/
    private void refreshPossibleReactions() {
        possibleReactions = new ArrayList<>();
        Set<LegacyReaction> newPossibleReactions = new HashSet<>();

        // Generic Reactions
        for (LegacyFunctionalGroupType<?> groupType : groupIDsAndMolecules.keySet()) {
            checkEachGenericReaction: for (GenericReaction genericReaction : LegacyFunctionalGroup.getReactionsOfGroupByID(groupType)) {

                if (!genericReaction.isPossibleIn(this)) continue checkEachGenericReaction;

                if (genericReaction.involvesSingleGroup()) {
                    newPossibleReactions.addAll(specifySingleGroupGenericReactions(genericReaction, groupIDsAndMolecules.get(groupType)));
                } else {
                    if (!(genericReaction instanceof DoubleGroupGenericReaction<?, ?> dggr)) continue checkEachGenericReaction;
                    if (groupType != dggr.getFirstGroupType()) continue checkEachGenericReaction;

                    LegacyFunctionalGroupType<?> secondGroupType = dggr.getSecondGroupType();
                    if (!groupIDsAndMolecules.keySet().contains(secondGroupType)) continue checkEachGenericReaction;

                    newPossibleReactions.addAll(specifyDoubleGroupGenericReactions(dggr,
                        groupIDsAndMolecules.get(groupType), groupIDsAndMolecules.get(secondGroupType)));
                }
            }
        }

        // All Reactions
        for (LegacySpecies possibleReactant : contents.keySet()) {
            newPossibleReactions.addAll(possibleReactant.getReactantReactions());
        }
        for (LegacyReaction reaction : newPossibleReactions) {
            // Check necessary reactants are all present
            boolean reactionHasAllReactants = true;
            for (LegacySpecies necessaryReactantOrCatalyst : reaction.getOrders().keySet()) {
                if (getConcentrationOf(necessaryReactantOrCatalyst) == 0) {
                    reactionHasAllReactants = false;
                    break;
                }
            }
            if (reactionHasAllReactants) {
                possibleReactions.add(reaction);
            }
        }
    }

    /**
 * Given a single-group Generic Reaction, generate the specified Reactions that apply to this Mixture.
*/
    @SuppressWarnings("unchecked")
    private <G extends LegacyFunctionalGroup<G>> List<LegacyReaction> specifySingleGroupGenericReactions(
        GenericReaction genericReaction, List<GenericReactant<?>> reactants) {
        List<LegacyReaction> reactions = new ArrayList<>();
        SingleGroupGenericReaction<G> singleGroupGenericReaction = (SingleGroupGenericReaction<G>) genericReaction;
        for (GenericReactant<?> reactant : reactants) {
            try {
                LegacyReaction reaction = singleGroupGenericReaction.generateReaction((GenericReactant<G>) reactant);
                if (reaction != null) reactions.add(reaction);
            } catch (ChemistryException e) {
                // swallow chemistry exceptions
            }
        }
        return reactions;
    }

    /**
 * Given a double-group Generic Reaction, generate the specified Reactions.
*/
    @SuppressWarnings("unchecked")
    private <G1 extends LegacyFunctionalGroup<G1>, G2 extends LegacyFunctionalGroup<G2>> List<LegacyReaction>
    specifyDoubleGroupGenericReactions(GenericReaction genericReaction,
                                        List<GenericReactant<?>> firstReactants,
                                        List<GenericReactant<?>> secondReactants) {
        DoubleGroupGenericReaction<G1, G2> doubleGroupGenericReaction = (DoubleGroupGenericReaction<G1, G2>) genericReaction;
        List<LegacyReaction> reactions = new ArrayList<>();
        for (GenericReactant<?> first : firstReactants) {
            for (GenericReactant<?> second : secondReactants) {
                if (first.getMolecule() == second.getMolecule()) continue;
                try {
                    LegacyReaction reaction = doubleGroupGenericReaction.generateReaction(
                        (GenericReactant<G1>) first,
                        (GenericReactant<G2>) second);
                    if (reaction != null) reactions.add(reaction);
                } catch (ChemistryException e) {
                    // Skip combinations that cannot form a valid reaction.
                }
            }
        }
        return reactions;
    }

    public static boolean areVeryClose(float f1, float f2) {
        return Math.abs(f1 - f2) <= 1 / 512f / 512f;
    }

    /**
 * The context for the reaction of a LegacyMixture. <strong>Do not modify its fields.</strong>
*/
    public static class ReactionContext {

        public final ImmutableList<ItemStack> availableItemStacks;
        public final float UVPower;
        public final boolean electrolysing;

        public ReactionContext(List<ItemStack> availableItemStacks, float UVPower, boolean electrolysing) {
            this.availableItemStacks = ImmutableList.copyOf(availableItemStacks);
            this.UVPower = UVPower;
            this.electrolysing = electrolysing;
        }
    }
}
