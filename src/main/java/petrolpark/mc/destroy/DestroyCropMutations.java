package petrolpark.mc.destroy;

import com.simibubi.create.AllBlocks;

import net.minecraft.world.level.block.Blocks;
import petrolpark.mc.destroy.content.processing.phytomining.CropMutation;

/**
 * The {@link CropMutation} table used by the Hyperaccumulating Fertilizer.
 *
 * <p>Every argument is a supplier, but {@link CropMutation}'s constructor immediately keys
 * {@link CropMutation#MUTATIONS} on {@code startCrop.get()}, so start crops have to resolve as the
 * constants are built; all of them are vanilla {@link Blocks}. The resulting crop and the ore
 * beneath it are only read when a mutation is looked up or applied.</p>
 */
public class DestroyCropMutations {

    public static final CropMutation

    GOLDEN_CARROTS = new CropMutation(() -> Blocks.CARROTS, () -> DestroyBlocks.GOLDEN_CARROTS.getDefaultState(), () -> Blocks.GOLD_ORE),
    GOLDEN_CARROTS_DEEPSLATE = new CropMutation(() -> Blocks.CARROTS, () -> DestroyBlocks.GOLDEN_CARROTS.getDefaultState(), () -> Blocks.DEEPSLATE_GOLD_ORE),
    GOLDEN_CARROTS_NETHER = new CropMutation(() -> Blocks.CARROTS, () -> DestroyBlocks.GOLDEN_CARROTS.getDefaultState(), () -> Blocks.NETHER_GOLD_ORE),
    HEFTY_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.HEFTY_BEETROOT.getDefaultState()),
    COAL_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.COAL_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.COAL_ORE),
    COAL_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.COAL_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.DEEPSLATE_COAL_ORE),
    COPPER_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.COPPER_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.COPPER_ORE),
    COPPER_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.COPPER_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.DEEPSLATE_COPPER_ORE),
    DIAMOND_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.DIAMOND_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.DIAMOND_ORE),
    DIAMOND_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.DIAMOND_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.DEEPSLATE_DIAMOND_ORE),
    EMERALD_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.EMERALD_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.EMERALD_ORE),
    EMERALD_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.EMERALD_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.DEEPSLATE_EMERALD_ORE),
    FLUORITE_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.FLUORITE_INFUSED_BEETROOT.getDefaultState(), DestroyBlocks.FLUORITE_ORE),
    FLUORITE_INFUSED_BEETROOT_END = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.FLUORITE_INFUSED_BEETROOT.getDefaultState(), DestroyBlocks.END_FLUORITE_ORE),
    FLUORITE_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.FLUORITE_INFUSED_BEETROOT.getDefaultState(), DestroyBlocks.DEEPSLATE_FLUORITE_ORE),
    GOLD_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.GOLD_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.GOLD_ORE),
    GOLD_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.GOLD_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.DEEPSLATE_GOLD_ORE),
    GOLD_INFUSED_BEETROOT_NETHER = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.GOLD_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.NETHER_GOLD_ORE),
    IRON_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.IRON_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.IRON_ORE),
    IRON_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.IRON_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.DEEPSLATE_IRON_ORE),
    LAPIS_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.LAPIS_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.LAPIS_ORE),
    LAPIS_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.LAPIS_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.DEEPSLATE_LAPIS_ORE),
    NICKEL_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.NICKEL_INFUSED_BEETROOT.getDefaultState(), DestroyBlocks.NICKEL_ORE),
    NICKEL_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.NICKEL_INFUSED_BEETROOT.getDefaultState(), DestroyBlocks.DEEPSLATE_NICKEL_ORE),
    NETHER_CROCOITE_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, DestroyBlocks.NETHER_CROCOITE_INFUSED_BEETROOT::getDefaultState, DestroyBlocks.NETHER_CROCOITE_ORE),
    QUARTZ_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, DestroyBlocks.QUARTZ_INFUSED_BEETROOT::getDefaultState, () -> Blocks.NETHER_QUARTZ_ORE),
    REDSTONE_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.REDSTONE_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.REDSTONE_ORE),
    REDSTONE_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.REDSTONE_INFUSED_BEETROOT.getDefaultState(), () -> Blocks.DEEPSLATE_REDSTONE_ORE),
    ZINC_INFUSED_BEETROOT = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.ZINC_INFUSED_BEETROOT.getDefaultState(), AllBlocks.ZINC_ORE),
    ZINC_INFUSED_BEETROOT_DEEPSLATE = new CropMutation(() -> Blocks.BEETROOTS, () -> DestroyBlocks.ZINC_INFUSED_BEETROOT.getDefaultState(), AllBlocks.DEEPSLATE_ZINC_ORE)
    ;

    /** Class-load trigger; field initializers do the actual registration.*/
    public static void register() {}
}
