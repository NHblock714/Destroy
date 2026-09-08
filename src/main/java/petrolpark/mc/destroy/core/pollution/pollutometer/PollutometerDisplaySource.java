package petrolpark.mc.destroy.core.pollution.pollutometer;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.PercentOrProgressBarDisplaySource;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import petrolpark.mc.destroy.core.pollution.PollutionHelper;
import petrolpark.mc.destroy.core.pollution.PollutionType;

/**
 * Display Link source for the Pollutometer — emits the player-selected pollution type's current
 * level as a {@code currentValue/max} ratio (rendered as % or progress bar by the linked sign /
 * flap display, depending on which {@link com.simibubi.create.content.redstone.displayLink.source.NumericSingleLineDisplaySource
 * NumericSingleLineDisplaySource} subclass is active). 1.21
 * moved {@code max} into a {@link PollutionType.Properties} record looked up via
 * {@link PollutionHelper#getPollutionTypeProperties(PollutionType)}.</li>
 * <li><b>{@code DestroyLang.translate}</b> preserved (same helper).</li>
 * <li><b>Registered via</b> {@link petrolpark.mc.destroy.DestroyDisplaySources#register()} —
 * added to {@code DisplaySource.BY_BLOCK_ENTITY} multimap on
 * {@code DestroyBlockEntityTypes.POLLUTOMETER.get()}.</li>
 * </ul>
*/
public class PollutometerDisplaySource extends PercentOrProgressBarDisplaySource {

    
    private static final boolean DIAG = Boolean.getBoolean("destroy.debug.pollutometer.display");

    @Override
    protected Float getProgress(DisplayLinkContext context) {
        BlockEntity be = context.getSourceBlockEntity();
        if (DIAG) petrolpark.mc.destroy.Destroy.LOGGER.info(
            "[PollutometerDS] getProgress called · sourcePos={} sourceBE={}",
            context.getSourcePos(), be == null ? "null" : be.getClass().getSimpleName());

        if (!(be instanceof PollutometerBlockEntity pollutometer)) {
            // Defensive — if BE not yet loaded or different type, return 0f instead of null so
            // the display still shows "0%" / empty progress bar instead of staying blank.
            return 0f;
        }

        PollutometerSelector selector = pollutometer.getSelector();
        if (selector == null) return 0f;
        PollutionType<?> type = selector.rawPollutionType();
        if (type == null) return 0f;

        int current = PollutionHelper.getPollution(context.level(), context.getSourcePos(), type);
        int max = PollutionHelper.getPollutionTypeProperties(type).max();
        if (DIAG) petrolpark.mc.destroy.Destroy.LOGGER.info(
            "[PollutometerDS] selector={} type={} current={} max={}",
            selector, type, current, max);

        if (max <= 0) return 0f;
        return (float) current / (float) max;
    }

    // Percentage mode is left to PercentOrProgressBarDisplaySource.formatNumeric, which prints
    // clamp(progress * 100, 0, 100) followed by "%" — the same rendering as the Stockpile
    // Switch's Fill Level source, which extends the same class.

    /**
 * Reads the "Mode" config widget added in {@link #initConfigurationWidgets}: 0=percentage,
 * 1=progress bar (mirrors Create's FillLevelDisplaySource convention).
*/
    @Override
    protected boolean progressBarActive(DisplayLinkContext context) {
        return context.sourceConfig().getInt("Mode") == 1;
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    
    @Override
    public void initConfigurationWidgets(DisplayLinkContext context,
                                         com.simibubi.create.foundation.gui.ModularGuiLineBuilder builder,
                                         boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (isFirstLine) return;
        builder.addSelectionScrollInput(0, 120, (selection, label) -> {
            selection
                .forOptions(java.util.List.of(
                    net.minecraft.network.chat.Component.translatable("create.display_source.fill_level.percent"),
                    net.minecraft.network.chat.Component.translatable("create.display_source.fill_level.progress_bar")))
                .titled(net.minecraft.network.chat.Component.translatable("create.display_source.fill_level.display"));
        }, "Mode");
    }

    @Override
    public Component getName() {
        return petrolpark.mc.destroy.client.DestroyLang
            .translate("display_source.pollutometer")
            .component();
    }
}
