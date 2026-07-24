package petrolpark.mc.destroy.core.chemistry.recipe;

import javax.annotation.Nullable;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe;
import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipeParams;

import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.GenericReaction;

/**
 * Display-only recipe type wrapping a {@link LegacyReaction} for JEI's ReactionCategory display.
 * These recipes never match at runtime — reactions are chemistry-engine-driven. The category
 * just uses this wrapper to fit into JEI's recipe-manager API.
*/
public class ReactionRecipe extends AdvancedProcessingRecipe<RecipeInput> {

    private static int counter;

    protected LegacyReaction reaction;

    public ReactionRecipe(AdvancedProcessingRecipeParams params) {
        super(DestroyRecipeTypes.REACTION, params);
    }

    public static ReactionRecipe create(LegacyReaction reaction) {
        ReactionRecipe recipe = new AdvancedProcessingRecipe.Builder<ReactionRecipe>(
            ReactionRecipe::new,
            Destroy.asResource("reaction_" + counter++)).build();
        recipe.reaction = reaction;
        return recipe;
    }

    public LegacyReaction getReaction() {
        return reaction;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false; // Display-only · never matches at runtime.
    }

    @Override
    protected int getMaxInputCount() {
        return 0;
    }

    @Override
    protected int getMaxOutputCount() {
        return 0;
    }

    public static class GenericReactionRecipe extends ReactionRecipe {

        protected GenericReaction genericReaction;

        public GenericReactionRecipe(AdvancedProcessingRecipeParams params) {
            super(params);
        }

        @Nullable
        public static GenericReactionRecipe create(GenericReaction genericReaction) {
            try {
                GenericReactionRecipe recipe = new AdvancedProcessingRecipe.Builder<GenericReactionRecipe>(
                    GenericReactionRecipe::new,
                    Destroy.asResource("generic_reaction_" + counter++)).build();
                recipe.reaction = genericReaction.getExampleReaction();
                recipe.genericReaction = genericReaction;
                return recipe;
            } catch (IllegalStateException e) {
                // If this fails, we still want the rest of the JEI plugin to load.
                return null;
            }
        }

        public GenericReaction getGenericReaction() {
            return genericReaction;
        }
    }
}
