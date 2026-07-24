package petrolpark.mc.destroy.content.processing.dynamo;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.client.DestroyLang;

/**
 * Charging recipe — single-slot item transformation driven by a Dynamo via
 * {@link ChargingBehaviour}. Used for BELT and WORLD mode (dropped items below a Dynamo); Basin
 * mode uses {@link ElectrolysisRecipe} / {@link petrolpark.mc.destroy.content.processing.dynamo.arcfurnace.ArcFurnaceRecipe}
 * instead. Implements {@link IAssemblyRecipe} so charging can be a step in a Create Sequenced
 * Assembly JEI recipe.
*/
public class ChargingRecipe extends AdvancedProcessingRecipe<RecipeInput> implements IAssemblyRecipe {

    public ChargingRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.CHARGING, params);
        if (processingDuration == 0) processingDuration = 200;
    }

    @Override
    protected int getMaxInputCount() {
        return 1;
    }

    @Override
    protected int getMaxOutputCount() {
        return 1;
    }

    @Override
    public boolean matches(RecipeInput inv, Level level) {
        if (inv.size() == 0) return false;
        return ingredients.get(0).test(inv.getItem(0));
    }

    @Override
    protected boolean canSpecifyDuration() {
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public Component getDescriptionForAssembly() {
        return DestroyLang.translate("recipe.assembly.charging").component();
    }

    @Override
    public void addRequiredMachines(Set<ItemLike> list) {
        list.add(DestroyBlocks.DYNAMO.get());
    }

    @Override
    public void addAssemblyIngredients(List<Ingredient> list) {}

    
    @Override
    public Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        return () -> petrolpark.mc.destroy.compat.jei.category.AssemblyChargingSubCategory::new;
    }
}
