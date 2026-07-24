package petrolpark.mc.destroy.content.processing.ageing;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.resources.ResourceLocation;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.core.recipe.SingleFluidRecipe;

/**
 * 熟化桶（Aging Barrel）配方：吃 1 fluid ingredient + 最多 2 item ingredient，输出 1 fluid。
 * 匹配逻辑仍手写在 {@link AgeingBarrelBlockEntity#checkRecipe()}，本类 {@code matches(...) = false}。
*/
public class AgeingRecipe extends SingleFluidRecipe {

    public AgeingRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.AGING, params);
    }

    @Override
    protected int getMaxInputCount() {
        return 2;
    }

    @Override
    protected int getMaxOutputCount() {
        return 0;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 1;
    }

    @Override
    public String getRecipeTypeName() {
        return "aging";
    }

    public static petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe.Builder<AgeingRecipe> builder(ResourceLocation id) {
        return new petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe.Builder<>(AgeingRecipe::new, id);
    }
}
