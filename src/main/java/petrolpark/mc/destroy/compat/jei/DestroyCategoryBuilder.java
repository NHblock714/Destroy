package petrolpark.mc.destroy.compat.jei;

import java.util.function.Consumer;

import com.petrolpark.compat.jei.category.builder.PetrolparkCategoryBuilder;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;

import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.crafting.Recipe;

import petrolpark.mc.destroy.Destroy;

/**
 * Destroy-specific subclass of {@link PetrolparkCategoryBuilder} that adds
 * Mixture-awareness metadata to the build pipeline. Categories that accept
 * {@link petrolpark.mc.destroy.core.recipe.ingredient.fluid.MixtureFluidIngredient
 * Mixture fluid ingredients} (Aging, Distillation, Centrifugation, Mixture conversion, etc.)
 * call {@link #acceptsMixtures()} during fluent build → at
 * {@link #finalizeBuilding finalizeBuilding} time the {@code (RecipeType, recipeClass)} pair is
 * recorded into {@link DestroyJEI#MIXTURE_APPLICABLE_RECIPE_TYPES}, which the
 * {@code ChemicalSpeciesRecipeManagerPlugin} consumes to drill down from
 * "I'm looking up species X" → "show all recipe categories that consume/produce a Mixture
 * containing X".
*/
public class DestroyCategoryBuilder<R extends Recipe<?>>
    extends PetrolparkCategoryBuilder<R, DestroyCategoryBuilder<R>> {

    private boolean acceptsMixtures = false;

    public DestroyCategoryBuilder(Class<? extends R> recipeClass, Consumer<CreateRecipeCategory<?>> categoryAdder) {
        super(Destroy.MOD_ID, recipeClass, categoryAdder);
    }

    /**
 * Mark this category as accepting Mixture fluid ingredients/results. At build time the
 * resulting {@link RecipeType} is recorded in {@link DestroyJEI#MIXTURE_APPLICABLE_RECIPE_TYPES}
 * so the chemical-species recipe-manager plugin can find recipe types that interact with
 * a given molecule's Mixtures.
 *
 * @return self (fluent).
*/
    public DestroyCategoryBuilder<R> acceptsMixtures() {
        this.acceptsMixtures = true;
        return this;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void finalizeBuilding(RecipeType<R> type, CreateRecipeCategory<R> category, Class<? extends R> trueClass) {
        if (acceptsMixtures) {
            // CRITICAL: must use category.getRecipeType() (which is
            // RecipeType<RecipeHolder<R>> with recipeClass=RecipeHolder.class), NOT the raw
            // `type` parameter (which is RecipeType[uid=..., recipeClass=R.class]).
            // Create's CreateRecipeCategory<T> implements IRecipeCategory<RecipeHolder<T>>, so
            // JEI registers categories keyed by RecipeType[uid, RecipeHolder.class]. Putting
            // the raw type into MIXTURE_APPLICABLE_RECIPE_TYPES makes ChemicalSpeciesRecipeManagerPlugin
            // return these raw types from getRecipeTypes(focus), and JEI's
            // RecipeTypeDataMap.get() lookup then fails with IllegalStateException ("There is no
            // recipe category registered for: RecipeType[uid=destroy:tapping, recipeClass=
            // class TappingRecipe]") because the category was actually registered under
            // RecipeType[uid=destroy:tapping, recipeClass=RecipeHolder].
            // Using category.getRecipeType() keys on the same type as JEI's category map.
            DestroyJEI.MIXTURE_APPLICABLE_RECIPE_TYPES.put(category.getRecipeType(),
                (Class<? extends net.minecraft.world.item.crafting.Recipe<?>>) trueClass);
        }
    }
}
