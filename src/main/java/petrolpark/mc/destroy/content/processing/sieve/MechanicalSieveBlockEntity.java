package petrolpark.mc.destroy.content.processing.sieve;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import petrolpark.mc.library.compat.create.core.data.recipe.firstTimeLucky.FTLRecipesBehaviour;
import petrolpark.mc.library.util.RecipeHelper;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.DestroyRecipeTypes;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;

/**
 * Mechanical Sieve BE — tracks ItemEntity instances that fall onto the sieve, processes each
 * according to its matching {@link SievingRecipe}. First-time-lucky rolls are delegated to the
 * {@link FTLRecipesBehaviour} (if a SIEVING recipe declares a lucky key).
*/
public class MechanicalSieveBlockEntity extends KineticBlockEntity {

    protected FTLRecipesBehaviour luckyBehaviour;
    protected DestroyAdvancementBehaviour advancementBehaviour;

    protected SievingRecipe lastRecipe;

    protected List<ProcessingItem> items;

    public MechanicalSieveBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        lastRecipe = null;
        items = new ArrayList<>();
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        luckyBehaviour = new FTLRecipesBehaviour(this,
            holder -> holder.value().getType() == DestroyRecipeTypes.SIEVING.getType());
        behaviours.add(luckyBehaviour);

        advancementBehaviour = new DestroyAdvancementBehaviour(this, DestroyAdvancementTrigger.MECHANICAL_SIEVE);
        behaviours.add(advancementBehaviour);

        behaviours.add(
            new DirectBeltInputBehaviour(this)
                .onlyInsertWhen(s -> s != Direction.DOWN)
                .setInsertionHandler((transportedStack, side, simulate) -> {
                    Vec3 loc = getBlockPos().getCenter().add(Vec3.atBottomCenterOf(side.getNormal()).scale(-4 / 16d));
                    if (side != Direction.UP) loc = loc.add(0d, 2 / 16d, 0d);
                    getLevel().addFreshEntity(new ItemEntity(getLevel(), loc.x, loc.y, loc.z, transportedStack.stack, 0d, 0d, 0d));
                    return ItemStack.EMPTY;
                })
        );
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        items.addAll(NBTHelper.readCompoundList(compound.getList("Items", Tag.TAG_COMPOUND), this::processItem));
    }

    @Override
    protected void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("Items", NBTHelper.writeCompoundList(items, item -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Entity", item.item.getUUID());
            tag.putInt("Time", item.processingTime);
            return tag;
        }));
    }

    public void beginProcessing(ItemEntity entity) {
        ProcessingItem item = processItem(entity);
        if (item != null) items.add(item);
    }

    @Override
    public void tick() {
        super.tick();

        Iterator<ProcessingItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            ProcessingItem item = iterator.next();

            if (item == null || !item.item.isAlive() || !item.item.blockPosition().equals(getBlockPos())) {
                iterator.remove();
                continue;
            }

            if (!isSpeedRequirementFulfilled()) continue;

            item.processingTime -= Math.abs(getSpeed());

            if (level.isClientSide()) {
                addParticles(item);
                continue;
            }

            if (item.processingTime < 0) {
                ItemEntity entity = item.item;
                entity.kill();
                for (ItemStack stack : RecipeHelper.rollResults(level.random, item.getRecipe(),
                        luckyBehaviour.getPlayer(), entity.getItem().getCount())) {
                    getLevel().addFreshEntity(new ItemEntity(getLevel(),
                        entity.getX() - 0.125d + level.random.nextDouble() * 0.25d,
                        getBlockPos().getY(),
                        entity.getZ() - 0.125d + level.random.nextDouble() * 0.25d,
                        stack, 0d, 0d, 0d));
                }
                iterator.remove();
                advancementBehaviour.awardDestroyAdvancement(DestroyAdvancementTrigger.MECHANICAL_SIEVE);
            }
        }
        sendData();
    }

    public class ProcessingItem {

        public final ItemEntity item;
        public int processingTime;
        private final SievingRecipe recipe;

        private ProcessingItem(ItemEntity item, int time, SievingRecipe recipe) {
            this.item = item;
            this.processingTime = time;
            this.recipe = recipe;
        }

        public SievingRecipe getRecipe() {
            return recipe;
        }
    }

    protected ProcessingItem processItem(ItemEntity entity, int processingTime) {
        if (entity == null) return null;
        SingleRecipeInput input = new SingleRecipeInput(entity.getItem());
        SievingRecipe recipe;
        if (lastRecipe != null && lastRecipe.matches(input, getLevel())) {
            recipe = lastRecipe;
        } else {
            Optional<RecipeHolder<SievingRecipe>> recipeOp = DestroyRecipeTypes.SIEVING.find(input, getLevel());
            if (recipeOp.isEmpty()) return null;
            recipe = recipeOp.get().value();
            lastRecipe = recipe;
        }
        return new ProcessingItem(entity, processingTime == -1 ? recipe.getProcessingDuration() : processingTime, recipe);
    }

    public ProcessingItem processItem(ItemEntity entity) {
        return processItem(entity, -1);
    }

    public ProcessingItem processItem(CompoundTag tag) {
        return processItem(
            getLevel().getEntities(EntityType.ITEM, new AABB(getBlockPos()),
                e -> e.getUUID().equals(tag.getUUID("Entity"))).stream().findFirst().orElse(null),
            tag.getInt("Time"));
    }

    public void addParticles(ProcessingItem item) {
        float speed = getSpeed();
        if (speed == 0f) return;
        if ((item.processingTime / (int) speed) % 20 == 0) {
            ItemEntity entity = item.item;
            level.addAlwaysVisibleParticle(new ItemParticleOption(ParticleTypes.ITEM, entity.getItem()),
                entity.getX(), entity.getY(), entity.getZ(),
                -0.1d + level.random.nextFloat() * 0.2d,
                level.random.nextFloat() * 0.3d,
                -0.1d + level.random.nextFloat() * 0.2d);
        }
    }
}
