package petrolpark.mc.destroy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

import petrolpark.mc.destroy.DestroyItems;

/**
 * Allow baby villagers to pick up {@link DestroyItems#BUCKET_AND_SPADE} dropped by players, so
 * the {@link petrolpark.mc.destroy.content.sandcastle.BuildSandCastleGoal} AI goal can find one
 * in their inventory and build a sand castle.
 *
 * <p>Without this mixin, baby villagers ignore the dropped item, the AI goal's
 * {@code mustHaveBucketAndSpade} check fails, and no sand castle builds.</p>
*/
@Mixin(Villager.class)
public abstract class VillagerMixin {

    @Inject(method = "wantsToPickUp", at = @At("HEAD"), cancellable = true)
    private void destroy$babyWantsBucketAndSpade(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Villager self = (Villager) (Object) this;
        if (self.isBaby() && DestroyItems.BUCKET_AND_SPADE.isIn(stack)) {
            cir.setReturnValue(true);
        }
    }
}
