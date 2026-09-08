package petrolpark.mc.destroy.mixin.compat.create;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import petrolpark.mc.destroy.core.pollution.PollutingBehaviour;

/**
 * Attaches a {@link PollutingBehaviour} to Create's Item Drain.
 */
@Mixin(ItemDrainBlockEntity.class)
public abstract class ItemDrainBlockEntityAddBehavioursMixin extends SmartBlockEntity {

    public ItemDrainBlockEntityAddBehavioursMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        throw new AssertionError();
    };

    @Inject(
        method = "Lcom/simibubi/create/content/fluids/drain/ItemDrainBlockEntity;addBehaviours(Ljava/util/List;)V",
        at = @At("HEAD"),
        remap = false
    )
    public void destroy$addPollutingBehaviour(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        behaviours.add(new PollutingBehaviour(this));
    };
};
