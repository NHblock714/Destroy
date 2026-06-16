package petrolpark.mc.destroy.core.chemistry;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpecies;
import petrolpark.mc.destroy.chemistry.legacy.LegacySpeciesTag;
import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.client.DestroyLang.TemperatureUnit;
import petrolpark.mc.destroy.config.DestroyAllConfigs;
import petrolpark.mc.destroy.core.item.tooltip.DestroyTooltipComponent;

/**
 * Informational Item that carries a single {@link LegacySpecies molecule} reference in its
 * {@link DestroyDataComponents#MOLECULE} DataComponent. Shown in JEI as an expandable entry
 * (2D structure thumbnail + text lore); created + populated by {@link VatScreen} when the
 * player clicks a mixture species name to open its detail panel.
*/
public class MoleculeDisplayItem extends Item {

    private static final DecimalFormat df = new DecimalFormat();

    static {
        df.setMinimumFractionDigits(1);
        df.setMaximumFractionDigits(1);
    }

    public MoleculeDisplayItem(Properties properties) {
        super(properties);
    }

    /**
 * Factory — build a MOLECULE_DISPLAY stack whose {@link DestroyDataComponents#MOLECULE}
 * component carries the given species' fullID.
*/
    public static ItemStack with(LegacySpecies molecule) {
        ItemStack stack = new ItemStack(DestroyItems.MOLECULE_DISPLAY.get(), 1);
        stack.set(DestroyDataComponents.MOLECULE, molecule.getFullID());
        return stack;
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        LegacySpecies molecule = getMolecule(stack);
        if (molecule == null) return Optional.empty();
        return Optional.of(new MoleculeTooltip(molecule));
    }

    private static LegacySpecies getMolecule(ItemStack itemStack) {
        if (!DestroyItems.MOLECULE_DISPLAY.isIn(itemStack)) return null;
        String id = itemStack.get(DestroyDataComponents.MOLECULE);
        if (id == null) return null;
        return LegacySpecies.getMolecule(id);
    }

    /**
 * Build the lore lines for a given molecule — used by JEI and by {@link
 * petrolpark.mc.destroy.core.chemistry.vat.VatScreen VatScreen} when the player hovers
 * a species entry in the mixture list.
*/
    public static List<Component> getLore(LegacySpecies molecule) {
        List<Component> tooltip = new ArrayList<>();
        if (molecule == null) return tooltip;

        boolean novel = molecule.isNovel();
        boolean charged = molecule.getCharge() != 0;
        boolean hypothetical = molecule.isHypothetical();
        boolean nerdMode = DestroyAllConfigs.CLIENT.chemistry.nerdMode.get();

        TemperatureUnit unit = mapConfigUnit(DestroyAllConfigs.CLIENT.chemistry.temperatureUnit.get());

        if (hypothetical) tooltip.add(DestroyLang.translate("tooltip.molecule.r_group").style(ChatFormatting.DARK_GRAY).component());
        if (nerdMode && !novel) tooltip.add(DestroyLang.translate("tooltip.molecule.formula",
            molecule.getSerlializedMolecularFormula(true) + DestroyLang.toSuperscript(molecule.getSerializedCharge(false)))
            .component().withStyle(ChatFormatting.GRAY));
        if (charged && (!nerdMode || novel)) tooltip.add(DestroyLang.translate("tooltip.molecule.charge",
            molecule.getSerializedCharge(true)).component().withStyle(ChatFormatting.GRAY));
        if (!hypothetical) tooltip.add(DestroyLang.translate("tooltip.molecule.mass",
            df.format(molecule.getMass())).component().withStyle(ChatFormatting.GRAY));
        if (!charged && !hypothetical) {
            tooltip.add(DestroyLang.translate("tooltip.molecule.boiling_point",
                unit.of(molecule.getBoilingPoint(), df)).component().withStyle(ChatFormatting.GRAY));
            tooltip.add(DestroyLang.translate("tooltip.molecule.density",
                df.format(molecule.getDensity())).component().withStyle(ChatFormatting.GRAY));
            if (nerdMode) {
                tooltip.add(DestroyLang.translate("tooltip.molecule.heat_capacity",
                    df.format(molecule.getMolarHeatCapacity())).component().withStyle(ChatFormatting.GRAY));
                tooltip.add(DestroyLang.translate("tooltip.molecule.latent_heat",
                    df.format(molecule.getLatentHeat() / 1000f)).component().withStyle(ChatFormatting.GRAY));
            }
        }

        Set<LegacySpeciesTag> tags = molecule.getTags();
        if (!tags.isEmpty()) tooltip.add(Component.literal(" "));
        for (LegacySpeciesTag tag : tags) {
            tooltip.add(tag.getFormattedName());
        }

        return tooltip;
    }

    
    private static TemperatureUnit mapConfigUnit(petrolpark.mc.destroy.config.DestroyClientChemistryConfigs.TemperatureUnit c) {
        return switch (c) {
            case KELVIN -> TemperatureUnit.KELVINS;
            case DEGREES_CELCIUS -> TemperatureUnit.DEGREES_CELCIUS;
            case DEGREES_FAHRENHEIT -> TemperatureUnit.DEGREES_FARENHEIT;
        };
    }

    public static class MoleculeTooltip extends DestroyTooltipComponent<MoleculeTooltip, ClientMoleculeTooltipComponent> {

        private final LegacySpecies molecule;

        public MoleculeTooltip(LegacySpecies molecule) {
            this.molecule = molecule;
        }

        public LegacySpecies getMolecule() {
            return this.molecule;
        }

        @Override
        public ClientMoleculeTooltipComponent getClientTooltipComponent() {
            return new ClientMoleculeTooltipComponent(this);
        }
    }

    public static class ClientMoleculeTooltipComponent implements ClientTooltipComponent {

        private final MoleculeRenderer renderer;

        private final int height;
        private final int width;

        public ClientMoleculeTooltipComponent(MoleculeTooltip tooltipComponent) {
            renderer = tooltipComponent.getMolecule().getRenderer();
            height = renderer.getHeight() + 15;
            width = renderer.getWidth() + 15;
        }

        @Override
        public int getHeight() {
            return height;
        }

        @Override
        public int getWidth(Font pFont) {
            return width;
        }

        @Override
        public void renderImage(Font font, int mouseX, int mouseY, GuiGraphics graphics) {
            PoseStack poseStack = graphics.pose();
            poseStack.pushPose();
            poseStack.translate(0, 0, 250);
            renderer.render(mouseX + 10, mouseY + 5, graphics);
            poseStack.popPose();
        }
    }
}
