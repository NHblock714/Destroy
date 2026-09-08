package petrolpark.mc.destroy.mixin.compat.create;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.neoforged.neoforge.capabilities.BlockCapabilityCache;

/**
 * Closes a hole in Create's {@code ICapabilityProvider.BlockCapabilityCacheProvider} that crashes
 * the server when a fluid network outlives one of its endpoints — blow up a pipe or tank near a
 * running pump and the next tick dies with
 * {@code IllegalStateException: Do not call getCapability on an invalid cache or from the
 * invalidation listener!}
 *
 * <p>NeoForge's {@code BlockCapabilityCache} listener reads:</p>
 *
 * <pre>{@code
 * canQuery = false; cacheValid = false; cachedCap = null;
 * if (isValid.getAsBoolean()) {
 *     invalidationListener.run();   // Create's listener, the only thing that sets `invalid`
 *     canQuery = true;
 *     return true;
 * }
 * return false;                     // canQuery stays false, and getCapability() throws from now on
 * }</pre>
 *
 * <p>Create guards its provider with an {@code invalid} flag, but that flag is only ever set from
 * the invalidation listener — which NeoForge deliberately skips in the one branch that leaves the
 * cache permanently unqueryable. {@code FlowSource.FluidHandler} builds these caches with
 * {@code isValid = () -> !be.isRemoved()}, so destroying that block entity while another one (any
 * pump still ticking the network) keeps the provider alive lands exactly in the unguarded branch.</p>
 *
 * <p>Catch the throw once, set Create's own flag, and report no capability — which is what Create's
 * guard already does for every invalidation it does see. The network then drops the endpoint and
 * rebuilds, and Create's own check short-circuits every later call on that provider.</p>
 */
@Mixin(targets = "com.simibubi.create.foundation.ICapabilityProvider$BlockCapabilityCacheProvider", remap = false)
public abstract class BlockCapabilityCacheProviderMixin {

    @Shadow private volatile boolean invalid;

    @WrapOperation(
        method = "getCapability",
        at = @At(
            value = "INVOKE",
            target = "Lnet/neoforged/neoforge/capabilities/BlockCapabilityCache;getCapability()Ljava/lang/Object;"
        ),
        remap = false
    )
    private Object destroy$retireUnqueryableCache(BlockCapabilityCache<?, ?> cache, Operation<Object> original) {
        try {
            return original.call(cache);
        } catch (IllegalStateException e) {
            invalid = true;
            return null;
        }
    }
}
