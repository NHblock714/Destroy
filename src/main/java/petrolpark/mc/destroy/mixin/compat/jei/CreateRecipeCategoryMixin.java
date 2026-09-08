package petrolpark.mc.destroy.mixin.compat.jei;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import com.simibubi.create.compat.jei.category.CreateRecipeCategory;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory.Info;
import com.simibubi.create.content.fluids.transfer.EmptyingRecipe;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.mixer.CompactingRecipe;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.item.TooltipHelper;
import net.createmod.catnip.lang.FontHelper.Palette;

import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.chemistry.legacy.ClientMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpeciesTag;
import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.compat.jei.DestroyJEI;
import petrolpark.mc.destroy.config.DestroyAllConfigs;

/**
 * Teaches JEI's Create recipe categories about Mixtures:
 *
 * <ol>
 * <li>Records which Create categories can hold Mixtures, and the Recipe class each one displays,
 * in {@link DestroyJEI#MIXTURE_APPLICABLE_RECIPE_TYPES}.</li>
 * <li>Rewrites the Fluid tooltip of Mixture FluidStacks:
 * <ul>
 * <li>Output slots list the contents of the actual Mixture.</li>
 * <li>Input and Catalyst slots list the requirements of the ingredient instead (for example "the
 * combined concentration of these Molecules must be between 0.1M and 0.3M"). The spec data is
 * attached to each example FluidStack as a {@link DestroyDataComponents#MIXTURE_INGREDIENT_INFO}
 * component by the ingredient subtype's {@code ingredientInfoTag()} — see
 * {@link petrolpark.mc.destroy.core.recipe.ingredient.fluid.MixtureFluidIngredient#generateStacks}.</li>
 * </ul>
 * </li>
 * </ol>
 */
@Mixin(value = CreateRecipeCategory.class, remap = false)
public abstract class CreateRecipeCategoryMixin<T extends Recipe<?>> {

    @Unique
    private static final DecimalFormat destroy$df = new DecimalFormat();
    static {
        destroy$df.setMinimumFractionDigits(3);
        destroy$df.setMaximumFractionDigits(3);
    }

    /** Separate from destroy$df (3 decimals for actual content "0.200M").
*/
    @Unique
    private static final DecimalFormat destroy$specDf = new DecimalFormat();
    static {
        destroy$specDf.setMinimumFractionDigits(1);
        destroy$specDf.setMaximumFractionDigits(1);
    }

    @Unique
    private static final Map<String, Class<? extends Recipe<?>>> destroy$CATEGORIES_AND_CLASSES = new HashMap<>();
    static {
        destroy$CATEGORIES_AND_CLASSES.put("mixing", MixingRecipe.class);
        destroy$CATEGORIES_AND_CLASSES.put("packing", CompactingRecipe.class);
        destroy$CATEGORIES_AND_CLASSES.put("spout_filling", FillingRecipe.class);
        destroy$CATEGORIES_AND_CLASSES.put("draining", EmptyingRecipe.class);
        destroy$CATEGORIES_AND_CLASSES.put("sequenced_assembly", SequencedAssemblyRecipe.class);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    public void destroy$inInit(Info<T> info, CallbackInfo ci) {
        String recipeTypeId = info.recipeType().getUid().getPath();
        if (destroy$CATEGORIES_AND_CLASSES.containsKey(recipeTypeId)) {
            DestroyJEI.MIXTURE_APPLICABLE_RECIPE_TYPES.put(
                info.recipeType(), destroy$CATEGORIES_AND_CLASSES.get(recipeTypeId));
        }
    }

    @Inject(
        method = "addPotionTooltip(Lmezz/jei/api/gui/ingredient/IRecipeSlotView;Ljava/util/List;)V",
        at = @At(value = "INVOKE_ASSIGN", target = "Ljava/util/Optional;get()Ljava/lang/Object;"),
        remap = false,
        locals = LocalCapture.CAPTURE_FAILHARD)
    private static void destroy$inAddPotionTooltip(IRecipeSlotView view, List<Component> tooltip,
                                                   CallbackInfo ci, Optional<FluidStack> displayed) {
        FluidStack stack = displayed.get();
        if (!DestroyFluids.isMixture(stack)) return;

        boolean iupac = DestroyAllConfigs.CLIENT.chemistry.iupacNames.get();
        boolean isOutput = view.getRole() == RecipeIngredientRole.OUTPUT;

        // Determine the title (1st line). For output: actual mixture's translated name. For
        // input/spec: the generic "Mixture" name.
        Component title = DestroyLang.translate("mixture.mixture").component();
        List<Component> body = new ArrayList<>();

        if (isOutput) {
            CompoundTag mixtureTag = stack.get(DestroyDataComponents.MIXTURE);
            if (mixtureTag != null && !mixtureTag.isEmpty()) {
                ClientMixture mixture = ClientMixture.readNBT(ClientMixture::new, mixtureTag);
                title = mixture.getName();
                body.addAll(mixture.getContentsTooltip(iupac, false, false, stack.getAmount(), destroy$df));
            } else {
                body.add(DestroyLang.translate("mixture.empty").component());
            }
        } else {
            // INPUT / CATALYST — render spec from MIXTURE_INGREDIENT_INFO component.
            CompoundTag spec = stack.get(DestroyDataComponents.MIXTURE_INGREDIENT_INFO);
            if (spec != null && !spec.isEmpty()) {
                body.addAll(destroy$buildSpecLines(spec));
            } else {
                // No spec attached — fall back to the actual mixture content.
                CompoundTag mixtureTag = stack.get(DestroyDataComponents.MIXTURE);
                if (mixtureTag != null && !mixtureTag.isEmpty()) {
                    ClientMixture mixture = ClientMixture.readNBT(ClientMixture::new, mixtureTag);
                    body.addAll(mixture.getContentsTooltip(iupac, false, false, stack.getAmount(), destroy$df));
                }
            }
        }

        // Overwrite the title in place and insert the body after it, rather than clearing the
        // list and rebuilding it: JEI appends the amount line and the mod footer once this
        // callback has returned, so a rebuilt tooltip carries the amount twice. Their styling is
        // left to whichever pipeline drew them.
        if (tooltip.isEmpty()) {
            tooltip.add(0, title);
        } else {
            tooltip.set(0, title);
        }
        tooltip.addAll(1, body);
    }

    
    @Unique
    private static List<Component> destroy$buildSpecLines(CompoundTag spec) {
        String subtype = spec.getString("Subtype");
        float min = spec.getFloat("MinConcentration");
        float max = spec.getFloat("MaxConcentration");
        Palette palette = Palette.GRAY_AND_WHITE;
        List<Component> lines = new ArrayList<>();

        switch (subtype) {
            case "molecule": {
                LegacySpecies sp = LegacySpecies.getMolecule(spec.getString("Id"));
                Component molName = sp == null
                    ? DestroyLang.translate("tooltip.unknown_molecule").component()
                    : sp.getName(DestroyAllConfigs.CLIENT.chemistry.iupacNames.get());
                lines.addAll(TooltipHelper.cutTextComponent(
                    DestroyLang.translate("tooltip.mixture_ingredient.molecule",
                        molName, destroy$specDf.format(min), destroy$specDf.format(max)).component(),
                    palette));
                break;
            }
            case "molecule_tag": {
                LegacySpeciesTag tag = LegacySpeciesTag.MOLECULE_TAGS.get(spec.getString("Id"));
                lines.addAll(TooltipHelper.cutStringTextComponent(
                    DestroyLang.translate("tooltip.mixture_ingredient.molecule_tag_1").string(), palette));
                if (tag != null) lines.add(tag.getFormattedName());
                lines.addAll(TooltipHelper.cutTextComponent(
                    DestroyLang.translate("tooltip.mixture_ingredient.molecule_tag_2",
                        destroy$specDf.format(min), destroy$specDf.format(max)).component(),
                    palette.primary(), palette.highlight()));
                break;
            }
            case "refrigerants": {
                // RefrigerantDummyFluidIngredient hint slot.
                // RefrigerantDummyFluidIngredient.Type#getDescription: 1) molecule_tag_1 preamble,
                // 2) tag formatted name, 3) the "refrigerants" specific tip ("use high concentration").
                LegacySpeciesTag tag = LegacySpeciesTag.MOLECULE_TAGS.get(spec.getString("Id"));
                lines.addAll(TooltipHelper.cutStringTextComponent(
                    DestroyLang.translate("tooltip.mixture_ingredient.molecule_tag_1").string(), palette));
                if (tag != null) lines.add(tag.getFormattedName());
                lines.addAll(TooltipHelper.cutStringTextComponent(
                    DestroyLang.translate("tooltip.mixture_ingredient.refrigerants").string(), palette));
                break;
            }
            case "ion": {
                LegacySpecies sp = LegacySpecies.getMolecule(spec.getString("Id"));
                Component molName = sp == null
                    ? DestroyLang.translate("tooltip.unknown_molecule").component()
                    : sp.getName(DestroyAllConfigs.CLIENT.chemistry.iupacNames.get());
                boolean anion = spec.getBoolean("Anion");
                lines.addAll(TooltipHelper.cutTextComponent(
                    DestroyLang.translate("tooltip.mixture_ingredient." + (anion ? "anion" : "cation"),
                        molName, destroy$specDf.format(min), destroy$specDf.format(max)).component(),
                    palette));
                break;
            }
            case "pure": {
                LegacySpecies sp = LegacySpecies.getMolecule(spec.getString("Id"));
                Component molName = sp == null
                    ? DestroyLang.translate("tooltip.unknown_molecule").component()
                    : sp.getName(DestroyAllConfigs.CLIENT.chemistry.iupacNames.get());
                lines.addAll(TooltipHelper.cutTextComponent(
                    DestroyLang.translate("tooltip.mixture_ingredient.pure", molName).component(),
                    palette));
                break;
            }
            default:
                lines.add(Component.literal("Unknown subtype: " + subtype).withStyle(ChatFormatting.RED));
        }
        return lines;
    }
}
