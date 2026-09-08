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
import petrolpark.mc.destroy.chemistry.legacy.LegacySpeciesTag;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;

/**
 * Matches a Mixture fluid containing ANY molecule tagged with {@code moleculeTag} at total
 * concentration in [minConcentration, maxConcentration]. Tag format: {@code namespace:path}.
 *
 * <p>JSON is {@code "type": "destroy:mixture_with_molecule_tag"} plus {@code "molecule_tag"} and
 * either {@code "concentration"} (widened to a ±0.1 window, floored at 0) or an explicit
 * {@code "min_concentration"} / {@code "max_concentration"} pair.</p>
 */
public class MoleculeTagFluidIngredient extends MixtureFluidIngredient {

    public static final MapCodec<MoleculeTagFluidIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.STRING.fieldOf("molecule_tag").forGetter(i -> i.moleculeTagId),
        Codec.FLOAT.optionalFieldOf("min_concentration", -1f).forGetter(i -> i.minConcentration),
        Codec.FLOAT.optionalFieldOf("max_concentration", -1f).forGetter(i -> i.maxConcentration),
        Codec.FLOAT.optionalFieldOf("concentration", -1f).forGetter(i -> -1f)
    ).apply(instance, (tag, min, max, conc) -> {
        float effMin = min >= 0 ? min : (conc >= 0 ? Math.max(0f, conc - 0.1f) : 0f);
        float effMax = max >= 0 ? max : (conc >= 0 ? conc + 0.1f : Float.MAX_VALUE);
        return new MoleculeTagFluidIngredient(tag, effMin, effMax);
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, MoleculeTagFluidIngredient> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, i -> i.moleculeTagId,
            ByteBufCodecs.FLOAT, i -> i.minConcentration,
            ByteBufCodecs.FLOAT, i -> i.maxConcentration,
            MoleculeTagFluidIngredient::new
        );

    protected final String moleculeTagId;
    protected final float minConcentration;
    protected final float maxConcentration;

    public MoleculeTagFluidIngredient(String moleculeTagId, float minConcentration, float maxConcentration) {
        this.moleculeTagId = moleculeTagId;
        this.minConcentration = minConcentration;
        this.maxConcentration = maxConcentration;
    }

    /**
     * Resolves the singleton tag registered under {@link #moleculeTagId}.
     *
     * <p>{@link LegacySpeciesTag} doesn't override {@code equals/hashCode}, so map lookup is
     * identity-based: every {@code new LegacySpeciesTag(...)} is a different object, and
     * {@link LegacySpeciesTag#MOLECULES_WITH_TAGS}{@code .get(freshInstance)} returns null even
     * when a singleton with the same {@code ns:id} sits in the map. The actual singleton is only
     * reachable through {@code MOLECULE_TAGS.get("ns:id")}, which its constructor populates at
     * class-load. Constructing a fresh tag here rather than looking that one up would make both
     * {@link #testMixture} and {@link #getExampleMixtures} fail silently.</p>
     */
    private LegacySpeciesTag resolveTag() {
        return LegacySpeciesTag.MOLECULE_TAGS.get(moleculeTagId);
    }

    @Override
    protected boolean testMixture(LegacyMixture mixture) {
        LegacySpeciesTag tag = resolveTag();
        if (tag == null) return false;
        // Sum concentration of all molecules with this tag
        float total = 0f;
        for (LegacySpecies molecule : mixture.getContents(true)) {
            if (molecule.hasTag(tag)) total += mixture.getConcentrationOf(molecule);
        }
        return total >= minConcentration && total <= maxConcentration;
    }

    @Override
    public List<ReadOnlyMixture> getExampleMixtures() {
        LegacySpeciesTag tag = resolveTag();
        if (tag == null) return List.of();
        java.util.Set<LegacySpecies> tagged = LegacySpeciesTag.MOLECULES_WITH_TAGS.get(tag);
        if (tagged == null || tagged.isEmpty()) return List.of();

        float conc = (minConcentration + Math.min(maxConcentration, 1000f)) / 2f;
        float effConc = Math.max(conc, 0.01f);

        java.util.List<ReadOnlyMixture> examples = new java.util.ArrayList<>(tagged.size());
        for (LegacySpecies molecule : tagged) {
            LegacyMixture m = new LegacyMixture();
            m.addMolecule(molecule, effConc);
            examples.add(m);
        }
        return examples;
    }

    @Override
    public FluidIngredientType<?> getType() {
        return DestroyFluidIngredientTypes.MIXTURE_WITH_MOLECULE_TAG.get();
    }

    
    @Override
    public java.util.Collection<LegacySpecies> getReferencedMolecules() {
        LegacySpeciesTag tag = resolveTag();
        if (tag == null) return java.util.Collections.emptyList();
        java.util.Set<LegacySpecies> tagged = LegacySpeciesTag.MOLECULES_WITH_TAGS.get(tag);
        return tagged == null ? java.util.Collections.emptyList() : tagged;
    }

    
    @Override
    protected net.minecraft.nbt.CompoundTag ingredientInfoTag() {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putString("Subtype", "molecule_tag");
        t.putString("Id", moleculeTagId);
        t.putFloat("MinConcentration", minConcentration);
        t.putFloat("MaxConcentration", maxConcentration == Float.MAX_VALUE ? 1000f : maxConcentration);
        return t;
    }
}
