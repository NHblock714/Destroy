package petrolpark.mc.destroy.core.pollution;

import static petrolpark.mc.destroy.DestroyPollutionTypes.ACID_RAIN;
import static petrolpark.mc.destroy.core.pollution.PollutionHelper.changePollution;
import static petrolpark.mc.destroy.core.pollution.PollutionHelper.getPollutionProportion;
import static petrolpark.mc.destroy.core.pollution.PollutionHelper.getPollutionTypeProperties;
import static petrolpark.mc.destroy.core.pollution.PollutionHelper.isPollutionEnabled;

import petrolpark.mc.library.core.world.block.HandlePrecipitationEvent;
import petrolpark.mc.library.core.world.entity.npc.VillagerUpdateSpecialPricesEvent;
import petrolpark.mc.library.core.event.CommonEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.BonemealEvent;
import net.neoforged.neoforge.event.level.BlockGrowFeatureEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import petrolpark.mc.destroy.DestroyDataMapTypes;
import petrolpark.mc.destroy.DestroyTags;
import petrolpark.mc.destroy.config.DestroyConfigs;

/**
 * Effects of Pollution on the world.
*/
@EventBusSubscriber
public class PollutionEvents {
    
    /**
 * Lightning regenerates ozone.
 * @param event
*/
    @SubscribeEvent
    public static final void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof LightningBolt && isPollutionEnabled()) PollutionType.streamAll().forEach(pt -> 
            changePollution(event.getLevel(), event.getEntity().getOnPos(), pt, getPollutionTypeProperties(pt).lightningStrikeChange())
        );
    };

    /**
 * Animals may fail to breed.
 * @param event
*/
    @SubscribeEvent
    public static final void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (isPollutionEnabled()) {
            final Level level = event.getParentA().level();
            if (PollutionType.streamAll().anyMatch(pt -> 
                level.getRandom().nextFloat() < getPollutionTypeProperties(pt).breedingFailureChanceMultiplier() * getPollutionProportion(level, event.getParentA().getOnPos(), pt)
            )) CommonEvents.failToBreed(event);
        };
    };

    /**
 * Crops may fail to grow.
 * @param event
*/
    @SubscribeEvent
    public static final void onCropGrowPre(CropGrowEvent.Pre event) {
        if (isPollutionEnabled() && event.getLevel() instanceof final Level level && PollutionType.streamAll().anyMatch(pt -> 
            level.getRandom().nextFloat() < getPollutionTypeProperties(pt).growthFailureChanceMultiplier() * getPollutionProportion(level, event.getPos(), pt)
        )) {
            if (event.getLevel() instanceof ServerLevel serverLevel) serverLevel.sendParticles(PollutionHelper.getCropGrowthFailureParticles(), event.getPos().getX() + 0.5d, event.getPos().getY() + level.random.nextDouble() * event.getState().getShape(level, event.getPos()).max(Axis.Y), event.getPos().getZ() + 0.5d, 10, 0.25d, 0.25d, 0.25d, 0.02d);
            event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
        };
    };

    /**
 * Bonemeal may fail to work.
 * @param event
*/
    @SubscribeEvent
    public static final void onBonemeal(BonemealEvent event) {
        if (!isPollutionEnabled() || event.getStack().is(DestroyTags.Items.BONEMEAL_BYPASSES_POLLUTION.tag)) return;
        if (PollutionType.streamAll().anyMatch(pt -> 
            event.getLevel().getRandom().nextFloat() < getPollutionTypeProperties(pt).bonemealFailureChanceMultiplier() * getPollutionProportion(event.getLevel(), event.getPos(), pt)
        )) {
            if (event.getLevel() instanceof ServerLevel serverLevel) serverLevel.sendParticles(PollutionHelper.getCropGrowthFailureParticles(), event.getPos().getX() + 0.5d, event.getPos().getY() + event.getLevel().getRandom().nextDouble() * event.getState().getShape(event.getLevel(), event.getPos()).max(Axis.Y), event.getPos().getZ() + 0.5d, 10, 0.25d, 0.25d, 0.25d, 0.02d);
            event.setSuccessful(false);
            event.setCanceled(true);
        };
    };

    /**
 * Decrease Pollution when a tree is grown.
 * @param event
*/
    @SubscribeEvent
    public static final void onBlockGrowFeature(BlockGrowFeatureEvent event) {
        if (event.getLevel() instanceof Level level && isPollutionEnabled() && event.getFeature().value().feature() instanceof TreeFeature) {
            PollutionType.streamAll().forEach(pt -> 
                changePollution(level, event.getPos(), pt, getPollutionTypeProperties(pt).treeGrowthChange())
            );
        };
    };

    /**
 * Pollution increases Villager prices.
 * @param event
*/
    @SubscribeEvent
    public static final void onVillagerUpdateSpecialPrices(VillagerUpdateSpecialPricesEvent event) {
        if (!isPollutionEnabled()) return;
        final Level level = event.getEntity().level();
        final BlockPos pos = event.getVillager().getOnPos();
        PollutionType.streamAll().forEach(pt -> 
            event.getOffers().forEach(offer -> 
                offer.addToSpecialPriceDiff((int)(getPollutionProportion(level, pos, pt) * getPollutionTypeProperties(pt).villagerPriceMultiplier()))
            )
        );
    };

    /**
 * Acid Rain can destroy, oxidize or otherwise change blocks.
 * @param event
*/
    @SubscribeEvent
    @SuppressWarnings("deprecation")
    public static final void onHandlePrecipitation(HandlePrecipitationEvent event) {
        if (event.getPrecipitation() != Biome.Precipitation.RAIN) return;
        
        final float acidity = getPollutionProportion(event.getLevel(), ACID_RAIN);
        if (event.getLevel().getRandom().nextFloat() < acidity * DestroyConfigs.server().pollution.acidRainOxidationChance.getF() && event.getState().getBlock() instanceof WeatheringCopper weatheringCopper) {
            weatheringCopper.getNext(event.getState()).ifPresent(state -> {
                event.getLevel().setBlockAndUpdate(event.getPos(), state);
                event.setCanceled(true);
            });
        };
        if (event.getLevel().getRandom().nextFloat() < acidity * DestroyConfigs.server().pollution.acidRainReplacementChance.getF()) {
            final Block replacementBlock = event.getState().getBlock().builtInRegistryHolder().getData(DestroyDataMapTypes.ACID_RAIN_REPLACEMENTS);
            if (replacementBlock != null) {
                event.getLevel().setBlockAndUpdate(event.getPos(), replacementBlock.withPropertiesOf(event.getState()));
                event.setCanceled(true);
            };
        };
    };
};
