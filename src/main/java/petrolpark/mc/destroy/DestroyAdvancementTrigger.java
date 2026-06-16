package petrolpark.mc.destroy;

import static petrolpark.mc.destroy.Destroy.REGISTRATE;

import java.util.HashMap;
import java.util.Map;

import com.tterrag.registrate.util.entry.RegistryEntry;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import petrolpark.mc.destroy.chemistry.legacy.LegacyReaction;
import petrolpark.mc.destroy.chemistry.legacy.ReactionResult;
import petrolpark.mc.destroy.chemistry.legacy.reactionresult.DestroyAdvancementReactionResult;
import petrolpark.mc.destroy.core.data.advancement.SimpleAdvancementTrigger;

/**
 * Destroy's custom advancement trigger registry. Each {@link Stub} handle pairs a registered
 * {@link SimpleAdvancementTrigger} with an {@code award(level, player)} method that
 * actually fires the trigger on server-side {@link ServerPlayer}s.
 *
 * <p>Each {@link Stub} now holds a {@link RegistryEntry} pointing to its registered
 * {@link SimpleAdvancementTrigger}. {@link Stub#award(Level, Player)} is functional: server-side
 * Player → trigger fires; client-side Player / non-ServerPlayer → no-op.</p>
*/
public class DestroyAdvancementTrigger {

    /**
 * Shared registry for all Stub handles — ensures REGISTRATE calls happen at class-load time
 * (consistent with Destroy's other Registrate-based registrations).
*/
    private static final Map<String, Stub> REGISTRY = new HashMap<>();

    public static final Stub OPEN_AGING_BARREL    = new Stub("open_aging_barrel");
    public static final Stub HYPERACCUMULATE      = new Stub("hyperaccumulate");
    public static final Stub DETONATE             = new Stub("detonate");
    public static final Stub HANGOVER             = new Stub("hangover");
    public static final Stub VERY_DRUNK           = new Stub("very_drunk");
    public static final Stub CATALYTIC_CONVERTER  = new Stub("catalytic_converter");
    public static final Stub SIPHON               = new Stub("siphon");
    public static final Stub JUMP_ON_SAND_CASTLE  = new Stub("jump_on_sand_castle");
    public static final Stub CURE_HANGOVER        = new Stub("cure_hangover");
    public static final Stub TAKE_BABY_BLUE       = new Stub("take_baby_blue");
    public static final Stub TAP_TREE             = new Stub("tap_tree");
    public static final Stub URINATE              = new Stub("urinate");
    public static final Stub EXTRUDE              = new Stub("extrude");
    public static final Stub MECHANICAL_SIEVE     = new Stub("mechanical_sieve");
    public static final Stub FILL_SEISMOGRAPH     = new Stub("fill_seismograph");
    public static final Stub COMPLETE_SEISMOGRAPH = new Stub("complete_seismograph");
    public static final Stub USE_SEISMOMETER      = new Stub("use_seismometer");

    // Keypunch subdir ice-breaker triggers. Trigger id matches upstream "keypunch" so the
    // existing keypunch.json advancement file picks it up (was registered as "use_keypunch"
    // pre-v0.3.1, causing the advancement to never fire).
    public static final Stub USE_KEYPUNCH         = new Stub("keypunch");
    public static final Stub KEYPUNCH_FIVE        = new Stub("keypunch_five");

    // Centrifuge BE awards USE_CENTRIFUGE on every successful centrifugation recipe.
    public static final Stub USE_CENTRIFUGE       = new Stub("use_centrifuge");

    // BubbleCap (controller) awards DISTILL on every successful distillation.
    public static final Stub DISTILL              = new Stub("distill");

    // Pumpjack awards USE_PUMPJACK on every successful oil pump tick.
    public static final Stub USE_PUMPJACK         = new Stub("use_pumpjack");

    // BlowpipeBlockEntity awards BLOWPIPE on every finished glassblowing.
    public static final Stub BLOWPIPE             = new Stub("blowpipe");

    // Dynamo BE awards these when the corresponding recipe type completes.
    public static final Stub ARC_FURNACE              = new Stub("arc_furnace");
    public static final Stub CHARGE_WITH_DYNAMO       = new Stub("charge_with_dynamo");
    public static final Stub ELECTROLYZE_WITH_DYNAMO  = new Stub("electrolyze_with_dynamo");

    // VatControllerBE chunk 10 server tick awards this when the cached mixture
    // is reacting (i.e. not at equilibrium) after an insertion.
    public static final Stub USE_VAT                  = new Stub("use_vat");

    // ColorimeterBE awards this every tick when observing a molecule in an
    // assembled Vat.
    public static final Stub COLORIMETER              = new Stub("colorimeter");

    // PeriodicTableBlock.onEntityPlace awards this when a player places an element
    // block that completes the 2D periodic-table grid (via EntityPlaceEvent check).
    public static final Stub PERIODIC_TABLE           = new Stub("periodic_table");

    // Stray-capture-with-empty-Blaze-Burner advancement trigger. Fires when player
    // right-clicks an EMPTY_BLAZE_BURNER on a Stray entity and gets a COOLER block back. Wired
    // by DestroyCommonEvents#onPlayerEntityInteractSpecific.
    public static final Stub CAPTURE_STRAY      = new Stub("capture_stray");

    // Tear-collection advancement (right-click glass bottle on a crying entity, or
    // right-click block while having CRYING effect). Wired by the same handler as CAPTURE_STRAY
    // + onPlayerRightClickBlock.
    public static final Stub COLLECT_TEARS      = new Stub("collect_tears");

    // Chemistry-batch trigger handles — referenced by generic reactions + the
    // DestroyReactions data file.
    // Stubs added v0.3.1 to plug the trigger ↔ advancement registry gap. Each Stub is
    // wired below: SHOOT_HEFTY_BEETROOT + FIREPROOF_FLINT_AND_STEEL
    // in DestroyCommonEvents, CUT_ONIONS in the FD CuttingBoardMixin, HABER_PROCESS +
    // STEAM_REFORMATION in DestroyReactions reaction definitions.
    public static final Stub SHOOT_HEFTY_BEETROOT     = new Stub("beetroot_potato_cannon");
    public static final Stub CUT_ONIONS               = new Stub("cut_onions");
    public static final Stub FIREPROOF_FLINT_AND_STEEL = new Stub("fireproof_flint_and_steel");
    public static final Stub HABER_PROCESS            = new Stub("haber_process");
    public static final Stub STEAM_REFORMATION        = new Stub("steam_reformation");

    public static final Stub ACETONE            = new Stub("acetone");
    public static final Stub PROPANOL           = new Stub("propanol");
    public static final Stub ADDITION_POLYMER   = new Stub("addition_polymer");
    public static final Stub AIBN               = new Stub("aibn");
    public static final Stub ANDRUSSOW_PROCESS  = new Stub("andrussow_process");
    public static final Stub ETHYLANTHRAQUINONE = new Stub("ethylanthraquinone");
    public static final Stub HYDRAZINE          = new Stub("hydrazine");
    public static final Stub HYDROGEN_PEROXIDE  = new Stub("hydrogen_peroxide");
    public static final Stub OSTWALD_PROCESS    = new Stub("ostwald_process");
    public static final Stub TRY_TO_MAKE_METH   = new Stub("try_to_make_meth");

    /** Class-load trigger — REGISTRATE-based registration happens in Stub ctor via static init.*/
    public static void register() {
        // no-op: Registrate handles bus registration
    }

    /**
 * Handle for a registered custom criterion trigger. Each Stub wraps a
 * {@link RegistryEntry}{@code <CriterionTrigger<?>, SimpleAdvancementTrigger>}; calling
 * {@link #award(Level, Player)} fires the trigger on server-side ServerPlayer.
*/
    public static final class Stub {
        private final String id;
        private final RegistryEntry<CriterionTrigger<?>, SimpleAdvancementTrigger> entry;

        private Stub(String id) {
            this.id = id;
            // register via Destroy's REGISTRATE (auto wires NeoForge DeferredRegister)
            this.entry = REGISTRATE.criterionTrigger(id, SimpleAdvancementTrigger::new);
            REGISTRY.put(id, this);
        }

        /**
 * Fire this trigger for the given player. Server-side only — client-side calls no-op.
*/
        public void award(Level level, Player player) {
            if (level.isClientSide()) return;
            if (player instanceof ServerPlayer serverPlayer) {
                entry.get().trigger(serverPlayer);
            }
        }

        public String id() {
            return id;
        }

        /**
 * Factory for the chemistry Reaction pathway — used as a method reference
 * ({@code DestroyAdvancementTrigger.ACETONE::asReactionResult}) passed to
 * {@link LegacyReaction.ReactionBuilder#withResult(float, java.util.function.BiFunction)
 * withResult}. Returns a {@link DestroyAdvancementReactionResult} whose
 * {@code onBasinReaction}/{@code onVatReaction} calls {@link #award} server-side.
*/
        public ReactionResult asReactionResult(Float moles, LegacyReaction reaction) {
            return new DestroyAdvancementReactionResult(moles, reaction, this);
        }
    }
}
