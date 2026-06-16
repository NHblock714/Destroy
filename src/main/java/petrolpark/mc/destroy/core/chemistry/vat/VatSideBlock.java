package petrolpark.mc.destroy.core.chemistry.vat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.decoration.copycat.CopycatBlock;
import com.simibubi.create.content.decoration.copycat.CopycatBlockEntity;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;
import com.simibubi.create.foundation.block.IBE;

import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.core.chemistry.storage.IMixtureStorageItem;
import petrolpark.mc.destroy.core.chemistry.storage.ISpecialMixtureContainerBlock;
import petrolpark.mc.destroy.core.chemistry.vat.VatSideBlockEntity.DisplayType;
import petrolpark.mc.destroy.core.chemistry.vat.observation.RedstoneMonitorVatSideScreen;

/**
 * Side-cell block of a {@link Vat} multi-block — one block per face of the Vat's outer shell.
 * Hosts a {@link VatSideBlockEntity} that stores the {@link VatSideBlockEntity.DisplayType}
 * (NORMAL / PIPE / OPEN_VENT / CLOSED_VENT / THERMOMETER / BAROMETER / etc.).
 *
 * <ul>
 * <li>{@link CopycatBlock} base — visually wraps the source block's texture (so a glass-walled
 * vat has glass-textured sides, etc.).</li>
 * <li>{@link SpecialBlockItemRequirement} + {@link ISpecialMixtureContainerBlock} interfaces
 * — schematic build requirement (zero, since the side spawns from controller placement)
 * and test-tube fill/drain via vat's internal tanks.</li>
 * <li>{@link #useWithoutItem} / {@link #useItemOn} — opens
 * {@link RedstoneMonitorVatSideScreen} when the side is in BAROMETER/THERMOMETER mode.</li>
 * <li>{@link #onWrenched} / {@link #onSneakWrenched} — wrench cycles DisplayType (NORMAL →
 * OPEN_VENT/THERMOMETER → BAROMETER → NORMAL).</li>
 * <li>{@link #getDirectSignal} / {@link #getSignal} — redstone output from the side's
 * {@link VatSideBlockEntity#redstoneMonitor} (RedstoneQuantityMonitorBehaviour).</li>
 * <li>{@link #getDrops} — drops the wrapped material's loot, not the VatSideBlock itself.</li>
 * <li>{@link #getCloneItemStack} — middle-click yields the consumed wrapped item.</li>
 * <li>{@link #getTankForMixtureStorageItems} — routes to controller's gas/liquid tank based on
 * face hit position (test tube fill from outside the vat).</li>
 * </ul>
*/
public class VatSideBlock extends CopycatBlock implements SpecialBlockItemRequirement, ISpecialMixtureContainerBlock {

    public static final MapCodec<VatSideBlock> CODEC = simpleCodec(VatSideBlock::new);

    public VatSideBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<VatSideBlock> codec() {
        return CODEC;
    }

    /** 1.21 right-click handler — empty hand (or non-wrench / non-mixture-storage). Opens screen.*/
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        return onBlockEntityUse(level, pos, be -> {
            if (!(be instanceof VatSideBlockEntity vbe)) return InteractionResult.PASS;
            if (vbe.getDisplayType().quantityObserved.isPresent()) {
                if (level.isClientSide()) {
                    openScreenClient(vbe, player);
                }
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    /**
 * 1.21 right-click handler — with item. Wrench / test-tube cases delegate to the item's own
 * {@code Item.useOn} via {@link ItemInteractionResult#SKIP_DEFAULT_BLOCK_INTERACTION}.
 *
 * <p>Symptom: the first wrench right-click (NORMAL→THERMOMETER) worked, but the second
 * (THERMOMETER→BAROMETER) did not — it opened the GUI instead. Cause: returning
 * {@link ItemInteractionResult#PASS_TO_DEFAULT_BLOCK_INTERACTION} for the wrench/storage case
 * means "fall through to useWithoutItem". Once state=THERMOMETER, quantityObserved.isPresent()
 * is true, so useWithoutItem opens the GUI immediately and the wrench's Item.useOn never runs.</p>
 *
 * <p>The correct return is {@link ItemInteractionResult#SKIP_DEFAULT_BLOCK_INTERACTION} — skip
 * useWithoutItem so the chain flows to Item.useOn, where the wrench calls onWrenched to switch
 * display mode.</p>
*/
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                              BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        // Wrench OR mixture storage item → SKIP useWithoutItem so the item's own Item.useOn
        // can take over (wrench → onWrenched cycle DisplayType, storage → defaultUseOn fluid I/O).
        if (AllItems.WRENCH.isIn(stack) || IMixtureStorageItem.isHolding(player, hand)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        // Otherwise — same logic as useWithoutItem (open screen if observable display).
        return onBlockEntityUseItemOn(level, pos, be -> {
            if (!(be instanceof VatSideBlockEntity vbe)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (vbe.getDisplayType().quantityObserved.isPresent()) {
                if (level.isClientSide()) {
                    openScreenClient(vbe, player);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide());
            }
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        });
    }

    
    public static void openScreenClient(VatSideBlockEntity vbe, Player player) {
        if (net.neoforged.fml.loading.FMLEnvironment.dist != Dist.CLIENT) return;
        if (player instanceof LocalPlayer)
            ClientScreenOpener.open(vbe);
    }

    /** Loaded only on physical client.*/
    public static final class ClientScreenOpener {
        private ClientScreenOpener() {}
        public static void open(VatSideBlockEntity vbe) {
            ScreenOpener.open(new RedstoneMonitorVatSideScreen(vbe));
        }
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        // CopycatBlock parameterizes IBE<CopycatBlockEntity>; instanceof narrows here.
        return onBlockEntityUse(context.getLevel(), context.getClickedPos(), be -> handleWrench(be, context));
    }

    private static InteractionResult handleWrench(BlockEntity be, UseOnContext context) {
        if (!(be instanceof VatSideBlockEntity vatSide)) return InteractionResult.PASS;
        boolean blocked = !be.getLevel().getBlockState(be.getBlockPos().relative(context.getClickedFace())).isAir();
        switch (vatSide.getDisplayType()) {
            case PIPE: {
                return InteractionResult.PASS;
            } case NORMAL: {
                vatSide.setDisplayType(vatSide.direction == Direction.UP ? DisplayType.OPEN_VENT : (blocked ? DisplayType.THERMOMETER_BLOCKED : DisplayType.THERMOMETER));
                return InteractionResult.SUCCESS;
            } case THERMOMETER: {
                vatSide.setDisplayType(DisplayType.BAROMETER);
                return InteractionResult.SUCCESS;
            } case THERMOMETER_BLOCKED: {
                vatSide.setDisplayType(DisplayType.BAROMETER_BLOCKED);
                return InteractionResult.SUCCESS;
            } case BAROMETER: case BAROMETER_BLOCKED: case OPEN_VENT: case CLOSED_VENT: {
                vatSide.setDisplayType(DisplayType.NORMAL);
                return InteractionResult.SUCCESS;
            } default:
                return InteractionResult.PASS;
        }
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
    }

    /** Without this override the BE may
 * compute a signal strength internally but vanilla never asks for it → no redstone output.
*/
    @Override
    @SuppressWarnings("deprecation")
    public boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Only logs when {@code -Ddestroy.vatDebug=true}. Every
 * call is logged so "wire queries signal" can be correlated against "redstoneMonitor.tick
 * computes strength". If getSignal/getDirectSignal are never called, vanilla never queries the
 * block — likely {@link #isSignalSource} isn't being honored or the wire is too far / wrong
 * neighbor.
*/
    private static final boolean DEBUG_LOG_ENABLED =
        Boolean.parseBoolean(System.getProperty("destroy.vatDebug", "false"));

    @Override
    @SuppressWarnings("deprecation")
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // fixed direction check: vanilla passes direction = "from wire to source"
        // (so for a wire NORTH of vat side, vanilla calls with direction=SOUTH from wire's POV).
        // The vat side emits signal in its OUTWARD direction (vatSide.direction). So strong signal
        // should be returned when direction == vatSide.direction.getOpposite(), NOT == direction.
        // Old check was backwards; left getSignal direction-agnostic so weak signal still worked.
        int result = getBlockEntityOptional(level, pos)
            .map(be -> be instanceof VatSideBlockEntity vatSide
                && vatSide.direction != null
                && vatSide.direction == direction.getOpposite()
                ? vatSide.redstoneMonitor.getStrength() : 0)
            .orElse(0);
        if (DEBUG_LOG_ENABLED && result > 0) {
            petrolpark.mc.destroy.Destroy.LOGGER.info(
                "[VAT-DBG] VatSideBlock.getDirectSignal @ {}: direction={}, result={}",
                pos, direction, result);
        }
        return result;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        int result = getBlockEntityOptional(level, pos)
            .map(be -> be instanceof VatSideBlockEntity vatSide ? vatSide.redstoneMonitor.getStrength() : 0)
            .orElse(0);
        if (DEBUG_LOG_ENABLED) {
            petrolpark.mc.destroy.Destroy.LOGGER.info(
                "[VAT-DBG] VatSideBlock.getSignal @ {}: side={}, result={}",
                pos, side, result);
        }
        return result;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState state, Direction facing, BlockState facingState,
                                  LevelAccessor level, BlockPos currentPos, BlockPos facingPos) {
        // cast inside lambda. CopycatBlock parameterizes IBE<CopycatBlockEntity>, so the
        // BE param is CopycatBlockEntity; instanceof narrows to VatSideBlockEntity.
        withBlockEntityDo(level, currentPos, be -> {
            if (!(be instanceof VatSideBlockEntity vatSide)) return;
            vatSide.updateRedstoneInput();
            if (facing != vatSide.direction) return;
            vatSide.updateDisplayType(facingPos);
            vatSide.setPowerFromAdjacentBlock(facingPos);
        });
        return state;
    }

    /** NeoForge-specific neighbor-change hook (loose neighbors — fluid pipes, etc).*/
    @Override
    public void onNeighborChange(BlockState state, LevelReader level, BlockPos pos, BlockPos neighbor) {
        // Use a lambda to skip when level is read-only (LevelReader may not be a Level).
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof VatSideBlockEntity vatSide) || vatSide.direction == null) return;
        if (!pos.relative(vatSide.direction).equals(neighbor)) return;
        vatSide.updateDisplayType(neighbor);
        vatSide.setPowerFromAdjacentBlock(neighbor);
        super.onNeighborChange(state, level, pos, neighbor);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                BlockPos fromPos, boolean isMoving) {
        withBlockEntityDo(level, pos, be -> {
            if (be instanceof VatSideBlockEntity vatSide) vatSide.updateRedstoneInput();
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
        level.removeBlockEntity(pos);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder paramsBuilder) {
        BlockEntity be = paramsBuilder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (be instanceof VatSideBlockEntity vatSide) {
            BlockState wrapped = vatSide.getMaterial();
            ResourceKey<LootTable> lootKey = wrapped.getBlock().getLootTable();
            if (!lootKey.equals(BuiltInLootTables.EMPTY)) {
                LootParams params = paramsBuilder
                    .withParameter(LootContextParams.BLOCK_STATE, wrapped)
                    .create(LootContextParamSets.BLOCK);
                ServerLevel sLevel = params.getLevel();
                LootTable lootTable = sLevel.getServer().reloadableRegistries().getLootTable(lootKey);
                // Loot drops the wrapped material's items (e.g., glass shards w/o silk touch),
                // not the VatSideBlock itself.
                return lootTable.getRandomItems(params);
            }
        }
        return Collections.emptyList();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        // Block was placed without a placer (e.g., world-edit / dispenser / structure block).
        // The only legitimate way to spawn a VatSideBlock is via Vat assembly; if we got here
        // without a placer the block is orphan — replace with the wrapped material directly so
        // we don't leave an unpaired side without a controller.
        if (placer == null) {
            withBlockEntityDo(level, pos, be -> {
                if (be instanceof VatSideBlockEntity vatSide) {
                    level.setBlockAndUpdate(pos, vatSide.getMaterial());
                }
            });
        }
    }

    @Override
    public boolean canFaceBeOccluded(BlockState state, Direction face) {
        return true;
    }

    @Override
    public boolean canConnectTexturesToward(BlockAndTintGetter reader, BlockPos fromPos, BlockPos toPos, BlockState state) {
        return true;
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return getPickBlockItemStack(level, pos);
    }

    protected ItemStack getPickBlockItemStack(BlockGetter level, BlockPos pos) {
        if (getBlockEntity(level, pos) instanceof VatSideBlockEntity vatSide) return vatSide.getConsumedItem();
        return ItemStack.EMPTY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState, Direction dir) {
        // vanilla 1.21 made Block.skipRendering protected. Use the public BlockState
        // wrapper which calls the same method via the Block instance.
        return getMaterial(level, pos).skipRendering(neighborState, dir);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return getMaterial(level, pos).getShadeBrightness(level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return getMaterial(reader, pos).propagatesSkylightDown(reader, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getVisualShape(BlockState pState, BlockGetter pLevel, BlockPos pPos, CollisionContext pContext) {
        return Shapes.empty();
    }

    // getBlockEntityClass() override removed. CopycatBlock pins IBE<CopycatBlockEntity>
    // (concrete generic), so Java covariance forbids narrowing the return type here. We return
    // CopycatBlockEntity.class via the parent's default, and cast to VatSideBlockEntity inside
    // every withBlockEntityDo / onBlockEntityUse lambda.

    @Override
    public BlockEntityType<? extends CopycatBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.VAT_SIDE.get();
    }

    /**
 *
 * <p>{@link CopycatBlock#getTicker(Level, BlockState, BlockEntityType) CopycatBlock.getTicker}
 * unconditionally returns {@code null} (verified by bytecode disassembly: {@code aconst_null;
 * areturn}). VatSideBlock inherits this null ticker → vanilla never schedules
 * {@link VatSideBlockEntity#tick} on the side BE → all behaviours (including
 * {@link RedstoneQuantityMonitorBehaviour}, {@code tryInsertFluidInVat}, spout-tick decrement,
 * {@code initializationTicks} countdown) silently NEVER FIRE.</p>
 *
 * <ul>
 * <li>Redstone monitor strength stays 0 forever even though
 * {@link RedstoneQuantityMonitorBehaviour#tick} would correctly compute > 0 — because
 * tick never runs. {@link VatSideBlock#getSignal} is queried 826× by adjacent wires
 * per the diagnostic log, all returning 0 because {@code redstoneMonitor.oldStrength = 0}.</li>
 * <li>Side cell input buffer fills via pipe push but never drains to controller because
 * {@link VatSideBlockEntity#tryInsertFluidInVat} never runs (this is why earlier
 * chemistry tests showed fluid arriving via paths OTHER than addFluid — pipe→side route
 * was broken; only direct controller fills worked).</li>
 * <li>Spout fluid animation tick never decrements (visual stays).</li>
 * </ul>
 *
 * <p>Fix: standard Create {@code IBE.ticker(this, type)} pattern that delegates back to the
 * BE's {@code tick()} method. This is the canonical 1.21 ticker hook for any IBE-backed
 * SmartBlockEntity that needs server/client ticks.</p>
*/
    @Override
    @Nullable
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T>
        getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Use Create's standard SmartBlockEntityTicker directly (IBE.getTicker default does the
        // same thing internally). CopycatBlock's null-returning override is the bug being fixed.
        return new com.simibubi.create.foundation.blockEntity.SmartBlockEntityTicker<>();
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity blockEntity) {
        // Vat sides are placed as part of vat assembly (controller does the spawning). Schematic
        // build doesn't need to provide raw VatSideBlock items.
        return ItemRequirement.NONE;
    }

    @Override
    @Nullable
    public IFluidHandler getTankForMixtureStorageItems(IMixtureStorageItem item, Level level, BlockPos pos,
                                                       BlockState state, @Nullable Direction face,
                                                       Player player, InteractionHand hand,
                                                       ItemStack stack, boolean filling) {
        if (getBlockEntity(level, pos) instanceof VatSideBlockEntity vatSide) {
            VatControllerBlockEntity vatController = vatSide.getController();
            if (vatController != null) {
                // split fill vs drain routes:
                // - FILL must go through {@code vatController.addFluid} so VatFluidHandler.fill
                // does proper liquid/gas phase separation. Without this, gas-phase contents
                // of a test tube (e.g. methane) get dumped into the LIQUID tank as-is, never
                // reaching the gas mixture (symptom: the gas does not mix into the gas mixture).
                // - DRAIN routes to the liquid tank.
                // apply MixtureConversionRecipe for non-Mixture inputs (mirrors
                // VatTankWrapper.fill / VatSideFluidCapability used by the BLOCK FluidHandler
                // cap exposed to pipes/pumps). Without this, flask + vanilla water right-click on
                // vat side would skip the {@code minecraft:water → destroy:mixture(pure H₂O)}
                // conversion that the pump path triggers, and the fill silently fails (vat tanks
                // only accept Mixture stacks).
                final Object recipeCacheKey = new Object();
                return new net.neoforged.neoforge.fluids.capability.IFluidHandler() {
                    petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe lastRecipe = null;
                    @Override public int getTanks() { return 1; }
                    @Override public net.neoforged.neoforge.fluids.FluidStack getFluidInTank(int tank) {
                        return vatController.getLiquidTank().getFluidInTank(tank);
                    }
                    @Override public int getTankCapacity(int tank) {
                        return vatController.getLiquidTank().getTankCapacity(tank);
                    }
                    @Override public boolean isFluidValid(int tank, net.neoforged.neoforge.fluids.FluidStack stack) {
                        // Accept Mixture, OR any fluid that has a MixtureConversionRecipe (so
                        // vanilla water etc. can be poured in and auto-converted).
                        return petrolpark.mc.destroy.DestroyFluids.isMixture(stack)
                            || resolveConversion(stack) != null;
                    }
                    @Override public int fill(net.neoforged.neoforge.fluids.FluidStack resource, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
                        if (resource.isEmpty()) return 0;
                        net.neoforged.neoforge.fluids.FluidStack toFill = resource;
                        if (!petrolpark.mc.destroy.DestroyFluids.isMixture(resource)) {
                            petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe recipe =
                                resolveConversion(resource);
                            if (recipe == null) return 0;
                            toFill = recipe.apply(resource);
                        }
                        return vatController.addFluid(toFill, action);
                    }
                    private petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe resolveConversion(
                        net.neoforged.neoforge.fluids.FluidStack stack) {
                        if (lastRecipe != null && lastRecipe.getFluidIngredients().get(0).ingredient().test(stack)) {
                            return lastRecipe;
                        }
                        lastRecipe = com.simibubi.create.foundation.recipe.RecipeFinder
                            .get(recipeCacheKey, level,
                                rh -> rh.value().getType() == petrolpark.mc.destroy.DestroyRecipeTypes.MIXTURE_CONVERSION.getType())
                            .stream()
                            .map(rh -> (petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe) rh.value())
                            .filter(r -> r.getFluidIngredients().get(0).ingredient().test(stack))
                            .findFirst()
                            .orElse(null);
                        return lastRecipe;
                    }
                    @Override public net.neoforged.neoforge.fluids.FluidStack drain(net.neoforged.neoforge.fluids.FluidStack resource, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
                        net.neoforged.neoforge.fluids.FluidStack s = vatController.getLiquidTank().drain(resource, action);
                        afterLiquidDrain(s, action);
                        return s;
                    }
                    @Override public net.neoforged.neoforge.fluids.FluidStack drain(int maxDrain, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
                        net.neoforged.neoforge.fluids.FluidStack s = vatController.getLiquidTank().drain(maxDrain, action);
                        afterLiquidDrain(s, action);
                        return s;
                    }
                    /** Mirror {@code VatTankWrapper.updateVatGasVolume}: after the liquid
                     * level drops, expand the gas tank to fill the new headspace AND refresh
                     * cachedMixture so the next vat tick's writeback uses the post-drain
                     * chemistry. Without this synchronous refresh, cachedMixture stays at the
                     * pre-drain composition and setMixture's writeback at vat.getCapacity()
                     * restores the original liquid level — Create's whenFluidUpdates callback
                     * fires too lazily (gated on syncCooldown) to plug this hole on its own.*/
                    private void afterLiquidDrain(net.neoforged.neoforge.fluids.FluidStack drained, net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction action) {
                        if (action == net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE
                            && !drained.isEmpty()
                            && level != null && !level.isClientSide()) {
                            vatController.updateGasVolume();
                            vatController.updateCachedMixture();
                        }
                    }
                };
            }
        }
        return null;
    }
}
