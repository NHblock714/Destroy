package petrolpark.mc.destroy.content.processing.phytomining;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import petrolpark.mc.destroy.DestroyAdvancementTrigger;

/**
 * Bone meal which first tries the {@link CropMutation} path on the clicked crop, consuming one
 * item if a mutation fires, and otherwise falls back to vanilla's growCrop / growWaterPlant.
 */
public class HyperaccumulatingFertilizerItem extends BoneMealItem {

    public HyperaccumulatingFertilizerItem(Properties properties) {
        super(properties);
        DispenserBlock.registerBehavior(this, new HyperaccumulatingFertilizerDispenserBehaviour());
    }

    /**
 * Try growing the crop at {@code cropPos} via a {@link CropMutation} recipe. Returns whether a
 * mutation fired (and thus whether the caller should consume a fertilizer item).
*/
    private static boolean grow(Level level, BlockPos cropPos) {
        BlockState cropState = level.getBlockState(cropPos);
        Block cropBlock = cropState.getBlock();
        BlockPos potentialOrePos = cropPos.below(2);
        BlockState potentialOreState = level.getBlockState(potentialOrePos);

        if (cropBlock instanceof BonemealableBlock && cropState.is(BlockTags.CROPS)) {
            CropMutation mutation = CropMutation.getMutation(cropState, potentialOreState);
            if (mutation.isSuccessful()) {
                if (level.isClientSide()) {
                    addGrowthParticles(level, cropPos, 100);
                    return true;
                }
                level.setBlockAndUpdate(cropPos, mutation.getResultantCropSupplier().get());
                if (mutation.isOreSpecific()) {
                    level.setBlockAndUpdate(potentialOrePos, mutation.getResultantBlockUnder(potentialOreState));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation") // growCrop is the deprecated shorthand for applyBonemeal with a null Player
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        if (grow(level, pos)) {
            Player player = context.getPlayer();
            if (!level.isClientSide() && player != null && !player.isCreative()) {
                stack.shrink(1);
            }
            DestroyAdvancementTrigger.HYPERACCUMULATE.award(level, player);
            return InteractionResult.SUCCESS;
        }
        if (BoneMealItem.growCrop(stack, level, pos)
            || BoneMealItem.growWaterPlant(stack, level, pos, (Direction) null)) {
            if (!level.isClientSide()) level.levelEvent(1505, pos, 0);
            return InteractionResult.SUCCESS;
        }
        return super.useOn(context);
    }

    public static class HyperaccumulatingFertilizerDispenserBehaviour extends OptionalDispenseItemBehavior {

        @Override
        @SuppressWarnings("deprecation")
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            Level level = source.level();
            BlockPos blockPos = source.pos().relative(source.state().getValue(DispenserBlock.FACING));
            if (grow(level, blockPos)) {
                stack.shrink(1);
                setSuccess(true);
            } else if (BoneMealItem.growCrop(stack, level, blockPos)
                || BoneMealItem.growWaterPlant(stack, level, blockPos, (Direction) null)) {
                setSuccess(true);
                if (!level.isClientSide()) level.levelEvent(1505, blockPos, 0);
            } else {
                setSuccess(false);
            }
            return stack;
        }
    }
}
