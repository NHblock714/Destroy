package petrolpark.mc.destroy.core.recipe.ingredient.fluid;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;

import petrolpark.mc.destroy.DestroyFluidIngredientTypes;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;

/**
 * Matches a Mixture fluid containing {@code molecule} at concentration in [minConcentration,
 * maxConcentration].
 *
 * <p>1.21 JSON: {@code {"type": "destroy:mixture_with_molecule", "molecule": "destroy:X",
 * "concentration_min": 1.0, "concentration_max": 99.0}} (wrapped in SizedFluidIngredient with
 * {@code "amount"} at outer level).</p>
*/
public class MoleculeFluidIngredient extends MixtureFluidIngredient {

    public static final MapCodec<MoleculeFluidIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.STRING.fieldOf("molecule").forGetter(i -> i.moleculeId),
        // and "concentration_min" aliases). Default 0 if none present.
        Codec.FLOAT.optionalFieldOf("min_concentration", -1f).forGetter(i -> i.minConcentration),
        Codec.FLOAT.optionalFieldOf("max_concentration", -1f).forGetter(i -> i.maxConcentration),
        Codec.FLOAT.optionalFieldOf("concentration", -1f).forGetter(i -> -1f)
    ).apply(instance, (mol, min, max, conc) -> {
        float effMin = min >= 0 ? min : (conc >= 0 ? Math.max(0f, conc - 0.1f) : 0f);
        float effMax = max >= 0 ? max : (conc >= 0 ? conc + 0.1f : Float.MAX_VALUE);
        return new MoleculeFluidIngredient(mol, effMin, effMax);
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoleculeFluidIngredient> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, i -> i.moleculeId,
            ByteBufCodecs.FLOAT, i -> i.minConcentration,
            ByteBufCodecs.FLOAT, i -> i.maxConcentration,
            MoleculeFluidIngredient::new
        );

    protected final String moleculeId;
    protected final float minConcentration;
    protected final float maxConcentration;

    public MoleculeFluidIngredient(String moleculeId, float minConcentration, float maxConcentration) {
        this.moleculeId = moleculeId;
        this.minConcentration = minConcentration;
        this.maxConcentration = maxConcentration;
    }

    @Override
    protected boolean testMixture(LegacyMixture mixture) {
        LegacySpecies molecule = LegacySpecies.getMolecule(moleculeId);
        if (molecule == null) return false;
        float conc = mixture.getConcentrationOf(molecule);
        return conc >= minConcentration && conc <= maxConcentration;
    }

    @Override
    public List<ReadOnlyMixture> getExampleMixtures() {
        LegacySpecies molecule = LegacySpecies.getMolecule(moleculeId);
        if (molecule == null) return List.of();
        LegacyMixture m = new LegacyMixture();
        float conc = Math.max((minConcentration + Math.min(maxConcentration, 1000f)) / 2f, 0.01f);
        m.addMolecule(molecule, conc);
        // Fill the remaining volume with water so the example mixture has a realistic concentration
        // and heat capacity rather than the lone solute.
        float water = DestroyMolecules.WATER.getPureConcentration() * (1f - (conc / molecule.getPureConcentration()));
        if (water > 0f) m.addMolecule(DestroyMolecules.WATER, water);
        return List.of(m);
    }

    
    @Override
    public java.util.Collection<LegacySpecies> getReferencedMolecules() {
        LegacySpecies molecule = LegacySpecies.getMolecule(moleculeId);
        return molecule == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(molecule);
    }

    @Override
    public FluidIngredientType<?> getType() {
        return DestroyFluidIngredientTypes.MIXTURE_WITH_MOLECULE.get();
    }

    
    @Override
    protected net.minecraft.nbt.CompoundTag ingredientInfoTag() {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putString("Subtype", "molecule");
        t.putString("Id", moleculeId);
        t.putFloat("MinConcentration", minConcentration);
        t.putFloat("MaxConcentration", maxConcentration == Float.MAX_VALUE ? 1000f : maxConcentration);
        return t;
    }
}
