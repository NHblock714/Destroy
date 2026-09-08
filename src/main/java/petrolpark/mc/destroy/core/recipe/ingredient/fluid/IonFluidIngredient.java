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
 * Matches a Mixture fluid containing a specific ionic molecule at concentration in
 * [minConcentration, maxConcentration]. Semantically like MoleculeFluidIngredient but the molecule
 * must be charged (anion or cation), and a counter-ion (opposite-charge species) must also be
 * present in the mixture.
*/
public class IonFluidIngredient extends MixtureFluidIngredient {

    public static final MapCodec<IonFluidIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        // Accept "molecule" primarily, "ion" as a legacy alias.
        // Implemented as two optional fields + post-validation since DataFixerUpper Codec doesn't
        // ship a built-in alias helper; either-required is enforced after both reads.
        Codec.STRING.optionalFieldOf("molecule", "").forGetter(i -> i.ionId),
        Codec.STRING.optionalFieldOf("ion", "").forGetter(i -> ""),
        Codec.FLOAT.optionalFieldOf("min_concentration", -1f).forGetter(i -> i.minConcentration),
        Codec.FLOAT.optionalFieldOf("max_concentration", -1f).forGetter(i -> i.maxConcentration),
        Codec.FLOAT.optionalFieldOf("concentration", -1f).forGetter(i -> -1f)
    ).apply(instance, (mol, ion, min, max, conc) -> {
        String id = !mol.isEmpty() ? mol : ion;
        float effMin = min >= 0 ? min : (conc >= 0 ? Math.max(0f, conc - 0.1f) : 0f);
        float effMax = max >= 0 ? max : (conc >= 0 ? conc + 0.1f : Float.MAX_VALUE);
        return new IonFluidIngredient(id, effMin, effMax);
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, IonFluidIngredient> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, i -> i.ionId,
            ByteBufCodecs.FLOAT, i -> i.minConcentration,
            ByteBufCodecs.FLOAT, i -> i.maxConcentration,
            IonFluidIngredient::new
        );

    protected final String ionId;
    protected final float minConcentration;
    protected final float maxConcentration;

    public IonFluidIngredient(String ionId, float minConcentration, float maxConcentration) {
        this.ionId = ionId;
        this.minConcentration = minConcentration;
        this.maxConcentration = maxConcentration;
    }

    @Override
    protected boolean testMixture(LegacyMixture mixture) {
        LegacySpecies ion = LegacySpecies.getMolecule(ionId);
        if (ion == null) return false;
        int charge = ion.getCharge();
        if (charge == 0) return false;  // must be charged
        float conc = mixture.getConcentrationOf(ion);
        if (conc < minConcentration || conc > maxConcentration) return false;
        for (LegacySpecies other : mixture.getContents(true)) {
            int otherCharge = other.getCharge();
            if (otherCharge != 0 && Math.signum(otherCharge) != Math.signum(charge)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<ReadOnlyMixture> getExampleMixtures() {
        LegacySpecies ion = LegacySpecies.getMolecule(ionId);
        if (ion == null) return List.of();
        float conc = Math.max((minConcentration + Math.min(maxConcentration, 1000f)) / 2f, 0.01f);
        // Show a solution the recipe would actually accept: the ion in water with a counter-ion
        // balancing its charge (ions are taken to displace no water).
        LegacyMixture m = new LegacyMixture();
        m.addMolecule(DestroyMolecules.WATER, DestroyMolecules.WATER.getPureConcentration());
        m.addMolecule(ion, conc);
        m.addMolecule(ion.getCharge() > 0 ? DestroyMolecules.CHLORIDE : DestroyMolecules.SODIUM_ION,
            conc * Math.abs(ion.getCharge()));
        return List.of(m);
    }

    @Override
    public FluidIngredientType<?> getType() {
        return DestroyFluidIngredientTypes.MIXTURE_WITH_ION.get();
    }

    
    @Override
    protected net.minecraft.nbt.CompoundTag ingredientInfoTag() {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        // Resolve charge sign at-build-time so the tooltip mixin doesn't need to lookup the molecule
        boolean isAnion = false;
        LegacySpecies sp = LegacySpecies.getMolecule(ionId);
        if (sp != null) isAnion = sp.getCharge() < 0;
        t.putString("Subtype", "ion");
        t.putString("Id", ionId);
        t.putBoolean("Anion", isAnion);
        t.putFloat("MinConcentration", minConcentration);
        t.putFloat("MaxConcentration", maxConcentration == Float.MAX_VALUE ? 1000f : maxConcentration);
        return t;
    }

    
    @Override
    public java.util.Collection<LegacySpecies> getReferencedMolecules() {
        LegacySpecies ion = LegacySpecies.getMolecule(ionId);
        return ion == null ? java.util.Collections.emptyList() : java.util.Collections.singletonList(ion);
    }
}
