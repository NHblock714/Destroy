package petrolpark.mc.destroy.core.player;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import petrolpark.mc.destroy.DestroyAttachmentTypes;
import petrolpark.mc.destroy.config.DestroyAllConfigs;

/**
 * Samples each Player's position once a second into their {@link PlayerPreviousPositions}, server
 * side only, keeping the trail Chorus Wine teleports back along.
 */
@EventBusSubscriber
public class PlayerPreviousPositionsHandler {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide()) return;

        PlayerPreviousPositions data = player.getData(DestroyAttachmentTypes.PLAYER_PREVIOUS_POSITIONS);
        data.incrementTickCounter();
        if (data.hasBeenSecond()) {
            data.recordPosition(player.blockPosition(),
                DestroyAllConfigs.SERVER.substances.chorusWineTeleportTime.get());
        }
    }
}
