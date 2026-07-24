package petrolpark.mc.destroy.mixin.plugin;

import petrolpark.mc.library.mixin.plugin.PetrolparkMixinPlugin;

public class DestroyMixinPlugin extends PetrolparkMixinPlugin {
    
    @Override
    protected String getMixinPackage() {
        return "petrolpark.mc.destroy.mixin";
    };

    @Override
    public void onLoad(String mixinPackage) {
        // Gate JEI-touching mixins by mod presence. The parent's auto-gating in
        // PetrolparkMixinPlugin#shouldApplyMixin only fires when split[1] of the mixin class
        // name equals "compat"; under our package petrolpark.mc.destroy.mixin.compat.jei.*
        // split[1] is "mc" so auto-gating never activates and the mixins try to attach even
        // when JEI is absent — references to mezz.jei.api.recipe.RecipeType then throw
        // ClassNotFoundException at mixin pre-process time and the whole game refuses to boot.
        //
        // requireMultipleMods stores a key built as "<getMixinPackage()>.compat.<mods[0]>.<arg1>"
        // so passing the bare class name plus the modid produces the full mixin class name as
        // the key. Mods listed in the vararg are ALL required for the mixin to apply.
        requireMultipleMods("CreateRecipeCategoryMixin", "jei", "create");
        requireMultipleMods("BasinCategoryMixin",        "jei", "create");
        requireMultipleMods("PackingCategoryMixin",      "jei", "create");
        requireMultipleMods("MixingCategoryMixin",       "jei", "create");
        requireMultipleMods("JeiProcessingRecipeMixin",  "jei");
        // FD-gated mixin — drops cut-onion advancement award through CuttingBoardBlockEntity.
        requireMultipleMods("CuttingBoardBlockEntityMixin", "farmersdelight");
    };
};
