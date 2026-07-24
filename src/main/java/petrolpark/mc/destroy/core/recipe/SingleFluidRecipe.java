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
 * fluid input slot.
 *
 * <ul>
 * <li><b>基类从 {@code ProcessingRecipe<RecipeWrapper>} 换为 {@code AdvancedProcessingRecipe<RecipeInput>}</b>——
 * Create 6.0.8 的 {@code ProcessingRecipe} 现在是泛型双参数 {@code <I extends RecipeInput, P extends ProcessingRecipeParams>}，
 * 我们继承 petrolpark-Library 的 {@code AdvancedProcessingRecipe} 以一次拿到 {@code bookRequired / allowedBiomes /
 * firstTimeLuckyKey} 三个扩展字段。子类具体实现（如 AgeingRecipe）无需关心 params codec。</li>
 * <li><b>{@code FluidIngredient → SizedFluidIngredient}</b>：Create 6.0.8 的 {@code fluidIngredients} 列表元素类型已改。
 * {@code getRequiredFluid()} 返回 {@code SizedFluidIngredient}，调用方用 {@code .ingredient().test(fluidStack)}。</li>
 * <li><b>{@code matches(...)} 默认返 {@code false}</b>：SingleFluid 系列的匹配都是手写（见各 BE 的 checkRecipe），
 * 不走 RecipeManager 的 {@code matches} 路径。参照 petrolpark 的 {@code CentrifugationRecipe}。</li>
 * <li>{@code getMaxFluidInputCount()} 是 {@code final 1} —— 这就是"single fluid"的本质约束。</li>
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
