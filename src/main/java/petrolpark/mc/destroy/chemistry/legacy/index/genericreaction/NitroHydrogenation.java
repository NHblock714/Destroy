package petrolpark.mc.destroy.chemistry.legacy.index.genericreaction;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.legacy.LegacyElement;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMolecularStructure;
import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.GenericReactant;
import petrolpark.mc.destroy.chemistry.legacy.genericreaction.SingleGroupGenericReaction;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyGroupTypes;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.chemistry.legacy.index.group.NitroGroup;
import petrolpark.mc.library.PetrolparkTags;

public class NitroHydrogenation extends SingleGroupGenericReaction<NitroGroup> {

    public NitroHydrogenation() {
        super(Destroy.asResource("nitro_hydrogenation"), DestroyGroupTypes.NITRO);
    };

    @Override
    public boolean isPossibleIn(ReadOnlyMixture mixture) {
        return mixture.getConcentrationOf(DestroyMolecules.HYDROGEN) > 0f;
    };

    @Override
    public LegacyReaction generateReaction(GenericReactant<NitroGroup> reactant) {
        NitroGroup group = reactant.getGroup();
        LegacyMolecularStructure structure = reactant.molecule.shallowCopyStructure();
        structure.moveTo(group.nitrogen)
            .remove(group.firstOxygen)
            .remove(group.secondOxygen)
            .addAtom(LegacyElement.HYDROGEN)
            .addAtom(LegacyElement.HYDROGEN);
        return reactionBuilder()
            .addReactant(reactant.getMolecule())
            .addReactant(DestroyMolecules.HYDROGEN, 3)
            .addProduct(DestroyMolecules.WATER, 2)
            .addProduct(moleculeBuilder().structure(structure).build())
            .addSimpleItemTagCatalyst(PetrolparkTags.commonItemTag("dusts/palladium"), 1.0f)
            .build();
    };
    
};
