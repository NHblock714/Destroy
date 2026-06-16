package petrolpark.mc.destroy.mixin.compat.create;

import java.util.ArrayList;
import java.util.Arrays;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;

/**
 * Inject a new {@code FROSTING} value into Create's {@link HeatLevel} enum. Destroy's Cooler
 * block uses this value to signal "inverse Blaze Burner" behaviour: Basin + Mixer + Deployer
 * etc. can pattern-match against {@code HeatLevel.valueOf("FROSTING")} the same way they match
 * against {@code KINDLED}/{@code SEETHING}, so FROSTING-tagged recipes can be wired through
 * Create's existing processing BE machinery without forking every BE class.
 *
 * <p>Registered via {@code destroy.mixins.json} {@code "mixins"} array.
 * Technique originally from
 * <a href="https://github.com/LudoCrypt/Noteblock-Expansion-Forge/blob/main/src/main/java/net/ludocrypt/nbexpand/mixin/NoteblockInstrumentMixin.java">Noteblock-Expansion-Forge NoteblockInstrumentMixin</a>.</p>
*/
@Mixin(HeatLevel.class)
public abstract class HeatLevelMixin {

    @Shadow
    @Final
    @Mutable
    private static HeatLevel[] $VALUES;

    @SuppressWarnings("unused")
    private static final HeatLevel FROSTING = heatLevelModifier$addValue("FROSTING");

    @Invoker("<init>")
    public static HeatLevel heatLevelModifier$invokeInit(String internalName, int internalId) {
        throw new AssertionError();
    }

    /**
 * Appends a new enum constant to {@link HeatLevel#$VALUES}. The constant's ordinal is
 * {@code lastExisting.ordinal() + 1} (so it sorts as the "hottest" — arbitrary, since
 * Destroy's FROSTING is conceptually opposite to hotness but {@code $VALUES} ordering only
 * matters for {@code values()} iteration).
*/
    private static HeatLevel heatLevelModifier$addValue(String internalName) {
        ArrayList<HeatLevel> heatLevels = new ArrayList<HeatLevel>(Arrays.asList(HeatLevelMixin.$VALUES));
        HeatLevel heatLevel = heatLevelModifier$invokeInit(internalName,
            heatLevels.get(heatLevels.size() - 1).ordinal() + 1);
        heatLevels.add(heatLevel);
        HeatLevelMixin.$VALUES = heatLevels.toArray(new HeatLevel[0]);
        return heatLevel;
    }
}
