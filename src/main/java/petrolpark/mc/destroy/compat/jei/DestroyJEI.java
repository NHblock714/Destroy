package petrolpark.mc.destroy.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.petrolpark.compat.jei.category.builder.PetrolparkCategoryBuilder;
import com.simibubi.create.compat.jei.category.CreateRecipeCategory;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.compat.jei.category.AgingCategory;
import petrolpark.mc.destroy.compat.jei.category.ArcFurnaceCategory;
import petrolpark.mc.destroy.compat.jei.category.CartographyTableCategory;
import petrolpark.mc.destroy.compat.jei.category.CentrifugationCategory;
import petrolpark.mc.destroy.compat.jei.category.ChargingCategory;
import petrolpark.mc.destroy.compat.jei.category.DistillationCategory;
import petrolpark.mc.destroy.compat.jei.category.ElectrolysisCategory;
import petrolpark.mc.destroy.compat.jei.category.ElementTankFillingCategory;
import petrolpark.mc.destroy.compat.jei.category.ExtrusionCategory;
import petrolpark.mc.destroy.compat.jei.category.FlameRetardantApplicationCategory;
import petrolpark.mc.destroy.compat.jei.category.GlassblowingCategory;
import petrolpark.mc.destroy.compat.jei.category.GenericReactionCategory;
import petrolpark.mc.destroy.compat.jei.category.MixableExplosiveCategory;
import petrolpark.mc.destroy.compat.jei.category.MixtureConversionCategory;
import petrolpark.mc.destroy.compat.jei.category.MutationCategory;
import petrolpark.mc.destroy.compat.jei.category.ObliterationCategory;
import petrolpark.mc.destroy.compat.jei.category.ReactionCategory;
import petrolpark.mc.destroy.compat.jei.category.SievingCategory;
import petrolpark.mc.destroy.compat.jei.category.TappingCategory;
import petrolpark.mc.destroy.compat.jei.category.VatMaterialCategory;
import petrolpark.mc.destroy.content.processing.ageing.AgeingRecipe;
import petrolpark.mc.destroy.content.processing.centrifuge.CentrifugationRecipe;
import petrolpark.mc.destroy.content.processing.distillation.DistillationRecipe;
import petrolpark.mc.destroy.content.processing.dynamo.ChargingRecipe;
import petrolpark.mc.destroy.content.processing.extrusion.ExtrusionRecipe;
import petrolpark.mc.destroy.content.processing.glassblowing.GlassblowingRecipe;
import petrolpark.mc.destroy.content.processing.sieve.SievingRecipe;
import petrolpark.mc.destroy.content.processing.treetap.TappingRecipe;
import petrolpark.mc.destroy.core.explosion.ObliterationRecipe;
import petrolpark.mc.destroy.content.product.fireretardant.FlameRetardantApplicationRecipe;
import petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe;
import petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;

import com.simibubi.create.content.processing.basin.BasinRecipe;

/**
 * Destroy's JEI plugin. S268 introduced the minimal skeleton; S270 adds the first registered
 * category (MixtureConversionCategory from S269) + the category-registration pipeline.
 *
 * <p><b>Registration pattern</b> (follows Create 1.21 CreateJEI + Create-Library's
 * {@link PetrolparkCategoryBuilder}):</p>
 * <ol>
 * <li>{@link #loadCategories} builds up {@link #allCategories} via fluent builder (icon +
 * background + recipe-type source + factory reference).</li>
 * <li>{@link #registerCategories} calls loadCategories then adds all categories to JEI's
 * registry.</li>
 * <li>{@link #registerRecipes} iterates each category's registerRecipes.</li>
 * <li>{@link #registerRecipeCatalysts} iterates each category's registerCatalysts.</li>
 * </ol>
*/
@JeiPlugin
public class DestroyJEI implements IModPlugin {

    public static final ResourceLocation ID = Destroy.asResource("jei_plugin");

    /** Holds the JEI runtime once JEI finishes init · populated by {@link #onRuntimeAvailable}.*/
    public static Optional<IJeiRuntime> jeiRuntime = Optional.empty();

    /** RecipeHolders that {@link #refreshDatapackReactionsClientSide} has previously pushed into
     * JEI's runtime recipe manager. Tracked so we can {@code hideRecipes(...)} them before the
     * next refresh adds the new set — without this, repeated server-side {@code /reload} would
     * accumulate stale datapack reaction recipes in the JEI Reaction category in MP.*/
    private static final java.util.List<net.minecraft.world.item.crafting.RecipeHolder<petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe>>
        CLIENT_DATAPACK_REACTION_HOLDERS = new java.util.ArrayList<>();

    // ---- S364 Mixture infrastructure (currently empty stubs; future session populates) ----
    // category, walked the recipe class hierarchy + Mixture-aware Ingredient types to record
    // (a) which RecipeType's are Mixture-applicable, (b) which Molecules each recipe consumes
    // (input) or produces (output). 1.21 port currently leaves these empty —
    // ChemicalSpeciesRecipeManagerPlugin (S363) checks size + skips its Mixture-FluidStack
    // drill-down branch when empty (degrades gracefully to molecule-only lookup).
    // To re-enable Mixture drill-down: hook population into loadCategories() per-category
    // .put(type, recipeClassForMixtures)` pattern). Walking MoleculeFluidIngredient sub-types
    // requires 1.21 codec-aware Mixture introspection (defer till that infrastructure exists).

    /** RecipeType → recipe class mapping for recipes that accept Mixture fluid inputs.*/
    public static final java.util.Map<mezz.jei.api.recipe.RecipeType<?>, Class<? extends net.minecraft.world.item.crafting.Recipe<?>>>
        MIXTURE_APPLICABLE_RECIPE_TYPES = new java.util.HashMap<>();

    /** Molecule → recipes that consume it as an input (excludes Reactions, which are tracked
 * via {@link petrolpark.mc.destroy.compat.jei.category.ReactionCategory#RECIPES}).*/
    public static final java.util.Map<petrolpark.mc.destroy.chemistry.legacy.LegacySpecies,
        java.util.List<net.minecraft.world.item.crafting.Recipe<?>>> MOLECULES_INPUT = new java.util.HashMap<>();

    /** Molecule → recipes that produce it as an output (excludes Reactions).*/
    public static final java.util.Map<petrolpark.mc.destroy.chemistry.legacy.LegacySpecies,
        java.util.List<net.minecraft.world.item.crafting.Recipe<?>>> MOLECULES_OUTPUT = new java.util.HashMap<>();

    /** Set true once population has run; used by JEI lookups to lazy-init on first query.*/
    public static boolean MOLECULE_RECIPES_NEED_PROCESSING = true;

    private final List<CreateRecipeCategory<?>> allCategories = new ArrayList<>();

    public DestroyJEI() {
        // JEI requires a public no-arg ctor.
    }

    /**
 * Build all category instances. Called from {@link #registerCategories} (JEI init callback).
 * Each category entry follows the fluent builder pattern:
 * {@code builder(RecipeClass).addTypedRecipes(type).itemIcon(item).emptyBackground(w, h).build(name, Factory)}.
*/
    private void loadCategories() {
        allCategories.clear();

        // MixtureConversionCategory (S269 port): single-fluid → Mixture-fluid conversion display.
        // register Mechanical Mixer + Basin + Vat Controller as catalysts. Mixture conversion
        // recipes drive any chemistry-accepting block (the conversion is invoked at fill-time inside
        // VatTankWrapper.fill / ReactionInBasinRecipe.create). Without these catalysts, JEI U-key on a
        builder(MixtureConversionRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.MIXTURE_CONVERSION)
            .catalyst(com.simibubi.create.AllBlocks.MECHANICAL_MIXER::get)
            .catalyst(com.simibubi.create.AllBlocks.BASIN::get)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.VAT_CONTROLLER::get)
            .itemIcon(Items.WATER_BUCKET)
            .emptyBackground(125, 20)
            .build("mixture_conversion", MixtureConversionCategory::new);

        // CentrifugationCategory: single-fluid → 2-fluid separation with animated Centrifuge block.
        builder(CentrifugationRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.CENTRIFUGATION)
            .acceptsMixtures()  // fluid input + 2 fluid outputs may carry Mixture
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.CENTRIFUGE::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.CENTRIFUGE.get())
            .emptyBackground(150, 125)
            .build("centrifugation", CentrifugationCategory::new);

        // Programmatic JEI recipes from
        // PotionSeparationRecipes.createSeparationRecipes(level) — separates a mixed potion fluid
        // back into a base potion + the fluid-equivalent of the brewing ingredient. Level access
        // via Minecraft.getInstance().level (client-side only — JEI is a client plugin).
        // Empty list returned pre-world-load (level == null) so JEI category still registers; the
        // first JEI lookup after world join populates the map (cached).
        builder(CentrifugationRecipe.class)
            .addRecipes(() -> {
                net.minecraft.world.level.Level level = net.minecraft.client.Minecraft.getInstance().level;
                if (level == null) return java.util.List.of();
                return petrolpark.mc.destroy.content.processing.centrifuge.potion.PotionSeparationRecipes
                    .createSeparationRecipes(level).values();
            })
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.CENTRIFUGE::get)
            .doubleItemIcon(petrolpark.mc.destroy.DestroyBlocks.CENTRIFUGE.get(), Items.BREWING_STAND)
            .emptyBackground(120, 115)
            .build("potion_centrifugation", CentrifugationCategory::new);

        // GlassblowingCategory: fluid input → glassblown item with animated Blowpipe.
        builder(GlassblowingRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.GLASSBLOWING)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.BLOWPIPE::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.BLOWPIPE.get())
            .emptyBackground(150, 80)
            .build("glassblowing", GlassblowingCategory::new);

        // ChargingCategory: item input → charged item outputs with animated Dynamo (basin=false · no arc-furnace lid).
        builder(ChargingRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.CHARGING)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.DYNAMO::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.DYNAMO.get())
            .emptyBackground(177, 75)
            .build("charging", ChargingCategory::new);

        // ElectrolysisCategory: Basin-based electrolysis recipe display. Basin + Dynamo-above (basin=true, arcFurnace=false).
        // 1-arg PetrolparkRecipeCategory.Factory variant not used since ElectrolysisCategory/ArcFurnaceCategory extend BasinCategory
        // (CreateRecipeCategory 1-arg ctor); we use Factory<R>::create with a helpers-ignoring lambda wrapper.
        builder(BasinRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.ELECTROLYSIS)
            .acceptsMixtures()
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.DYNAMO::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.DYNAMO.get())
            .emptyBackground(177, 103)
            .build("electrolysis", (info, helpers) -> new ElectrolysisCategory(info));

        // ArcFurnaceCategory: Basin-based arc-furnace recipe display. Basin + Dynamo-above + arc-furnace-lid (basin=true, arcFurnace=true).
        // 恢复 ArcFurnaceIcon 作为 category tab icon (AnimatedDynamo 缩略 + DYNAMO item 覆盖) · 替代之前的 plain itemIcon.
        builder(BasinRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.ARC_FURNACE)
            .acceptsMixtures()
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.DYNAMO::get)
            .icon(new petrolpark.mc.destroy.compat.jei.animation.ArcFurnaceIcon(petrolpark.mc.destroy.DestroyBlocks.DYNAMO::asStack))
            .emptyBackground(177, 103)
            .build("arc_furnace", (info, helpers) -> new ArcFurnaceCategory(info));

        // ExtrusionCategory: item input → extruded item with EXTRUSION_DIE block in centre.
        // switched from .addTypedRecipes(EXTRUSION) (queries non-existent JSON) to
        // .addRecipes(() -> ExtrusionRecipe.RECIPES) (programmatic, populated from
        // BlockExtrusion.EXTRUSIONS via DestroyBlockExtrusions.register). Same fix as
        // §7.55 TappingRecipe — author's S88 stub-removal of ExtrusionRecipe.create + RECIPES
        // initializer broke JEI display. User report: "挤压模具的挤压配方丢了".
        builder(ExtrusionRecipe.class)
            .addRecipes(() -> petrolpark.mc.destroy.content.processing.extrusion.ExtrusionRecipe.RECIPES)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.EXTRUSION_DIE::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.EXTRUSION_DIE.get())
            .emptyBackground(177, 75)
            .build("extrusion", ExtrusionCategory::new);

        // TappingCategory: log-item input → fluid result with animated TreeTap.
        // Recipes come from TappingCategory.RECIPES (static-init populated from
        // BlockTapping.ALL_TAPPINGS via TappingRecipe.create factory). Initial S276 port had
        // .addTypedRecipes(TAPPING) expecting JSON tapping recipes that were never created —
        // result was an empty JEI category (bug "取液器配方丢失").
        builder(TappingRecipe.class)
            .addRecipes(() -> TappingCategory.RECIPES)
            .acceptsMixtures()  // fluid output may carry Mixture (e.g. tapped sap mixed with extracts)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.TREE_TAP::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.TREE_TAP.get())
            .emptyBackground(177, 75)
            .build("tapping", TappingCategory::new);

        // SievingCategory: single-item input → multi-output sifting with animated MECHANICAL_SIEVE.
        builder(SievingRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.SIEVING)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.MECHANICAL_SIEVE::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.MECHANICAL_SIEVE.get())
            .emptyBackground(150, 100)
            .build("sieving", SievingCategory::new);

        // FlameRetardantApplicationCategory: spout-applies fire-retardant fluid to items.
        // name reverted from "flame_retardant_application" → "fireproofing" to match the
        // The lang files (en_us / zh_cn /
        // ja_jp / etc.) all carry the *fireproofing* key — using flame_retardant_application here
        // would generate the unmapped key `destroy.recipe.flame_retardant_application` which JEI
        // shows literally as the category title.
        builder(FlameRetardantApplicationRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.FLAME_RETARDANT_APPLICATION)
            .catalyst(com.simibubi.create.AllBlocks.SPOUT::get)
            .itemIcon(com.simibubi.create.AllBlocks.SPOUT.get())
            .emptyBackground(177, 85)
            .build("fireproofing", FlameRetardantApplicationCategory::new);

        // CartographyTableCategory: vanilla + Destroy cartography-table operations.
        // Synthetic recipes generated statically via CartographyTableCategory.getAllRecipes.
        builder(CartographyTableCategory.CartographyTableRecipe.class)
            .addRecipes(CartographyTableCategory::getAllRecipes)
            .catalyst(() -> Items.CARTOGRAPHY_TABLE)
            .itemIcon(Items.CARTOGRAPHY_TABLE)
            .emptyBackground(125, 20)
            .build("cartography_table", CartographyTableCategory::new);

        // AgingCategory: item + fluid inputs aging in Aging Barrel → fluid output + duration text.
        builder(AgeingRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.AGING)
            .acceptsMixtures()  // fluid input/output may carry Mixture
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.AGING_BARREL::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.AGING_BARREL.get())
            .emptyBackground(177, 85)
            .build("aging", AgingCategory::new);

        // DistillationCategory: distillation-tower fractionation display.
        // HeatConditionRenderer integration deferred (unported Refrigerant + removed BLAZE_BURNER_FUEL_SPECIAL tag).
        builder(DistillationRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.DISTILLATION)
            .acceptsMixtures()  // distillation tower fractionates a Mixture into N fluid outputs
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.BUBBLE_CAP::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.BUBBLE_CAP.get())
            .emptyBackground(125, 130)
            .build("distillation", DistillationCategory::new);

        // ObliterationCategory: display-only block-loot-replacement on explosive-mix detonation.
        builder(ObliterationRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.OBLITERATION)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.CUSTOM_EXPLOSIVE_MIX::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.get())
            .emptyBackground(177, 85)
            .build("obliteration", ObliterationCategory::new);

        // MixableExplosiveCategory: T3b · item + registered ExplosiveProperties chart.
        builder(MixableExplosiveCategory.MixableExplosiveRecipe.class)
            .addRecipes(MixableExplosiveCategory::getAllRecipes)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.CUSTOM_EXPLOSIVE_MIX::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.CUSTOM_EXPLOSIVE_MIX.get())
            .emptyBackground(150, 130)
            .build("mixable_explosive", MixableExplosiveCategory::new);

        // Recipes populated statically via ReactionCategory.RECIPES map (LegacyReaction-keyed).
        // Each ReactionRecipe's id comes from ReactionRecipe.create which counts up at class-load;
        // we wrap each in a RecipeHolder via its built-in id.
        // register reaction catalysts (Mechanical Mixer + Basin + Vat Controller).
        // had a {@code reactionCatalysts()} helper called on this category; the 1.21 port omitted
        // it, so JEI U-key on a vat / mixer / basin item only listed the Vat structure (vat_material)
        // category and never linked back to the chemistry reactions catalog.
        // 385-387 of DestroyJEI: any block that can drive chemistry should reverse-link here.
        builder(ReactionRecipe.class)
            .addRecipes(() -> {
                // Skip datapack-sourced reactions here. They take a separate path through
                // {@link #refreshDatapackReactionsClientSide} which pushes them into JEI's
                // runtime via {@code addRecipes} and tracks the resulting holders for hide-on-
                // refresh. Registering them through this static supplier as well duplicates
                // every datapack reaction: this supplier re-runs on any JEI reload
                // ({@code /jei reload}, resource-pack switch, datapack hot reload), and by then
                // {@link ReactionCategory#RECIPES} has been populated with datapack entries
                // too — so each shows up once via static register + once via dynamic push.
                java.util.List<net.minecraft.world.item.crafting.RecipeHolder<ReactionRecipe>> list =
                    new java.util.ArrayList<>();
                int[] counter = { 0 };
                ReactionCategory.RECIPES.values().forEach(r -> {
                    if (r.getReaction() != null && r.getReaction().isDatapack()) return;
                    list.add(new net.minecraft.world.item.crafting.RecipeHolder<>(
                        petrolpark.mc.destroy.Destroy.asResource("reaction_" + counter[0]++),
                        r));
                });
                return list;
            })
            .catalyst(com.simibubi.create.AllBlocks.MECHANICAL_MIXER::get)
            .catalyst(com.simibubi.create.AllBlocks.BASIN::get)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.VAT_CONTROLLER::get)
            .itemIcon(petrolpark.mc.destroy.DestroyItems.ABS.get())
            .emptyBackground(177, 103)
            .build("reaction", ReactionCategory::new);

        // GenericReactionCategory: functional-group-pattern reactions (primary amine + acid → amide etc.)
        // same reasoning as ReactionCategory: register all 3 reaction-driver catalysts.
        builder(ReactionRecipe.GenericReactionRecipe.class)
            .addRecipes(() -> {
                java.util.List<net.minecraft.world.item.crafting.RecipeHolder<ReactionRecipe.GenericReactionRecipe>> list =
                    new java.util.ArrayList<>();
                int[] counter = { 0 };
                GenericReactionCategory.RECIPES.values().forEach(r -> {
                    if (r != null) list.add(new net.minecraft.world.item.crafting.RecipeHolder<>(
                        petrolpark.mc.destroy.Destroy.asResource("generic_reaction_" + counter[0]++),
                        r));
                });
                return list;
            })
            .catalyst(com.simibubi.create.AllBlocks.MECHANICAL_MIXER::get)
            .catalyst(com.simibubi.create.AllBlocks.BASIN::get)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.VAT_CONTROLLER::get)
            .itemIcon(petrolpark.mc.destroy.DestroyItems.ABS.get())
            .emptyBackground(177, 103)
            .build("generic_reaction", GenericReactionCategory::new);

        // MutationCategory: synthetic JEI-display recipes per CropMutation.
        // MutationCategory.RECIPES is List<PhytominingRecipe>; wrap each in a RecipeHolder using
        builder(petrolpark.mc.destroy.content.processing.phytomining.PhytominingRecipe.class)
            .addRecipes(() -> {
                java.util.List<net.minecraft.world.item.crafting.RecipeHolder<petrolpark.mc.destroy.content.processing.phytomining.PhytominingRecipe>> list =
                    new java.util.ArrayList<>();
                int[] counter = { 0 };
                MutationCategory.RECIPES.forEach(r -> list.add(new net.minecraft.world.item.crafting.RecipeHolder<>(
                    petrolpark.mc.destroy.Destroy.asResource("mutation_display_" + counter[0]++),
                    r)));
                return list;
            })
            .catalyst(petrolpark.mc.destroy.DestroyItems.HYPERACCUMULATING_FERTILIZER::get)
            .itemIcon(petrolpark.mc.destroy.DestroyItems.HYPERACCUMULATING_FERTILIZER.get())
            .emptyBackground(120, 125)
            .build("mutation", MutationCategory::new);

        // VatMaterialCategory: one recipe per registered VatMaterial · pressure / conductivity /
        // transparent displayed. getAllRecipes returns pre-wrapped RecipeHolders (S284 pattern).
        // emptyBackground 125 → 177 px wide. {@link VatMaterialCategory#draw} renders
        // {@code PetrolparkGuiTexture.JEI_LINE} which is 177 px wide; previous 125 px caused the
        // dashed separator to overflow ~52 px out of the frame. we
        // restore that. Other categories (e.g. ReactionCategory at 177×103) already had matching
        // width so their separators rendered correctly.
        builder(VatMaterialCategory.VatMaterialRecipe.class)
            .addRecipes(VatMaterialCategory::getAllRecipes)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.VAT_CONTROLLER::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.VAT_CONTROLLER.get())
            .emptyBackground(177, 85)
            .build("vat_material", VatMaterialCategory::new);

        // ElementTankFillingCategory: fluid → element-sample block via ELEMENT_TANK.
        // Standard JSON-driven · addTypedRecipes(ELEMENT_TANK_FILLING).
        builder(petrolpark.mc.destroy.content.product.periodictable.ElementTankFillingRecipe.class)
            .addTypedRecipes(DestroyRecipeTypes.ELEMENT_TANK_FILLING)
            .catalyst(petrolpark.mc.destroy.DestroyBlocks.ELEMENT_TANK::get)
            .itemIcon(petrolpark.mc.destroy.DestroyBlocks.ELEMENT_TANK.get())
            .emptyBackground(125, 50)
            .build("element_tank_filling", ElementTankFillingCategory::new);
    }

    /** Currently:
*/
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        // raw type for the IGhostIngredientHandler matches Create 1.21's
        // GhostIngredientHandler registration pattern (CreateJEI.java:405-410). JEI's API
        // {@code addGhostIngredientHandler(Class<S>, IGhostIngredientHandler<S>)} cannot infer
        // {@code S = RedstoneProgrammerScreen} from
        // {@code DestroyGhostIngredientHandler<T extends GhostItemMenu<?>> implements
        // IGhostIngredientHandler<AbstractSimiContainerScreen<T>>} due to Java generics
        // limitations on transitive bounds. Raw type cast at registration is the standard
        registration.addGhostIngredientHandler(
            petrolpark.mc.destroy.content.redstone.programmer.RedstoneProgrammerScreen.class,
            new DestroyGhostIngredientHandler());
    }

    @Override
    public void registerIngredients(IModIngredientRegistration registration) {
        // register MoleculeJEIIngredient type · JEI recognizes LegacySpecies as ingredients
        // so ReactionCategory's addIngredient(MoleculeJEIIngredient.TYPE, species) works.
        registration.register(
            MoleculeJEIIngredient.TYPE,
            LegacySpecies.MOLECULES.values(),
            MoleculeJEIIngredient.HELPER,
            MoleculeJEIIngredient.RENDERER);
    }

    /** Builder bridge · captures the allCategories collector. S367: returns
 * {@link DestroyCategoryBuilder} subclass so categories can call {@code .acceptsMixtures()}
 * to register themselves into {@link #MIXTURE_APPLICABLE_RECIPE_TYPES}.*/
    private <T extends Recipe<? extends RecipeInput>> DestroyCategoryBuilder<T> builder(Class<T> recipeClass) {
        return new DestroyCategoryBuilder<>(recipeClass, allCategories::add);
    }

    @Override
    public ResourceLocation getPluginUid() {
        return ID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        PetrolparkCategoryBuilder.helpers = registration.getJeiHelpers();
        loadCategories();
        registration.addRecipeCategories(allCategories.toArray(IRecipeCategory[]::new));

        // Petrolpark library 1.4.31 ships PetrolparkJEI.registerAdvanced(...) which
        // registers the petrolpark:item_contaminants recipe MANAGER PLUGIN, but its
        // PetrolparkJEI.registerCategories(...) is empty (verified via javap on
        // petrolpark-1.21.1-1.4.31.jar). Result: clicking a contaminated item in JEI ("show
        // recipes") crashes with "There is no recipe category registered for: RecipeType[uid=
        // petrolpark:item_contaminants]". Library expects downstream mods to register the
        // ContaminantInfoCategory themselves. Destroy is the canonical consumer (everything
        // contamination-related is for Destroy's chemistry mixtures), so we register here.
        registration.addRecipeCategories(new com.petrolpark.compat.jei.category.ContaminantInfoCategory<>(
            registration.getJeiHelpers().getGuiHelper(),
            mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
            com.petrolpark.compat.jei.category.ContaminantInfoCategory.ITEM_RECIPE_TYPE));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        allCategories.forEach(c -> c.registerRecipes(registration));
        // register dynamic DiscStampingRecipe entries into Create's "Deploying" JEI
        // category.
        // registerRecipes), but in Create 1.21 DeployingCategory inherits registerRecipes from
        // CreateRecipeCategory without overriding it — there's no concrete method to mixin into,
        // so the mixin approach fails with "could not find any targets matching 'registerRecipes'"
        // (verified via runtime crash). Direct JEI registration here works regardless of class
        // hierarchy.
        destroy$registerDiscStampingRecipes(registration);
    }

    /**
 *
 * <p>Music-disc detection: 1.21 vanilla removed {@code ItemTags.MUSIC_DISCS}, so we iterate
 * {@link net.minecraft.core.registries.BuiltInRegistries#ITEM} and filter on items whose
 * default ItemStack carries a {@link net.minecraft.core.component.DataComponents#JUKEBOX_PLAYABLE}
 * component (every vanilla + modded music disc has one). Same pattern as
 * {@link petrolpark.mc.destroy.compat.jei.category.ElectrolysisCategory#registerRecipes}.</p>
 *
 * <p>For each disc:</p>
 * <ol>
 * <li>Build a stamped {@link petrolpark.mc.destroy.content.processing.discstamping.DiscStamperItem}
 * carrying the disc via {@code DiscStamperItem.of(discStack)}.</li>
 * <li>Call {@link petrolpark.mc.destroy.content.processing.discstamping.DiscStampingRecipe#create}
 * to construct the runtime DeployerApplicationRecipe (input: blank disc + stamper,
 * output: the original disc, tool not consumed).</li>
 * <li>Wrap in a {@link net.minecraft.world.item.crafting.RecipeHolder} with a synthetic
 * per-disc id and {@code registration.addRecipes(...)} into the Deploying RecipeType.</li>
 * </ol>
*/
    private void destroy$registerDiscStampingRecipes(IRecipeRegistration registration) {
        net.minecraft.world.item.Item blankDisc = petrolpark.mc.destroy.DestroyItems.BLANK_MUSIC_DISC.get();
        java.util.List<net.minecraft.world.item.crafting.RecipeHolder<com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe>> stampingRecipes = new java.util.ArrayList<>();

        for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
            if (item == blankDisc) continue;
            net.minecraft.world.item.ItemStack discStack = item.getDefaultInstance();
            if (!discStack.has(net.minecraft.core.component.DataComponents.JUKEBOX_PLAYABLE)) continue;
            net.minecraft.world.item.ItemStack stamper =
                petrolpark.mc.destroy.content.processing.discstamping.DiscStamperItem.of(discStack);
            com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe recipe =
                petrolpark.mc.destroy.content.processing.discstamping.DiscStampingRecipe.create(stamper);
            if (recipe == null) continue;
            ResourceLocation id = Destroy.asResource(
                "disc_stamping_" + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath());
            stampingRecipes.add(new net.minecraft.world.item.crafting.RecipeHolder<>(id, recipe));
        }

        if (!stampingRecipes.isEmpty()) {
            // JEI RecipeType for Create's Deploying. AllRecipeTypes.DEPLOYING.getType()
            // returns the *vanilla* {@code net.minecraft.world.item.crafting.RecipeType<R>};
            // {@link mezz.jei.api.recipe.RecipeType#createFromVanilla} wraps it into the JEI-side
            // {@code mezz.jei.api.recipe.RecipeType<RecipeHolder<R>>} that {@code addRecipes}
            // expects. RecipeType identity is by ResourceLocation; this preserves Create's
            // {@code create:deploying} id so JEI merges with Create's existing category entries.
            mezz.jei.api.recipe.RecipeType<net.minecraft.world.item.crafting.RecipeHolder<com.simibubi.create.content.kinetics.deployer.DeployerApplicationRecipe>> deployingType =
                mezz.jei.api.recipe.RecipeType.createFromVanilla(
                    com.simibubi.create.AllRecipeTypes.DEPLOYING.getType());
            registration.addRecipes(deployingType, stampingRecipes);
        }
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        allCategories.forEach(c -> c.registerCatalysts(registration));
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = Optional.of(runtime);
    }

    /**
     * Called from {@link petrolpark.mc.destroy.core.chemistry.data.SyncReactionsS2CPacket#handle}
     * after the client has applied a fresh batch of datapack reactions to
     * {@link petrolpark.mc.destroy.chemistry.legacy.LegacyReaction#REACTIONS}. Rebuilds the
     * datapack entries in {@link petrolpark.mc.destroy.compat.jei.category.ReactionCategory#RECIPES}
     * and pushes them into JEI's live recipe manager so the Reaction category page picks them up
     * without a {@code /jei reload}.
     */
    public static void refreshDatapackReactionsClientSide() {
        // Rebuild ReactionCategory.RECIPES — drop old datapack entries, add the current set.
        java.util.Iterator<java.util.Map.Entry<
                petrolpark.mc.destroy.chemistry.legacy.LegacyReaction,
                petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe>> it =
            petrolpark.mc.destroy.compat.jei.category.ReactionCategory.RECIPES.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getKey().isDatapack()) it.remove();
        }
        for (petrolpark.mc.destroy.chemistry.legacy.LegacyReaction reaction
                : petrolpark.mc.destroy.chemistry.legacy.LegacyReaction.REACTIONS.values()) {
            if (reaction.isDatapack() && reaction.includeInJei()) {
                petrolpark.mc.destroy.compat.jei.category.ReactionCategory.RECIPES.put(
                    reaction,
                    petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe.create(reaction));
            }
        }

        // Push to JEI runtime if available (JEI may not have finished init yet on first join).
        jeiRuntime.ifPresent(runtime -> {
            mezz.jei.api.recipe.IRecipeManager rm = runtime.getRecipeManager();
            // ReactionCategory.TYPE is declared with a wildcard upper bound; JEI's
            // addRecipes / hideRecipes need a concrete RecipeType<T>. Cast through raw type.
            @SuppressWarnings({"unchecked", "rawtypes"})
            mezz.jei.api.recipe.RecipeType<net.minecraft.world.item.crafting.RecipeHolder<petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe>>
                reactionType = (mezz.jei.api.recipe.RecipeType)
                    petrolpark.mc.destroy.compat.jei.category.ReactionCategory.TYPE;
            // Hide previously-pushed datapack recipe holders so they don't persist as duplicates.
            if (!CLIENT_DATAPACK_REACTION_HOLDERS.isEmpty()) {
                rm.hideRecipes(reactionType, CLIENT_DATAPACK_REACTION_HOLDERS);
                CLIENT_DATAPACK_REACTION_HOLDERS.clear();
            }
            // Wrap each current datapack reaction in a RecipeHolder + push it.
            int[] counter = { 0 };
            java.util.List<net.minecraft.world.item.crafting.RecipeHolder<petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe>>
                fresh = new java.util.ArrayList<>();
            for (petrolpark.mc.destroy.chemistry.legacy.LegacyReaction reaction
                    : petrolpark.mc.destroy.chemistry.legacy.LegacyReaction.REACTIONS.values()) {
                if (!reaction.isDatapack() || !reaction.includeInJei()) continue;
                petrolpark.mc.destroy.core.chemistry.recipe.ReactionRecipe recipe =
                    petrolpark.mc.destroy.compat.jei.category.ReactionCategory.RECIPES.get(reaction);
                if (recipe == null) continue;
                fresh.add(new net.minecraft.world.item.crafting.RecipeHolder<>(
                    Destroy.asResource("datapack_reaction_" + counter[0]++), recipe));
            }
            if (!fresh.isEmpty()) {
                rm.addRecipes(reactionType, fresh);
                CLIENT_DATAPACK_REACTION_HOLDERS.addAll(fresh);
            }
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        jeiRuntime = Optional.empty();
    }

    /** 1.21 JEI hook is {@code registerAdvanced(IAdvancedRegistration)}
 * (was {@code registerRecipeManagerPlugins(IRecipeManagerPluginRegistration)} in older JEI
 * APIs). Method {@code addRecipeManagerPlugin(IRecipeManagerPlugin)} preserved.
*/
    @Override
    public void registerAdvanced(mezz.jei.api.registration.IAdvancedRegistration registration) {
        registration.addRecipeManagerPlugin(
            new petrolpark.mc.destroy.compat.jei.recipemanager.FireproofingRecipeManagerPlugin());
        registration.addRecipeManagerPlugin(
            new petrolpark.mc.destroy.compat.jei.recipemanager.ItemReverseReactionRecipeManagerPlugin());
        registration.addRecipeManagerPlugin(
            new petrolpark.mc.destroy.compat.jei.recipemanager.ChemicalSpeciesRecipeManagerPlugin(
                registration.getJeiHelpers()));
    }
}
