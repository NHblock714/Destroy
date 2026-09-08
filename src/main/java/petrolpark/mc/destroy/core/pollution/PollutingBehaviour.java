package petrolpark.mc.destroy.core.pollution;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Behaviour for BlockEntities which contain Fluids and should release those Fluids
 * into the atmosphere if destroyed.
*/
public class PollutingBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<PollutingBehaviour> TYPE = new BehaviourType<>();

    public PollutingBehaviour(SmartBlockEntity be) {
        super(be);
    };

    @Override
    public void destroy() {
        // Can be called once the BlockEntity has already left the Level, so tolerate a null one.
        var level = getWorld();
        var pos = getPos();
        if (level == null || level.isClientSide()) { super.destroy(); return; };

        final IFluidHandler handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler == null) { super.destroy(); return; };

        final List<FluidStack> toRelease = new ArrayList<>(handler.getTanks());
        for (int i = 0; i < handler.getTanks(); i++) {
            FluidStack stack = handler.getFluidInTank(i);
            if (stack.isEmpty()) continue;
            toRelease.add(stack);
        };

        if (!toRelease.isEmpty()) {
            PollutionHelper.pollute(level, pos, toRelease.toArray(FluidStack[]::new));
        };

        super.destroy();
    };

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    };
};
