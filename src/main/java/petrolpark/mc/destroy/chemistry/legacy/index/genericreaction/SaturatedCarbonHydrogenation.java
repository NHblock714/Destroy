package petrolpark.mc.destroy.chemistry.legacy.index.genericreaction;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMolecularStructure;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction.ReactionBuilder;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.library.PetrolparkTags;

public class SaturatedCarbonHydrogenation extends ElectrophilicAddition {

    public SaturatedCarbonHydrogenation(boolean alkyne) {
        super(Destroy.MOD_ID, "hydrogenation", alkyne);
    };

    @Override
    public LegacyMolecularStructure getLowDegreeGroup() {
        return LegacyMolecularStructure.atom(LegacyElement.HYDROGEN);
    };

    @Override
    public LegacyMolecularStructure getHighDegreeGroup() {
        return LegacyMolecularStructure.atom(LegacyElement.HYDROGEN);
    };

    @Override
    public LegacySpecies getElectrophile() {
        return DestroyMolecules.HYDROGEN;
    };

    @Override
    public void transform(ReactionBuilder builder) {
        builder.addSimpleItemTagCatalyst(PetrolparkTags.commonItemTag("dusts/nickel"), 1f);
    };
    
};
