package petrolpark.mc.destroy.content.product.periodictable;

import com.google.gson.JsonSyntaxException;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.core.recipe.SingleFluidRecipe;

/**
 * Recipe type for filling a tank variant of a periodic-table block with a specific fluid
 * (molten metal / liquid element). Input: 1 FluidStack (SizedFluidIngredient). Output:
 * exactly 1 block-item result (parsed at construction into {@link #blockResult}).
*/
public class ElementTankFillingRecipe extends SingleFluidRecipe {

    public final Block blockResult;

    public ElementTankFillingRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.ELEMENT_TANK_FILLING, params);
        ItemStack result = getRollableResultsAsItemStacks().get(0);
        if (result.getItem() instanceof BlockItem blockItem) {
            blockResult = blockItem.getBlock();
        } else {
            throw new JsonSyntaxException("Element Tank Filling recipes must give a block");
        }
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return true;
    }

    @Override
    protected int getMaxInputCount() {
        return 0;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    @Override
    protected int getMaxFluidOutputCount() {
        return 0;
    }

    @Override
    protected boolean canSpecifyDuration() {
        return false;
    }

    @Override
    public String getRecipeTypeName() {
        return "element tank filling";
    }
}
