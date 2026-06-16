package petrolpark.mc.destroy.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.simibubi.create.content.fluids.FluidNetwork;

import net.neoforged.neoforge.fluids.FluidStack;

import petrolpark.mc.destroy.DestroyFluids;

/**
 *
 * <p><b>Symptom</b>: a 3-tank pump chain A→B→C carrying mixtures of differing composition
 * stalls B→C transfer whenever A→B is actively pumping. Pattern: B accumulates a few hundred
 * mB instantly, then pauses several seconds, repeats. B→C only resumes once A→B halts (source
 * empty). The stall disappears when A and B hold identical mixtures.</p>
 *
 * <p><b>Root cause</b> (Create source · {@code FluidNetwork.java} L97 / L201 / L210):</p>
 *
 * <pre>{@code
 * // L97 — when discovering flows from frontier:
 * if (!fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(flow.fluid, fluid)) {
 * iterator.remove(); // tank doesn't match network's tracked fluid → pruned
 * continue;
 * }
 *
 * // L201 — when draining source tank by-tank lookup:
 * if (!FluidStack.isSameFluidSameComponents(contained, fluid))
 * continue; // tank's content doesn't match network → skip this tank
 *
 * // L210 — fallback generic drain check:
 * if (!genericExtract.isEmpty() && FluidStack.isSameFluidSameComponents(genericExtract, fluid))
 * transfer = genericExtract;
 * }</pre>
 *
 * <p>FluidNetwork tracks the active fluid being transported in a {@code fluid} field, set when
 * the network first establishes flow. Once set, every tick it requires source tank contents to
 * match this snapshot via {@code isSameFluidSameComponents} — which compares Fluid type AND
 * full {@code DataComponentMap}. Each {@link
 * petrolpark.mc.destroy.core.fluid.GeniusFluidTankBehaviour.GeniusFluidTank#fill} call into B
 * mutates B's {@code MIXTURE} component (weighted-average of existing + incoming mixture) →
 * components differ from {@code network.fluid} → check fails → no drain → B→C stalls.</p>
 *
 * <h3>The relaxation</h3>
 *
 * For two {@code destroy:mixture} stacks (or {@code destroy:gas}) this returns {@code true}
 * regardless of MIXTURE component differences. This makes Create's pipe network treat all
 * mixtures as one network-fluid — appropriate because the underlying Fluid type IS the same
 * ({@code destroy:mixture}); they just carry different chemistry payloads. The receiving
 * tank's {@code GeniusFluidTank.fill} still does the proper molar-weighted merge, so chemistry
 * stays correct.
 *
 * <p>Non-mixture fluids fall through to the original {@code FluidStack.isSameFluidSameComponents}
 * — vanilla / other-mod fluids retain the strict identity check Create relies on.</p>
 *
 * <p><b>Tradeoff</b>: a pipe network that started transporting mixture-A could now also pull
 * mixture-B from a side-tank en route, mixing it into the flow. Acceptable: this is
 * Destroy-specific chemistry semantics and matches the in-tank merge behavior players
 * already expect. Vanilla / non-mixture pipe networks are unaffected.</p>
*/
@Mixin(FluidNetwork.class)
public abstract class FluidNetworkMixin {

    @WrapOperation(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/fluids/FluidStack;isSameFluidSameComponents(Lnet/neoforged/neoforge/fluids/FluidStack;Lnet/neoforged/neoforge/fluids/FluidStack;)Z"
        ),
        remap = false
    )
    private static boolean destroy$relaxMixtureIdentity(FluidStack a, FluidStack b, Operation<Boolean> original) {
        // Both stacks are destroy:mixture / destroy:gas → treat as same (skip components check).
        // The mixture-aware fill on the destination tank will handle the merge correctly.
        if (DestroyFluids.isMixture(a) && DestroyFluids.isMixture(b)) {
            return true;
        }
        return original.call(a, b);
    }
}
