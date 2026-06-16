package petrolpark.mc.destroy.core.chemistry.vat.observation.colorimeter;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;

/**
 * Colorimeter block — observes Vat mixture + reports selected molecule concentration via
 * redstone output. HorizontalDirectionalBlock with FACING + POWERED + BLUSHING properties.
 * BLUSHING auto-updates based on adjacent {@link AllBlocks#SMART_OBSERVER}.
 *
 * <ul>
 * <li>{@code use()} right-click GUI interaction — requires {@link ColorimeterBlockEntity}
 * full observation pipeline + {@code ColorimeterScreen} GUI port (deferred).</li>
 * <li>{@code getDirectSignal / getSignal} redstone output — requires
 * {@code ColorimeterBlockEntity.redstoneMonitor} (stubbed BE has no monitor).</li>
 * <li>{@code neighborChanged(...)} BE.onNeighborChanged hook — BE stub has no handler.</li>
 * <li>{@code openScreen} client method — requires ColorimeterScreen port.</li>
 * </ul>
 *
 * <p>Retained: HorizontalDirectionalBlock base + FACING/POWERED/BLUSHING properties + IBE wiring
 * + IWrenchable + {@code checkForSmartObserver} BLUSHING update logic + {@code updateShape /
 * getStateForPlacement}. Enough for block placement + redstone-indicator blockstate changes +
 * colorimeter Ponder scene's {@code cycleBlockProperty(POWERED)} call to work.</p>
*/
public class ColorimeterBlock extends HorizontalDirectionalBlock implements IBE<ColorimeterBlockEntity>, IWrenchable {

    public static final MapCodec<ColorimeterBlock> CODEC = simpleCodec(ColorimeterBlock::new);

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    public static final BooleanProperty BLUSHING = BooleanProperty.create("blushing");

    public ColorimeterBlock(Properties properties) {
        super(properties);
        registerDefaultState(
            defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(BLUSHING, false)
                .setValue(POWERED, false)
        );
    }

    @Override
    protected MapCodec<ColorimeterBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING).add(BLUSHING).add(POWERED);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        return checkForSmartObserver(state, level, currentPos);
    }

    /**
 * {@link ColorimeterBlockEntity#onNeighborChanged} schedules a vat-rebind on next tick when
 * the FACING-direction neighbor changes (e.g. adjacent VatSide was placed/broken).
*/
    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, net.minecraft.world.level.Level level, BlockPos pos, Block block,
                                BlockPos neighborPos, boolean isMoving) {
        withBlockEntityDo(level, pos, cbe -> cbe.onNeighborChanged(neighborPos));
        super.neighborChanged(state, level, pos, block, neighborPos, isMoving);
    }

    /** Other faces report 0.
*/
    @Override
    @SuppressWarnings("deprecation")
    public int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction direction) {
        if (direction != state.getValue(FACING).getOpposite()) return 0;
        return getBlockEntityOptional(level, pos).map(c -> c.redstoneMonitor.getStrength()).orElse(0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, Direction side) {
        return getBlockEntityOptional(level, pos).map(c -> c.redstoneMonitor.getStrength()).orElse(0);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    /**
 * Gathers the set of molecules available for observation from the adjacent Vat's liquid + gas
 * mixtures (IMixtureStorageItem scan deferred · not yet ported in 1.21).
*/
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, net.minecraft.world.level.Level level,
                                                                   BlockPos pos, net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        return onBlockEntityUse(level, pos, be -> {
            java.util.List<net.neoforged.neoforge.fluids.FluidStack> fluids = new java.util.ArrayList<>();
            java.util.Set<petrolpark.mc.destroy.chemistry.legacy.LegacySpecies> species = new java.util.HashSet<>();
            species.add(null); // Null-sentinel entry represents "no species selected" in the UI.

            be.getVatOptional().ifPresent(vat -> {
                fluids.add(vat.getLiquidTankContents());
                fluids.add(vat.getGasTankContents());
            });
            // also collect species from any windowed fluid tank the colorimeter is
            // looking into (Create FluidTank / create_connected FluidVessel / addons). Without
            // this, the GUI's molecule selector shows an empty list when the colorimeter faces
            // a tank instead of a vat side, so the user can't pick a molecule to observe.
            be.getWindowedFluidHandler().ifPresent(handler -> {
                for (int i = 0; i < handler.getTanks(); i++) {
                    fluids.add(handler.getFluidInTank(i));
                }
            });
            // Placeholder: IMixtureStorageItem player-inventory scan deferred (storage-item subsystem
            // partially implemented). It would scan held mixture-carrying items and add their
            // species. Skipping this reduces the species list but the two Vat tank scans still
            // provide the primary options.

            for (net.neoforged.neoforge.fluids.FluidStack fluid : fluids) {
                if (petrolpark.mc.destroy.DestroyFluids.isMixture(fluid)) {
                    species.addAll(petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture.readNBT(
                            petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture::new,
                            fluid.getOrDefault(petrolpark.mc.destroy.DestroyDataComponents.MIXTURE, new net.minecraft.nbt.CompoundTag()))
                        .getContents(false));
                }
            }

            if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                openScreen(be, species, player);
            }
            return net.minecraft.world.InteractionResult.SUCCESS;
        });
    }

    /** Item-in-hand right-click → fall through to useWithoutItem for GUI open (wrench preempts).*/
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack, BlockState state,
                                                                   net.minecraft.world.level.Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.InteractionHand hand,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    
    public void openScreen(ColorimeterBlockEntity be,
                           java.util.Set<petrolpark.mc.destroy.chemistry.legacy.LegacySpecies> species,
                           net.minecraft.world.entity.player.Player player) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        if (player instanceof net.minecraft.client.player.LocalPlayer) {
            ClientScreenOpener.open(be, species);
        }
    }

    /** Loaded only on physical client.*/
    public static final class ClientScreenOpener {
        private ClientScreenOpener() {}
        public static void open(ColorimeterBlockEntity be,
                                java.util.Set<petrolpark.mc.destroy.chemistry.legacy.LegacySpecies> species) {
            net.createmod.catnip.gui.ScreenOpener.open(new ColorimeterScreen(be, new java.util.ArrayList<>(species)));
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return checkForSmartObserver(defaultBlockState().setValue(FACING, context.getHorizontalDirection()), context.getLevel(), context.getClickedPos());
    }

    public BlockState checkForSmartObserver(BlockState colorimeter, LevelAccessor level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).getBlock().equals(AllBlocks.SMART_OBSERVER.get())) return colorimeter.setValue(BLUSHING, true);
        }
        return colorimeter.setValue(BLUSHING, false);
    }

    @Override
    public Class<ColorimeterBlockEntity> getBlockEntityClass() {
        return ColorimeterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ColorimeterBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.COLORIMETER.get();
    }
}
