package petrolpark.mc.destroy.content.processing.glassblowing;

import java.util.List;

import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.recipe.RecipeFinder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.content.processing.ISpecialAirCurrentBehaviour;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;

/**
 * Block Entity behind the Blowpipe — holds one {@link FluidTank} of molten glass, tracks a
 * {@link GlassblowingRecipe}, and advances a blowing "progress" counter either via encased-fan
 * {@link AirCurrent} ticks (when placed as a block with a fan pointing at it) or via the
 * Blowpipe {@code Item}'s player-use (latter deferred — see BlowpipeItem port). When progress
 * reaches {@link #BLOWING_DURATION}, the tank empties, the recipe's rollable result is spawned
 * as an ItemEntity in front, and the {@link DestroyAdvancementTrigger#BLOWPIPE} advancement
 * triggers.
*/
public class BlowpipeBlockEntity extends SmartBlockEntity {

    public static final int TANK_CAPACITY = 1000;
    public static final int BLOWING_DURATION = 100;
    public static final float BLOWING_TIME_PROPORTION = 0.875f;

    private static final Object recipeCacheKey = new Object();

    public FluidTank tank;
    public int luminosity;

    protected DestroyAdvancementBehaviour advancementBehaviour;

    protected ResourceLocation recipeId;
    protected GlassblowingRecipe recipe;
    public int progress = 0;
    public int progressLastTick = 0;

    public BlowpipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        recipe = null;
        tank = new FluidTank(TANK_CAPACITY) {
            @Override
            protected void onContentsChanged() {
                onFluidStackChanged();
            }
        };
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new GlassblowingBehaviour());
        advancementBehaviour = new DestroyAdvancementBehaviour(this, DestroyAdvancementTrigger.BLOWPIPE);
        behaviours.add(advancementBehaviour);
    }

    public void onFluidStackChanged() {
        int newLuminosity = tank.getFluid().getFluid().getFluidType().getLightLevel();
        if (newLuminosity != luminosity && getLevel().isClientSide()) {
            luminosity = newLuminosity;
            sendData();
        }
    }

    public FluidStack getFluid() {
        return tank.getFluid();
    }

    public GlassblowingRecipe getRecipe() {
        if (recipeId == null) return null;
        recipe = getRecipe(getLevel(), recipeId);
        return recipe;
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        readBlowing(tag, registries);
        int oldLuminosity = luminosity;
        luminosity = tag.getInt("Luminosity");
        if (oldLuminosity != luminosity) {
            level.getChunkSource()
                .getLightEngine()
                .checkBlock(getBlockPos());
        }
    }

    /**
 * Read the "blowing state" (tank + recipeId + progress) sub-tree from NBT. Broken out so
 * the Blowpipe Item's {@code readFromNBT}-equivalent can share this logic.
*/
    public void readBlowing(CompoundTag tag, HolderLookup.Provider registries) {
        tank.readFromNBT(registries, tag.getCompound("Tank"));
        String recipeStr = tag.getString("Recipe");
        recipeId = recipeStr.isEmpty() ? null : ResourceLocation.parse(recipeStr);
        progress = tag.getInt("Progress");
        progressLastTick = tag.getInt("LastProgress");
    }

    public static GlassblowingRecipe readRecipe(Level level, CompoundTag tag) {
        String recipeStr = tag.getString("Recipe");
        if (recipeStr.isEmpty()) return null;
        return getRecipe(level, ResourceLocation.parse(recipeStr));
    }

    /**
 * Stack-aware recipe lookup. used by {@link BlowpipeItemRenderer}
 * to fetch the selected recipe for rendering the in-hand glass-blob animation.
*/
    public static GlassblowingRecipe readRecipeFromStack(Level level, net.minecraft.world.item.ItemStack stack) {
        ResourceLocation recipeId = stack.get(petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_RECIPE);
        if (recipeId == null) return null;
        return getRecipe(level, recipeId);
    }

    
    protected static GlassblowingRecipe getRecipe(Level level, ResourceLocation recipeId) {
        return RecipeFinder.get(recipeCacheKey, level, rh -> rh.value() instanceof GlassblowingRecipe).stream()
            .filter(rh -> rh.id().equals(recipeId))
            .map(rh -> (GlassblowingRecipe) rh.value())
            .findFirst()
            .orElse(null);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        writeBlowing(tag, registries, false);
        tag.putInt("Luminosity", luminosity);
    }

    /**
 * Write the "blowing state" sub-tree. Use {@link
 * #writeToItem} for BE→ItemStack state copy instead.
*/
    public void writeBlowing(CompoundTag tag, HolderLookup.Provider registries, boolean forItem) {
        if (recipeId != null) tag.putString("Recipe", recipeId.toString());
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Progress", progress);
        tag.putInt("LastProgress", progressLastTick);
        // forItem=true: kept as a parameter for API compatibility; the Item-side state copy now
        // goes through writeToItem — no NBT round-trip needed.
    }

    // ============================================================
    // ItemStack ↔ BlockEntity state bridge (closes 6 inline stubs in BlowpipeBlock)
    // ============================================================

    /** Called
 * by {@link BlowpipeBlock#setPlacedBy} when a Blowpipe Item is placed as a block, so the
 * pipe preserves its tank contents, selected recipe, and in-progress counter across the
 * item→block transition.
 *
 * <p>The sixth BLOWPIPE_BLOWING flag is Item-only (tracks whether the player is currently
 * right-holding to blow) and is not restored to the BE — the BE's air-current driven
 * progress is independent.</p>
*/
    public void readFromItem(net.minecraft.world.item.ItemStack stack) {
        FluidStack tankFluid = stack.getOrDefault(
            petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_TANK, FluidStack.EMPTY);
        tank.setFluid(tankFluid.copy());
        recipeId = stack.get(petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_RECIPE);
        progress = stack.getOrDefault(petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
        progressLastTick = stack.getOrDefault(
            petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_LAST_PROGRESS, 0);
    }

    /**
 *
 * <p>Also writes {@link petrolpark.mc.destroy.DestroyDataComponents#BLOWPIPE_REQUIRED_FLUID}
 * from the selected recipe's first fluid ingredient, so the Item's
 * {@link BlowpipeItem#getFluidIngredient} (needed by
 * {@link SelectGlassblowingRecipeC2SPacket} server-side validation + in-hand renderer) can
 * light up without needing to re-read the recipe.</p>
*/
    public void writeToItem(net.minecraft.world.item.ItemStack stack) {
        stack.set(petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_TANK, tank.getFluid().copy());
        if (recipeId != null) {
            stack.set(petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_RECIPE, recipeId);
        }
        stack.set(petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_PROGRESS, progress);
        stack.set(petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_LAST_PROGRESS, progressLastTick);
        GlassblowingRecipe r = getRecipe();
        if (r != null && !r.getFluidIngredients().isEmpty()) {
            stack.set(petrolpark.mc.destroy.DestroyDataComponents.BLOWPIPE_REQUIRED_FLUID,
                r.getFluidIngredients().get(0));
        }
    }

    @Override
    public void tick() {
        super.tick();

        if ((float) progress / (float) BLOWING_DURATION > BLOWING_TIME_PROPORTION && progress < BLOWING_DURATION) progress++;

        boolean send = progress != progressLastTick;
        progressLastTick = progress;

        if (progress >= BLOWING_DURATION) {
            tank.setFluid(FluidStack.EMPTY);
            progress = progressLastTick = 0;
            advancementBehaviour.awardDestroyAdvancement(DestroyAdvancementTrigger.BLOWPIPE);
            send = true;
            // Uses the vanilla BlockStateProperties.FACING property.
            Direction facing = getBlockState().getValue(BlockStateProperties.FACING);
            Vec3 itemPos = Vec3.atCenterOf(getBlockPos().relative(facing)).subtract(Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5f));
            getLevel().addFreshEntity(new ItemEntity(getLevel(), itemPos.x(), itemPos.y(), itemPos.z(), getRecipe().getRollableResultsAsItemStacks().get(0)));
        }

        if (send) notifyUpdate();
    }

    @Override
    protected AABB createRenderBoundingBox() {
        // Same substitution as tick() — uses the vanilla BlockStateProperties.FACING property.
        return new AABB(worldPosition).expandTowards(Vec3.atLowerCornerOf(getBlockState().getValue(BlockStateProperties.FACING).getNormal()));
    }

    /**
 * Fan-driven air-current consumer. Each tick the Create fan pointed at this BE emits an
 * {@link AirCurrent} event; this behaviour increments the blowing progress when the fan
 * is pushing along the BE's facing direction and the tank has fluid. The AIR_CURRENT
 * cooldown phase (progress &gt; BLOWING_TIME_PROPORTION) ignores the fan — those final
 * ticks are the "cooling" pause before the glass finishes.
*/
    public class GlassblowingBehaviour extends TransportedItemStackHandlerBehaviour implements ISpecialAirCurrentBehaviour {

        public GlassblowingBehaviour() {
            super(BlowpipeBlockEntity.this, (f, s) -> {});
        }

        @Override
        public void tickAir(AirCurrent airCurrent, FanProcessingType processingType) {
            if (getRecipe() != null && !tank.isEmpty() && (float) progress / (float) BLOWING_DURATION < BLOWING_TIME_PROPORTION
                && airCurrent.direction == getBlockState().getValue(BlockStateProperties.FACING) && airCurrent.pushing) {
                progress++;
            }
        }
    }
}
