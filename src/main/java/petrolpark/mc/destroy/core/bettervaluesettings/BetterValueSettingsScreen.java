package petrolpark.mc.destroy.core.bettervaluesettings;

import java.util.function.Consumer;

import com.simibubi.create.AllKeys;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsPacket;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsScreen;

import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;

/**
 * Improved {@link ValueSettingsScreen} that propagates the access face + interaction hand back
 * to the server in the {@link ValueSettingsPacket}, enabling per-side / per-hand value box
 * behaviours like {@link SidedScrollValueBehaviour} to know which face / hand the player set
 * the value from.
 *
 * <p><b>Wire</b>: needed when a {@link BetterValueSettingsBehaviour} wants its access info
 * preserved end-to-end. SidedScrollValueBehaviour uses lastSideAccessed which gets set
 * via {@code acceptAccessInformation} from the server-side packet handler — this Screen ensures
 * the packet actually carries the side+hand info.</p>
*/
public class BetterValueSettingsScreen extends ValueSettingsScreen {

    protected final Direction sideAccessed;
    protected final InteractionHand hand;
    protected final BlockPos pos;       // Mirrors parent's private 'pos'
    protected final int packetNetId;    // Mirrors parent's private 'netId' (1.21 Create made it
                                        // private; cross-jar AT didn't apply, so it is captured
                                        // at ctor time)

    public BetterValueSettingsScreen(BlockPos pos, Direction sideAccessed, InteractionHand hand,
                                     ValueSettingsBoard board, ValueSettings valueSettings,
                                     Consumer<ValueSettings> onHover, int netId) {
        super(pos, board, valueSettings, onHover, netId);
        this.pos = pos;
        this.sideAccessed = sideAccessed;
        this.hand = hand;
        this.packetNetId = netId;
    }

    @Override
    protected void saveAndClose(double mouseX, double mouseY) {
        ValueSettings closest = getClosestCoordinate((int) mouseX, (int) mouseY);
        // Mirror parent's 1.21 send path but inject hand + sideAccessed so the server's
        // BetterValueSettingsBehaviour.acceptAccessInformation sees the correct face/hand.
        CatnipServices.NETWORK.sendToServer(new ValueSettingsPacket(
            pos, closest.row(), closest.value(),
            hand,             // 1.21 parent passes null; this passes the actual hand
            null,             // hitResult (not needed by SidedScrollValueBehaviour)
            sideAccessed,     // 1.21 parent passes Direction.UP; this passes the actual side
            AllKeys.ctrlDown(),
            packetNetId));    // mirrored from parent's private netId at ctor time
        onClose();
    }
}
