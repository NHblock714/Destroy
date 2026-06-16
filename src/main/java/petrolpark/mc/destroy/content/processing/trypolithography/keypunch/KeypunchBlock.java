package petrolpark.mc.destroy.content.processing.trypolithography.keypunch;

import com.mojang.serialization.MapCodec;
import com.petrolpark.compat.create.core.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.simpleRelays.ICogWheel;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyBlockEntityTypes;

/**
 * The Keypunch block — kinetic belt-processing station that punches {@code CircuitMaskItem}s
 * passing underneath a Create belt. {@link HorizontalKineticBlock} (sets facing on place) +
 * {@link ICogWheel} (rotates in-place on Y-axis) + {@link IBE}<{@link KeypunchBlockEntity}> (BE
 * link).
*/
public class KeypunchBlock extends HorizontalKineticBlock implements IBE<KeypunchBlockEntity>, ICogWheel {

    public static final MapCodec<KeypunchBlock> CODEC = simpleCodec(KeypunchBlock::new);

    public static final ResourceLocation NAME_LIST_ID = Destroy.asResource("keypunch");

    public KeypunchBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<KeypunchBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        AbstractRememberPlacerBehaviour.setPlacedBy(level, pos, placer);
    }

    
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return onBlockEntityUse(level, pos, be -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                displayScreen(be, player);
            }
            return InteractionResult.SUCCESS;
        });
    }

    
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public Axis getRotationAxis(BlockState state) {
        return Axis.Y;
    }

    
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /**
 * Client-only screen opener — opens {@link KeypunchScreen}. The guard against
 * a null blockState is kept to avoid edge-case crashes if a block entity
 * is unloaded between the right-click and the class-load.
*/
    protected void displayScreen(KeypunchBlockEntity be, Player player) {
        if (be.getBlockState() == null) return;
        ClientScreenOpener.open(be);
    }

    /** Loaded only on physical client.*/
    public static final class ClientScreenOpener {
        private ClientScreenOpener() {}
        public static void open(KeypunchBlockEntity be) {
            ScreenOpener.open(new KeypunchScreen(be));
        }
    }

    @Override
    public Class<KeypunchBlockEntity> getBlockEntityClass() {
        return KeypunchBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends KeypunchBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.KEYPUNCH.get();
    }

}
