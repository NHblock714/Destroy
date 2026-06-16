package petrolpark.mc.destroy.core.recipe.ingredient.fluid;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;

import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;

/**
 * Abstract base for all Destroy Mixture-aware FluidIngredients. Subclasses test specific chemistry
 * criteria (molecule present · ion pair present · pure species · tag match · etc.) against the
 * {@link LegacyMixture} payload stored in a FluidStack's {@code destroy:mixture} DataComponent.
 *
 * <p><b>Subtypes</b>: MoleculeFluidIngredient · SaltFluidIngredient · MoleculeTagFluidIngredient
 * · IonFluidIngredient · PureSpeciesFluidIngredient · RefrigerantDummyFluidIngredient.</p>
*/
public abstract class MixtureFluidIngredient extends FluidIngredient {

    /**
 * Test if a fluid stack carries a Mixture DataComponent and passes the subtype-specific
 * {@link #testMixture(LegacyMixture)} predicate.
*/
    @Override
    public boolean test(FluidStack stack) {
        if (stack.isEmpty()) return false;
        if (!DestroyFluids.isMixture(stack.getFluid())) return false;
        CompoundTag mixtureTag = stack.get(DestroyDataComponents.MIXTURE);
        if (mixtureTag == null || mixtureTag.isEmpty()) return false;
        LegacyMixture mixture = LegacyMixture.readNBT(mixtureTag);
        if (mixture == null) return false;
        return testMixture(mixture);
    }

    @Override
    public boolean isSimple() {
        return false;
    }

    /**
 * For JEI display — generates example Mixture stacks that would match this ingredient.
*/
    @Override
    protected Stream<FluidStack> generateStacks() {
        net.minecraft.nbt.CompoundTag info = ingredientInfoTag();
        return getExampleMixtures().stream().map(mixture -> {
            FluidStack stack = MixtureFluid.of(getAmountRequired(), mixture);
            if (info != null) stack.set(petrolpark.mc.destroy.DestroyDataComponents.MIXTURE_INGREDIENT_INFO, info);
            return stack;
        });
    }

    /** Keys per {@link petrolpark.mc.destroy.DestroyDataComponents#MIXTURE_INGREDIENT_INFO}
 * javadoc. Default returns null (no spec tooltip).
*/
    protected net.minecraft.nbt.CompoundTag ingredientInfoTag() {
        return null;
    }

    /**
 * Subtype-specific test: does the given parsed {@link LegacyMixture} satisfy the ingredient?
*/
    protected abstract boolean testMixture(LegacyMixture mixture);

    /**
 * Which {@link LegacySpecies molecules} this ingredient references — i.e. molecules whose
 * presence in a mixture would influence the {@link #testMixture testMixture} verdict.
 * Used by {@link petrolpark.mc.destroy.mixin.compat.jei.JeiProcessingRecipeMixin
 * JeiProcessingRecipeMixin} to populate
 * {@link petrolpark.mc.destroy.compat.jei.DestroyJEI#MOLECULES_INPUT MOLECULES_INPUT} so JEI
 * can drill from "I'm looking up molecule X" → "show recipes whose Mixture inputs reference X".
 *
 * <p>Subtypes return:</p>
 * <ul>
 * <li><b>MoleculeFluidIngredient</b>: singleton list of the configured molecule</li>
 * <li><b>IonFluidIngredient</b>: singleton list of the configured ion species</li>
 * <li><b>SaltFluidIngredient</b>: list of [anion, cation]</li>
 * <li><b>PureSpeciesFluidIngredient</b>: singleton list of the configured species</li>
 * <li><b>MoleculeTagFluidIngredient</b>: all molecules carrying the configured tag</li>
 * <li><b>RefrigerantDummyFluidIngredient</b>: empty (no JEI display)</li>
 * </ul>
 *
 * <p>Default returns empty so future subtypes don't break compilation; concrete subtypes
 * override.</p>
*/
    public Collection<LegacySpecies> getReferencedMolecules() {
        return Collections.emptyList();
    }

    /**
 * Example Mixtures to show in JEI when hovering this ingredient. Return empty list for
 * abstract / no-display subtypes (e.g., RefrigerantDummy used for UI labelling only).
*/
    public List<ReadOnlyMixture> getExampleMixtures() {
        return List.of();
    }

    /**
 * Base amount (mB) required for this ingredient. Used by {@link #generateStacks} JEI display.
 * Subtypes override to provide actual recipe amount — base defaults to 1 bucket.
*/
    public int getAmountRequired() {
        return 1000;
    }

    // FluidIngredient declares equals/hashCode abstract. Provide default class-identity
    // implementations; subclasses with additional fields may override for precise comparisons.
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        return obj != null && this.getClass() == obj.getClass();
    }

    @Override
    public int hashCode() {
        return this.getClass().hashCode();
    }
}
