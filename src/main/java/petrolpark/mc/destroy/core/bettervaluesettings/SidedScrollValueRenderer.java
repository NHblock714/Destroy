package petrolpark.mc.destroy.core.bettervaluesettings;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.AllItems;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBox;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the on-face value box for {@link SidedScrollValueBehaviour} while it is being looked at.
 *
 * <p>Create's own {@code ScrollValueRenderer.tick()} only renders a box for behaviours that are
 * instances of the concrete {@code ScrollValueBehaviour} class. {@code SidedScrollValueBehaviour}
 * extends {@code BlockEntityBehaviour} directly (so it can key one value per face), so it is skipped
 * there and its box never appears — even though its value-settings interaction still works, because
 * that path is gated on the {@code ValueSettingsBehaviour} interface instead. This mirrors Create's
 * renderer but shows the value stored for the face the player is looking at.</p>
*/
@OnlyIn(Dist.CLIENT)
public class SidedScrollValueRenderer {

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        HitResult target = mc.hitResult;
        if (!(target instanceof BlockHitResult result)) return;
        ClientLevel world = mc.level;
        BlockPos pos = result.getBlockPos();
        Direction face = result.getDirection();

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof SmartBlockEntity sbe)) return;

        for (BlockEntityBehaviour behaviour : sbe.getAllBehaviours()) {
            if (!(behaviour instanceof SidedScrollValueBehaviour sided)) continue;
            if (!sided.isActive()) {
                Outliner.getInstance().remove(sided);
                continue;
            }
            ItemStack mainhand = mc.player.getItemInHand(InteractionHand.MAIN_HAND);
            boolean clipboard = sided.bypassesInput(mainhand);
            if (sided.onlyVisibleWithWrench() && !AllItems.WRENCH.isIn(mainhand) && !clipboard) continue;

            boolean highlight = sided.testHit(target.getLocation()) && !clipboard;
            addBox(pos, face, sided, highlight);

            if (!highlight) continue;
            List<MutableComponent> tip = new ArrayList<>();
            tip.add(sided.label.copy());
            tip.add(CreateLang.translateDirect("gui.value_settings.hold_to_edit"));
            CreateClient.VALUE_SETTINGS_HANDLER.showHoverTip(tip);
        }
    }

    private static void addBox(BlockPos pos, Direction face, SidedScrollValueBehaviour behaviour, boolean highlight) {
        AABB bb = new AABB(Vec3.ZERO, Vec3.ZERO).inflate(0.5).contract(0.0, 0.0, -0.5).move(0.0, 0.0, -0.125);
        // Scrolling on this face edits the value keyed by this face (see the behaviour's
        // lastSideAccessed handling), so show that same value.
        ValueBox box = new ValueBox.TextValueBox(behaviour.label, bb, pos, Component.literal(behaviour.formatValue(face)));
        box.passive(!highlight).wideOutline();
        Outliner.getInstance().showOutline(behaviour, box.transform(behaviour.getSlotPositioning())).highlightFace(face);
    }
}
