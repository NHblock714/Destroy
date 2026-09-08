package petrolpark.mc.destroy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import petrolpark.mc.destroy.core.pollution.FluidPollutionEntry;
import petrolpark.mc.destroy.core.pollution.PollutionType;

@EventBusSubscriber
public class DestroyDataMapTypes {
    
    private static final List<DataMapType<?, ?>> DATA_MAP_TYPES = new ArrayList<>(1);

    public static final DataMapType<PollutionType<Level>, PollutionType.Properties> LEVEL_POLLUTION_PROPERTIES = register(DataMapType
        .builder(
            Destroy.asResource("properties"),
            DestroyRegistries.Keys.LEVEL_POLLUTION_TYPE,
            PollutionType.Properties.CODEC
        ).synced(PollutionType.Properties.CODEC, true)
        .build()
    );

    public static final DataMapType<PollutionType<ChunkAccess>, PollutionType.Properties> CHUNK_POLLUTION_PROPERTIES = register(DataMapType
        .builder(
            Destroy.asResource("properties"),
            DestroyRegistries.Keys.CHUNK_POLLUTION_TYPE,
            PollutionType.Properties.CODEC
        ).synced(PollutionType.Properties.CODEC, true)
        .build()
    ); 

    public static final DataMapType<PollutionType<ChunkAccess>, PollutionType.SpreadingProperties> CHUNK_POLLUTION_SPREADING_PROPERTIES = register(DataMapType
        .builder(
            Destroy.asResource("spreading_properties"),
            DestroyRegistries.Keys.CHUNK_POLLUTION_TYPE,
            PollutionType.SpreadingProperties.CODEC
        ).synced(PollutionType.SpreadingProperties.CODEC, true)
        .build()
    ); 

    // Attaches one FluidPollutionEntry (a Fluid tag plus a multiplier) to a PollutionType.
    // PollutionHelper.polluteSingleFluid walks every registered type, reads its entry back through
    // holder.getData(...), and pollutes only if the released Fluid carries that entry's tag.
    // A type with no entry here accrues no pollution from fluids at all.
    public static final DataMapType<PollutionType<Level>, FluidPollutionEntry> LEVEL_POLLUTION_FLUID_TAG = register(DataMapType
        .builder(
            Destroy.asResource("fluid_tag"),
            DestroyRegistries.Keys.LEVEL_POLLUTION_TYPE,
            FluidPollutionEntry.CODEC
        ).synced(FluidPollutionEntry.CODEC, true)
        .build()
    );

    public static final DataMapType<PollutionType<ChunkAccess>, FluidPollutionEntry> CHUNK_POLLUTION_FLUID_TAG = register(DataMapType
        .builder(
            Destroy.asResource("fluid_tag"),
            DestroyRegistries.Keys.CHUNK_POLLUTION_TYPE,
            FluidPollutionEntry.CODEC
        ).synced(FluidPollutionEntry.CODEC, true)
        .build()
    );

    public static final DataMapType<Block, Block> ACID_RAIN_REPLACEMENTS = register(DataMapType
        .builder(
            Destroy.asResource("acid_rain_replacement"),
            Registries.BLOCK,
            BuiltInRegistries.BLOCK.byNameCodec()
        ).synced(BuiltInRegistries.BLOCK.byNameCodec(), true)
        .build()
    );

    private static final <TYPE extends DataMapType<?, ?>> TYPE register(TYPE dataMapType) {
        DATA_MAP_TYPES.add(dataMapType);
        return dataMapType;
    };

    @SubscribeEvent
    public static final void onRegisterDataMapTypes(RegisterDataMapTypesEvent event) {
        DATA_MAP_TYPES.forEach(event::register);
    };
};
