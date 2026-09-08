package petrolpark.mc.destroy.chemistry.legacy;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.chemistry.api.util.Constants;
import petrolpark.mc.destroy.chemistry.legacy.index.DestroyMolecules;
import petrolpark.mc.destroy.client.DestroyLang;

/**
 * A {@link LegacyMixture Mixture} which cannot react. Instantiating — or adding a {@link LegacySpecies}
 * to — a Read-Only Mixture skips out on the processing-intensive generation of {@link LegacyReaction
 * Reactions}, making them useful for GUI displays.
 *
 * <p>No direct 1.21 API migrations beyond package-root rename; catnip {@code NBTHelper} is available
 * in 1.21, and {@code CompoundTag/ListTag/Component/ChatFormatting} are unchanged.</p>
*/
public class ReadOnlyMixture {

    /**
     * A Decimal Formatter used for displaying the contents of Mixtures. Three
     * fraction digits so concentrations show meaningful precision (e.g.
     * {@code 0.300 M} instead of {@code 0.3 M}). The {@code .quantity(...)}
     * formatter in {@code DestroyLang} also reads {@code getMaximumFractionDigits()}
     * to pick the unit-prefix cutoff, so this keeps the M / mM / μM threshold at
     * 10⁻³ rather than 10⁻¹.
     */
    private static DecimalFormat df = new DecimalFormat();
    static {
        df.setMinimumFractionDigits(3);
        df.setMaximumFractionDigits(3);
    }

    /** The minimum value below which a Molecule is considered an impurity.*/
    public static final float IMPURITY_THRESHOLD = 0.1f;

    /** The display name of this Mixture.*/
    protected Component name;

    /** The full translation key of this Mixture if it has a custom name.*/
    protected String translationKey;

    /** The RGBA color of this Mixture.*/
    protected Integer color;

    /** How hot (in kelvins) this Mixture is. Temperature affects the rate of Reactions.*/
    protected float temperature;

    /** The Molecules contained by this Mixture, mapped to their concentrations (in moles per Bucket).*/
    protected Map<LegacySpecies, Float> contents;

    /** The Molecules in this Mixture, mapped to the proportion of which are gaseous.*/
    protected Map<LegacySpecies, Float> states;

    /** Whether any Molecules are currently boiling or condensing.*/
    protected boolean boiling;

    public ReadOnlyMixture() {
        this(298f);
    }

    public ReadOnlyMixture(float temperature) {
        translationKey = "";
        contents = new HashMap<>();
        if (temperature < 0f) throw new IllegalStateException("Mixtures cannot be below 0K");
        this.temperature = temperature;
        states = new HashMap<>();
        boiling = false;
    }

    /**
 * Converts this Mixture into a storeable CompoundTag that can be read back via
 * {@link #readNBT(Supplier, CompoundTag)}.
*/
    public CompoundTag writeNBT() {
        CompoundTag compound = new CompoundTag();
        if (translationKey != null && !translationKey.isEmpty()) {
            compound.putString("TranslationKey", translationKey);
        }
        compound.putFloat("Temperature", temperature);
        compound.put("Contents", NBTHelper.writeCompoundList(
            contents.entrySet().stream().filter(e -> e.getValue() > 0f).toList(),
            entry -> {
                CompoundTag moleculeTag = new CompoundTag();
                moleculeTag.putString("Molecule", entry.getKey().getFullID());
                moleculeTag.putFloat("Concentration", entry.getValue());
                float gaseous = states.get(entry.getKey());
                if (gaseous != 1f && gaseous != 0f) moleculeTag.putFloat("Gaseous", states.get(entry.getKey()));
                return moleculeTag;
            }));
        return compound;
    }

    /**
 * Generates a Read-Only Mixture from the given Compound Tag.
*/
    public static <T extends ReadOnlyMixture> T readNBT(Supplier<T> newMixture, CompoundTag compound) {
        T mixture = newMixture.get();
        if (compound == null) {
            Destroy.LOGGER.warn("Null Mixture read");
            return mixture;
        }
        mixture.translationKey = compound.getString("TranslationKey");
        if (compound.contains("Temperature")) mixture.temperature = compound.getFloat("Temperature");
        ListTag contents = compound.getList("Contents", 10);
        contents.forEach(tag -> {
            CompoundTag moleculeTag = (CompoundTag) tag;
            LegacySpecies molecule = LegacySpecies.getMolecule(moleculeTag.getString("Molecule"));
            mixture.addMolecule(molecule, moleculeTag.getFloat("Concentration"));
            float state = moleculeTag.getFloat("Gaseous");
            if (state != 0f && state != 1f) mixture.boiling = true;
            mixture.states.put(molecule, state);
        });
        mixture.updateName();
        mixture.updateColor();
        return mixture;
    }

    /** The display name of this Mixture. Lazily computed on first access.*/
    public Component getName() {
        if (name == null) updateName();
        return name;
    }

    /** The color of this Mixture. Lazily computed on first access.*/
    public int getColor() {
        if (color == null) updateColor();
        return color;
    }

    /** Sets the display name of this Mixture to avoid recomputation.*/
    public void setTranslationKey(String translationKey) {
        this.translationKey = translationKey;
    }

    /** How hot (in kelvins) this Mixture is.*/
    public float getTemperature() {
        return temperature;
    }

    /** Whether any Molecules are currently boiling or condensing.*/
    public boolean isBoiling() {
        return boiling;
    }

    /** Whether this Mixture has no Molecules in it.*/
    public boolean isEmpty() {
        return contents.isEmpty();
    }

    /** The concentration of the given Molecule in this Mixture.*/
    public float getConcentrationOf(LegacySpecies molecule) {
        if (contents.containsKey(molecule)) {
            return contents.get(molecule);
        } else {
            return 0f;
        }
    }

    /**
     * The gaseous fraction of the given Molecule (1 = entirely gas, 0 = entirely liquid),
     * defaulting to 1 for Molecules which are not being tracked yet. {@code VatControllerBlockEntity}
     * snapshots this for every Molecule immediately before its heat loop and re-reads it later in
     * the same tick, because it is {@code heat()} which boils and condenses species; any difference
     * forces the {@code setMixture} writeback that redistributes the contents between the liquid
     * and gas tanks.
     */
    public float getState(LegacySpecies molecule) {
        Float s = states.get(molecule);
        return s == null ? 1f : s;
    }

    /** Get the combined concentration of every Molecule in this Mixture (moles/bucket).*/
    public float getTotalConcentration() {
        float total = 0f;
        for (Float concentration : contents.values()) {
            total += concentration;
        }
        return total;
    }

    /**
 * Checks that this Mixture contains a suitable concentration of the given Molecule, and that all
 * other substances present are solvents or low-concentration impurities.
*/
    public boolean hasUsableMolecule(LegacySpecies molecule, float minConcentration, float maxConcentration,
                                     @Nullable Predicate<LegacySpecies> ignore) {
        return hasUsableMolecules(molecule::equals, minConcentration, maxConcentration, ignore);
    }

    /**
 * Checks that this Mixture contains a suitable concentration of Molecules passing the predicate.
*/
    public boolean hasUsableMolecules(Predicate<LegacySpecies> molecules, float minConcentration, float maxConcentration,
                                      @Nullable Predicate<LegacySpecies> ignore) {
        if (ignore == null) ignore = m -> false;
        float combinedConcentration = 0f;
        for (Entry<LegacySpecies, Float> entry : contents.entrySet()) {
            if (ignore.test(entry.getKey())) continue;
            if (molecules.test(entry.getKey())) {
                combinedConcentration += entry.getValue();
                continue;
            }
            if (entry.getKey().hasTag(DestroyMolecules.Tags.SOLVENT)) continue;
            if (entry.getValue() > IMPURITY_THRESHOLD) return false;
        }
        return (combinedConcentration < maxConcentration + 0.05f && combinedConcentration > minConcentration - 0.05f);
    }

    /**
 * Adds the given Molecule to this Read-Only Mixture with the given concentration.
 * Hypothetical Molecules are rejected with a warning.
*/
    public ReadOnlyMixture addMolecule(LegacySpecies molecule, float concentration) {

        if (molecule == null || concentration == 0f) {
            return this;
        }
        if (molecule.isHypothetical()) {
            Destroy.LOGGER.warn("Could not add hypothetical Molecule '" + molecule.getFullID() + "'' to a real Mixture.");
            return this;
        }

        contents.put(molecule, concentration);
        states.put(molecule, molecule.getBoilingPoint() < temperature ? 1f : 0f);

        return this;
    }

    /** Get all the Molecules present in this Mixture.*/
    public List<LegacySpecies> getContents(boolean excludeNovel) {
        return contents.keySet().stream().filter(molecule -> !molecule.isNovel() || !excludeNovel).toList();
    }

    /** Get the list of Molecules in this Mixture as a String (debugging).*/
    public String getContentsString() {
        if (contents.isEmpty()) return "";
        StringBuilder string = new StringBuilder();
        for (Entry<LegacySpecies, Float> entry : contents.entrySet()) {
            string.append(entry.getKey().getFullID()).append(" (").append(entry.getValue()).append("M), ");
        }
        return string.substring(0, string.length() - 2);
    }

    /**
 * The tooltip listing the contents of this Mixture.
*/
    public List<Component> getContentsTooltip(boolean iupac, boolean monospace, boolean useMoles, int amount,
                                              DecimalFormat concentrationFormatter) {
        int i = 0;
        List<Component> tooltip = new ArrayList<>();
        List<LegacySpecies> molecules = new ArrayList<>(contents.keySet());
        molecules.sort((m1, m2) -> contents.get(m2).compareTo(contents.get(m1)));

        int quantityLabelLength = DestroyLang.quantity(0f, useMoles, concentrationFormatter).string().length() + 2;
        for (LegacySpecies molecule : molecules) {
            float quantity = contents.get(molecule) * (useMoles ? amount / Constants.MILLIBUCKETS_PER_LITER : 1);
            tooltip.add(i, DestroyLang.builder()
                .space().space()
                .add(Component.literal(monospace
                    ? String.format("%1$" + quantityLabelLength + "s",
                        DestroyLang.quantity(quantity, useMoles, concentrationFormatter).string())
                    : DestroyLang.quantity(quantity, useMoles, concentrationFormatter).string()))
                .space()
                .add(molecule.getName(iupac).plainCopy())
                .add(Component.literal(
                    molecule.getCharge() == 0 ? "" : " [" + molecule.getSerializedCharge(false) + "]"))
                .style(ChatFormatting.GRAY)
                .component());
            i++;
        }
        return tooltip;
    }

    protected void updateColor() {
        color = 0x20FFFFFF;
    }

    /** Update the name of this Mixture to reflect what's in it.*/
    protected void updateName() {
        name = DestroyLang.translate("mixture.mixture").component();
    }
}
