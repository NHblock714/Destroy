package petrolpark.mc.destroy.content.processing.moltenblock;

import petrolpark.mc.library.core.world.fluid.ICustomBlockStateFluid;
import com.simibubi.create.content.fluids.VirtualFluid;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyItems;

public class MoltenStainlessSteelFluid extends VirtualFluid implements ICustomBlockStateFluid {

    public MoltenStainlessSteelFluid(Properties properties, boolean source) {
        super(properties, source);
    }

    public static MoltenStainlessSteelFluid createSource(Properties properties) {
        return new MoltenStainlessSteelFluid(properties, true);
    }

    public static MoltenStainlessSteelFluid createFlowing(Properties properties) {
        return new MoltenStainlessSteelFluid(properties, false);
    }

    @Override
    public Item getBucket() {
        return DestroyItems.MOLTEN_STAINLESS_STEEL_BUCKET.get();
    }

    @Override
    public BlockState getBlockState() {
        return DestroyBlocks.MOLTEN_STAINLESS_STEEL.getDefaultState();
    }
}
