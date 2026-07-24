package petrolpark.mc.destroy.mixin.compat.jei;

import java.util.ArrayList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.compat.jei.DestroyJEI;
import petrolpark.mc.destroy.core.recipe.ingredient.fluid.MixtureFluidIngredient;

/**
 * Mixin into Create's {@link ProcessingRecipe} constructor: at {@code <init>} → RETURN, walk the
 * recipe's fluid ingredients + fluid results and populate
 * {@link DestroyJEI#MOLECULES_INPUT MOLECULES_INPUT} +
 * {@link DestroyJEI#MOLECULES_OUTPUT MOLECULES_OUTPUT} maps so JEI can drill down from
 * "I'm looking up molecule X" → "show recipe categories whose Mixture inputs/outputs reference X".
*/
@Mixin(ProcessingRecipe.class)
public abstract class JeiProcessingRecipeMixin {

    // fix: both fields are NonNullList, NOT List. Mixin @Shadow
    // requires EXACT bytecode-level type match — `List<SizedFluidIngredient>` declaration
    // failed runtime validation with "@Shadow field fluidIngredients was not located in the
    // target class" because NonNullList ≠ List at the descriptor level. Verified via
    // ProcessingRecipe.java in Create 6.0.10: lines 38-39 declare both as NonNullList.
    @Shadow protected NonNullList<SizedFluidIngredient> fluidIngredients;
    @Shadow protected NonNullList<FluidStack> fluidResults;

    @Inject(method = "<init>", at = @At("RETURN"))
    public void destroy$captureMixtureMolecules(CallbackInfo ci) {
        // JEI optional-dependency guard. mods.toml doesn't declare JEI, but this mixin
        // injects into Create's {@code ProcessingRecipe.<init>} which loads with or without JEI.
        // Without this guard, the next line's {@code DestroyJEI.MOLECULE_RECIPES_NEED_PROCESSING}
        // GETSTATIC triggers DestroyJEI class load → DestroyJEI implements {@code mezz.jei.api.IModPlugin}
        // → JEI absent → NoClassDefFoundError → every ProcessingRecipe instantiation crashes.
        // {@code Mods.JEI.isLoading()} short-circuits BEFORE the DestroyJEI reference; JVM lazy
        // class resolution means DestroyJEI never loads on JEI-less clients.
        if (!petrolpark.mc.library.compat.Mods.JEI.isLoading()) return;
        if (!DestroyJEI.MOLECULE_RECIPES_NEED_PROCESSING) return;
        // Cast safely: ProcessingRecipe extends Recipe<? extends RecipeInput> so this is always a Recipe<?>.
        Recipe<?> self = (Recipe<?>)(Object)this;

        // Inputs — walk SizedFluidIngredient.ingredient() looking for MixtureFluidIngredient subtypes.
        if (fluidIngredients != null) {
            for (SizedFluidIngredient sized : fluidIngredients) {
                if (sized == null) continue;
                if (sized.ingredient() instanceof MixtureFluidIngredient mfi) {
                    for (LegacySpecies molecule : mfi.getReferencedMolecules()) {
                        DestroyJEI.MOLECULES_INPUT
                            .computeIfAbsent(molecule, k -> new ArrayList<>())
                            .add(self);
                    }
                }
            }
        }

        // Outputs — walk FluidStack list, parse the destroy:mixture DataComponent into a Mixture
        // and record each contained molecule.
        if (fluidResults != null) {
            for (FluidStack result : fluidResults) {
                if (result == null || result.isEmpty()) continue;
                if (!DestroyFluids.isMixture(result)) continue;
                CompoundTag mixtureTag = result.get(DestroyDataComponents.MIXTURE);
                if (mixtureTag == null || mixtureTag.isEmpty()) continue;
                LegacyMixture mixture = LegacyMixture.readNBT(mixtureTag);
                if (mixture == null) continue;
                for (LegacySpecies molecule : mixture.getContents(true)) {
                    DestroyJEI.MOLECULES_OUTPUT
                        .computeIfAbsent(molecule, k -> new ArrayList<>())
                        .add(self);
                }
            }
        }
    }
}
