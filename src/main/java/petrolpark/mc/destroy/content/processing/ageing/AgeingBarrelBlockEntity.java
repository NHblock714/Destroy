package petrolpark.mc.destroy.content.processing.ageing;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.simibubi.create.foundation.fluid.SmartFluidTank;
import com.simibubi.create.foundation.item.SmartInventory;
import com.simibubi.create.foundation.recipe.RecipeFinder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.DestroySoundEvents;
import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.core.pollution.PollutingBehaviour;

/**
 * Aging Barrel block entity. Holds one fluid tank plus up to two item ingredients; once an
 * {@link AgeingRecipe} matches, the barrel consumes them, fills the tank with the result fluid and
 * seals itself (tank insertion and extraction forbidden) until the timer runs out. The seal lifts
 * as soon as the timer hits 0; {@link #tryOpen} only pops the lid so the fluid is rendered.
 *
 * <p>Capabilities are exposed through {@link #registerCapabilities(RegisterCapabilitiesEvent)}.</p>
 */
public class AgeingBarrelBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {

    private static final Object agingRecipeKey = new Object();
    private static final int TANK_CAPACITY = 1000;

    public SmartInventory inventory;
    protected SmartFluidTankBehaviour tank;

    /** Direct handle kept for {@link AgingBarrelBlock} and {@link #registerCapabilities} to reuse.*/
    public IItemHandlerModifiable itemCapability;

    protected DirectBeltInputBehaviour beltBehaviour;
    protected PollutingBehaviour pollutingBehaviour;

    private int timer; // -1 = finished, 0 = progress at 100%
    private int totalTime;

    public AgeingBarrelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        inventory = new SmartInventory(2, this, 1, false)
            .whenContentsChanged($ -> checkRecipe())
            .forbidExtraction();
        itemCapability = inventory;
        timer = -1;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        tank = new SmartFluidTankBehaviour(SmartFluidTankBehaviour.TYPE, this, 1, TANK_CAPACITY, true)
            .whenFluidUpdates(() -> {
                checkRecipe();
                sendData();
                if (timer == 0) timer = -1;
                onTimerChange();
            });
        behaviours.add(tank);

        beltBehaviour = new DirectBeltInputBehaviour(this);
        behaviours.add(beltBehaviour);

        pollutingBehaviour = new PollutingBehaviour(this);
        behaviours.add(pollutingBehaviour);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            Capabilities.ItemHandler.BLOCK,
            DestroyBlockEntityTypes.AGING_BARREL.get(),
            (be, context) -> be.itemCapability);
        event.registerBlockEntity(
            Capabilities.FluidHandler.BLOCK,
            DestroyBlockEntityTypes.AGING_BARREL.get(),
            (be, context) -> be.tank.getCapability());
    }

    /**
 * Manual recipe match — walks fluid + up to 2 item ingredients and starts processing if a recipe fits.
 * Not routed through {@code RecipeManager.getRecipeFor}; see {@link AgeingRecipe#matches}.
*/
    public void checkRecipe() {
        if (!hasLevel() || getLevel().isClientSide()) return;
        List<RecipeHolder<? extends Recipe<?>>> allRecipes =
            RecipeFinder.get(agingRecipeKey, level, r -> r.value().getType() == DestroyRecipeTypes.AGING.getType());

        List<AgeingRecipe> possibleRecipes = allRecipes.stream()
            .map(h -> (AgeingRecipe) h.value())
            .filter(recipe -> {
                if (!recipe.getFluidIngredients().get(0).ingredient().test(getTank().getFluid())) return false;
                List<ItemStack> availableItems = new ArrayList<>();
                availableItems.add(inventory.getItem(0));
                availableItems.add(inventory.getItem(1));
                for (int i = 0; i < recipe.getIngredients().size(); i++) {
                    Ingredient ingredient = recipe.getIngredients().get(i);
                    boolean matched = false;
                    ItemStack extracted = ItemStack.EMPTY;
                    for (ItemStack stack : availableItems) {
                        if (ingredient.test(stack)) {
                            matched = true;
                            extracted = stack;
                            break;
                        }
                    }
                    if (!matched) return false;
                    availableItems.remove(extracted);
                }
                return true;
            })
            .collect(Collectors.toList());

        if (!possibleRecipes.isEmpty()) {
            AgeingRecipe recipe = possibleRecipes.get(0);
            onTimerChange();
            getTank().drain(TANK_CAPACITY, FluidAction.EXECUTE);
            inventory.clearContent();
            getTank().fill(recipe.getFluidResults().get(0), FluidAction.EXECUTE);
            totalTime = recipe.getProcessingDuration();
            timer = recipe.getProcessingDuration();
            tank.forbidExtraction();
            tank.forbidInsertion();
        }
    }

    public int getLuminosity() {
        if (getBlockState().getValue(AgingBarrelBlock.IS_OPEN) && !getTank().isEmpty()) {
            FluidStack fluidStack = getTank().getFluid();
            return fluidStack.getFluid().getFluidType().getLightLevel(fluidStack);
        }
        return 0;
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        timer = compound.getInt("Timer");
        totalTime = compound.getInt("TotalTime");
        // Tank contents persisted by SmartFluidTankBehaviour.
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("Inventory", inventory.serializeNBT(registries));
        compound.putInt("Timer", timer);
        compound.putInt("TotalTime", totalTime);
    }

    @Override
    public void tick() {
        if (timer > 0) {
            timer--;
            onTimerChange();
        }
        super.tick();
    }

    public void onTimerChange() {
        if (!hasLevel()) return;
        BlockState oldState = getBlockState();
        BlockState newState = oldState.setValue(AgingBarrelBlock.IS_OPEN,
            timer >= 0 ? false : oldState.getValue(AgingBarrelBlock.IS_OPEN));

        if (timer <= 0) {
            tank.allowExtraction();
            tank.allowInsertion();
        } else {
            tank.forbidExtraction();
            tank.forbidInsertion();
        }

        int progress;
        if (timer < 0) progress = 0;
        else if (timer == 0) progress = 4;
        else progress = (int) Mth.clamp(5 * (0.995f - (float) timer / (float) totalTime), 0, 4.9f);

        newState = newState.setValue(AgingBarrelBlock.PROGRESS, progress);
        if (newState != oldState) {
            getLevel().setBlockAndUpdate(getBlockPos(), newState);
            sendData();
            if (newState.getValue(AgingBarrelBlock.PROGRESS) != 0)
                DestroySoundEvents.AGING_BARREL_BALLOON.playOnServer(level, getBlockPos());
            if (timer < 0)
                DestroySoundEvents.AGING_BARREL_OPEN.playOnServer(level, getBlockPos());
        }
    }

    /**
 * @return {@code true} if the barrel was successfully opened (sealed & finished).
*/
    public boolean tryOpen() {
        if (!hasLevel() || getLevel().isClientSide()) return false;
        if (timer == 0) {
            timer = -1;
            getLevel().setBlockAndUpdate(getBlockPos(), getBlockState().setValue(AgingBarrelBlock.IS_OPEN, true));
            onTimerChange();
            return true;
        }
        return false;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        containedFluidTooltip(tooltip, isPlayerSneaking, tank.getCapability());
        if (timer >= 0) {
            int percent = timer == 0 ? 100 : (int) ((0.995 - (timer / (float) totalTime)) * 100);
            DestroyLang.translate("tooltip.aging_barrel.progress", percent + "%")
                .style(ChatFormatting.WHITE)
                .forGoggles(tooltip);
        }
        return true;
    }

    public SmartFluidTank getTank() {
        return tank.getPrimaryHandler();
    }

    /** Fluid to render in-world when the barrel is open.*/
    public TankSegment getTankToRender() {
        return tank.getPrimaryTank();
    }
}
