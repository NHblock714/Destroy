package petrolpark.mc.destroy.core.registrate;

import com.mojang.datafixers.util.Function3;
import com.petrolpark.AbstractPetrolparkRegistrate;
import com.simibubi.create.foundation.gui.AllIcons;
import com.tterrag.registrate.util.entry.RegistryEntry;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import petrolpark.mc.destroy.DestroyRegistries;
import petrolpark.mc.destroy.core.pollution.PollutionType;

/**
 * 1.4.31 made {@code PetrolparkRegistrate} no-args (hardcoded to the "petrolpark" modid), so this
 * extends {@link AbstractPetrolparkRegistrate} directly to keep the {@code (String)} ctor path.
 * Create-Library's own {@code Mods.DESTROY.registrate()} returns a different type
 * {@code OtherModRegistrate} that isn't needed here (Destroy has its own registrate
 * extensions below for pollution types).
*/
public class DestroyRegistrate extends AbstractPetrolparkRegistrate<DestroyRegistrate> {

    public DestroyRegistrate(String modid) {
        super(modid);
    };

    public <POLLUTION_TYPE extends PollutionType<Level>> RegistryEntry<PollutionType<Level>, POLLUTION_TYPE> levelPollutionType(String name, AllIcons icon, Function3<Boolean, AllIcons, String, POLLUTION_TYPE> factory) {
        return simple(name, DestroyRegistries.Keys.LEVEL_POLLUTION_TYPE, () -> factory.apply(false, icon, Util.makeDescriptionId("pollution_type", ResourceLocation.fromNamespaceAndPath(getModid(), name))));
    };

    public <POLLUTION_TYPE extends PollutionType<ChunkAccess>> RegistryEntry<PollutionType<ChunkAccess>, POLLUTION_TYPE> chunkPollutionType(String name, AllIcons icon, Function3<Boolean, AllIcons, String, POLLUTION_TYPE> factory) {
        return simple(name, DestroyRegistries.Keys.CHUNK_POLLUTION_TYPE, () -> factory.apply(true, icon, Util.makeDescriptionId("pollution_type", ResourceLocation.fromNamespaceAndPath(getModid(), name))));
    };
    
};
