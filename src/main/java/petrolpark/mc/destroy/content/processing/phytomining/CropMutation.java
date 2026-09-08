package petrolpark.mc.destroy.content.processing.phytomining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import petrolpark.mc.library.PetrolparkTags;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Hyperaccumulating-Fertilizer-driven crop mutation. Start crop → end crop, optionally gated by
 * a specific ore two blocks beneath the crop.
*/
public class CropMutation {

    public static final Map<Block, List<CropMutation>> MUTATIONS = new HashMap<>();

    private Supplier<Block> startCrop;
    private Supplier<BlockState> endCrop;
    private boolean oreSpecific;
    @Nullable private Supplier<Block> ore;
    private boolean successful;

    /**
     * A mutation with no ore gating. {@link #getMutation} still prefers an ore-specific mutation
     * for the same start crop whenever that ore is the block beneath the Farmland.
     * @param startCrop the crop which mutates
     * @param endCrop the state it becomes
     */
    public CropMutation(Supplier<Block> startCrop, Supplier<BlockState> endCrop) {
        this.startCrop = startCrop;
        this.endCrop = endCrop;
        this.oreSpecific = false;
        this.ore = null;
        this.successful = true;
        register();
    }

    /**
     * A mutation which only happens if the given ore is the block beneath the Farmland.
     */
    public CropMutation(Supplier<Block> startCrop, Supplier<BlockState> endCrop, Supplier<Block> ore) {
        this.startCrop = startCrop;
        this.endCrop = endCrop;
        this.oreSpecific = true;
        this.ore = ore;
        this.successful = true;
        register();
    }

    /** Null-object "no mutation available" sentinel returned by {@link #getMutation}.*/
    private CropMutation(BlockState crop) {
        this.endCrop = () -> crop;
        this.oreSpecific = false;
        this.successful = false;
    }

    private void register() {
        MUTATIONS.computeIfAbsent(startCrop.get(), k -> new ArrayList<>()).add(this);
    }

    public static CropMutation getMutation(BlockState cropBlockState, BlockState blockUnder) {
        Block cropBlock = cropBlockState.getBlock();
        CropMutation mutation = null;
        List<CropMutation> candidates = MUTATIONS.get(cropBlock);
        if (candidates != null) {
            for (CropMutation possible : candidates) {
                if (possible.oreSpecific) {
                    Supplier<Block> ore = possible.ore;
                    if (ore != null && blockUnder.is(ore.get())) {
                        mutation = possible;
                        break; // ore-specific mutations take priority
                    }
                } else {
                    mutation = possible;
                }
            }
        }
        return mutation == null ? new CropMutation(cropBlockState) : mutation;
    }

    public Supplier<Block> getStartCropSupplier() {
        return startCrop;
    }

    public Supplier<BlockState> getResultantCropSupplier() {
        return endCrop;
    }

    public Supplier<Block> getOreSupplier() {
        return ore;
    }

    public boolean isOreSpecific() {
        return oreSpecific;
    }

    /**
 * After consuming the ore block, replace it with the matching stone variant based on
 * {@code c:ores_in_ground/*} common tags.
*/
    public BlockState getResultantBlockUnder(BlockState ore) {
        if (!successful || !oreSpecific) return ore;
        if (ore.is(PetrolparkTags.commonBlockTag("ores_in_ground/deepslate")))
            return Blocks.DEEPSLATE.defaultBlockState();
        if (ore.is(PetrolparkTags.commonBlockTag("ores_in_ground/netherrack")))
            return Blocks.NETHERRACK.defaultBlockState();
        if (ore.is(PetrolparkTags.commonBlockTag("ores_in_ground/end_stone")))
            return Blocks.END_STONE.defaultBlockState();
        return Blocks.STONE.defaultBlockState();
    }

    public boolean isSuccessful() {
        return successful;
    }
}
