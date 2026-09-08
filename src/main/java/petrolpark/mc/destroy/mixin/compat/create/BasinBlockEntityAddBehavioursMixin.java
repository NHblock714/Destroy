package petrolpark.mc.destroy.mixin.compat.create;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import petrolpark.mc.destroy.core.data.advancement.DestroyAdvancementBehaviour;
import petrolpark.mc.destroy.core.pollution.PollutingBehaviour;

/**
 * Attaches Destroy's pollution and Advancement Behaviours to Create's Basin.
 *
 * <p>The class name is qualified with {@code AddBehaviours} so it does not collide with other
 * mixins into the same target — several may share one target, but distinct names keep debug and
 * remap output readable.</p>
 */
@Mixin(BasinBlockEntity.class)
public abstract class BasinBlockEntityAddBehavioursMixin extends SmartBlockEntity {

    public BasinBlockEntityAddBehavioursMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        throw new AssertionError();
    };

    @Inject(
        method = "Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;addBehaviours(Ljava/util/List;)V",
        at = @At("HEAD"),
        remap = false
    )
    public void destroy$addPollutingBehaviour(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        behaviours.add(new PollutingBehaviour(this));
        // Tracks the placer so DestroyAdvancementReactionResult can award chemistry
        // achievements (acetone, propanol, polymers, Andrussow, ostwald, …) when reactions
        // happen inside this basin. Has to be attached during addBehaviours rather than as a
        // deferred behaviour — otherwise the placer-record event has already fired by the time
        // it gets registered.
        behaviours.add(new DestroyAdvancementBehaviour(this));
    };
};
