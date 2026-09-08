package petrolpark.mc.destroy.compat.emi.category;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiStack;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyItems;

/**
 * EMI category for chemistry reactions — paired with JEI's
 * {@link petrolpark.mc.destroy.compat.jei.category.ReactionCategory}. Shares the same
 * underlying {@link petrolpark.mc.destroy.compat.jei.category.ReactionCategory#RECIPES}
 * registry of reactions, but presents them through EMI's slot/widget model so EMI
 * users get the same chemistry coverage JEI users have.
 *
 * <p>The category ID intentionally matches the JEI category id (
 * {@code destroy:reaction}) so the same translation key {@code destroy.recipe.reaction}
 * supplies the display name across both viewers — no duplicate lang work.</p>
 */
public class ReactionEmiCategory extends EmiRecipeCategory {

    public static final ResourceLocation ID = Destroy.asResource("reaction");

    public ReactionEmiCategory() {
        // ABS plastic item icon matches the JEI category icon choice.
        super(ID, EmiStack.of(DestroyItems.ABS.asStack()));
    }

    /**
     * Reuse the JEI-side translation key {@code destroy.recipe.reaction} so the category
     * display name is consistent across JEI and EMI without maintaining duplicate lang
     * entries. EMI's default of {@code emi.category.<namespace>.<id>} would otherwise
     * surface as the raw untranslated key ({@code emi.category.destroy.reaction}) in
     * viewers that haven't shipped that specific key.
     */
    @Override
    public Component getName() {
        return Component.translatable("destroy.recipe.reaction");
    }
}
