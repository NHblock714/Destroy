package petrolpark.mc.destroy.content.processing.centrifuge.potion;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import petrolpark.mc.library.compat.create.core.data.recipe.AdvancedProcessingRecipe;
import com.simibubi.create.content.fluids.potion.PotionFluid;
import com.simibubi.create.content.fluids.potion.PotionFluid.BottleType;
import com.simibubi.create.content.fluids.potion.PotionFluidHandler;
import com.simibubi.create.foundation.mixin.accessor.PotionBrewingAccessor;

import net.createmod.catnip.data.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyPotions;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugationRecipe;

/**
 * Programmatic JEI-display "recipes" that visualize potion-separation as {@link CentrifugationRecipe}
 * entries in JEI (takes a mixed potion fluid + separates into 2 component fluids).
*/
public class PotionSeparationRecipes {

    /**
 * Map of (Potion holder, BottleType) → RecipeHolder<CentrifugationRecipe>. Populated lazily
 * on first {@link #createSeparationRecipes(Level)} call (1.21 PotionBrewing is per-level).
 * Key type uses {@code Holder<Potion>} to match 1.21 vanilla API.
*/
    private static Map<Pair<Holder<Potion>, BottleType>, RecipeHolder<CentrifugationRecipe>> ALL;
    private static boolean alreadyGenerated = false;

    /** Public API preserved for existing doc references; populated by first {@link #createSeparationRecipes}.*/
    public static Map<Pair<Holder<Potion>, BottleType>, RecipeHolder<CentrifugationRecipe>> ALL() {
        return ALL == null ? new HashMap<>() : ALL;
    }

    /**
 * Build (or return cached) separation-recipe map. Safe to call repeatedly — lazy-init gated.
 * Same caching pattern as {@link PotionFluidMixingRecipes#createRecipes}.
*/
    public static Map<Pair<Holder<Potion>, BottleType>, RecipeHolder<CentrifugationRecipe>> createSeparationRecipes(Level level) {
        if (!alreadyGenerated) {
            ALL = createSeparationRecipesImpl(level);
            alreadyGenerated = true;
        }
        return ALL;
    }

    private static Map<Pair<Holder<Potion>, BottleType>, RecipeHolder<CentrifugationRecipe>> createSeparationRecipesImpl(Level level) {
        PotionBrewing potionBrewing = level.potionBrewing();
        PotionBrewingAccessor accessor = (PotionBrewingAccessor) potionBrewing;

        Map<Pair<Holder<Potion>, BottleType>, RecipeHolder<CentrifugationRecipe>> recipes = new HashMap<>();
        int recipeIndex = 0;

        List<Reference<Potion>> allPotions = level.registryAccess()
            .lookupOrThrow(Registries.POTION)
            .listElements()
            .toList();

        // ---- Loop 1 — highest priority: lingering/splash CONTAINER_MIXES × FLUID_EQUIVALENTS
        // × registered potions. For each container transformation + each ingredient that
        // has a fluid-equivalent + each potion, emit a separation recipe keyed on the
        // destination bottle type.
        for (PotionBrewing.Mix<Item> mix : accessor.create$getContainerMixes()) {
            Item from = mix.from().value();
            if (!PotionFluidMixingRecipes.VANILLA_CONTAINERS.contains(from)) continue;

            Item to = mix.to().value();
            BottleType fromBottleType = PotionFluidHandler.bottleTypeFromItem(from);
            BottleType toBottleType = PotionFluidHandler.bottleTypeFromItem(to);

            for (Entry<Item, FluidStack> entry : PotionFluidMixingRecipes.FLUID_EQUIVALENTS.entrySet()) {
                if (mix.ingredient().test(new ItemStack(entry.getKey()))) {
                    for (Reference<Potion> potion : allPotions) {
                        // Potions.EMPTY dropped from 1.21 vanilla Potions class API · registry iteration
                        // via level.registryAccess() naturally excludes the unregistered empty-potion
                        // sentinel so no explicit skip needed.
                        Pair<Holder<Potion>, BottleType> key = Pair.of(potion, toBottleType);

                        FluidStack fromFluid = PotionFluidHandler.getFluidFromPotion(
                            new PotionContents(potion), fromBottleType, 1000);
                        FluidStack toFluid = PotionFluidHandler.getFluidFromPotion(
                            new PotionContents(potion), toBottleType, 1000);

                        recipes.putIfAbsent(key, createRecipe(
                            "potion_separation_" + recipeIndex++, toFluid, entry.getValue(), fromFluid));
                    }
                }
            }
        }

        // ---- Loop 2 — medium priority: POTION_MIXES × FLUID_EQUIVALENTS × supported containers.
        // Separates the "to" potion back into the original "from" potion + the fluid-equivalent
        // of the ingredient. Skips if already covered by loop 1.
        for (Item container : PotionFluidMixingRecipes.VANILLA_CONTAINERS) {
            BottleType bottleType = PotionFluidHandler.bottleTypeFromItem(container);
            for (PotionBrewing.Mix<Potion> mix : accessor.create$getPotionMixes()) {
                for (Entry<Item, FluidStack> entry : PotionFluidMixingRecipes.FLUID_EQUIVALENTS.entrySet()) {
                    Holder<Potion> toPotion = mix.to();
                    Pair<Holder<Potion>, BottleType> key = Pair.of(toPotion, bottleType);
                    if (mix.ingredient().test(new ItemStack(entry.getKey())) && !recipes.containsKey(key)) {
                        FluidStack fromFluid = PotionFluidHandler.getFluidFromPotion(
                            new PotionContents(mix.from()), bottleType, 1000);
                        FluidStack toFluid = PotionFluidHandler.getFluidFromPotion(
                            new PotionContents(toPotion), bottleType, 1000);

                        // Skip if the "from" fluid collapses to water.
                        if (!fromFluid.getFluid().isSame(Fluids.WATER)) {
                            recipes.putIfAbsent(key, createRecipe(
                                "potion_separation_" + recipeIndex++, toFluid, entry.getValue(), fromFluid));
                        }
                    }
                }
            }
        }

        // ---- Loop 3 — lowest priority: split multi-effect potions into 2 UNIQUE half-effect
        // potions. Uses DestroyPotions.UNIQUE as the output Potion and attaches the split
        // effects directly via PotionContents' customEffects field.
        for (Reference<Potion> potion : allPotions) {
            // Potions.EMPTY dropped from 1.21 vanilla Potions class API · registry iteration
                        // via level.registryAccess() naturally excludes the unregistered empty-potion
                        // sentinel so no explicit skip needed.
            List<MobEffectInstance> effects = potion.value().getEffects();
            if (effects.size() > 1) {
                List<MobEffectInstance> firstEffects = new ArrayList<>();
                List<MobEffectInstance> secondEffects = new ArrayList<>();
                int i = 0;
                for (MobEffectInstance effect : effects) {
                    (i < effects.size() / 2 ? firstEffects : secondEffects).add(effect);
                    i++;
                }
                for (Item item : PotionFluidMixingRecipes.VANILLA_CONTAINERS) {
                    BottleType bottleType = PotionFluidHandler.bottleTypeFromItem(item);
                    Pair<Holder<Potion>, BottleType> key = Pair.of(potion, bottleType);
                    if (recipes.containsKey(key)) continue;

                    // Build UNIQUE potion FluidStacks carrying the split effect list directly via
                    // PotionContents customEffects.
                    PotionContents lightContents = new PotionContents(
                        Optional.of(DestroyPotions.UNIQUE), Optional.empty(), firstEffects);
                    PotionContents denseContents = new PotionContents(
                        Optional.of(DestroyPotions.UNIQUE), Optional.empty(), secondEffects);

                    FluidStack lightOutput = PotionFluid.of(1000, lightContents, bottleType);
                    FluidStack denseOutput = PotionFluid.of(1000, denseContents, bottleType);
                    FluidStack inputFluid = PotionFluidHandler.getFluidFromPotion(
                        new PotionContents(potion), bottleType, 1000);

                    recipes.put(key, createRecipe(
                        "potion_separation_" + recipeIndex++, inputFluid, lightOutput, denseOutput));
                }
            }
        }

        return recipes;
    }

    private static RecipeHolder<CentrifugationRecipe> createRecipe(String name, FluidStack from,
                                                                   FluidStack light, FluidStack dense) {
        ResourceLocation recipeId = Destroy.asResource(name);
        CentrifugationRecipe recipe = new AdvancedProcessingRecipe.Builder<CentrifugationRecipe>(
                CentrifugationRecipe::new, recipeId)
            .require(new net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient(
                net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient.of(false, from),
                from.getAmount()))
            .duration(200)
            .output(light)
            .output(dense)
            .build();
        return new RecipeHolder<>(recipeId, recipe);
    }
}
