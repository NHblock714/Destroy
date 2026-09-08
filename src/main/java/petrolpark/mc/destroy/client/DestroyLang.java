package petrolpark.mc.destroy.client;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.lang.FontHelper.Palette;
import net.createmod.catnip.lang.Lang;
import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.lang.LangNumberFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import petrolpark.mc.destroy.Destroy;

/**
 * {@code Component} and tooltip helpers: goggle tooltips, number and temperature formatting, and
 * the chemistry-specific tooltips (Vat material stats, pre-exponential factors, quantities).
 */
public class DestroyLang {

    public static final Palette WHITE_AND_WHITE = Palette.ofColors(ChatFormatting.WHITE, ChatFormatting.WHITE);
    public static final Palette GRAYS = Palette.ofColors(ChatFormatting.DARK_GRAY, ChatFormatting.GRAY);

    private static final DecimalFormat df = new DecimalFormat();
    static {
        df.setMaximumFractionDigits(2);
        df.setMinimumFractionDigits(2);
    }

    private static final String[] subscriptNumbers = {"\u2080", "\u2081", "\u2082", "\u2083", "\u2084", "\u2085", "\u2086", "\u2087", "\u2088", "\u2089"};
    private static final String[] superscriptNumbers = {"\u2070", "\u00b9", "\u00b2", "\u00b3", "\u2074", "\u2075", "\u2076", "\u2077", "\u2078", "\u2079"};

    public static String pascal(String string) {
        String s = Lang.asId(string);
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public static LangBuilder builder() {
        return new LangBuilder(Destroy.MOD_ID);
    }

    public static LangBuilder translate(String langKey, Object... args) {
        return builder().translate(langKey, args);
    }

    public static MutableComponent translateDirect(String langKey, Object... args) {
        Object[] resolved = LangBuilder.resolveBuilders(args);
        return Component.translatable(Destroy.MOD_ID + "." + langKey, resolved);
    }

    public static List<Component> translatedOptions(String prefix, String... keys) {
        List<Component> result = new ArrayList<>(keys.length);
        for (String key : keys)
            result.add(translate((prefix != null ? prefix + "." : "") + key).component());
        return result;
    }

    public static LangBuilder number(double d) {
        return builder().text(LangNumberFormat.format(d));
    }

    public static LangBuilder fluidName(FluidStack stack) {
        return builder().add(stack.getHoverName().copy());
    }

    public static LangBuilder direction(Direction direction) {
        return translate("generic.direction." + Lang.asId(direction.name()));
    }

    /**
 * Shortens a String to fit within {@code maxWidth} pixels of the given font, replacing the
 * tail with an ellipsis.
*/
    public static String shorten(String string, Font font, int maxWidth) {
        if (font.width(string) <= maxWidth) return string;
        if (string.isBlank()) return "";
        String elipses = "...";
        int elipsesWidth = font.width(elipses);
        while (font.width(string) > maxWidth - elipsesWidth || string.charAt(string.length() - 1) == ' ') {
            string = string.substring(0, string.length() - 1);
            if (string.isBlank()) return "";
        }
        string += elipses;
        return string;
    }

    public static void fluidContainerInfoHeader(List<Component> tooltip) {
        CreateLang.translate("gui.goggles.fluid_container").forGoggles(tooltip);
    }

    public static void tankInfoTooltip(List<Component> tooltip, LangBuilder tankName, FluidTank tank) {
        tankInfoTooltip(tooltip, tankName, tank.getFluid(), tank.getCapacity());
    }

    public static void tankInfoTooltip(List<Component> tooltip, LangBuilder tankName, FluidStack contents, int capacity) {
        LangBuilder mb = CreateLang.builder().translate("generic.unit.millibuckets");

        tankName.style(ChatFormatting.GRAY).forGoggles(tooltip, 0);

        if (contents.isEmpty()) {
            CreateLang.builder().translate("gui.goggles.fluid_container.capacity")
                .add(number(capacity).add(mb).style(ChatFormatting.GOLD))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip, 1);
        } else {
            fluidName(contents).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
            builder()
                .add(number(contents.getAmount()).add(mb).style(ChatFormatting.GOLD))
                .text(ChatFormatting.GRAY, " / ")
                .add(number(capacity).add(mb).style(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);
        }
    }

    public static MutableComponent barMeterComponent(int value, int maxValue) {
        return barMeterComponent(value, maxValue, maxValue);
    }

    public static MutableComponent barMeterComponent(int value, int maxValue, int totalBars) {
        float proportion = (float) value / maxValue;
        ChatFormatting color;
        if (proportion <= 0.25f) color = ChatFormatting.DARK_RED;
        else if (proportion <= 0.5f) color = ChatFormatting.GOLD;
        else color = ChatFormatting.DARK_GREEN;
        int bars = Math.round(proportion * totalBars);
        return Component.empty()
            .append(Component.literal("|".repeat(bars)).withStyle(color))
            .append(Component.literal("|".repeat(totalBars - bars)).withStyle(ChatFormatting.DARK_GRAY));
    }

    public static MutableComponent tickOrCross(boolean tick) {
        return tick ? tick() : cross();
    }

    public static MutableComponent tick() {
        return Component.literal("\u2714").withStyle(ChatFormatting.GREEN).copy();
    }

    public static MutableComponent cross() {
        return Component.literal("\u2718").withStyle(ChatFormatting.RED).copy();
    }

    public static ChatFormatting getStatColor(float stat, boolean inverted) {
        if (stat > 0.67f) return inverted ? ChatFormatting.RED : ChatFormatting.GREEN;
        if (stat > 0.33f) return ChatFormatting.YELLOW;
        return inverted ? ChatFormatting.GREEN : ChatFormatting.RED;
    }

    // VatMaterial stat-range constants for vatMaterialMaxPressure / vatMaterialConductivity
    // progress-bar normalization.
    private static final float pressureMin = 0f;
    private static final float pressureMax = 1000000f;
    private static final float conductivityMin = 0f;
    private static final float conductivityMax = 100f;

    /**
 * Render a labelled 5-segment progress bar for a {@link petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial}'s
 * max pressure. Used by both goggle-tooltip vatMaterialTooltip and the JEI
 * VatMaterialCategory draw pipeline.
*/
    public static Component vatMaterialMaxPressure(petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial material, Palette palette) {
        float pressurePercent = net.minecraft.util.Mth.clamp((material.maxPressure() - pressureMin) / (pressureMax - pressureMin), 0f, 1f);
        return DestroyLang.translate("tooltip.vat_material.pressure")
            .space()
            .add(Component.literal(TooltipHelper.makeProgressBar(5, (int)(5 * pressurePercent + 0.5f))).withStyle(getStatColor(pressurePercent, false)))
            .component().withStyle(palette.highlight());
    }

    /**
 * Render a labelled 5-segment progress bar for a {@link petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial}'s
 * thermal conductivity · color inverted (high conductivity bad for heated vat containment).
*/
    public static Component vatMaterialConductivity(petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial material, Palette palette) {
        float conductivityPercent = net.minecraft.util.Mth.clamp((material.thermalConductivity() - conductivityMin) / (conductivityMax - conductivityMin), 0f, 1f);
        return DestroyLang.translate("tooltip.vat_material.conductivity")
            .space()
            .add(Component.literal(TooltipHelper.makeProgressBar(5, (int)(5 * conductivityPercent + 0.5f))).withStyle(getStatColor(conductivityPercent, true)))
            .component()
            .withStyle(palette.highlight());
    }

    /** Render a tick/cross for a {@link petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial}'s transparency.*/
    public static Component vatMaterialTransparent(petrolpark.mc.destroy.core.chemistry.vat.material.VatMaterial material, Palette palette) {
        return DestroyLang.translate("tooltip.vat_material.transparent")
            .space()
            .add(tickOrCross(material.transparent()))
            .component()
            .withStyle(palette.highlight());
    }

    public static String toSubscript(int value) {
        StringBuilder sb = new StringBuilder();
        for (char c : String.valueOf(value).toCharArray()) {
            if (c == '-') sb.append("\u208b");
            else sb.append(subscriptNumbers[Integer.parseInt(String.valueOf(c))]);
        }
        return sb.toString();
    }

    /**
     * @param value digits and {@code +} / {@code -} characters only
     */
    public static String toSuperscript(String value) {
        StringBuilder sb = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (c == '-') sb.append("\u207b");
            else if (c == '+') sb.append("\u207a");
            else try {
                sb.append(superscriptNumbers[Integer.parseInt(String.valueOf(c))]);
            } catch (Throwable ignored) {}
        }
        return sb.toString();
    }

    public static String nothingIfOne(int n) {
        if (n == 1) return "";
        return String.valueOf(n);
    }

    /** Uses the 'k = A·e^(-Ea/RT)'
 * notation where A's units depend on the overall order: 1st order → s⁻¹; nth order →
 * L^(n-1) · mol^(1-n) · s⁻¹.
*/
    public static net.minecraft.network.chat.Component preexponentialFactor(
            petrolpark.mc.destroy.chemistry.legacy.LegacyReaction reaction) {
        int totalOrder = 0;
        for (int order : reaction.getOrders().values()) totalOrder += order;
        if (totalOrder == 1) {
            return translate("tooltip.reaction.preexponential_factor.frequency_factor",
                reaction.getPreexponentialFactor()).component();
        }
        return translate("tooltip.reaction.preexponential_factor",
            reaction.getPreexponentialFactor(),
            toSuperscript("" + nothingIfOne(1 - totalOrder)),
            toSuperscript("" + nothingIfOne(totalOrder - 1))).component();
    }

    /**
 * Format a chemical concentration/mole quantity for the Mixture contents tooltip. Handles
 * kilo/milli/micro scale switching based on the formatter's maximum fraction digits.
 *
 * @param quantity raw value (moles or molarity)
 * @param useMoles if true, uses the "moles" translation key family; else "concentration"
 * @param concentrationFormatter DecimalFormat whose max fraction digits determines the scale cutoff
*/
    public static LangBuilder quantity(float quantity, boolean useMoles, DecimalFormat concentrationFormatter) {
        String translationKey = useMoles ? "tooltip.mixture_contents.moles" : "tooltip.mixture_contents.concentration";
        double smallestVisibleQuantity = Math.pow(10, -concentrationFormatter.getMaximumFractionDigits());
        if (quantity != 0f) {
            if (Math.abs(quantity) >= 1000f) {
                quantity /= 1000f;
                translationKey += ".kilo";
            } else if (Math.abs(quantity) <= smallestVisibleQuantity / 1000f) {
                quantity *= 1000000f;
                translationKey += ".micro";
            } else if (Math.abs(quantity) <= smallestVisibleQuantity) {
                quantity *= 1000f;
                translationKey += ".milli";
            }
        }
        return translate(translationKey, concentrationFormatter.format(quantity));
    }

    /** Reference to {@link TooltipHelper} kept to ensure a non-no-op import if future consumers extend this.*/
    @SuppressWarnings("unused")
    private static final Class<?> TOOLTIP_HELPER_REF = TooltipHelper.class;

    public enum TemperatureUnit {

        KELVINS(t -> t, "K"),
        DEGREES_CELCIUS(t -> t - 273f, "\u00B0C"),
        DEGREES_FARENHEIT(t -> (t - 273f) * 9 / 5 + 32, "\u00B0F");

        private static final DecimalFormat DF = new DecimalFormat();
        static {
            DF.setMinimumFractionDigits(1);
            DF.setMaximumFractionDigits(1);
        }

        private final UnaryOperator<Float> conversionFromKelvins;
        private final String symbol;

        TemperatureUnit(UnaryOperator<Float> conversionFromKelvins, String symbol) {
            this.conversionFromKelvins = conversionFromKelvins;
            this.symbol = symbol;
        }

        public String of(float temperature) {
            return DF.format(conversionFromKelvins.apply(temperature)) + symbol;
        }

        public String of(float temperature, DecimalFormat formatter) {
            return formatter.format(conversionFromKelvins.apply(temperature)) + symbol;
        }
    }
}
