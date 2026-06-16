package petrolpark.mc.destroy.core.chemistry.vat.observation;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * {@link BlockEntityBehaviour} that observes a caller-supplied float quantity + maps it to a
 * 0-15 redstone signal strength based on configurable lower/upper thresholds. Used by Vat-side
 * BEs (thermometer / barometer) + Colorimeter BE for redstone monitor outputs.
 *
 * <p><b>Consumers</b> (future T2a work):</p>
 * <ul>
 * <li>{@code RedstoneQuantityMonitorThresholdChangeC2SPacket} — looks up this
 * behaviour on the server via {@code BlockEntityBehaviour.get}.</li>
 * <li>{@code VatSideBlockEntity.addBehaviours} (future) — attaches this to THERMOMETER /
 * BAROMETER side-cells with VatControllerBE.getTemperature / getPressure suppliers.</li>
 * <li>{@code ColorimeterBlockEntity.addBehaviours} — attach with molecule-concentration
 * supplier.</li>
 * </ul>
*/
public class RedstoneQuantityMonitorBehaviour extends BlockEntityBehaviour {

    public static final BehaviourType<RedstoneQuantityMonitorBehaviour> TYPE = new BehaviourType<>();

    @Nonnull
    public Optional<Supplier<Float>> quantityObserved;
    protected Function<Float, Component> label = f -> Component.empty();

    public float lowerThreshold;
    public float upperThreshold;
    protected int oldStrength;
    protected boolean isFirstTick;

    protected IntConsumer strengthChangeCallback = i -> {};

    public RedstoneQuantityMonitorBehaviour(SmartBlockEntity be) {
        super(be);
        quantityObserved = Optional.empty();
    }

    @Override
    public void initialize() {
        isFirstTick = true;
    }

    public RedstoneQuantityMonitorBehaviour withLabel(Function<Float, Component> label) {
        this.label = label;
        return this;
    }

    public RedstoneQuantityMonitorBehaviour onStrengthChanged(IntConsumer callback) {
        strengthChangeCallback = callback;
        return this;
    }

    public int getStrength() {
        return oldStrength;
    }

    
    public void update() {
        if (getWorld() == null) return;
        getWorld().updateNeighborsAt(getPos(), blockEntity.getBlockState().getBlock());
        // also fire neighbor-change on each adjacent position so wires that propagated
        // signal can re-evaluate. Mirrors vanilla diode/comparator update behavior.
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
            getWorld().updateNeighborsAt(getPos().relative(dir), blockEntity.getBlockState().getBlock());
        }
    }

    public Component getLabelledQuantity() {
        return quantityObserved.map(Supplier::get).map(label::apply).orElse(Component.empty());
    }

    /** on 0 → force update + reset).*/
    private int periodicUpdateCounter = 0;

    
    private static final boolean DEBUG_LOG_ENABLED =
        Boolean.parseBoolean(System.getProperty("destroy.vatDebug", "false"));
    private long debugTickCounter = 0;

    @Override
    public void tick() {
        if (isFirstTick) {
            isFirstTick = false;
            // on first compute pass, force an update regardless of strength change so
            // adjacent wire/comparator immediately see whatever initial signal exists. Without this,
            // if oldStrength happens to equal initial-computed-strength (e.g. both 0), update() never
            // fires → wire stays unpowered even though signal logic is correct.
            periodicUpdateCounter = 0;
            return;
        }

        int strength = 0;
        if (quantityObserved.isPresent()) {
            strength = (int) (Mth.clamp((quantityObserved.get().get() - lowerThreshold) / (upperThreshold - lowerThreshold), 0f, 1f) * 15f);
        }

        if (DEBUG_LOG_ENABLED && (debugTickCounter++ % 20 == 0)) {
            float observed = quantityObserved.isPresent() ? quantityObserved.get().get() : Float.NaN;
            petrolpark.mc.destroy.Destroy.LOGGER.info(
                "[VAT-DBG] redstoneMonitor.tick @ {}: observed={}, lower={}, upper={}, computed={}, oldStrength={}, hasObserver={}",
                getPos(), observed, lowerThreshold, upperThreshold, strength, oldStrength, quantityObserved.isPresent());
        }
        // call update() on EITHER strength change OR every 20 ticks (1 second) when there's
        // an active observer. This handles the case where strength is stable but vanilla redstone
        // missed an earlier neighbor-change notification (chunk load, BE re-init, etc.).
        boolean strengthChanged = strength != oldStrength;
        boolean periodicTick = quantityObserved.isPresent() && --periodicUpdateCounter <= 0;
        if (strengthChanged) {
            oldStrength = strength;
        }
        if (strengthChanged || periodicTick) {
            update();
            periodicUpdateCounter = 20; // 1 second
        }
        if (strengthChanged) {
            strengthChangeCallback.accept(strength);
        }
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(nbt, registries, clientPacket);
        oldStrength = nbt.getInt("OldRedstoneStrength");
        lowerThreshold = nbt.getFloat("LowerObservedQuantityThreshold");
        upperThreshold = nbt.getFloat("UpperObservedQuantityThreshold");
    }

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(nbt, registries, clientPacket);
        nbt.putInt("OldRedstoneStrength", oldStrength);
        nbt.putFloat("LowerObservedQuantityThreshold", lowerThreshold);
        nbt.putFloat("UpperObservedQuantityThreshold", upperThreshold);
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public void notifyUpdate() {
        blockEntity.notifyUpdate();
    }
}
