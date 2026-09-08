package petrolpark.mc.destroy.compat.jei.recipemanager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.advanced.IRecipeManagerPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.compat.jei.category.GenericReactionCategory;
import petrolpark.mc.destroy.compat.jei.category.ReactionCategory;
import petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe;

/**
 * Surfaces reversible Reactions in JEI when the user focuses on an item ingredient that the
 * reaction's reactant/precipitate set could match — fixes a JEI default-behavior gap where
 * reversible Reactions wouldn't show under the "Item produces" recipe page if the reaction was
 * defined with the item on the input side.
*/
public class ItemReverseReactionRecipeManagerPlugin implements IRecipeManagerPlugin {

    public static final List<RecipeType<?>> TYPES = List.of(ReactionCategory.TYPE, GenericReactionCategory.TYPE);

    @Override
    public <V> List<RecipeType<?>> getRecipeTypes(IFocus<V> focus) {
        return TYPES;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T, V> List<T> getRecipes(IRecipeCategory<T> recipeCategory, IFocus<V> focus) {
        List<T> recipes = new ArrayList<>();
        focus.checkedCast(VanillaTypes.ITEM_STACK)
            .map(IFocus::getTypedValue)
            .map(ITypedIngredient::getIngredient)
            .ifPresent(stack -> {
                Stream<? extends ReactionRecipe> recipesToCheck;
                String holderIdPrefix;
                if (recipeCategory instanceof GenericReactionCategory) {
                    recipesToCheck = GenericReactionCategory.RECIPES.values().stream();
                    holderIdPrefix = "reverse_generic_reaction_";
                } else if (recipeCategory instanceof ReactionCategory) {
                    recipesToCheck = ReactionCategory.RECIPES.values().stream();
                    holderIdPrefix = "reverse_reaction_";
                } else {
                    return;
                }
                int[] counter = { 0 };
                recipesToCheck.filter(recipe -> {
                    LegacyReaction reaction = recipe.getReaction();
                    RecipeIngredientRole role = focus.getRole();

                    // Plugin scope: SUPPLEMENT JEI's static lookup, never duplicate it. JEI's
                    // own per-slot ingredient matcher already finds every reaction where the
                    // focus item appears in a rendered slot of the recipe layout
                    // (item-reactant INPUT, precipitate OUTPUT, item-catalyst CATALYST). The
                    // gap is reversible reactions: their two sides are interchangeable in the
                    // physical sense, but JEI's slot matcher only sees the one direction the
                    // recipe layout was authored in. For reversible reactions the plugin
                    // surfaces the "other-side" hits that the static path would miss:
                    //   * focus OUTPUT + reversible → match item-reactants (INPUT side)
                    //   * focus INPUT  + reversible → match precipitates (OUTPUT side)
                    // For irreversible reactions, the static path already does the job — any
                    // match here would just be a duplicate of what JEI is already rendering
                    // (was the original bug: item precipitates of irreversible reactions
                    // showed twice in U/R-key lookups). For CATALYST focus the static path
                    // also handles item catalysts natively.
                    if (!reaction.displayAsReversible()) return false;

                    if (role == RecipeIngredientRole.OUTPUT) {
                        // User asked "what makes X" — surface reactions where X is a
                        // non-catalyst item reactant of a reversible reaction (the reverse
                        // direction would produce X).
                        return reaction.getItemReactants().stream()
                            .anyMatch(ir -> !ir.isCatalyst() && ir.isItemValid(stack));
                    }
                    if (role == RecipeIngredientRole.INPUT) {
                        // User asked "what uses X" — surface reactions where X is a
                        // precipitate output of a reversible reaction (the reverse direction
                        // would consume X).
                        return reaction.hasResult()
                            && reaction.getResult().getAllPrecipitates().stream()
                                .anyMatch(p -> ItemStack.matches(p.getPrecipitate(), stack));
                    }
                    return false;
                }).forEach(r -> {
                    // Wrap each ReactionRecipe in a synthetic RecipeHolder — Create's
                    // CreateRecipeCategory<R> implements IRecipeCategory<RecipeHolder<R>>, so
                    // JEI's setRecipe(Object) bridge does `checkcast RecipeHolder` on every
                    // recipe the plugin returns. A bare ReactionRecipe fails that cast and the
                    // layout build collapses into JEI's "recipe has crashed" overlay. The holder
                    // id only needs to be unique within this list.
                    recipes.add((T) new RecipeHolder<>(
                        Destroy.asResource(holderIdPrefix + counter[0]++), r));
                });
            });
        return recipes;
    }

    @Override
    public <T> List<T> getRecipes(IRecipeCategory<T> recipeCategory) {
        return List.of();
    }
}
