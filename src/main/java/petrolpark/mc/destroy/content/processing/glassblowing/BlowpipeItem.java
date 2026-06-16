package petrolpark.mc.destroy.content.processing.glassblowing;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.deployer.DeployerFakePlayer;
import com.simibubi.create.foundation.item.CustomArmPoseItem;

import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.gui.ScreenOpener;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyAdvancementTrigger;
import petrolpark.mc.destroy.DestroyBlocks;
import petrolpark.mc.destroy.DestroyDataComponents;
import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.core.block.IPickUpPutDownBlock;
import petrolpark.mc.destroy.core.chemistry.hazard.ChemistryHazardHelper;

/**
 * The Blowpipe item form — a {@link BlockItem} that places a {@link BlowpipeBlock} and also lets
 * the player "blow" on it in-hand to solidify molten glass into the selected
 * {@link GlassblowingRecipe} output.
 *
 * <ul>
 * <li>class shape + constants + DataComponents</li>
 * <li>BlowpipeItemRenderer / RenderLayer (visual while held/blowing)</li>
 * <li>SelectGlassblowingRecipeC2SPacket (recipe selection)</li>
 * <li>BlowpipeScreen (recipe picker GUI)</li>
 * </ul>
*/
public class BlowpipeItem extends BlockItem implements CustomArmPoseItem {

    public static final int TIME_TO_MOVE_TO_MOUTH = 10;

    public BlowpipeItem(BlowpipeBlock block, Properties properties) {
        super(block, properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int progress = stack.getOrDefault(DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
        GlassblowingRecipe recipe = BlowpipeBlockEntity.readRecipeFromStack(level, stack);

        // Choose / switch recipe
        if (!(player instanceof DeployerFakePlayer) && ((player.isShiftKeyDown() && progress == 0) || recipe == null)) {
            // DistExecutor + @OnlyIn(CLIENT) method pattern, which can run into RuntimeDistCleaner
            // edge cases on dedicated server class loading).
            if (net.neoforged.fml.loading.FMLEnvironment.dist == Dist.CLIENT) {
                ClientScreenOpener.open(hand);
            }
            return InteractionResultHolder.success(stack);
        }

        // Default place-block behavior
        InteractionResultHolder<ItemStack> result = super.use(level, player, hand);

        // Continuing to blow (only when holding fluid)
        FluidStack tankFluid = stack.getOrDefault(DestroyDataComponents.BLOWPIPE_TANK, FluidStack.EMPTY);
        if (result.getResult() == InteractionResult.PASS && recipe != null && !tankFluid.isEmpty()) {
            if (progress > BlowpipeBlockEntity.BLOWING_DURATION) {
                return InteractionResultHolder.pass(stack); // Already done
            } else if ((float) progress / BlowpipeBlockEntity.BLOWING_DURATION > BlowpipeBlockEntity.BLOWING_TIME_PROPORTION) {
                return InteractionResultHolder.pass(stack); // Cooling — wait
            } else {
                if (ChemistryHazardHelper.Protection.MOUTH_COVERED.isProtected(player)) {
                    player.displayClientMessage(DestroyLang.translate("tooltip.eating_prevented.mouth_protected").component(), true);
                    return InteractionResultHolder.fail(stack);
                }
                player.startUsingItem(hand);
                // was fail(stack), but in 1.21 vanilla client only sends
                // ServerboundUseItemPacket when result.consumesAction() is true (CONSUME or
                // SUCCESS). With fail(), client called startUsingItem locally but never told
                // server → server's player.isUsingItem() stayed false → server's inventoryTick
                // never incremented BLOWPIPE_PROGRESS → server's authoritative state (progress=0)
                // overwrote any client-side increment via slot sync. CONSUME mirrors vanilla
                // food-eating pattern (Item.use returns consume() after startUsingItem) and
                // ensures the server-side state matches.
                return InteractionResultHolder.consume(stack);
            }
        }
        return result;
    }

    /** Read the required fluid directly from the item's DataComponent.*/
    public static SizedFluidIngredient getFluidIngredient(ItemStack stack) {
        return stack.get(DestroyDataComponents.BLOWPIPE_REQUIRED_FLUID);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        ItemStack stack = context.getItemInHand();
        SizedFluidIngredient ingredient = getFluidIngredient(stack);
        if (ingredient != null) {
            BlockPos pos = context.getClickedPos();
            Direction face = context.getClickedFace();
            FluidStack blowpipeFluid = stack.getOrDefault(DestroyDataComponents.BLOWPIPE_TANK, FluidStack.EMPTY);
            if (blowpipeFluid.isEmpty()) {
                InteractionResult drainResult = tryDrainFromBlock(level, pos, face, stack, ingredient);
                if (drainResult != InteractionResult.PASS) return drainResult;
            } else {
                // tank already full: don't fall through to BlockItem.place which would
                // consume the click for placement (returning FAIL when target spot is occupied,
                // or placing the blowpipe block). Return PASS so vanilla proceeds to use() which
                // starts the blowing animation. Without this, long-pressing while aiming at any
                // block (e.g. the basin you just drained from) retries placement every tick and
                // never reaches the blow path.
                // BlockItem-place behaviour appears to have been more lenient (or the player was
                // expected to look at empty air), masking the bug.
                return InteractionResult.PASS;
            }
        }
        if (context.getPlayer() instanceof DeployerFakePlayer deployer) {
            if (BlowpipeBlock.getDeployerPlacer(level, deployer) == null) return InteractionResult.FAIL;
        }
        return super.useOn(context);
    }

    /**
     * Drain {@code ingredient.amount()} mB of the required fluid from the IFluidHandler at
     * {@code (pos, face)} into this blowpipe's TANK DataComponent. Shared between
     * {@link #useOn} (right-click placement path) and the left-click event handler in
     * {@code DestroyCommonEvents.onPlayerLeftClickBlock} — Destroy's other fluid-storage
     * items (test tube, beaker, measuring cylinder) all extract on LEFT-click via
     * {@code IMixtureStorageItem.defaultAttack}; the blowpipe didn't follow that convention
     * upstream, leaving players unable to suck molten borosilicate glass out of a Create
     * basin or vat output via the same gesture they use for every other glassware.
     *
     * @return {@link InteractionResult#SUCCESS} on drain, {@link InteractionResult#FAIL} on
     *         capability mismatch / insufficient fluid, {@link InteractionResult#PASS} when
     *         no IFluidHandler is attached at the clicked face (so the caller can fall
     *         through to other logic).
     */
    public InteractionResult tryDrainFromBlock(Level level, BlockPos pos, Direction face,
                                               ItemStack stack, SizedFluidIngredient ingredient) {
        // 1.21 capability lookup: level-side, nullable return.
        IFluidHandler fh = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, face);
        if (fh == null) return InteractionResult.PASS;
        // split the simulate/execute pair from a for-loop into explicit sequential calls,
        // and DEFENSIVE-COPY the executed result before storing into the TANK
        // DataComponent. Some IFluidHandler implementations (Create basin / vanilla potion
        // cauldron) cache the FluidStack instance they return, and subsequent operations
        // on the same handler can MUTATE that instance back to empty. Without .copy(), the
        // TANK component would observe its FluidStack getting silently zeroed-out, making
        // the next click see an empty tank and re-drain.
        FluidStack simulated = fh.drain(ingredient.amount(), IFluidHandler.FluidAction.SIMULATE);
        if (!ingredient.ingredient().test(simulated) || simulated.getAmount() < ingredient.amount()) {
            return InteractionResult.FAIL;
        }
        FluidStack drained = fh.drain(ingredient.amount(), IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty() || drained.getAmount() < ingredient.amount()) {
            return InteractionResult.FAIL;
        }
        stack.set(DestroyDataComponents.BLOWPIPE_TANK, drained.copy());
        return InteractionResult.SUCCESS;
    }

    /**
 * Finish-of-blow production path — called by the Blowpipe BE's air-current animation when the
 * BE tick loop sees the player-held Blowpipe reach BLOWING_DURATION. Drops the recipe's first
 * rollable result back into the player's inventory + plays GLASS_BREAK.
*/
    public boolean finishBlowing(ItemStack stack, Level level, Player player) {
        int progress = stack.getOrDefault(DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
        if (progress < BlowpipeBlockEntity.BLOWING_DURATION) return false;
        stack.set(DestroyDataComponents.BLOWPIPE_TANK, FluidStack.EMPTY);
        stack.set(DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
        stack.set(DestroyDataComponents.BLOWPIPE_LAST_PROGRESS, 0);
        GlassblowingRecipe recipe = BlowpipeBlockEntity.readRecipeFromStack(level, stack);
        if (recipe == null) return false;
        level.playSound(null, player.getOnPos(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS);
        recipe.getRollableResultsAsItemStacks().forEach(s -> player.getInventory().placeItemBackInInventory(s));
        return true;
    }

    /**
 * Hot-glass burn: touching an entity while the Blowpipe has molten glass mid-blow (before
 * cooling phase) sets the target on fire for 3s.
*/
    public boolean onLeftClickEntity(ItemStack stack, Player player, Entity entity) {
        FluidStack tankFluid = stack.getOrDefault(DestroyDataComponents.BLOWPIPE_TANK, FluidStack.EMPTY);
        int progress = stack.getOrDefault(DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
        if (!tankFluid.isEmpty()
            && (float) progress / (float) BlowpipeBlockEntity.BLOWING_DURATION < BlowpipeBlockEntity.BLOWING_TIME_PROPORTION) {
            entity.igniteForSeconds(3);
        }
        return false;
    }

    /** Loaded only on physical client.*/
    public static final class ClientScreenOpener {
        private ClientScreenOpener() {}
        public static void open(InteractionHand hand) {
            ScreenOpener.open(new BlowpipeScreen(hand));
        }
    }

    @Override
    public InteractionResult place(BlockPlaceContext pContext) {
        return IPickUpPutDownBlock.removeItemFromInventory(pContext, super.place(pContext));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || newStack.getItem() != oldStack.getItem();
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (BlowpipeBlockEntity.readRecipeFromStack(level, stack) == null) {
            stack.set(DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
            stack.set(DestroyDataComponents.BLOWPIPE_LAST_PROGRESS, 0);
            stack.set(DestroyDataComponents.BLOWPIPE_BLOWING, false);
            return;
        }

        int progress = stack.getOrDefault(DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
        stack.set(DestroyDataComponents.BLOWPIPE_LAST_PROGRESS, progress);

        boolean increaseProgress = (float) progress / (float) BlowpipeBlockEntity.BLOWING_DURATION > BlowpipeBlockEntity.BLOWING_TIME_PROPORTION;

        if (entity instanceof Player player && player.isUsingItem() && isSelected) {
            int ticksUsing = player.getTicksUsingItem();
            if (ticksUsing > TIME_TO_MOVE_TO_MOUTH) {
                increaseProgress = true;
                player.setAirSupply(player.getAirSupply() - 10);
            }
            stack.set(DestroyDataComponents.BLOWPIPE_BLOWING, true);
        } else {
            stack.set(DestroyDataComponents.BLOWPIPE_BLOWING, false);
        }

        if (increaseProgress && progress < BlowpipeBlockEntity.BLOWING_DURATION) {
            stack.set(DestroyDataComponents.BLOWPIPE_PROGRESS, progress + 1);
            if (progress < BlowpipeBlockEntity.BLOWING_DURATION
                && progress + 1 >= BlowpipeBlockEntity.BLOWING_DURATION
                && entity instanceof Player player) {
                DestroyAdvancementTrigger.BLOWPIPE.award(level, player);
            }
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public @Nullable ArmPose getArmPose(ItemStack stack, AbstractClientPlayer player, InteractionHand hand) {
        if (!player.swinging && stack.getOrDefault(DestroyDataComponents.BLOWPIPE_BLOWING, false)) return ArmPose.SPYGLASS;
        return null;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return BlowpipeBlockEntity.BLOWING_DURATION + TIME_TO_MOVE_TO_MOUTH
            - stack.getOrDefault(DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity livingEntity) {
        super.finishUsingItem(stack, level, livingEntity);
        int progress = stack.getOrDefault(DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
        if (progress == BlowpipeBlockEntity.BLOWING_DURATION) {
            stack.set(DestroyDataComponents.BLOWPIPE_PROGRESS, 0);
            stack.set(DestroyDataComponents.BLOWPIPE_LAST_PROGRESS, 0);
            stack.set(DestroyDataComponents.BLOWPIPE_TANK, FluidStack.EMPTY);
        }
        return stack;
    }

    // ═══════════════════════════════════════════════════════════════════════════════════════════
    // all removed in 1.21 NeoForge. Static {@link #registerCapabilities} invoked via EventBusSubscriber.
    // ═══════════════════════════════════════════════════════════════════════════════════════════

    /** Reason: Create's
 * {@code BasinBlock.useItemOn} → {@code FluidHelper.tryFillItemFromBE} → {@code GenericItemFilling.fillItem}
 * intercepts right-clicks on a fluid-bearing basin if the held item has a FluidHandler.ITEM
 * cap. That code path:
 * <ol>
 * <li>Copies the held stack ({@code split.copy(); split.setCount(1)})</li>
 * <li>Calls our {@code fill()} on the COPY → COPY's TANK is set</li>
 * <li>Calls {@code stack.shrink(1)} on the original held stack</li>
 * <li>Drains the basin</li>
 * <li>**Only in survival** does it call {@code player.getInventory().placeItemBackInInventory(out)}
 * to give the player the modified copy</li>
 * </ol>
 * In CREATIVE mode (e.g. when a Blowpipe is taken from the creative tab), the
 * `out` stack is silently discarded — the basin drains but TANK never reaches the player's hand.
 * Symptom: every right-click drains basin fluid, the blowpipe never appears loaded, can't blow.
 *
 * <p>Original purpose of the cap was Spout-block filling (Create Spout's drip-fill via
 * tryFluidTransfer). With the cap disabled, Spout-filling of Blowpipe is broken, but
 * manual right-click-basin → drain via {@code BlowpipeItem.useOn} works in all modes.
 * Spout-fill can be restored later via a different mechanism (custom Spout behaviour /
 * recipe overlay) once the broader interaction model is settled.</p>
*/
    @EventBusSubscriber(modid = Destroy.MOD_ID)
    public static class CapabilityRegistrar {
        @SubscribeEvent
        public static void registerCapabilities(RegisterCapabilitiesEvent event) {
            // DISABLED: Create basin's GenericItemFilling.fillItem hijacks the click
            // and discards the modified copy in creative mode. Manual fill via useOn handles
            // both creative + survival cleanly.
            // event.registerItem(Capabilities.FluidHandler.ITEM,
            // (stack, ctx) -> new BlowpipeSpoutFillingFluidHandler(stack),
            // DestroyBlocks.BLOWPIPE.asItem());
        }
    }

    /**
 * {@link IFluidHandlerItem} attached to Blowpipe stacks for Spout-filling. Single tank; fill
 * validated against the stack's {@link DestroyDataComponents#BLOWPIPE_REQUIRED_FLUID} (set by
 * {@link SelectGlassblowingRecipeC2SPacket}). Drain is no-op — player can only pour glass in
 * via Spout / right-click-on-tank, never extract back.
*/
    protected static class BlowpipeSpoutFillingFluidHandler implements IFluidHandlerItem {

        protected final ItemStack stack;

        public BlowpipeSpoutFillingFluidHandler(ItemStack stack) {
            this.stack = stack;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tankNo) {
            return stack.getOrDefault(DestroyDataComponents.BLOWPIPE_TANK, FluidStack.EMPTY);
        }

        @Override
        public int getTankCapacity(int tank) {
            return BlowpipeBlockEntity.TANK_CAPACITY;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack resource) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            SizedFluidIngredient ingredient = getFluidIngredient(stack);
            if (ingredient != null && ingredient.ingredient().test(resource) && resource.getAmount() >= ingredient.amount()) {
                FluidStack existing = stack.getOrDefault(DestroyDataComponents.BLOWPIPE_TANK, FluidStack.EMPTY);
                if (!existing.isEmpty()) return 0;
                if (action.execute()) {
                    stack.set(DestroyDataComponents.BLOWPIPE_TANK, resource.copy());
                }
                return ingredient.amount();
            }
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack getContainer() {
            return stack;
        }
    }
}
