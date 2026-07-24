package petrolpark.mc.destroy.content.processing.trypolithography;

import petrolpark.mc.library.compat.create.core.world.item.transported.DirectionalTransportedItemStack;
import petrolpark.mc.library.compat.create.core.world.item.transported.IDirectionalBeltItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyItem;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import petrolpark.mc.destroy.DestroyDataComponents;

/**
 * Intermediate item in the trypolithography (circuit fabrication) pipeline — carries a 4×4 binary
 * matrix circuit pattern (16 bits packed as one {@code int}) stored on the stack as a DataComponent.
 * Both {@link CircuitPatternItem} and Create's {@link SequencedAssemblyItem} can carry patterns —
 * the {@link #getPattern}/{@link #putPattern} static methods accept both item types and only
 * read/write on valid carriers.
*/
public class CircuitPatternItem extends Item implements IDirectionalBeltItem<DirectionalTransportedItemStack> {

    public CircuitPatternItem(Properties properties) {
        super(properties);
    }

    @Override
    public DirectionalTransportedItemStack makeTransportedItemStack(TransportedItemStack transported) {
        return DirectionalTransportedItemStack.copyFully(transported);
    }

    /**
 * Read the 16-bit circuit pattern (4×4 binary matrix packed as int) from an ItemStack. Returns
 * 0 for stacks that aren't valid pattern carriers (not a CircuitPatternItem nor
 * SequencedAssemblyItem) or haven't been assigned a pattern yet.
*/
    public static int getPattern(ItemStack stack) {
        if (!(stack.getItem() instanceof CircuitPatternItem || stack.getItem() instanceof SequencedAssemblyItem)) return 0;
        return stack.getOrDefault(DestroyDataComponents.CIRCUIT_PATTERN, 0);
    }

    /**
 * Write the 16-bit circuit pattern onto an ItemStack (no-op for stacks that aren't valid
 * pattern carriers). Overwrites any prior pattern.
*/
    public static void putPattern(ItemStack stack, int pattern) {
        if (stack.getItem() instanceof CircuitPatternItem || stack.getItem() instanceof SequencedAssemblyItem) {
            stack.set(DestroyDataComponents.CIRCUIT_PATTERN, pattern);
        }
    }
}
