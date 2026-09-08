package petrolpark.mc.destroy.core.recipe;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;

import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

/**
 * Destroy-side abstract base for recipe types that consume exactly one fluid ingredient
 * and use Create's ProcessingRecipe pipeline (aging, distillation, mixture conversion,
 * flame retardant application, element tank filling). Shared constraint: exactly one
 * fluid input slot, enforced by the {@code final} {@link #getMaxFluidInputCount()}.
 *
 * <ul>
 * <li>Extending the Library's {@code AdvancedProcessingRecipe} gives subclasses the
 * {@code bookRequired}, {@code allowedBiomes} and {@code firstTimeLuckyKey} fields without
 * touching the params codec.</li>
 * <li>{@code getRequiredFluid()} returns a {@code SizedFluidIngredient}; callers test a
 * stack against it with {@code .ingredient().test(fluidStack)}.</li>
 * <li>{@code matches(...)} always returns {@code false}: these recipes are matched by hand
 * in the Block Entities which use them (see their {@code checkRecipe}) rather than through
 * the RecipeManager.</li>
 * </ul>
 */
public abstract class SingleFluidRecipe extends AdvancedProcessingRecipe<RecipeInput> {

    public SingleFluidRecipe(IRecipeTypeInfo typeInfo, AdvancedProcessingRecipeParams params) {
        super(typeInfo, params);
        // Only set the default duration when this recipe type allows specifying a
        // duration. DistillationRecipe overrides canSpecifyDuration()=false (duration is implicit
        // in fraction count); unconditionally writing 20 here triggered Create's
        // ProcessingRecipe.validate() rejection: "Recipe specified a duration. Durations have no
        // impact on this type of recipe." — broke ALL distillation recipe loads.
        if (canSpecifyDuration() && processingDuration <= 0) processingDuration = 20;
    }

    public SizedFluidIngredient getRequiredFluid() {
        if (fluidIngredients.isEmpty()) {
            throw new IllegalStateException(
                getRecipeTypeName() + " Recipe has no fluid ingredient!");
        }
        return fluidIngredients.get(0);
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    protected int getMaxInputCount() {
        return 0;
    }

    @Override
    protected int getMaxOutputCount() {
        return 0;
    }

    @Override
    protected final int getMaxFluidInputCount() {
        return 1;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 2;
    }

    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }

    /**
 * Human-readable name used in validation error messages (e.g. "Aging").
*/
    public abstract String getRecipeTypeName();
}
