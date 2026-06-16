package petrolpark.mc.destroy.core.chemistry.storage;

import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.templates.FluidHandlerItemStack;

import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.DestroyFluids;
import petrolpark.mc.destroy.chemistry.legacy.LegacyMixture;
import petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture;
import petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid;

/**
 * Item-backed Mixture fluid tank. Stores 1 Mixture FluidStack in the item's
 * {@link DataComponents#FLUID_CONTENTS} data component via NeoForge's 1.21 FluidHandlerItemStack
 * pattern (uses the standard DataComponentType rather than legacy NBT). The vanilla
 * {@code super.fill} rejects a fluid that differs from the one already stored.
 *
 * <p>{@link #fill} is overridden to handle that case: it first calls {@code super.fill} for the
 * vanilla path; if that returns 0 and space remains, and both the incoming and stored fluids are
 * mixtures carrying a MIXTURE component, it performs a molar-weighted merge (same approach as
 * {@code GeniusFluidTank.fill}) and writes the result back via
 * {@code setFluid(MixtureFluid.of(...))}.</p>
*/
public class ItemMixtureTank extends FluidHandlerItemStack {

    public ItemMixtureTank(ItemStack container, int capacity) {
        super(() -> DestroyDataComponents.MIXTURE_TANK, container, capacity);
    }

    /**
 *
 * <p>(symptom: a flask carrying vanilla {@code minecraft:water} could not be right-clicked to
 * empty into a Vat — the fluid-conversion recipe that turns it into Destroy's Mixture water
 * never ran; filling the Vat with a pump did convert successfully.)</p>
 *
 * <ol>
 * <li>Player picks up {@code minecraft:water} into a flask/beaker/cylinder (via Create spout,
 * item drain, or any future fill path)</li>
 * <li>Player right-clicks the Vat side block → {@link IMixtureStorageItem#defaultUseOn} →
 * {@code tryEmpty} → {@code VatSideFluidCapability.fill(vanilla_water)} →
 * {@link petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity.VatTankWrapper#fill}
 * resolves a {@link petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe}
 * (the {@code minecraft:water → destroy:mixture(pure H₂O)} recipe is shipped) →
 * converts and stores as Mixture in the Vat.</li>
 * </ol>
 *
 * <p>Without isFluidValid relaxation, step 1 fails (item tank rejects {@code minecraft:water})
 * → user can never reach step 2. Pump path works because pipe→VatSide goes through the same
 * {@code VatTankWrapper.fill} conversion path WITHOUT going through the flask. So the bug is
 * the flask gate, not the conversion logic.</p>
 *
 * <p>The conversion happens at the **sink** (Vat side cap) not at the **source** (flask),
 * which means the flask's stored fluid stays as-is (e.g., {@code minecraft:water} → flask
 * tooltip shows "Water"). When poured into the Vat, it converts. When poured into anything
 * else (vanilla cauldron, other mods' tanks), it transfers as the original fluid. This matches
 * how the pump path works.</p>
*/
    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return !stack.isEmpty();
    }

    public int getRemainingSpace() {
        return getTankCapacity(0) - getFluidInTank(0).getAmount();
    }

    public int getFluidAmount() {
        return getFluidInTank(0).getAmount();
    }

    /** Mixture-aware fill merge: if the standard fill rejects
 * (because existing mixture differs from incoming), attempt a molar-weighted merge.
*/
    @Override
    public int fill(FluidStack resource, FluidAction doFill) {
        int filled = super.fill(resource, doFill);
        if (filled != 0) return filled;

        // Standard fill rejected. Try mixture merge if both are mixtures with MIXTURE component.
        if (container.getCount() != 1 || resource.isEmpty()) return 0;
        FluidStack contained = getFluid();
        if (contained.isEmpty()) return 0;  // empty case is already handled by super.fill
        if (!DestroyFluids.isMixture(resource) || !DestroyFluids.isMixture(contained)) return 0;
        if (!resource.has(DestroyDataComponents.MIXTURE) || !contained.has(DestroyDataComponents.MIXTURE)) return 0;

        int space = capacity - contained.getAmount();
        if (space <= 0) return 0;

        int amountAdded = Math.min(space, resource.getAmount());
        if (doFill.simulate()) return amountAdded;

        LegacyMixture existingMixture = LegacyMixture.readNBT(
            contained.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()), false);
        LegacyMixture addedMixture = LegacyMixture.readNBT(
            resource.getOrDefault(DestroyDataComponents.MIXTURE, new CompoundTag()), false);

        ReadOnlyMixture newMixture = LegacyMixture.mix(Map.of(
            existingMixture, (double) contained.getAmount() / 1000d,
            addedMixture, (double) amountAdded / 1000d), false);

        setFluid(MixtureFluid.of(contained.getAmount() + amountAdded, newMixture));
        return amountAdded;
    }

    
    public static FluidStack read(ItemStack stack) {
        SimpleFluidContent content = stack.get(DestroyDataComponents.MIXTURE_TANK);
        return content == null ? FluidStack.EMPTY : content.copy();
    }

    /** Attach a tank to a stack as a NeoForge IFluidHandlerItem capability provider.*/
    public static IFluidHandlerItem of(ItemStack stack, int capacity) {
        return new ItemMixtureTank(stack, capacity);
    }
}
