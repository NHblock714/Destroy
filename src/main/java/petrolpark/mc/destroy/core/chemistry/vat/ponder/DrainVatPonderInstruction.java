package petrolpark.mc.destroy.core.chemistry.vat.ponder;

import net.createmod.ponder.foundation.PonderScene;
import net.createmod.ponder.foundation.instruction.PonderInstruction;
import net.minecraft.core.BlockPos;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;

/**
 * One-shot {@link PonderInstruction} that drains a specified amount from a
 * {@link petrolpark.mc.destroy.core.chemistry.vat.VatControllerBlockEntity VatControllerBlockEntity}
 * — liquid or gas phase — at the given {@link BlockPos} in a Ponder scene world. Used by Vat Ponder
 * scenes (future vatFluids work) to demonstrate Vat draining via the vent / pipe side-cells.
*/
public class DrainVatPonderInstruction extends PonderInstruction {

    public final BlockPos vatControllerPos;
    public final boolean liquid;
    public final int drainAmount;

    public DrainVatPonderInstruction(BlockPos vatControllerPos, int drainAmount) {
        this(vatControllerPos, true, drainAmount);
    }

    public DrainVatPonderInstruction(BlockPos vatControllerPos, boolean liquid, int drainAmount) {
        this.vatControllerPos = vatControllerPos;
        this.liquid = liquid;
        this.drainAmount = drainAmount;
    }

    @Override
    public boolean isComplete() {
        return true;
    }

    @Override
    public void tick(PonderScene scene) {
        // The VatControllerBlockEntity is referenced via getBlockEntity() to validate scene-world
        // presence + preserve ifPresent-chain invariants; drain itself is no-op until the tank
        // wrapper + storage subsystem lands.
        scene.getWorld().getBlockEntity(vatControllerPos, DestroyBlockEntityTypes.VAT_CONTROLLER.get()).ifPresent(vat -> {
            // TODO(full port): new SinglePhaseVatExtraction(vat, !liquid).drain(drainAmount, FluidAction.EXECUTE);
        });
    }
}
