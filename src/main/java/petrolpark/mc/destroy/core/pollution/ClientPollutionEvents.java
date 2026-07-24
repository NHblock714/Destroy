package petrolpark.mc.destroy.core.pollution;

import petrolpark.mc.library.core.client.rendering.world.BlendedBlockColorEvent;
import petrolpark.mc.library.util.ColorHelper;

import net.createmod.catnip.theme.Color;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import petrolpark.mc.destroy.DestroyPollutionTypes;
import petrolpark.mc.destroy.config.DestroyConfigs;

@EventBusSubscriber(Dist.CLIENT)
public class ClientPollutionEvents {

    private static volatile SmogCache SMOG_CACHE = null;

    public static final int BROWN = 0xFF3F3832;

    @SubscribeEvent
    public static void onBlendedBlockColors(BlendedBlockColorEvent event) {
        if (
            !(DestroyConfigs.client().pollution.smogAffectsBlockColors.get()) || (
            event.getColorResolver() != BiomeColors.GRASS_COLOR_RESOLVER
            && event.getColorResolver() != BiomeColors.FOLIAGE_COLOR_RESOLVER
            && event.getColorResolver() != BiomeColors.WATER_COLOR_RESOLVER
        )) return;
        final long chunkPos = new ChunkPos(event.getPos()).toLong();
        // capture into a local; this handler runs on chunk-compile worker threads and
        // another thread (e.g. render thread via refreshSmog) can null out SMOG_CACHE between
        // the check and the read. Also `volatile` on the field so reads observe recent writes.
        SmogCache cache = SMOG_CACHE;
        if (cache == null || cache.chunkPos() != chunkPos) {
            cache = new SmogCache(chunkPos, PollutionHelper.getPollutionProportion(event.getLevel(), event.getPos(), DestroyPollutionTypes.SMOG.get()));
            SMOG_CACHE = cache;
        };
        event.setColor(Color.mixColors(event.getColor(), BROWN, cache.smogPollutionProportion()));
    };

    static record SmogCache(long chunkPos, float smogPollutionProportion) {};

    public static final void refreshSmog(ChunkPos pos) {
        SMOG_CACHE = null;
        ColorHelper.refreshChunkColors(pos);
    };
};
