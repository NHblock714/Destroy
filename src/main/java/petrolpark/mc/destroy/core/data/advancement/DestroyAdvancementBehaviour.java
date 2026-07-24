package petrolpark.mc.destroy.core.data.advancement;

import java.util.Set;
import java.util.function.Supplier;

import petrolpark.mc.library.compat.create.core.world.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import petrolpark.mc.destroy.DestroyAdvancementTrigger;

/**
 * A {@link AbstractRememberPlacerBehaviour} attached to Destroy BE's that need to award
 * {@link DestroyAdvancementTrigger advancement triggers} back to the player who placed the block.
 * Used by Basin / Vat / other machine BE's so chemistry reactions can grant advancements when the
 * placing player is still online + hasn't yet received them.
*/
public class DestroyAdvancementBehaviour extends AbstractRememberPlacerBehaviour {

    public static final BehaviourType<DestroyAdvancementBehaviour> TYPE = new BehaviourType<>();

    private final Set<DestroyAdvancementTrigger.Stub> advancements;

    public DestroyAdvancementBehaviour(SmartBlockEntity be, DestroyAdvancementTrigger.Stub... advancements) {
        super(be);
        this.advancements = Set.of(advancements);
    }

    public void awardDestroyAdvancement(DestroyAdvancementTrigger.Stub advancement) {
        awardDestroyAdvancementIf(advancement, () -> true);
    }

    /**
 * Trigger the given Destroy Advancement conditionally. Computes the condition lazily so it is
 * only evaluated if the player is online + ServerPlayer.
*/
    public void awardDestroyAdvancementIf(DestroyAdvancementTrigger.Stub advancement, Supplier<Boolean> condition) {
        Player placer = getPlayer();
        if (placer == null || !(placer instanceof ServerPlayer player)) return;
        if (condition.get()) advancement.award(getWorld(), player);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    @Override
    public boolean shouldRememberPlacer(Player placer) {
        return placer instanceof ServerPlayer && advancements.size() > 0;
    }
}
