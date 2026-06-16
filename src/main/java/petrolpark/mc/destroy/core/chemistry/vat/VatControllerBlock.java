package petrolpark.mc.destroy.core.chemistry.vat;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition.Builder;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;

/**
 * Controller block of a {@link Vat} multi-block chemistry reactor — marker block placed at one
 * corner of the reactor's inner cavity that claims the rest of the Vat via the {@link Vat} class.
 *
 * <ul>
 * <li>Right-click interaction — opens the {@code VatScreen} GUI and triggers
 * {@code tryMakeVat()}.</li>
 * <li>{@code displayScreen()} — client-side screen opener.</li>
 * <li>{@code getTankForMixtureStorageItems()} — routes through
 * {@code ISpecialMixtureContainerBlock} + {@code IMixtureStorageItem}.</li>
 * <li>{@code onPlace()} auto-tryMakeVat + {@code setPlacedBy()}
 * {@code AbstractRememberPlacerBehaviour} placer attribution.</li>
 * </ul>
*/
public class VatControllerBlock extends HorizontalDirectionalBlock implements IBE<VatControllerBlockEntity>, IWrenchable {

    public static final MapCodec<VatControllerBlock> CODEC = simpleCodec(VatControllerBlock::new);

    public VatControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<VatControllerBlock> codec() {
        return CODEC;
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.FAIL;
    }

    /** Without this method the player's right-click on the
 * controller never triggers Vat formation (the reactor cannot be assembled, regardless of
 * which wall blocks are used) because {@link VatControllerBlockEntity#tryMakeVat} is never
 * called.
 *
 * <ul>
 * <li>{@code Block.use(...)} signature was split into {@code useItemOn(ItemStack, ...)}
 * and {@code useWithoutItem(...)}. Vat-formation right-click is independent of held
 * item, so the empty-hand variant {@code useWithoutItem} is overridden. Players holding
 * an item still trigger this path because {@code useItemOn} default returns
 * {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} which falls through to {@code useWithoutItem}.</li>
 * <li>{@code AllSoundEvents.CONFIRM/DENY} via Create's sound API for click feedback;
 * Create 1.21 keeps this API at the same path.</li>
 * <li>{@code AbstractRememberPlacerBehaviour.setPlacedBy} placer-tracking provides Vat
 * owner attribution.</li>
 * </ul>
*/
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, net.minecraft.core.BlockPos pos,
                                            net.minecraft.world.entity.player.Player player,
                                            net.minecraft.world.phys.BlockHitResult hit) {
        return onBlockEntityUse(level, pos, be -> {
            if (be.getVatOptional().isPresent()) {
                // Vat already formed → open VatScreen (full molecule list + 3D vat preview +
                // tank views). Pure client-side dispatch — no DistExecutor needed in 1.21,
                // just gate on isClientSide.
                if (level.isClientSide()) displayScreen(be, player);
            } else if (!level.isClientSide()) {
                boolean success = be.tryMakeVat();
                net.minecraft.sounds.SoundEvent sound = success
                    ? com.simibubi.create.AllSoundEvents.CONFIRM.getMainEvent()
                    : com.simibubi.create.AllSoundEvents.DENY.getMainEvent();
                level.playSound(null, pos.getX(), pos.getY(), pos.getZ(), sound,
                    net.minecraft.sounds.SoundSource.BLOCKS, 1f, 1f);
            }
            return InteractionResult.SUCCESS;
        });
    }

    
    
    protected void displayScreen(VatControllerBlockEntity be, net.minecraft.world.entity.player.Player player) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != net.neoforged.api.distmarker.Dist.CLIENT) return;
        if (!(player instanceof net.minecraft.client.player.LocalPlayer)) return;
        if (be.getBlockState() == null) return;
        ClientScreenOpener.open(be);
    }

    /** Loaded only on physical client.*/
    public static final class ClientScreenOpener {
        private ClientScreenOpener() {}
        public static void open(VatControllerBlockEntity be) {
            net.createmod.catnip.gui.ScreenOpener.open(new VatScreen(be));
        }
    }

    /** Mostly a quality-of-life
 * convenience; the right-click path above also works for incremental wall-block placement.
*/
    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(BlockState state, Level level, net.minecraft.core.BlockPos pos,
                        BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        withBlockEntityDo(level, pos, VatControllerBlockEntity::tryMakeVat);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, net.minecraft.core.BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public Class<VatControllerBlockEntity> getBlockEntityClass() {
        return VatControllerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends VatControllerBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.VAT_CONTROLLER.get();
    }
}
