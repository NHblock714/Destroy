package petrolpark.mc.destroy.content.processing.centrifuge.potion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.simibubi.create.content.fluids.potion.PotionFluid.BottleType;
import com.simibubi.create.content.fluids.potion.PotionFluidHandler;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.foundation.mixin.accessor.PotionBrewingAccessor;

import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.brewing.BrewingRecipe;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyFluids;

/**
 * Programmatic JEI-display "recipes" that visualize potion-mixing-with-fluid as
 * {@link MixingRecipe} entries in JEI. Data classes {@link #VANILLA_CONTAINERS} +
 * {@link #FLUID_EQUIVALENTS} are consumed by sibling {@link PotionSeparationRecipes} as well.
*/
public class PotionFluidMixingRecipes {

    /** Vanilla potion container items supported by the potion-mixing display.*/
    public static final List<Item> VANILLA_CONTAINERS = List.of(Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION);

    /**
 * Map of item → FluidStack "fluid equivalent" for runtime potion-mixing programmatic recipes.
 * All 5 DestroyFluids potion entries are registered (LONG/STRONG/SPLASH/
 * LINGERING/CORRUPTING_POTION · DestroyFluids.java lines 125-129).
*/
    public static final Map<Item, FluidStack> FLUID_EQUIVALENTS = Map.of(
        Items.DRAGON_BREATH,         new FluidStack((Fluid) DestroyFluids.LINGERING_POTION.getSource(),  100),
        Items.GUNPOWDER,             new FluidStack((Fluid) DestroyFluids.SPLASH_POTION.getSource(),     100),
        Items.REDSTONE,              new FluidStack((Fluid) DestroyFluids.LONG_POTION.getSource(),       100),
        Items.GLOWSTONE_DUST,        new FluidStack((Fluid) DestroyFluids.STRONG_POTION.getSource(),     100),
        Items.FERMENTED_SPIDER_EYE,  new FluidStack((Fluid) DestroyFluids.CORRUPTING_POTION.getSource(), 100)
    );

    /**
 * List of generated MixingRecipe entries. Populated lazily on first {@link #createRecipes(Level)}
 * call (1.21 PotionBrewing is per-level, not static class-load-time).
*/
    private static List<RecipeHolder<MixingRecipe>> ALL;
    private static boolean alreadyGenerated = false;

    /**
 * Returns the cached list of potion-mixing-with-fluid MixingRecipe entries, building it on
 * first call. Safe to call repeatedly — the cache is keyed off a single boolean flag (same
 * pattern as Create 1.21 {@code PotionMixingRecipes}).
*/
    public static List<RecipeHolder<MixingRecipe>> createRecipes(Level level) {
        if (!alreadyGenerated) {
            ALL = createRecipesImpl(level);
            alreadyGenerated = true;
        }
        return ALL;
    }

    private static List<RecipeHolder<MixingRecipe>> createRecipesImpl(Level level) {
        PotionBrewing potionBrewing = level.potionBrewing();
        PotionBrewingAccessor accessor = (PotionBrewingAccessor) potionBrewing;

        List<RecipeHolder<MixingRecipe>> mixingRecipes = new ArrayList<>();
        int recipeIndex = 0;

        // Loop 1 — POTION_MIXES: for each container, for each potion→potion mix,
        // emit a MixingRecipe that substitutes the vanilla ingredient with the fluid-equivalent.
        for (Item container : VANILLA_CONTAINERS) {
            BottleType bottleType = PotionFluidHandler.bottleTypeFromItem(container);
            for (PotionBrewing.Mix<Potion> mix : accessor.create$getPotionMixes()) {
                for (Entry<Item, FluidStack> entry : FLUID_EQUIVALENTS.entrySet()) {
                    if (mix.ingredient().test(new ItemStack(entry.getKey()))) {
                        FluidStack fromFluid = PotionFluidHandler.getFluidFromPotion(
                            new PotionContents(mix.from()), bottleType, 1000);
                        FluidStack toFluid = PotionFluidHandler.getFluidFromPotion(
                            new PotionContents(mix.to()), bottleType, 1000);
                        mixingRecipes.add(createRecipe(
                            "potion_mixing_with_fluid_" + recipeIndex++,
                            entry.getValue(), fromFluid, toFluid));
                    }
                }
            }
        }

        // Loop 2 — CONTAINER_MIXES: for each allowed container→container conversion, for each
        // registered potion, emit a MixingRecipe that pours between bottle types via the fluid.
        for (PotionBrewing.Mix<Item> mix : accessor.create$getContainerMixes()) {
            Item from = mix.from().value();
            if (!VANILLA_CONTAINERS.contains(from)) continue;
            Item to = mix.to().value();
            BottleType fromBottleType = PotionFluidHandler.bottleTypeFromItem(from);
            BottleType toBottleType = PotionFluidHandler.bottleTypeFromItem(to);

            for (Entry<Item, FluidStack> entry : FLUID_EQUIVALENTS.entrySet()) {
                if (mix.ingredient().test(new ItemStack(entry.getKey()))) {
                    for (Reference<Potion> potion : level.registryAccess()
                            .lookupOrThrow(Registries.POTION).listElements().toList()) {
                        FluidStack fromFluid = PotionFluidHandler.getFluidFromPotion(
                            new PotionContents(potion), fromBottleType, 1000);
                        FluidStack toFluid = PotionFluidHandler.getFluidFromPotion(
                            new PotionContents(potion), toBottleType, 1000);
                        mixingRecipes.add(createRecipe(
                            "potion_mixing_with_fluid_" + recipeIndex++,
                            entry.getValue(), fromFluid, toFluid));
                    }
                }
            }
        }

        // Loop 3 — NeoForge-registered BrewingRecipe entries (modded potions). Filter to ones
        // whose input + output are both supported potion containers.
        for (IBrewingRecipe recipe : potionBrewing.getRecipes()) {
            if (recipe instanceof BrewingRecipe brewingRecipe) {
                ItemStack output = brewingRecipe.getOutput();
                if (!VANILLA_CONTAINERS.contains(output.getItem())) continue;

                Ingredient input = brewingRecipe.getInput();
                Ingredient ingredient = brewingRecipe.getIngredient();
                FluidStack outputFluid = null;

                for (Item item : VANILLA_CONTAINERS) {
                    if (input.test(new ItemStack(item))) {
                        for (Entry<Item, FluidStack> entry : FLUID_EQUIVALENTS.entrySet()) {
                            ItemStack stack = new ItemStack(entry.getKey());
                            if (ingredient.test(stack)) {
                                FluidStack fromFluid = PotionFluidHandler.getFluidFromPotionItem(stack);
                                if (outputFluid == null) {
                                    outputFluid = PotionFluidHandler.getFluidFromPotionItem(output);
                                }
                                mixingRecipes.add(createRecipe(
                                    "potion_mixing_with_fluid_" + recipeIndex++,
                                    entry.getValue(), fromFluid, outputFluid));
                            }
                        }
                    }
                }
            }
        }

        return mixingRecipes;
    }

    private static RecipeHolder<MixingRecipe> createRecipe(String id, FluidStack itemSubstitute,
                                                            FluidStack fromFluid, FluidStack toFluid) {
        ResourceLocation recipeId = Destroy.asResource(id);
        MixingRecipe recipe = new StandardProcessingRecipe.Builder<>(MixingRecipe::new, recipeId)
            .require(new SizedFluidIngredient(
                DataComponentFluidIngredient.of(false, itemSubstitute), itemSubstitute.getAmount()))
            .require(new SizedFluidIngredient(
                DataComponentFluidIngredient.of(false, fromFluid), fromFluid.getAmount()))
            .output(toFluid)
            .requiresHeat(HeatCondition.HEATED)
            .build();
        return new RecipeHolder<>(recipeId, recipe);
    }
}
