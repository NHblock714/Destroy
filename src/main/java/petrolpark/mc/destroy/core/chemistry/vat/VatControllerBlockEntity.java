package petrolpark.mc.destroy.core.chemistry.vat;

import java.util.List;
import java.util.function.BiFunction;

import org.apache.commons.lang3.tuple.Pair;

import com.petrolpark.compat.create.PetrolparkCreateClient;
import com.simibubi.create.api.contraption.transformable.TransformableBlockEntity;
import com.simibubi.create.content.contraptions.StructureTransform;
import com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchObservable;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import petrolpark.mc.destroy.client.DestroyLang;
import petrolpark.mc.destroy.core.block.entity.IHaveLabGoggleInformation;
import petrolpark.mc.destroy.core.block.entity.ISpecialWhenHoveredBlockEntity;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;
import petrolpark.mc.destroy.core.explosion.SmartExplosion;

/**
 * Controller block entity for the Vat — Destroy's large-scale chemistry reactor. Coordinates
 * mixture storage, heating/cooling, agitation, and reaction ticking across the multi-block assembly.
*/
public class VatControllerBlockEntity extends SmartBlockEntity implements IHaveLabGoggleInformation, ISpecialWhenHoveredBlockEntity, ThresholdSwitchObservable, TransformableBlockEntity {

    /**
     * 9-slot inventory matching upstream 1.20.1's {@code SmartInventory(9, this)}. Holds both
     * reactant items (powders the player drops in as catalysts — e.g., methanol synthesis
     * needs two distinct dust catalysts) and precipitate output items spawned by
     * {@link petrolpark.mc.destroy.chemistry.legacy.reactionresult.PrecipitateReactionResult}.
     * Overriding {@link ItemStackHandler#onContentsChanged} to disturb equilibrium matches
     * upstream's {@code whenContentsChanged(i -> cachedMixture.disturbEquilibrium())} so the
     * next tick's reaction loop re-evaluates whether item-catalyzed reactions can now fire.
     */
    public final IItemHandler inventory = new ItemStackHandler(9) {
        @Override
        protected void onContentsChanged(int slot) {
            super.onContentsChanged(slot);
            if (cachedMixture != null) cachedMixture.disturbEquilibrium();
            setChanged();
        }
    };

    /**
 * Pressure animation value. {@link LerpedFloat} initialized to 1.0f
 * (sea-level-ish default). Used by
 * {@link petrolpark.mc.destroy.core.chemistry.vat.ponder.SetVatPressurePonderInstruction
 * SetVatPressurePonderInstruction} via {@code vc.pressure.chase(target, speed, EXP)} in Ponder
 * scenes.
*/
    public final LerpedFloat pressure = LerpedFloat.linear().startWithValue(1f);

    /** Initialized to 298K (room temp). Driven from server-side
 * cachedMixture.getTemperature() via sendData sync.
*/
    public final LerpedFloat temperature = LerpedFloat.linear().startWithValue(298f);

    /** Negative for coolers. Driven by VatSideBE.setPowerFromAdjacentBlock
 * on neighbor change.
*/
    protected float heatingPower = 0f;

    /**
 * Atmospheric pressure baseline (Pa) — used by {@link #getPressure} as the offset so the
 * returned value represents "pressure above atmosphere" (positive = pressurized,
 * 0 = atmospheric, negative = vacuum).
*/
    public static final float AIR_PRESSURE = 101000f;

    /** Driven by VatSideBE.setPowerFromAdjacentBlock.
*/
    protected float UVPower = 0f;

    /** Populated by
 * {@link #tryMakeVat} on assembly + by {@link #read} on NBT load. Cleared
 * by {@link #deleteVat}.
*/
    protected java.util.Optional<Vat> vat = java.util.Optional.empty();


    protected boolean underDeconstruction = false;


    @javax.annotation.Nullable
    protected net.minecraft.core.BlockPos openVentPos;

    /** Drives VatSideRenderer particle spawns.*/
    protected boolean cachedMixtureReacting = false;

    /** Drives bubble-particle
 * spawns in {@link #addParticles}. Client reads it via the AnythingBoiling tag in
 * {@link #read}.*/
    protected boolean cachedMixtureBoiling = false;

    /** Initialized
 * with a default capacity (4000 mB); gets reassigned via {@link VatFluidTankBehaviour#setCapacity}
 * when a Vat multi-block is successfully constructed. Null-safety:
 * getters check for null so partial-state BEs don't NPE.
*/
    protected VatFluidTankBehaviour fluidBehaviour;

    /** Default Vat capacity when no multi-block has been assembled; otherwise
 * {@link Vat#getCapacity} from the constructed Vat.*/
    protected static final int DEFAULT_CAPACITY = 4000;

    /** The tick pipeline uses this for {@code heat / react / disturbEquilibrium}.
*/
    protected petrolpark.mc.destroy.chemistry.legacy.LegacyMixture cachedMixture =
        new petrolpark.mc.destroy.chemistry.legacy.LegacyMixture();

    public VatControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
 * Add fluid to the Vat's internal mixture tank. Routes to {@link VatFluidTankBehaviour}'s
 * combined handler (auto-dispatches liquid vs gas by phase). Called by
 * {@link petrolpark.mc.destroy.core.chemistry.vat.ponder.FillVatPonderInstruction
 * FillVatPonderInstruction} via {@code vc.addFluid(stack, SIMULATE)} in Ponder scenes, and by
 * pipe inputs via VatSideBlockEntity → VatSideFluidCapability → controller.
 *
 * <ul>
 * <li>{@link #cachedMixture} stays at pre-add state → reactions don't see newly-added molecules
 * → no chemistry triggers (e.g. methane added but no new reactions become possible)</li>
 * <li>Gas tank's {@code freeSpace} volume not rescaled — if liquid level changed during fill,
 * gas tank doesn't auto-expand to fill new headspace</li>
 * <li>Client doesn't get updated — Jade / GUI tooltip shows stale tank contents → side cell's
 * input buffer methane appears stuck (combined with controller's pre-fill air mixture =
 * 3 separate fluid stacks displayed instead of merged)</li>
 * </ul>
 *
 * @param stack fluid stack to add
 * @param action {@link FluidAction} (EXECUTE or SIMULATE)
 * @return amount accepted
*/
    public int addFluid(FluidStack stack, FluidAction action) {
        if (fluidBehaviour == null) return 0;
        int amountAdded = fluidBehaviour.getCombinedFluidHandler().fill(stack, action);
        if (amountAdded != 0 && action == FluidAction.EXECUTE) {
            updateCachedMixture();
            // disturb equilibrium ONLY here, not inside updateCachedMixture itself.
            // addFluid is the only path that introduces NEW chemical content; drains call
            // updateCachedMixture too but should NOT trigger reactions (which causes the
            // tank-refill-via-shouldUpdateFluidMixture-path infinite-extract bug).
            // Why disturb is needed: cachedMixture rebuilt from tank NBT may have inherited
            // AtEquilibrium=true from previous flush state. Without disturb, even adding new
            // reactants doesn't re-fire reactForTick → "chemistry only runs once after load".
            if (cachedMixture != null) cachedMixture.disturbEquilibrium();
            updateGasVolume();
            sendData();
            // diagnostic: log every addFluid that actually executes so a fluid-insertion
            // action can be correlated with the subsequent tick log to verify cachedMixture
            // sees the new reactants.
            if (DEBUG_LOG_ENABLED) {
                String contents = (cachedMixture == null) ? "null" :
                    cachedMixture.getContents(false).stream()
                        .map(m -> m.getFullID() + "=" + cachedMixture.getConcentrationOf(m))
                        .collect(java.util.stream.Collectors.joining(", "));
                petrolpark.mc.destroy.Destroy.LOGGER.info(
                    "[VAT-DBG] addFluid @ {}: amountAdded={}mB, fluid={}, postContents=[{}], isAtEqAfterDisturb={}",
                    getBlockPos(), amountAdded, stack.getHoverName().getString(), contents,
                    cachedMixture != null && cachedMixture.isAtEquilibrium());
            }
        }
        return amountAdded;
    }

    /**
 * Flush the Vat's gas-phase mixture tank at room temperature (298K, used by Ponder scenes).
 * Real OPEN_VENT gas venting is driven by {@link #tick} server-tick path (which uses
 * {@link #cachedMixture}'s actual temperature + emits pollution), not by this method.
 *
 * <p>Called by {@link petrolpark.mc.destroy.core.pollution.PollutionPonderScenes#basinsAndVats}
 * Ponder scene via {@code vc::flush} method reference for demonstration purposes.</p>
*/
    public void flush() {
        if (fluidBehaviour != null) fluidBehaviour.flush(298f);
    }

    
    protected DestroyAdvancementBehaviour advancementBehaviour;

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // attach VatFluidTankBehaviour (liquid + gas tanks).
        fluidBehaviour = new VatFluidTankBehaviour(this, DEFAULT_CAPACITY);
        // Keep cachedMixture in sync with tank state for any drain/fill path that goes
        // through the SmartFluidTankBehaviour fluid-update notifier (pumps, vent flush,
        // upstream addFluid). Without it, post-drain ticks may write back a stale cachedMixture
        // and revert player-visible changes.
        //
        // Why this callback does NOT call updateGasVolume(): updateGasVolume's setFluid would
        // reset the gas tank's {@code flushed=false}, defeating the
        // {@code !isEmptyOrFullOfAir} vent gate and triggering a per-tick flush cycle whenever
        // OPEN_VENT is active. Gas-side rebalancing is done in
        // {@link VatTankWrapper#updateVatGasVolume} (where it runs BEFORE the cachedMixture
        // rebuild so {@link VatFluidTankBehaviour#getCombinedMixture}'s scale factor stays 1).
        //
        // No whenFluidUpdates callback here — the v0.3.0 attempt ran
        // updateCachedMixture() on EVERY tank change as a "catch-all" for extraction
        // paths, but it caused a floating-point drift snowball: each rebuild routed
        // through getCombinedMixture → mix() → scale() recomputes temperature via
        // heat-energy summation and reapplication, and the round-trip is not bit-exact.
        // Over the hundreds of tank updates a cooled vat goes through, accumulated
        // drift inflates concentration counts (1mB liquid tank "containing" 30 mol of
        // condensed gas was observed) which balloons volHeatCap to the point where
        // the tick heat loop's kPerTick threshold can never clear 0.001 — heat
        // exchange locks, temperature can't recover to ambient even after the cooler
        // is removed. Upstream 1.20.1 has no equivalent callback and never exhibits
        // the regression. Real extraction paths already call updateCachedMixture
        // explicitly via {@link VatTankWrapper#updateVatGasVolume}, so the callback
        // is redundant. Energy is preserved across an extraction-driven rebuild
        // because {@link LegacyMixture#mix} sums per-mixture energy contributions and
        // reapplies them via heat() — no warm-temperature ratchet needed.
        behaviours.add(fluidBehaviour);
        // advancement behaviour (USE_VAT trigger award site).
        advancementBehaviour = new DestroyAdvancementBehaviour(this, petrolpark.mc.destroy.DestroyAdvancementTrigger.USE_VAT);
        behaviours.add(advancementBehaviour);
    }


    /** Logs vat state every {@code DEBUG_LOG_INTERVAL} ticks
 * (default 20 = once per second). Toggle via the {@code -Ddestroy.vatDebug=true}
 * JVM arg or by editing the {@link #DEBUG_LOG_ENABLED} flag below.*/
    private long debugTickCounter = 0;
    private static final boolean DEBUG_LOG_ENABLED =
        Boolean.parseBoolean(System.getProperty("destroy.vatDebug", "false"));
    private static final int DEBUG_LOG_INTERVAL = 20;

    // Equilibrium handling relies on:
    // - the addFluid path for external fluid insertions
    // - reactForTick's own equilibrium tracking for in-vat-produced substances
    // - cachedMixture in-memory mutation for chained reactions
    // sendData() is called directly in {@link #addFluid}, {@link #updateFluidMixture}, and at
    // the end of {@link #tick} (no throttling).

    @Override
    @SuppressWarnings("null")
    public void tick() {
        super.tick();
        if (initializationTicks > 0) initializationTicks--;

        if (!hasLevel()) return;

        if (getLevel().isClientSide()) {
            pressure.tickChaser();
            temperature.tickChaser();
            addParticles();
            return;
        }

        // ---- Server-side tick ----
        if (vat.isEmpty() || fluidBehaviour == null) return;
        Vat v = vat.get();
        boolean shouldUpdateFluidMixture = false;
        double fluidAmount = getCapacity() / petrolpark.mc.destroy.chemistry.api.util.Constants.MILLIBUCKETS_PER_LITER;

        int cyclesPerTick = getSimulationLevel();

        // Relies on the addFluid path + cachedMixture in-memory mutation for chained reactions.
        // See the field-level notes above; the failure mode to watch for is chemistry only
        // running once after a world reload.

        // diagnostic logger. Logs every 20 ticks at INFO level so "[VAT-DBG]" can be grepped
        // in latest.log to trace the vat tick / chemistry.
        boolean shouldLog = DEBUG_LOG_ENABLED && (debugTickCounter++ % DEBUG_LOG_INTERVAL == 0);
        if (shouldLog) {
            String contents = cachedMixture.getContents(false).stream()
                .map(m -> m.getFullID() + "=" + cachedMixture.getConcentrationOf(m))
                .collect(java.util.stream.Collectors.joining(", "));
            petrolpark.mc.destroy.Destroy.LOGGER.info(
                "[VAT-DBG] tick @ {}: temp={}K, eq={}, contents=[{}], heatPower={}, fluidAmount={}L, cap={}, hasLiquid={}, hasGas={}",
                getBlockPos(),
                String.format("%.2f", cachedMixture.getTemperature()),
                cachedMixture.isAtEquilibrium(),
                contents,
                heatingPower,
                fluidAmount,
                getCapacity(),
                getLiquidTank() != null && !getLiquidTank().isEmpty(),
                getGasTank() != null && !getGasTank().isEmpty()
            );
        }

        // heat() condenses
        // species when crossing boiling points downward, so the change happens INSIDE the heat
        // loop (not in reactForTick). Must capture pre-heat states to detect transitions.
        java.util.Map<petrolpark.mc.destroy.chemistry.legacy.LegacySpecies, Float> statesBeforeHeat =
            cachedMixture == null ? java.util.Collections.emptyMap()
                : new java.util.HashMap<>(cachedMixture.getContents(false).stream().collect(
                    java.util.stream.Collectors.toMap(
                        m -> m,
                        m -> cachedMixture.getState(m))));

        // Heating (Fourier's Law): energyChange = heatingPower + (ambient - currentT) * conductance
        int heatCyclesRun = 0;
        boolean heatDisturbedAtLeastOnce = false;
        for (int cycle = 0; cycle < cyclesPerTick; cycle++) {
            float energyChange = heatingPower;
            energyChange += (petrolpark.mc.destroy.core.pollution.PollutionHelper.getLocalTemperature(getLevel(), getBlockPos())
                - cachedMixture.getTemperature()) * v.getConductance();
            energyChange /= 20 * cyclesPerTick;
            float volHeatCap = cachedMixture.getVolumetricHeatCapacity();
            float kPerTick = (fluidAmount != 0d) ? Math.abs(energyChange / ((float) fluidAmount * volHeatCap)) : 0f;
            if (Math.abs(energyChange / (fluidAmount * volHeatCap)) > 0.001f && fluidAmount != 0d) {
                cachedMixture.heat(energyChange / (float) fluidAmount);
                cachedMixture.disturbEquilibrium();
                heatDisturbedAtLeastOnce = true;
                heatCyclesRun++;
            } else {
                if (shouldLog && cycle == 0) {
                    petrolpark.mc.destroy.Destroy.LOGGER.info(
                        "[VAT-DBG]   heat-loop break @cycle0: energyChange={}J, fluidAmount={}L, volHeatCap={}, kPerTick={}, threshold=0.001",
                        energyChange, fluidAmount, volHeatCap, kPerTick);
                }
                break;
            }
        }
        if (shouldLog) {
            petrolpark.mc.destroy.Destroy.LOGGER.info(
                "[VAT-DBG]   heat-loop ran {} cycles (of {}), disturbedDuringHeat={}",
                heatCyclesRun, cyclesPerTick, heatDisturbedAtLeastOnce);
        }

        // Unconditional disturb (not gated). The perf cost
        // is always-disturb with an empty mixture, which is a HashMap comparison (cheap but
        // multiplied by tick count).
        boolean wasAtEquilibriumBeforeUnconditionalDisturb = cachedMixture != null && cachedMixture.isAtEquilibrium();
        if (cachedMixture != null) cachedMixture.disturbEquilibrium();
        if (shouldLog) {
            petrolpark.mc.destroy.Destroy.LOGGER.info(
                "[VAT-DBG]   unconditional-disturb: wasAtEq={}, nowAtEq={}",
                wasAtEquilibriumBeforeUnconditionalDisturb,
                cachedMixture != null && cachedMixture.isAtEquilibrium());
        }

        // Take Items out of inventory so they can be offered to dissolveItems.
        java.util.List<net.minecraft.world.item.ItemStack> availableItemStacks = new java.util.ArrayList<>();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            net.minecraft.world.item.ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) availableItemStacks.add(stack.copy());
        }

        petrolpark.mc.destroy.chemistry.legacy.LegacyMixture.ReactionContext context =
            new petrolpark.mc.destroy.chemistry.legacy.LegacyMixture.ReactionContext(availableItemStacks, UVPower, false);

        if (shouldLog) {
            petrolpark.mc.destroy.Destroy.LOGGER.info(
                "[VAT-DBG]   pre-react: isAtEq={}, availableItems={}",
                cachedMixture.isAtEquilibrium(), availableItemStacks.size());
        }
        if (!cachedMixture.isAtEquilibrium()) {
            availableItemStacks = cachedMixture.dissolveItems(context, fluidAmount);
            // Clear inventory — stacks get re-inserted below INSIDE this branch.
            if (inventory instanceof net.neoforged.neoforge.items.ItemStackHandler ish) {
                for (int slot = 0; slot < ish.getSlots(); slot++) ish.setStackInSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
            }

            // snapshot cachedMixture's contents BEFORE reactForTick to detect
            // whether reactions actually happened. With always-disturb, reactForTick
            // fires every tick — but if no real reactions happened, shouldUpdateFluidMixture must
            // STAY FALSE, otherwise updateFluidMixture refills tanks to vatCapacity → infinite
            // extract bug.
            java.util.Map<petrolpark.mc.destroy.chemistry.legacy.LegacySpecies, Float> contentsBefore =
                new java.util.HashMap<>(cachedMixture.getContents(false).stream().collect(
                    java.util.stream.Collectors.toMap(
                        m -> m,
                        m -> cachedMixture.getConcentrationOf(m))));
            // phase-state snapshot taken BEFORE heat loop (see {@code statesBeforeHeat}
            // declared above). Detection happens after reactForTick by comparing current states
            // to the pre-heat snapshot.

            context = new petrolpark.mc.destroy.chemistry.legacy.LegacyMixture.ReactionContext(availableItemStacks, UVPower, false);
            cachedMixture.reactForTick(context, cyclesPerTick);

            // Check if reactions actually changed concentrations (allows always-disturb without
            // triggering tank refill on no-op tick).
            boolean reactionsHappened = false;
            float maxConcDelta = 0f;
            petrolpark.mc.destroy.chemistry.legacy.LegacySpecies maxDeltaMolecule = null;
            for (petrolpark.mc.destroy.chemistry.legacy.LegacySpecies m : cachedMixture.getContents(false)) {
                Float old = contentsBefore.get(m);
                float delta = (old == null) ? cachedMixture.getConcentrationOf(m) : Math.abs(old - cachedMixture.getConcentrationOf(m));
                if (delta > maxConcDelta) {
                    maxConcDelta = delta;
                    maxDeltaMolecule = m;
                }
                if (old == null || Math.abs(old - cachedMixture.getConcentrationOf(m)) > 1e-6f) {
                    reactionsHappened = true;
                    // don't break — keep tracking maxConcDelta for log
                }
            }
            if (!reactionsHappened && contentsBefore.size() != cachedMixture.getContents(false).size()) {
                reactionsHappened = true;  // molecule list changed
            }
            // detect phase-state changes (boiling/condensation) since pre-heat snapshot.
            // Compares against {@code statesBeforeHeat} captured BEFORE the heat loop, since
            // {@code heat()} mutates states[] inside that loop when crossing boiling points.
            boolean phaseStateChanged = false;
            for (petrolpark.mc.destroy.chemistry.legacy.LegacySpecies m : cachedMixture.getContents(false)) {
                Float oldState = statesBeforeHeat.get(m);
                float newState = cachedMixture.getState(m);
                if (oldState == null || Math.abs(oldState - newState) > 1e-3f) {
                    phaseStateChanged = true;
                    break;
                }
            }
            shouldUpdateFluidMixture = reactionsHappened || phaseStateChanged;

            if (shouldLog) {
                petrolpark.mc.destroy.Destroy.LOGGER.info(
                    "[VAT-DBG]   post-react: isAtEq={}, reactionsHappened={}, maxConcDelta={} (molecule={}), nMoleculesBefore={}, nMoleculesAfter={}",
                    cachedMixture.isAtEquilibrium(),
                    reactionsHappened,
                    maxConcDelta,
                    maxDeltaMolecule == null ? "n/a" : maxDeltaMolecule.getFullID(),
                    contentsBefore.size(),
                    cachedMixture.getContents(false).size());
            }

            if (!cachedMixture.isAtEquilibrium() && advancementBehaviour != null) {
                advancementBehaviour.awardDestroyAdvancement(petrolpark.mc.destroy.DestroyAdvancementTrigger.USE_VAT);
            }

            // re-insert ONLY inside the reaction branch (only path that cleared inventory).
            // When this re-insert was OUTSIDE the if-branch it caused item duplication:
            // when isAtEquilibrium=true, availableItemStacks was a copy of inventory,
            // inventory wasn't cleared, but the copies still got re-inserted →
            // every tick: original (1) + copy (1) = 2 → 4 → 8 → ... → 64 (capped at stack max).
            // (symptom: a single nickel powder dropped into the vat duplicated up to a full
            // stack of 64.)
            // The disturb-equilibrium-every-tick behaviour normally keeps the reaction branch
            // active so the clear always happens; with an empty/cold mixture the heating
            // loop's threshold check breaks → equilibrium stays → dup chain.
            for (net.minecraft.world.item.ItemStack itemStack : availableItemStacks) {
                net.neoforged.neoforge.items.ItemHandlerHelper.insertItemStacked(inventory, itemStack, false);
            }
        }

        if (shouldUpdateFluidMixture) {
            if (shouldLog) {
                petrolpark.mc.destroy.Destroy.LOGGER.info(
                    "[VAT-DBG]   shouldUpdate=TRUE → updateFluidMixture() (write cachedMixture back to tanks)");
            }
            // Enact reaction results (precipitate / novel-compound / explosion / advancement).
            cachedMixture.getCompletedResults(fluidAmount).entrySet().forEach(entry -> {
                for (int i = 0; i < entry.getValue(); i++) entry.getKey().onVatReaction(getLevel(), this);
            });
            updateFluidMixture();
        } else {
            // No reaction or phase change this tick, so skip the volume-regenerating setMixture
            // write-back. If the heat loop changed the temperature, persist just that to the tank
            // stacks so the next fill/drain rebuild keeps it.
            if (heatDisturbedAtLeastOnce && cachedMixture != null && !cachedMixture.isEmpty()) {
                fluidBehaviour.syncTankTemperatures(cachedMixture.getTemperature());
            }
        }

        // Gas venting — fires only when the gas tank holds reaction-produced gas, not just
        // the initial atmospheric air. Matches the upstream 1.20.1 gate exactly:
        //   !gasHandler.isEmptyOrFullOfAir() == (!isEmpty() && !flushed)
        // {@code flushed} is set true by flush() and reset false by onContentsChanged whenever
        // new gas enters the tank. With an idle air-only vat the gate stays closed, so heat
        // applied to the vat is not wiped out by a per-tick flush; once a reaction generates
        // new gas, flushed flips to false, the gate opens, the gas is vented, and flushed
        // becomes true again — the gate self-quiets after each event.
        VatSideBlockEntity openVent = getOpenVent();
        if (openVent != null && fluidBehaviour.getGasHandler() != null
            && !fluidBehaviour.getGasHandler().isEmptyOrFullOfAir()) {
            FluidStack vented = fluidBehaviour.flush(cachedMixture.getTemperature());
            if (shouldLog) {
                petrolpark.mc.destroy.Destroy.LOGGER.info(
                    "[VAT-DBG]   gas-vent: ventedAmount={}mB, dir={}, pBefore={}Pa",
                    vented.getAmount(), openVent.direction, getPressure());
            }
            if (openVent.direction != null && !vented.isEmpty()) {
                petrolpark.mc.destroy.core.pollution.PollutionHelper.pollute(
                    getLevel(), openVent.getBlockPos().relative(openVent.direction), 10f, vented);
            }
            updateCachedMixture();
        }

        // Self-destruct if over-pressurized.
        if (petrolpark.mc.destroy.config.DestroyAllConfigs.SERVER.blocks.vatExplodesAtHighPressure.get()
            && Math.abs(getPercentagePressure()) >= 1f) {
            explode();
        }

        // reverted #2 throttling: unconditional sendData each tick.
        sendData();
    }

    /** Gated on vat
 * presence since setMixture uses vat.getCapacity().
*/
    private void updateFluidMixture() {
        if (vat.isEmpty() || fluidBehaviour == null) return;
        // diagnostic: log capacity used for setMixture to detect "tanks refilled to vatCap"
        // root cause of "infinite extraction" + suspected involvement in mixing/reaction issue.
        if (DEBUG_LOG_ENABLED) {
            petrolpark.mc.destroy.Destroy.LOGGER.info(
                "[VAT-DBG]   updateFluidMixture: vatCapacity={}mB, cachedMixtureSpecies={}, isAtEq={}",
                vat.get().getCapacity(),
                cachedMixture == null ? "null" : cachedMixture.getContents(false).size(),
                cachedMixture != null && cachedMixture.isAtEquilibrium());
        }
        fluidBehaviour.setMixture(cachedMixture, vat.get().getCapacity());
        updateGasVolume();
        sendData();
    }

    
    public static int getSimulationLevel() {
        return petrolpark.mc.destroy.config.DestroyAllConfigs.SERVER.blocks.simulationLevel.get();
    }

    
    public void explode() {
        explode((lvl, pos) -> new SmartExplosion(lvl, null, null, null, pos, 5, 0.6f));
    }

    /**
 * Bubble particles spawn at random positions inside the Vat volume when {@link
 * #cachedMixtureBoiling} (synced via AnythingBoiling tag) is true. The splash branch uses
 * the same bubble particle for now; a dedicated splash particle type is not yet defined.
*/
    @SuppressWarnings("null")
    public void addParticles() {
        FluidStack liquid = getLiquidTankContents();
        if (liquid.isEmpty()) return;
        if (vat.isEmpty()) return;
        Vat v = vat.get();
        if (cachedMixtureBoiling) {
            Vec3 position = getRandomParticlePosition(v);
            getLevel().addAlwaysVisibleParticle(
                new petrolpark.mc.destroy.core.fluid.gasparticle.BoilingFluidBubbleParticleData(liquid),
                position.x, position.y, position.z, 0d, 0d, 0d);
        }
        // cachedMixtureReacting splash particle: emit BoilingFluidBubbleParticle with
        // upward splash velocity. Dedicated splash particle type
        // can be introduced later as cosmetic polish.
        if (cachedMixtureReacting) {
            Vec3 position = getRandomParticlePosition(v);
            double vy = 0.15 + getLevel().getRandom().nextDouble() * 0.1;
            double vx = (getLevel().getRandom().nextDouble() - 0.5) * 0.05;
            double vz = (getLevel().getRandom().nextDouble() - 0.5) * 0.05;
            getLevel().addAlwaysVisibleParticle(
                new petrolpark.mc.destroy.core.fluid.gasparticle.BoilingFluidBubbleParticleData(liquid),
                position.x, position.y, position.z, vx, vy, vz);
        }
    }

    /** X / Z uniform over
 * internal width / length; Y reads current fluid level ratio so particles appear at the
 * liquid surface.
*/
    @SuppressWarnings("null")
    protected Vec3 getRandomParticlePosition(Vat v) {
        return Vec3.atLowerCornerOf(v.getInternalLowerCorner())
            .add(getLevel().getRandom().nextFloat() * v.getInternalWidth(),
                 getFluidLevel(),
                 getLevel().getRandom().nextFloat() * v.getInternalLength());
    }

    
    protected int initializationTicks = 3;

    // ============================================================
    // Multi-block assembly (tryMakeVat + deleteVat)
    // ============================================================

    /** On success, converts surrounding 6 faces of the detected volume into
 * {@link VatSideBlockEntity}s (backed by Copycat material storage) + wires each
 * side cell's direction + controllerPosition + refreshes capabilities.
 *
 * @return {@code true} if Vat was successfully constructed
*/
    @SuppressWarnings("null")
    public boolean tryMakeVat() {
        if (!hasLevel() || getLevel().isClientSide()) return false;

        BlockPos vatInternalStartPos = getBlockPos().relative(
            getLevel().getBlockState(getBlockPos()).getValue(VatControllerBlock.FACING).getOpposite());
        java.util.Optional<Vat> newVat = Vat.tryConstruct(getLevel(), vatInternalStartPos, getBlockPos());
        if (newVat.isEmpty()) return false;

        updateItemCapability();

        java.util.Collection<BlockPos> sides = newVat.get().getSideBlockPositions();
        sides.forEach(pos -> {
            net.minecraft.world.level.block.state.BlockState oldState = getLevel().getBlockState(pos);
            if (oldState.is(petrolpark.mc.destroy.DestroyBlocks.VAT_CONTROLLER.get())) return;
            getLevel().setBlockAndUpdate(pos, petrolpark.mc.destroy.DestroyBlocks.VAT_SIDE.getDefaultState());
            getLevel().getBlockEntity(pos, petrolpark.mc.destroy.DestroyBlockEntityTypes.VAT_SIDE.get())
                .ifPresent(vatSide -> {
                    vatSide.direction = newVat.get().whereIsSideFacing(pos);
                    vatSide.setMaterial(oldState);
                    vatSide.setConsumedItem(new net.minecraft.world.item.ItemStack(oldState.getBlock().asItem()));
                    vatSide.controllerPosition = getBlockPos();
                    BlockPos adjacentPos = pos.relative(vatSide.direction);
                    vatSide.refreshFluidCapability();
                    vatSide.updateDisplayType(adjacentPos);
                    vatSide.setPowerFromAdjacentBlock(adjacentPos);
                    vatSide.refreshItemCapability();
                    vatSide.invalidateRenderBoundingBox();
                    vatSide.notifyUpdate();
                });
        });

        vat = java.util.Optional.of(newVat.get());
        finalizeVatConstruction();
        updateCachedMixture();
        flush();
        updateCachedMixture();

        return true;
    }

    
    protected void finalizeVatConstruction() {
        if (vat.isPresent() && fluidBehaviour != null) {
            fluidBehaviour.setCapacity(vat.get().getCapacity());
        }
        notifyUpdate();
        // propagate to adjacent pumps so they reset their FluidNetwork state. Without
        // this, a pump that was already pulling from a Vat side cell before assembly/resize
        // doesn't see the new capability surface (capacity changed, side cells gained/lost
        // FluidHandler.BLOCK exposure) and gets stuck holding stale endpoint references.
        // Companion to FluidTankBlockEntityMixin.destroy$resetAdjacentPumpNetworks for Create
        // tanks. See that mixin's class javadoc for the symptom and root-cause analysis.
        notifyAdjacentFluidNetworks();
    }

    /** This wipes pipe pressure and forces pumps to rebuild their network →
 * next tick they re-discover the Vat's I/O sides as endpoints with current capability handles.
 *
 * <p>No-op if no Vat is assembled or running on client. Safe to call multiple times — each
 * propagation is bounded by the configured {@code mechanicalPumpRange}.</p>
*/
    @SuppressWarnings("null")
    protected void notifyAdjacentFluidNetworks() {
        if (getLevel() == null || getLevel().isClientSide() || vat.isEmpty()) return;
        Level level = getLevel();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        java.util.function.Consumer<BlockPos> propagateFrom = (cellPos) -> {
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos adjacent = cellPos.relative(d);
                if (!visited.add(adjacent)) continue;
                com.simibubi.create.content.fluids.FluidTransportBehaviour pipe =
                    com.simibubi.create.content.fluids.FluidPropagator.getPipe(level, adjacent);
                if (pipe == null) continue;
                BlockState adjacentState = level.getBlockState(adjacent);
                com.simibubi.create.content.fluids.FluidPropagator.propagateChangedPipe(
                    level, adjacent, adjacentState);
            }
        };
        propagateFrom.accept(getBlockPos());
        for (BlockPos sidePos : vat.get().getSideBlockPositions()) {
            propagateFrom.accept(sidePos);
        }
        // Also invalidate caps at controller pos so any BlockCapabilityCache pointing there
        // gets a clean refresh.
        level.invalidateCapabilities(getBlockPos());
    }

    /** <b>Intentionally no-op in 1.21</b>:
 * {@code Capabilities.ItemHandler.BLOCK} on VatSideBlockEntity is registered via
 * {@link VatSideBlockEntity#registerCapabilities}, so
 * pipe insertion is always routed to {@code controller.inventory} fresh on each query —
 * no stale cached handle to invalidate.
*/
    protected void updateItemCapability() {
        // Capability registration is query-based in 1.21 NeoForge.
    }

    /** Drops inventory / pollutes air with stored fluids / clears
 * tank fluids / restores original side cell materials via {@link VatSideBlockEntity#getMaterial}
 * / resets heating + UV / clears cachedMixture + vat Optional.
*/
    @SuppressWarnings("null")
    public void deleteVat(BlockPos posDestroyed) {
        if (underDeconstruction || !hasLevel() || getLevel().isClientSide()) return;
        underDeconstruction = true;

        // pollution emission position: prefer the side opposite the destroyed block
        // (so gases vent outward), fall back to the controller's facing direction.
        BlockPos pollutionPos = getBlockPos().relative(getBlockState().getValue(VatControllerBlock.FACING));
        java.util.Optional<VatSideBlockEntity> vatSideOptional = getLevel()
            .getBlockEntity(posDestroyed, petrolpark.mc.destroy.DestroyBlockEntityTypes.VAT_SIDE.get());
        if (vatSideOptional.isPresent() && vatSideOptional.get().direction != null) {
            pollutionPos = posDestroyed.relative(vatSideOptional.get().direction);
        }

        // drop stored items at destroyed position. Create's ItemHelper.dropContents
        // (also used by AgingBarrel / TestTubeRack in 1.21 port) is the canonical helper.
        com.simibubi.create.foundation.item.ItemHelper.dropContents(getLevel(), posDestroyed, inventory);

        removeVent();

        // pollute the world with the vat's stored liquid + gas before clearing them.
        // PollutionHelper.pollute(Level, BlockPos, FluidStack...) takes varargs of fluid stacks.
        FluidStack liquidContents = getLiquidTank() != null ? getLiquidTank().getFluid() : FluidStack.EMPTY;
        FluidStack gasContents = getGasTank() != null ? getGasTank().getFluid() : FluidStack.EMPTY;
        if (!liquidContents.isEmpty() || !gasContents.isEmpty()) {
            petrolpark.mc.destroy.core.pollution.PollutionHelper.pollute(
                getLevel(), pollutionPos, liquidContents, gasContents);
        }

        if (getLiquidTank() != null) getLiquidTank().setFluid(FluidStack.EMPTY);
        if (getGasTank() != null) getGasTank().setFluid(FluidStack.EMPTY);

        if (vat.isPresent()) {
            vat.get().getSideBlockPositions().forEach(pos -> {
                if (!pos.equals(posDestroyed)) {
                    getLevel().getBlockEntity(pos, petrolpark.mc.destroy.DestroyBlockEntityTypes.VAT_SIDE.get())
                        .ifPresent(vatSide -> {
                            net.minecraft.world.level.block.state.BlockState newState = vatSide.getMaterial();
                            if (newState != null) getLevel().setBlockAndUpdate(pos, newState);
                        });
                }
            });
        }

        heatingPower = 0f;
        UVPower = 0f;
        cachedMixture = new petrolpark.mc.destroy.chemistry.legacy.LegacyMixture();
        // capture side positions BEFORE clearing the vat field, so they can be iterated
        // for pump-network propagation below.
        java.util.List<BlockPos> sidesSnapshot = vat.isPresent()
            ? new java.util.ArrayList<>(vat.get().getSideBlockPositions())
            : java.util.Collections.emptyList();
        vat = java.util.Optional.empty();
        underDeconstruction = false;
        notifyUpdate();
        // same propagation as finalizeVatConstruction: when the Vat tears down, the
        // side cells lose FluidHandler.BLOCK exposure → adjacent pumps need to drop their
        // endpoint references so they don't stutter on the now-invalid handles.
        if (getLevel() != null && !getLevel().isClientSide() && !sidesSnapshot.isEmpty()) {
            Level level = getLevel();
            java.util.Set<BlockPos> visited = new java.util.HashSet<>();
            java.util.List<BlockPos> cellsToVisit = new java.util.ArrayList<>(sidesSnapshot);
            cellsToVisit.add(getBlockPos());
            for (BlockPos cellPos : cellsToVisit) {
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    BlockPos adjacent = cellPos.relative(d);
                    if (!visited.add(adjacent)) continue;
                    com.simibubi.create.content.fluids.FluidTransportBehaviour pipe =
                        com.simibubi.create.content.fluids.FluidPropagator.getPipe(level, adjacent);
                    if (pipe == null) continue;
                    BlockState adjacentState = level.getBlockState(adjacent);
                    com.simibubi.create.content.fluids.FluidPropagator.propagateChangedPipe(
                        level, adjacent, adjacentState);
                }
            }
            level.invalidateCapabilities(getBlockPos());
        }
    }

    /** Called on
 * tryMakeVat / deleteVat / side updateDisplayType transitions.
*/
    @SuppressWarnings("null")
    public void removeVent() {
        openVentPos = null;
        if (!hasLevel() || vat.isEmpty()) return;
        vat.get().getSideBlockPositions().forEach(pos -> {
            getLevel().getBlockEntity(pos, petrolpark.mc.destroy.DestroyBlockEntityTypes.VAT_SIDE.get())
                .ifPresent(vatSide -> {
                    if (vatSide.getDisplayType() == VatSideBlockEntity.DisplayType.OPEN_VENT) {
                        openVentPos = pos;
                    }
                });
        });
    }

    /**
 * Used by the tick path as the {@link petrolpark.mc.destroy.core.pollution.PollutionHelper}
 * emission site + gas-venting drain.
*/
    @javax.annotation.Nullable
    @SuppressWarnings("null")
    public VatSideBlockEntity getOpenVent() {
        if (getLevel() == null || openVentPos == null) return null;
        return getLevel().getBlockEntity(openVentPos, petrolpark.mc.destroy.DestroyBlockEntityTypes.VAT_SIDE.get())
            .orElse(null);
    }

    @Override
    public void destroy() {
        deleteVat(getBlockPos());
        super.destroy();
    }

    // ============================================================
    // NBT save/load
    // ============================================================

    @Override
    @SuppressWarnings("deprecation")
    protected void read(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        heatingPower = tag.getFloat("HeatingPower");
        UVPower = tag.getFloat("UVPower");
        underDeconstruction = tag.getBoolean("UnderDeconstruction");
        // Inventory: ItemStackHandler.deserializeNBT 1.21 takes provider
        if (tag.contains("Inventory") && inventory instanceof net.neoforged.neoforge.items.ItemStackHandler ish) {
            ish.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        // Vat.read round-trip: without this, the vat
        // multi-block dimensions / weakest block / conductance are lost on every world load,
        // forcing players to re-assemble. finalizeVatConstruction restores tank capacity from the
        // recovered Vat dimensions.
        if (tag.contains("Vat", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            vat = Vat.read(tag.getCompound("Vat"), getBlockPos());
            if (vat.isPresent()) finalizeVatConstruction();
        } else {
            vat = java.util.Optional.empty();
        }
        if (clientPacket) {
            pressure.chase(tag.getFloat("Pressure"), 0.125f, net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
            temperature.chase(tag.getFloat("Temperature"), 0.125f, net.createmod.catnip.animation.LerpedFloat.Chaser.EXP);
            cachedMixtureReacting = tag.getBoolean("AnythingReacting");
            cachedMixtureBoiling = tag.getBoolean("AnythingBoiling");
        } else {
            if (tag.contains("VentPos")) {
                openVentPos = net.minecraft.nbt.NbtUtils.readBlockPos(tag, "VentPos").orElse(null);
            }
            updateCachedMixture();
        }
    }

    @Override
    @SuppressWarnings("null")
    protected void write(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putFloat("HeatingPower", heatingPower);
        tag.putFloat("UVPower", UVPower);
        tag.putBoolean("UnderDeconstruction", underDeconstruction);
        // Inventory
        if (inventory instanceof net.neoforged.neoforge.items.ItemStackHandler ish) {
            tag.put("Inventory", ish.serializeNBT(registries));
        }
        // write the Vat structure to NBT so it survives
        // world reload + chunk unload/reload + setBlock-based block-entity migrations.
        if (vat.isPresent()) {
            net.minecraft.nbt.CompoundTag vatTag = new net.minecraft.nbt.CompoundTag();
            vat.get().write(vatTag, getBlockPos());
            tag.put("Vat", vatTag);
        }
        // Mixture sync (server-only field → client reads via clientPacket)
        if (hasLevel() && !getLevel().isClientSide()) {
            tag.putFloat("Pressure", getPressure());
            tag.putFloat("Temperature", getTemperature());
            tag.putBoolean("AnythingReacting", cachedMixture != null && !cachedMixture.isAtEquilibrium());
            // available in 1.21 LegacyMixture port. Omitted until isBoiling ports.
        }
        if (openVentPos != null) {
            tag.put("VentPos", net.minecraft.nbt.NbtUtils.writeBlockPos(openVentPos));
        }
    }

    /**
 * deletes the Vat before the explosion so the blocks are restored to their material state
 * (Create SmartExplosion then handles the actual destruction). Called by
 * {@link petrolpark.mc.destroy.chemistry.legacy.reactionresult.ExplosionReactionResult#onVatReaction}
 * and by the self-destruct path in {@link #tick} when pressure exceeds the weakest block's
 * rating.
*/
    @SuppressWarnings("null")
    public void explode(BiFunction<Level, Vec3, SmartExplosion> explosionFactory) {
        if (!(getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        vat.ifPresent(v -> {
            Vec3 center = v.getCenter();
            deleteVat(getBlockPos());
            SmartExplosion.explode(serverLevel, explosionFactory.apply(serverLevel, center));
        });
    }

    /**
 * Get the Player associated with this Vat (whoever placed the controller block). Used for
 * advancement awarding + novel compound unlock tracking.
*/
    public Player getPlayer() {
        DestroyAdvancementBehaviour behaviour = getBehaviour(DestroyAdvancementBehaviour.TYPE);
        return behaviour == null ? null : behaviour.getPlayer();
    }

    // ============================================================
    // Renderer-support getters (VatSideRenderer / VatRenderer / VatSideFluidCapability)
    // ============================================================

    /**
 * Return the associated {@link Vat} multi-block, or {@link java.util.Optional#empty()} if no
 * Vat is currently assembled. Populated by {@link #tryMakeVat} on assembly + by
 * {@link #read} on NBT load round-trip. Cleared by {@link #deleteVat}.
*/
    public java.util.Optional<petrolpark.mc.destroy.core.chemistry.vat.Vat> getVatOptional() {
        return vat;
    }

    /** Uses the tank's remaining space.
*/
    public boolean canFitFluid() {
        if (fluidBehaviour == null) return true;
        return !fluidBehaviour.isFull() && fluidBehaviour.getLiquidHandler().getSpace() > 0;
    }

    
    public FluidStack getLiquidTankContents() {
        if (fluidBehaviour == null) return FluidStack.EMPTY;
        return fluidBehaviour.getLiquidHandler().getFluid();
    }

    
    public FluidStack getGasTankContents() {
        if (fluidBehaviour == null) return FluidStack.EMPTY;
        return fluidBehaviour.getGasHandler().getFluid();
    }

    /** Uses VatFluidTankBehaviour's tank capacity; falls back to
 * {@link #DEFAULT_CAPACITY} when the behaviour is not yet initialized.*/
    public int getCapacity() {
        if (fluidBehaviour == null) return DEFAULT_CAPACITY;
        return fluidBehaviour.getLiquidHandler().getCapacity();
    }

    /**
 * Server-side fluid level (Y coordinate from Vat floor · internal height × amount / capacity).
 * Used by VatSideBE.isPipeSubmerged for authoritative pipe-submerged check.
*/
    public float getFluidLevel() {
        if (fluidBehaviour == null) return 0f;
        int amount = fluidBehaviour.getLiquidHandler().getFluidAmount();
        int capacity = fluidBehaviour.getLiquidHandler().getCapacity();
        if (capacity <= 0) return 0f;
        if (vat.isPresent()) {
            return (float) vat.get().getInternalHeight() * (float) amount / (float) capacity;
        }
        return (float) amount / (float) capacity;
    }

    /** Uses the tank segment's
 * {@code getTotalUnits(partialTicks)} to interpolate between server sync frames.
*/
    public float getRenderedFluidLevel(float partialTicks) {
        if (vat.isEmpty() || fluidBehaviour == null) return 0f;
        return (float) vat.get().getInternalHeight()
            * fluidBehaviour.getLiquidTank().getTotalUnits(partialTicks)
            / (float) getCapacity();
    }

    
    public float getClientTemperature(float partialTicks) {
        return temperature.getValue(partialTicks);
    }

    /**
 * Temperature in K. Server side reads from {@code cachedMixture} (set by reaction tick's
 * exothermic/endothermic heat changes); client side reads the lerped chase target so the
 * thermometer animation interpolates smoothly to the synced value.
*/
    public float getTemperature() {
        if (hasLevel() && getLevel().isClientSide()) return temperature.getChaseTarget();
        // An empty vat reads the ambient/local temperature, so it reflects pollution and biome warmth.
        if (cachedMixture == null || cachedMixture.isEmpty())
            return petrolpark.mc.destroy.core.pollution.PollutionHelper.getLocalTemperature(getLevel(), getBlockPos());
        return cachedMixture.getTemperature();
    }

    /** Called by VatSideBE.setPowerFromAdjacentBlock
 * when an IVatHeaterBlock on an adjacent side changes state. Full tick pipeline consumes this
 * via Fourier's Law energy change.
*/
    public void changeHeatingPower(float powerChange) {
        heatingPower += powerChange;
        notifyUpdate();
    }

    /** Called by VatSideBE.setPowerFromAdjacentBlock when an
 * IUVLampBlock on an adjacent side changes state. Full tick pipeline consumes this via
 * ReactionContext UV field.
*/
    public void changeUVPower(float UVChange) {
        UVPower += UVChange;
        notifyUpdate();
    }

    /**
 * Gas pressure in Pa, relative to atmosphere (positive = pressurized, 0 = atmospheric,
 * negative = vacuum). 1:1
 *
 * <ul>
 * <li>Client side: return the LerpedFloat chase target (animation-driven).</li>
 * * <li>Server side, gas present: compute via ideal gas law.</li>
 * </ul>
*/
    @SuppressWarnings("null")
    public float getPressure() {
        if (hasLevel() && getLevel().isClientSide()) return pressure.getChaseTarget();
        if (vat.isEmpty()) return 0f;
        if (fluidBehaviour == null) return 0f;
        // An OPEN_VENT physically equalizes the vat's interior with the atmosphere. Any reading
        // is atmospheric (pressure delta = 0). This guards the heat path against the
        // overpressure-explosion check: a 15 MW blaze burner raises in-memory cachedMixture
        // temperature dramatically each tick, and the hybrid formula below (in-memory T × stored
        // gas-tank concentration) would otherwise diverge from real physics and trip the
        // {@code getPercentagePressure() >= 1} explode gate the moment heating begins.
        // Reaction-emitted gas is still purged through the side vent via the
        // {@code !isEmptyOrFullOfAir()} tick gate (which is independent of this method).
        if (openVentPos != null) return 0f;
        // VatFluidTank is doubly-nested (VatFluidTankBehaviour.VatTankSegment.VatFluidTank); use var
        // to avoid the fully-qualified spelling. We only need isEmpty / getFluid / getFluidAmount /
        // getCapacity here, all defined on the parent FluidTank class.
        var gasTank = fluidBehaviour.getGasHandler();
        if (gasTank == null || gasTank.isEmpty()) {
            var liquidTank = fluidBehaviour.getLiquidHandler();
            if (liquidTank == null) return 0f;
            // Full liquid vat = 0 (no gas headspace, no pressure differential).
            // Partly-empty all-vacuum vat = -AIR_PRESSURE (player can implode it).
            return liquidTank.getFluidAmount() == liquidTank.getCapacity() ? 0f : -AIR_PRESSURE;
        }
        petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture gasMixture =
            petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture.readNBT(
                petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture::new,
                gasTank.getFluid().getOrDefault(petrolpark.mc.destroy.DestroyDataComponents.MIXTURE, new net.minecraft.nbt.CompoundTag()));
        return petrolpark.mc.destroy.chemistry.legacy.LegacyReaction.GAS_CONSTANT * 1000f
            * getTemperature() * gasMixture.getTotalConcentration()
            - AIR_PRESSURE;
    }

    /** Lerped client-side pressure for animations (reads the pressure LerpedFloat).*/
    public float getClientPressure(float partialTicks) {
        return pressure.getValue(partialTicks);
    }

    // ============================================================
    // ThresholdSwitchObservable impl + pressure ratio + AABB + transform hook
    // ============================================================

    /**
 * Returns 0 when no Vat is assembled. Drives the vatExplodesAtHighPressure trigger in the
 * server tick.
*/
    public float getPercentagePressure() {
        if (vat.isEmpty()) return 0f;
        return getPressure() / vat.get().getMaxPressure();
    }

    @Override
    public int getMaxValue() {
        if (vat.isEmpty()) return 0;
        return getCapacity();
    }

    @Override
    public int getMinValue() {
        return 0;
    }

    @Override
    public int getCurrentValue() {
        if (vat.isEmpty()) return 0;
        return getLiquidTankContents().getAmount();
    }

    @Override
    public MutableComponent format(int value) {
        return DestroyLang.translateDirect("gui.vat.vat_liquid_amount", value);
    }

    /**
 *
 * <p>Encapsulates the outer wall corners (lowerCorner + upperCorner) directly;
 * {@code encapsulatingFullBlocks} already includes the full face of the upperCorner block,
 * so no inflate is needed.</p>
*/
    public AABB wholeVatAABB() {
        return AABB.encapsulatingFullBlocks(vat.get().getLowerCorner(), vat.get().getUpperCorner());
    }

    /** When the vat is viewed from angles where the controller cube is off-screen,
 * the {@link VatRenderer} BER doesn't run → side-cell overlays (THERMOMETER / BAROMETER /
 * VENT bars / fluid box) disappear (symptom: at certain viewing angles the thermometer and
 * barometer are not visible).
 *
 * <p>Fix: expand the BER's render bounding box to {@link #wholeVatAABB} so vanilla's frustum
 * culling treats any vat-side cell visibility as keeping the BER active. {@code AABB}
 * computed lazily — falls back to vanilla default when no Vat is assembled (1×1 around
 * controller, since there's nothing else to render).</p>
*/
    @Override
    @SuppressWarnings({"null", "deprecation"})
    protected AABB createRenderBoundingBox() {
        if (vat.isEmpty()) return super.createRenderBoundingBox();
        return wholeVatAABB();
    }

    /** Delegates to {@link Vat#transform}; if the transform would rotate
 * the Vat around a horizontal axis by 90° the Vat is forcibly deleted instead (can't survive
 * orientation changes since controller face matters).
*/
    @Override
    public void transform(BlockEntity blockEntity, StructureTransform transform) {
        vat.ifPresent(v -> v.transform(transform, getBlockPos()));
        if (transform.rotationAxis != net.minecraft.core.Direction.Axis.Y
            && (transform.rotation == net.minecraft.world.level.block.Rotation.CLOCKWISE_90
                || transform.rotation == net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90)) {
            deleteVat(getBlockPos());
        }
    }

    // ============================================================
    // IHaveLabGoggleInformation + ISpecialWhenHoveredBlockEntity
    // ============================================================

    /** Uses petrolpark-library's
 * {@link PetrolparkCreateClient#OUTLINER} (extends catnip's Outliner with {@code showAABB}).
 * TTL of 20 ticks = 1 second. Colour {@code 0xFF_fffec2} is Destroy's warm-cream Vat tint.
*/
    @Override
    public void whenLookedAt(LocalPlayer player, BlockHitResult result) {
        if (vat.isPresent()) {
            PetrolparkCreateClient.OUTLINER.showAABB(Pair.of("vat", getBlockPos()), wholeVatAABB(), 20)
                .colored(0xFF_fffec2);
        }
    }

    /** Shows live liquid + gas tank contents when the Vat is
 * fully assembled; otherwise a "not initialized" red-text hint once {@link #initializationTicks}
 * has elapsed.
*/
    @Override
    public boolean addToGoggleTooltip(java.util.List<Component> tooltip, boolean isPlayerSneaking) {
        if (vat.isPresent()) {
            vatFluidTooltip(this, tooltip);
        } else if (initializationTicks == 0) {
            TooltipHelper.cutTextComponent(
                DestroyLang.translate("tooltip.vat.not_initialized").component(),
                FontHelper.Palette.RED
            ).forEach(component -> DestroyLang.builder().add(component.copy()).forGoggles(tooltip));
        }
        return true;
    }

    /** Uses the tank getters + {@link DestroyLang#tankInfoTooltip}
 * for formatting.
*/
    public static void vatFluidTooltip(VatControllerBlockEntity vatController, java.util.List<Component> tooltip) {
        CreateLang.translate("gui.goggles.fluid_container").forGoggles(tooltip);
        DestroyLang.tankInfoTooltip(tooltip, DestroyLang.translate("tooltip.vat.contents_liquid"),
            vatController.getLiquidTank().getFluid(), vatController.getCapacity());
        DestroyLang.tankInfoTooltip(tooltip, DestroyLang.translate("tooltip.vat.contents_gas"),
            vatController.getGasTank().getFluid(), vatController.getCapacity());
    }

    /**
 * VatScreen BOTH view now shows live combined liquid + gas contents.
*/
    public petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture getCombinedReadOnlyMixture() {
        if (fluidBehaviour == null) return new petrolpark.mc.destroy.chemistry.legacy.ReadOnlyMixture();
        return fluidBehaviour.getCombinedReadOnlyMixture();
    }

    /**
 * Liquid tank handler. Returns the
 * {@link petrolpark.mc.destroy.core.chemistry.vat.VatFluidTankBehaviour#getLiquidHandler
 * VatFluidTankBehaviour's liquid handler} when the controller has a VatFluidTankBehaviour
 * behaviour attached, otherwise {@code null}.
*/
    public petrolpark.mc.destroy.core.chemistry.vat.VatFluidTankBehaviour.VatTankSegment.VatFluidTank getLiquidTank() {
        return fluidBehaviour == null ? null : fluidBehaviour.getLiquidHandler();
    }

    
    public petrolpark.mc.destroy.core.chemistry.vat.VatFluidTankBehaviour.VatTankSegment.VatFluidTank getGasTank() {
        return fluidBehaviour == null ? null : fluidBehaviour.getGasHandler();
    }

    
    /**
 * Rebuild {@link #cachedMixture} from current tank state. <b>Caller-responsibility</b>: if the
 * caller is adding NEW chemical content (e.g. {@link #addFluid}), the caller must explicitly
 * call {@code cachedMixture.disturbEquilibrium()} after this method to allow reactForTick to
 * re-evaluate. Drain paths must NOT disturb — otherwise tick's reactForTick branch fires →
 * {@code shouldUpdateFluidMixture=true} → {@link #updateFluidMixture} writes cachedMixture
 * back at vatCapacity volume → tanks refill to full → pump can extract infinitely.
*/
    public void updateCachedMixture() {
        if (fluidBehaviour != null) {
            cachedMixture = fluidBehaviour.getCombinedMixture();
            // reverted blanket disturb here. disturb belongs in addFluid only,
            // not in updateCachedMixture (which is also called by drain paths via
            // VatTankWrapper.updateVatGasVolume).
            if (DEBUG_LOG_ENABLED) {
                String contents = (cachedMixture == null) ? "null" :
                    cachedMixture.getContents(false).stream()
                        .map(m -> m.getFullID() + "=" + cachedMixture.getConcentrationOf(m))
                        .collect(java.util.stream.Collectors.joining(", "));
                petrolpark.mc.destroy.Destroy.LOGGER.info(
                    "[VAT-DBG] updateCachedMixture @ {}: rebuiltContents=[{}], rebuiltIsAtEq={} (suspect: mix() size==1 inherits AtEq)",
                    getBlockPos(), contents,
                    cachedMixture != null && cachedMixture.isAtEquilibrium());
            }
        }
    }

    /** Keeps gas tank consistent after liquid-level
 * changes (drain/fill).
*/
    public void updateGasVolume() {
        if (fluidBehaviour != null) fluidBehaviour.updateGasVolume();
    }

    /**
 * Inner wrapper class for Vat fluid I/O dispatch. Extends Create's {@code CombinedTankWrapper}
 * for multi-tank composition (liquid + gas). Fill path: converts non-Mixture stacks to
 * Mixture via {@link petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe}
 * lookup, then delegates to the caller-supplied {@link FluidConsumer} (for direct insertion
 * that's {@link #addFluid}, for side-cells that's a buffered consumer).
*/
    public static abstract class VatTankWrapper extends com.simibubi.create.foundation.fluid.CombinedTankWrapper {

        public final Object recipeCacheKey = new Object();
        public petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe lastRecipe;

        protected final java.util.function.Supplier<VatControllerBlockEntity> vatControllerGetter;
        /**
 * Consumer for Mixture fluid going into the Vat. For direct insertion this can be
 * {@link VatControllerBlockEntity#addFluid}; for Vat Side cells it's a buffered consumer.
*/
        protected final FluidConsumer consumer;

        public VatTankWrapper(java.util.function.Supplier<VatControllerBlockEntity> vatControllerGetter,
                              FluidConsumer consumer,
                              net.neoforged.neoforge.fluids.capability.IFluidHandler... tanks) {
            super(tanks);
            this.vatControllerGetter = vatControllerGetter;
            this.consumer = consumer;
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return petrolpark.mc.destroy.DestroyFluids.isMixture(stack);
        }

        @Override
        public int fill(FluidStack stack, FluidAction fluidAction) {
            VatControllerBlockEntity controller = vatControllerGetter.get();
            if (controller == null || !controller.canFitFluid()) return 0;

            // Non-Mixture → Mixture conversion via recipe lookup
            if (lastRecipe == null || !lastRecipe.getFluidIngredients().get(0).ingredient().test(stack)) {
                lastRecipe = com.simibubi.create.foundation.recipe.RecipeFinder
                    .get(recipeCacheKey, controller.getLevel(),
                        rh -> rh.value().getType() == petrolpark.mc.destroy.DestroyRecipeTypes.MIXTURE_CONVERSION.getType())
                    .stream()
                    .map(rh -> (petrolpark.mc.destroy.core.chemistry.recipe.MixtureConversionRecipe) rh.value())
                    .filter(r -> r.getFluidIngredients().get(0).ingredient().test(stack))
                    .findFirst()
                    .orElse(null);
            }
            if (lastRecipe != null) return consumer.fill(lastRecipe.apply(stack), fluidAction);

            // Direct Mixture insertion
            if (!petrolpark.mc.destroy.DestroyFluids.isMixture(stack)) return 0;
            return consumer.fill(stack, fluidAction);
        }

        protected FluidStack drainLiquidTank(FluidStack resource, FluidAction action) {
            if (vatControllerGetter.get() == null || vatControllerGetter.get().getLiquidTank() == null) return FluidStack.EMPTY;
            FluidStack s = vatControllerGetter.get().getLiquidTank().drain(resource, action);
            updateVatGasVolume(s, action);
            return s;
        }

        protected FluidStack drainLiquidTank(int amount, FluidAction action) {
            if (vatControllerGetter.get() == null || vatControllerGetter.get().getLiquidTank() == null) return FluidStack.EMPTY;
            FluidStack s = vatControllerGetter.get().getLiquidTank().drain(amount, action);
            updateVatGasVolume(s, action);
            return s;
        }

        protected FluidStack drainGasTank(FluidStack resource, FluidAction action) {
            if (vatControllerGetter.get() == null || vatControllerGetter.get().getGasTank() == null) return FluidStack.EMPTY;
            FluidStack s = vatControllerGetter.get().getGasTank().drain(resource, action);
            updateVatGasVolume(s, action);
            return s;
        }

        protected FluidStack drainGasTank(int amount, FluidAction action) {
            if (vatControllerGetter.get() == null || vatControllerGetter.get().getGasTank() == null) return FluidStack.EMPTY;
            FluidStack s = vatControllerGetter.get().getGasTank().drain(amount, action);
            updateVatGasVolume(s, action);
            return s;
        }

        /**
 * Extract gas at a given total molar density. The molar density of the Mixture inside
 * will not be affected. The amount of Fluid lost from the tank and the amount extracted
 * will not necessarily be the same.
*/
        protected FluidStack drainGasTankWithMolarDensity(int amount, double molarDensity, FluidAction action) {
            VatControllerBlockEntity controller = vatControllerGetter.get();
            if (controller == null || controller.getGasTankContents().isEmpty()) return FluidStack.EMPTY;

            petrolpark.mc.destroy.chemistry.legacy.LegacyMixture mixture =
                petrolpark.mc.destroy.chemistry.legacy.LegacyMixture.readNBT(
                    controller.getGasTankContents().getOrDefault(
                        petrolpark.mc.destroy.DestroyDataComponents.MIXTURE,
                        new net.minecraft.nbt.CompoundTag()));

            double concentration = mixture.getTotalConcentration();
            if (concentration <= 0d) return FluidStack.EMPTY;

            // To hand out `amount` mB of gas at `molarDensity` the recipe must pull
            // `amount * molarDensity / concentration` mB out of the tank, whose gas sits at
            // `concentration` — so the moles removed equal the moles delivered. The original drained
            // the INVERSE (`amount * concentration / molarDensity`, capped at `amount`): for a gas
            // thinner than air (concentration < molarDensity) it removed fewer moles than it returned,
            // so extracting through a pipe and pumping the result back minted gas from nothing.
            int tankAmountToDrain = (int) Math.ceil(amount * molarDensity / concentration);

            // Size the delivery off what the tank can actually give (peek, no mutation), then floor +
            // cap at the request so the delivered moles can never exceed the moles removed — this is
            // what closes the duplication even under a tank-limited drain or integer rounding.
            int lostAmount = drainGasTank(tankAmountToDrain, FluidAction.SIMULATE).getAmount();
            int deliveredAmount = Math.min(amount, (int) Math.floor((double) lostAmount * concentration / molarDensity));
            if (deliveredAmount <= 0) return FluidStack.EMPTY;

            // Don't scale the mixture when simulating — it confuses Create's fluid-network planning.
            if (action.execute()) {
                lostAmount = drainGasTank(tankAmountToDrain, FluidAction.EXECUTE).getAmount();
                if (lostAmount <= 0) return FluidStack.EMPTY;
                // Spread exactly the removed moles (concentration * lostAmount) across deliveredAmount
                // mB, so moles-out == moles-removed with no drift in either direction.
                mixture.scale((float) lostAmount / deliveredAmount);
            }

            return petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid.of(deliveredAmount, mixture);
        }

        protected void updateVatGasVolume(FluidStack drained, FluidAction action) {
            VatControllerBlockEntity vc = vatControllerGetter.get();
            if (action == FluidAction.EXECUTE && !drained.isEmpty() && vc != null && !vc.getLevel().isClientSide()) {
                // Order matters: expand the gas tank to fill the new headspace BEFORE
                // rebuilding cachedMixture. Otherwise getCombinedMixture sees totalVolume <
                // vatCapacity (gas tank still at old size, liquid just shrunk), then
                // setMixture's writeback at vat.getCapacity() can leave the liquid tank
                // out of sync — the round-trip math only conserves moles when both ends
                // use the same effective volume.
                vc.updateGasVolume();
                vc.updateCachedMixture();
            }
        }

        @FunctionalInterface
        public static interface FluidConsumer {
            int fill(FluidStack stack, FluidAction action);
        }
    }

    /** Display-link source variants for a Vat's contents:
 * <ul>
 * <li>{@link #createAllSource ALL} — combined liquid + gas (reports moles).</li>
 * <li>{@link #createSolutionSource SOLUTION} — liquid tank only.</li>
 * <li>{@link #createGasSource GAS} — gas tank only.</li>
 * </ul>
*/
    public static class VatDisplaySource
        extends petrolpark.mc.destroy.core.chemistry.MixtureContentsDisplaySource {

        private final java.util.function.Function<VatControllerBlockEntity, FluidStack> fluidGetter;
        private final String tankId;

        public static VatDisplaySource createAllSource() {
            return new VatDisplaySource(true, "all",
                v -> petrolpark.mc.destroy.chemistry.minecraft.MixtureFluid.of(v.getCapacity(), v.cachedMixture));
        }

        public static VatDisplaySource createGasSource() {
            return new VatDisplaySource(false, "gas", VatControllerBlockEntity::getGasTankContents);
        }

        public static VatDisplaySource createSolutionSource() {
            return new VatDisplaySource(false, "solution", VatControllerBlockEntity::getLiquidTankContents);
        }

        private VatDisplaySource(boolean moles, String tankId,
                                 java.util.function.Function<VatControllerBlockEntity, FluidStack> fluidGetter) {
            super(moles);
            this.tankId = tankId;
            this.fluidGetter = fluidGetter;
        }

        @Override
        public FluidStack getFluidStack(com.simibubi.create.content.redstone.displayLink.DisplayLinkContext context) {
            VatControllerBlockEntity controller = null;
            net.minecraft.world.level.block.entity.BlockEntity be = context.getSourceBlockEntity();
            if (be instanceof VatControllerBlockEntity vbe) controller = vbe;
            if (be instanceof VatSideBlockEntity vatSide) controller = vatSide.getController();
            if (controller == null) return FluidStack.EMPTY;
            return fluidGetter.apply(controller);
        }

        @Override
        public net.minecraft.network.chat.Component getName() {
            return petrolpark.mc.destroy.client.DestroyLang
                .translate("display_source.vat." + tankId).component();
        }
    }
}
