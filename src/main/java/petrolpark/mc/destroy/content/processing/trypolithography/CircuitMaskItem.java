package petrolpark.mc.destroy.content.processing.trypolithography;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import petrolpark.mc.library.compat.create.core.world.item.transported.DirectionalTransportedItemStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyItems;
import petrolpark.mc.destroy.client.DestroyGuiTextures;
import petrolpark.mc.destroy.client.DestroyLang;

/**
 * Mask used in the trypolithography pipeline — punched by {@code KeypunchBlockEntity} to imprint
 * a 4×4 circuit pattern, then applied to other items via the photolithography process. Tracks up
 * to 3 unique punchers (UUIDs) per mask; on the 4th punch, the mask is consumed and replaced
 * with {@link DestroyItems#RUINED_CIRCUIT_MASK}. Displays a flipped orientation when launched
 * from a Weighted Ejector along certain axes.
*/
public class CircuitMaskItem extends CircuitPatternItem {

    public CircuitMaskItem(Properties properties) {
        super(properties);
    }

    public static final ChatFormatting[] directionColors = new ChatFormatting[] {
        ChatFormatting.WHITE, ChatFormatting.WHITE, ChatFormatting.RED,
        ChatFormatting.WHITE, ChatFormatting.YELLOW, ChatFormatting.BLUE
    };

    /**
 * Read the list of UUIDs that have previously punched this mask. Returns an empty list if the
 * stack has no PUNCHED_BY component yet (freshly-crafted mask).
*/
    public static List<UUID> getContaminants(ItemStack stack) {
        return stack.getOrDefault(DestroyDataComponents.PUNCHED_BY, List.of());
    }

    /**
 * Attempt to contaminate (punch) this mask with a new UUID.
 * <ul>
 * <li>If the UUID has already punched this mask → returns the stack unchanged (no dup).</li>
 * <li>If the mask already has 3 previous punches → returns a fresh {@link DestroyItems#RUINED_CIRCUIT_MASK}
 * stack (mask is consumed; caller should replace the original).</li>
 * <li>Otherwise appends the UUID to the PUNCHED_BY component and returns the modified stack.</li>
 * </ul>
*/
    public static ItemStack contaminate(ItemStack stack, UUID uuid) {
        stack = stack.copy();
        List<UUID> previousPunches = getContaminants(stack);
        if (previousPunches.contains(uuid)) return stack;
        if (previousPunches.size() >= 3) return DestroyItems.RUINED_CIRCUIT_MASK.asStack();
        // Write a new list with the extra UUID appended (PUNCHED_BY Codec handles List immutability)
        List<UUID> newPunches = new ArrayList<>(previousPunches);
        newPunches.add(uuid);
        stack.set(DestroyDataComponents.PUNCHED_BY, newPunches);
        return stack;
    }

    @Override
    public void onLaunchedByWeightedEjector(DirectionalTransportedItemStack stack, Direction launchDirection) {
        // If it is 'flipped', the Item has been rotated around 180° the north-south axis before
        // being rotated around the up-down axis.
        boolean alreadyFlipped = stack.stack.has(DestroyDataComponents.FLIPPED);
        stack.stack.remove(DestroyDataComponents.FLIPPED);

        Rotation rotation = stack.getRotation();
        if (launchDirection.getAxis() == Axis.Z && (rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_90)
            || launchDirection.getAxis() == Axis.X && (rotation == Rotation.COUNTERCLOCKWISE_90 || rotation == Rotation.CLOCKWISE_180)) {
            stack.rotate(Rotation.CLOCKWISE_180); // Fix the Rotation if certain orientations are flipped over certain axes
        }

        if (!alreadyFlipped) stack.stack.set(DestroyDataComponents.FLIPPED, true);
        super.onLaunchedByWeightedEjector(stack, launchDirection);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        // Clear the in-flight orientation state when the mask lands back in an inventory.
        stack.remove(DestroyDataComponents.FLIPPED);
        stack.remove(petrolpark.mc.library.registry.PetrolparkDataComponentTypes.ROTATION_WHILE_FLYING);

        if (level.isClientSide() && isSelected && entity instanceof Player player && player.isCrouching()) {
            Direction direction = player.getDirection();
            player.displayClientMessage(
                DestroyLang.translate("tooltip.circuit_mask.facing_direction",
                    DestroyLang.direction(direction).style(directionColors[direction.ordinal()]))
                    .component(), true);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendHoverText(stack, context, tooltipComponents, isAdvanced);
        if (stack.has(DestroyDataComponents.HIDE_CONTAMINANTS)) return;

        Level level = context.level();
        // Jade compatibility fix — context.level() can be null when invoked from a pre-game tooltip.
        // must access {@code Minecraft.getInstance().level} (typed as ClientLevel,
        // @OnlyIn(CLIENT)) only via a nested ClientFallback class so dedicated-server class loading
        // of CircuitMaskItem (during DestroyItems.<clinit>) doesn't trigger ClientLevel resolution.
        if (level == null && net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            level = ClientFallback.getCurrentLevel();
        }
        if (level == null) return; // still null? skip (shouldn't happen in practice)

        List<UUID> previousPunchers = getContaminants(stack);
        tooltipComponents.add(Component.literal(" "));
        tooltipComponents.add(
            DestroyLang.translate("tooltip.circuit_mask.punched_by", previousPunchers.size())
                .style(ChatFormatting.GRAY).component());
        for (UUID uuid : previousPunchers) {
            tooltipComponents.add(
                DestroyLang.translate("tooltip.circuit_mask.puncher",
                    Destroy.CIRCUIT_PUNCHER_HANDLER.getPuncher(level, uuid).getName())
                    .style(ChatFormatting.GRAY).component());
        }
        for (int i = 0; i < 3 - previousPunchers.size(); i++) {
            tooltipComponents.add(
                DestroyLang.translate("tooltip.circuit_mask.puncher_free")
                    .style(ChatFormatting.GRAY).component());
        }
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new CircuitPatternTooltipComponent(
            getPattern(stack), false,
            DestroyGuiTextures.CIRCUIT_MASK_BORDER,
            DestroyGuiTextures.CIRCUIT_MASK_CELL,
            DestroyGuiTextures.CIRCUIT_MASK_CELL_SHADING));
    }

    /** Wrapping in a nested class keeps dedicated-server class loading of
 * {@code CircuitMaskItem} from triggering {@code ClientLevel} resolution via
 * {@code RuntimeDistCleaner}.
*/
    public static final class ClientFallback {
        private ClientFallback() {}
        public static Level getCurrentLevel() {
            return Minecraft.getInstance().level;
        }
    }
}
