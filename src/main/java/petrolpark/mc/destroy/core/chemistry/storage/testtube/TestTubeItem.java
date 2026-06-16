package petrolpark.mc.destroy.core.chemistry.storage.testtube;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;
import petrolpark.mc.destroy.core.chemistry.hazard.ChemistryHazardHelper;
import petrolpark.mc.destroy.core.chemistry.storage.IMixtureStorageItem;
import petrolpark.mc.destroy.core.chemistry.storage.ItemMixtureTank;

/**
 * Small glass container for 200 mB of Mixture — the iconic Destroy experimentation + sampling item.
 * Players right-click a fluid-holding block (Vat / Basin / tank) to fill/empty.
*/
public class TestTubeItem extends Item implements IMixtureStorageItem {

    /** Capacity in mB.*/
    public static final int CAPACITY = 200;

    public TestTubeItem(Properties properties) {
        // Stackable fluid items break with
        // FluidHandlerItemStack — the cap stores fluid in the stack-shared DataComponent, so
        // 16 tubes share 1 cap state. Filling/draining a stack of 16 only fills 1 tube's worth
        // (200 mB); the rest of the stack appears empty but can't be re-filled because the shared
        // component blocks. Vanilla / Create's convention for fluid items is stacksTo(1) for
        // exactly this reason (a stacked test tube otherwise couldn't have its fluid extracted),
        // so single-stack is enforced.
        super(properties.stacksTo(1));
    }

    @Override
    public int getCapacity(ItemStack stack) {
        return CAPACITY;
    }

    @Override
    public Component getNameRegardlessOfFluid(ItemStack stack) {
        return Component.translatable(this.getDescriptionId());
    }

    @Override
    public Component getName(ItemStack stack) {
        return getNameWithFluid(stack);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        return IMixtureStorageItem.defaultUseOn(this, context);
    }

    /** Left-click support (fill from block) — called from a player-interact event handler if wired.*/
    public InteractionResult attack(Level level, Player player, InteractionHand hand, ItemStack stack) {
        return InteractionResult.PASS;  // Left-click path wired via common events if needed
    }

    /**
 *
 * <p>Calls {@link ChemistryHazardHelper#damage} with {@code skinContact = false} (test tube
 * exposes only mouth/nose/eyes-area unless submerged). Helper internally checks the Mixture
 * for ACUTELY_TOXIC / SMELLY / CARCINOGEN / LACRIMATOR / lead-element and gates each effect
 * by the corresponding {@link ChemistryHazardHelper.Protection} body slot.</p>
*/
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (entity instanceof LivingEntity livingEntity) {
            getContents(stack).ifPresent(contents -> {
                if (!contents.isEmpty()) {
                    ChemistryHazardHelper.damage(level, livingEntity, contents, false);
                }
            });
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        addContentsDescription(stack, tooltip);
        IFluidHandlerItem cap = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (cap != null) {
            FluidStack held = cap.drain(CAPACITY, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
            if (!held.isEmpty()) {
                tooltip.add(Component.literal("  " + held.getAmount() + " / " + CAPACITY + " mB")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    /** Factory for JEI cheat-mode / creative-tab TEST_TUBE pre-filled with a Mixture.*/
    public static ItemStack of(ItemStack baseStack, ReadOnlyMixture mixture) {
        ItemStack stack = baseStack.copy();
        IFluidHandlerItem cap = stack.getCapability(Capabilities.FluidHandler.ITEM);
        if (cap != null) {
            FluidStack mixtureStack = MixtureFluid.of(CAPACITY, mixture);
            cap.fill(mixtureStack, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        }
        return stack;
    }
}
