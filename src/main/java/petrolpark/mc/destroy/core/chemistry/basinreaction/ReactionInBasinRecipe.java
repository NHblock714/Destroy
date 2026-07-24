package petrolpark.mc.destroy.core.chemistry.basinreaction;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import petrolpark.mc.library.util.BigItemStack;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.recipe.RecipeFinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.chemistry.api.util.Constants;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture.Phases;
import petrolpark.mc.destroy.chemistry.legacy.ReactionResult;
import petrolpark.mc.destroy.chemistry.legacy.reactionresult.CombinedReactionResult;
import petrolpark.mc.destroy.chemistry.legacy.reactionresult.PrecipitateReactionResult;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe;
import petrolpark.mc.destroy.core.chemistry.vat.IVatHeaterBlock;
import petrolpark.mc.destroy.core.pollution.PollutionHelper;

/**
 * A {@link BasinRecipe} synthesized at Basin-recipe-lookup time by running a
 * {@link petrolpark.mc.destroy.chemistry.legacy.LegacyMixture Mixture} through its chemistry
 * reactions and recording the resulting FluidStacks + ItemStacks as the recipe's outputs. This
 * lets Destroy's chemistry engine hook into Create's vanilla Basin-processing pipeline: the Basin
 * BE queries for a matching recipe; one is dynamically constructed from the current basin contents.
 *
 * </p>
*/
public class ReactionInBasinRecipe extends BasinRecipe {

    /** Max resultant Mixture volume that can fit back in a Basin (1000 mB = 1 Bucket).*/
    static final int BASIN_MAX_OUTPUT = 1000;

    /** Recipe-finder cache key for {@link MixtureConversionRecipe} lookup.*/
    private static final Object recipeCacheKey = new Object();

    public ReactionInBasinRecipe(ProcessingRecipeParams params) {
        super(params);
    }

    /**
 * Attempt to build a dynamic Basin recipe from the given available Fluids + Items + Basin state.
 * Returns {@code null} if the reaction can't proceed (not enough Mixtures, output won't fit,
 * equilibrium reached, etc.).
*/
    @Nullable
    public static ReactionInBasinRecipe create(
            Collection<FluidStack> availableFluids,
            Collection<ItemStack> availableItems,
            BasinBlockEntity basin) {

        StandardProcessingRecipe.Builder<ReactionInBasinRecipe> builder = new StandardProcessingRecipe.Builder<>(
            ReactionInBasinRecipe::new, Destroy.asResource("reaction_in_basin_"));

        List<ItemStack> availableItemsCopy = availableItems.stream()
            .map(ItemStack::copy)
            .filter(stack -> !stack.isEmpty())
            .toList();

        boolean canReact = true;
        boolean containsRawMixtures = false;
        boolean isBasinTooFullToReact = false;

        Level level = basin.getLevel();
        BlockPos pos = basin.getBlockPos();
        float heatingPower = IVatHeaterBlock.getHeatingPower(level, pos.below(), Direction.UP);
        float outsideTemperature = PollutionHelper.getLocalTemperature(level, pos);
        ExtendedBasinBehaviour behaviour = basin.getBehaviour(ExtendedBasinBehaviour.TYPE);
        boolean shouldUpdateBasin = false;

        // Map of Mixtures → volume in liters
        Map<LegacyMixture, Double> mixtures = new HashMap<>(availableFluids.size());
        int totalAmount = 0;

        // Verify all fluids are Mixtures (or convertible)
        for (FluidStack fluidStack : availableFluids) {
            LegacyMixture mixture;
            if (DestroyFluids.isMixture(fluidStack)) {
                mixture = LegacyMixture.readNBT(
                    fluidStack.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
                containsRawMixtures = true;
            } else {
                // Non-Mixture → Mixture conversion via MixtureConversionRecipe
                MixtureConversionRecipe recipe = RecipeFinder
                    .get(recipeCacheKey, level,
                        rh -> rh.value().getType() == DestroyRecipeTypes.MIXTURE_CONVERSION.getType())
                    .stream()
                    .map(rh -> (MixtureConversionRecipe) rh.value())
                    .filter(r -> r.getFluidIngredients().get(0).ingredient().test(fluidStack))
                    .findFirst()
                    .orElse(null);
                if (recipe == null) {
                    canReact = false;
                    break;
                }
                mixture = LegacyMixture.readNBT(
                    recipe.getFluidResults().get(0)
                        .getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()));
            }

            int amount = fluidStack.getAmount();
            totalAmount += amount;
            mixtures.put(mixture, (double) amount / Constants.MILLIBUCKETS_PER_LITER);
        }

        // Don't react without raw Mixtures, even if there are convertible fluids
        if (!containsRawMixtures && mixtures.size() == 1) canReact = false;

        tryReact: if (canReact) {
            LegacyMixture mixture = LegacyMixture.mix(mixtures);

            // snapshot pre-reaction state to detect "no meaningful change". Without this
            // guard, an already-equilibrated mixture (e.g. just-formed impure H₂SO₄) keeps matching
            // the recipe each mixer poll: thermal equilibration / heat() boundary crossings flip
            // {@code equilibrium=false} → {@code reactForTick} fires → returns {@code ticks > 0}
            // even though no real chemistry occurred → recipe is generated → mixer consumes
            // 1mB per cycle (drift from {@code recalculateVolume} {@code (int)} truncation +
            // recipe re-creation each tick). (symptom: impure sulfuric acid repeatedly triggered
            // the mixer and lost 1mB after each cycle, with concentrations stepping down like
            // 483→383→358→358→357→356→… — the 1e-4 tolerance was too tight, since forward+reverse
            // reaction floating-point asymmetry drifts ~1e-3 mol/L per cycle near equilibrium).
            java.util.Map<petrolpark.mc.destroy.chemistry.legacy.LegacySpecies, Float> contentsBefore =
                new HashMap<>();
            for (petrolpark.mc.destroy.chemistry.legacy.LegacySpecies species : mixture.getContents(false)) {
                contentsBefore.put(species, mixture.getConcentrationOf(species));
            }
            // Snapshot item counts (dissolveItems mutates availableItemsCopy in-place).
            int[] itemCountsBefore = new int[availableItemsCopy.size()];
            for (int i = 0; i < availableItemsCopy.size(); i++) {
                itemCountsBefore[i] = availableItemsCopy.get(i).getCount();
            }

            ReactionInBasinResult result = mixture.reactInBasin(
                totalAmount, availableItemsCopy, heatingPower, outsideTemperature);

            // No equilibrium disturbance — nothing to do
            if (result.ticks() == 0) {
                canReact = false;
                break tryReact;
            }

            Phases phases = mixture.separatePhases(result.amount());
            int outputAmount = (int) Math.round(phases.liquidVolume());

            // comprehensive meaningful-change detection. Bail iff ALL of:
            // (a) result.reactionResults (post-getCompletedResults) empty — no precipitates /
            // one-off effects fired this cycle
            // (b) output volume within ±1 mB of input volume — no significant phase / chemistry
            // moles shift (1mB allowance covers recalculateVolume's int-truncation drift)
            // (c) every species concentration within 1e-2 mol/L tolerance vs pre-reaction
            // (covers near-equilibrium reactForTick drift up to ~1e-3 mol/L; threshold
            // picked so genuine reactions still slip through)
            // (d) no items consumed (dissolveItems didn't change any stack count)
            // Lower thresholds don't work: reactForTick at near-equilibrium fires forward+reverse
            // reactions with FP asymmetry → drift up to 1e-3 mol/L per call. recalculateVolume's
            // (int) truncation pumps that drift back into stored mixture each cycle.
            boolean meaningfulChange = !result.reactionResults().isEmpty();
            if (!meaningfulChange && Math.abs(outputAmount - totalAmount) > 1) {
                meaningfulChange = true;
            }
            if (!meaningfulChange) {
                java.util.Set<petrolpark.mc.destroy.chemistry.legacy.LegacySpecies> allSpecies =
                    new java.util.HashSet<>(contentsBefore.keySet());
                allSpecies.addAll(mixture.getContents(false));
                for (petrolpark.mc.destroy.chemistry.legacy.LegacySpecies species : allSpecies) {
                    float before = contentsBefore.getOrDefault(species, 0f);
                    float after = mixture.getConcentrationOf(species);
                    if (Math.abs(before - after) > 1e-2f) {
                        meaningfulChange = true;
                        break;
                    }
                }
            }
            if (!meaningfulChange) {
                for (int i = 0; i < availableItemsCopy.size(); i++) {
                    if (availableItemsCopy.get(i).getCount() != itemCountsBefore[i]) {
                        meaningfulChange = true;
                        break;
                    }
                }
            }
            if (!meaningfulChange) {
                canReact = false;
                break tryReact;
            }

            // Resultant liquid Mixture goes back in the basin
            FluidStack outputMixtureStack = MixtureFluid.of(outputAmount, phases.liquidMixture());
            builder.output(outputMixtureStack);

            // Reject if output won't fit (player needs to drain basin first)
            if (outputMixtureStack.getAmount() > BASIN_MAX_OUTPUT) {
                isBasinTooFullToReact = true;
                canReact = false;
            }

            int duration = Mth.clamp(result.ticks(), 40, 600);
            builder.duration(duration);

            // Resultant ItemStacks
            availableItemsCopy.forEach(stack -> {
                if (stack.isEmpty()) return;
                builder.output(stack);
            });

            // Required ingredients (fluids)
            availableFluids.forEach(fs -> builder.require(new SizedFluidIngredient(
                DataComponentFluidIngredient.of(false, fs), fs.getAmount())));
            // Required ingredients (items)
            availableItems.forEach(stack -> {
                if (stack.isEmpty()) return;
                for (int i = 0; i < stack.getCount(); i++) builder.require(Ingredient.of(stack.getItem()));
            });

            Map<ReactionResult, Integer> reactionResults = new HashMap<>();
            gatherReactionResults(result.reactionResults(), reactionResults, builder);

            // Schedule reaction-results to fire when the mixer finishes its stir cycle
            behaviour.setReactionResults(reactionResults);
            behaviour.evaporatedFluid = MixtureFluid.of(
                (int) Math.round(phases.gasVolume()), phases.gasMixture());
            shouldUpdateBasin = true;
        }

        if (behaviour.tooFullToReact != isBasinTooFullToReact) {
            behaviour.tooFullToReact = isBasinTooFullToReact;
            shouldUpdateBasin = true;
        }
        if (shouldUpdateBasin) basin.sendData();

        if (!canReact) return null;

        return builder.build();
    }

    /** Recursively flatten {@link CombinedReactionResult}s and split out
 * {@link PrecipitateReactionResult precipitates} into recipe outputs.*/
    private static void gatherReactionResults(
            Map<ReactionResult, Integer> resultsOfReaction,
            Map<ReactionResult, Integer> resultsToEnact,
            StandardProcessingRecipe.Builder<ReactionInBasinRecipe> builder) {
        for (ReactionResult reactionresult : resultsOfReaction.keySet()) {
            if (reactionresult instanceof CombinedReactionResult combinedResult) {
                Map<ReactionResult, Integer> childMap = new HashMap<>();
                for (ReactionResult childResult : combinedResult.getChildren()) {
                    childMap.put(childResult, resultsOfReaction.get(combinedResult));
                }
                gatherReactionResults(childMap, resultsToEnact, builder);
            } else if (reactionresult instanceof PrecipitateReactionResult precipitationResult) {
                ItemStack precipitate = precipitationResult.getPrecipitate();
                new BigItemStack(precipitate, (long) resultsOfReaction.get(reactionresult) * precipitate.getCount())
                    .getAsStacks().forEach(builder::output);
            } else {
                resultsToEnact.put(reactionresult, resultsOfReaction.get(reactionresult));
            }
        }
    }

    @Override
    protected int getMaxFluidInputCount() {
        return 4;
    }

    /**
 * The outcome of reacting a Mixture in a Basin. Returned by
 * {@link petrolpark.mc.destroy.chemistry.legacy.LegacyMixture#reactInBasin} and consumed by
 * {@link #create(Collection, Collection, BasinBlockEntity)} to build the recipe output list.
*/
    public static record ReactionInBasinResult(int ticks, Map<ReactionResult, Integer> reactionResults, int amount) {}
}
