package petrolpark.mc.destroy;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import petrolpark.mc.destroy.core.chemistry.hazard.EntityChemicalPoisonAttachment;
import petrolpark.mc.destroy.core.chemistry.novelcompounds.PlayerNovelCompoundsAttachment;
import petrolpark.mc.destroy.core.player.PlayerPreviousPositions;
import petrolpark.mc.destroy.core.pollution.ChunkPollution;
import petrolpark.mc.destroy.core.pollution.LevelPollution;

public class DestroyAttachmentTypes {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Destroy.MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<LevelPollution>> LEVEL_POLLUTION = ATTACHMENT_TYPES.register("level_pollution", AttachmentType.builder(LevelPollution::create)
        .serialize(LevelPollution.SERIALIZER)
        ::build
    );

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ChunkPollution>> CHUNK_POLLUTION = ATTACHMENT_TYPES.register("chunk_pollution", AttachmentType.builder(ChunkPollution::create)
        .serialize(ChunkPollution.SERIALIZER)
        ::build
    );

    // PLAYER_PREVIOUS_POSITIONS: rolling queue of recent positions, wound back by
    // CHORUS_WINE_BOTTLE to teleport the drinker to where they were; kept across death.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerPreviousPositions>> PLAYER_PREVIOUS_POSITIONS =
        ATTACHMENT_TYPES.register("player_previous_positions", () -> AttachmentType
            .builder(PlayerPreviousPositions::new)
            .serialize(PlayerPreviousPositions.CODEC)
            .copyOnDeath()
            .build());

    // Replaces Forge Capability API.
    // ENTITY_CHEMICAL_POISON: per-LivingEntity toxic Molecule tracker for chemistry poisoning.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<EntityChemicalPoisonAttachment>> ENTITY_CHEMICAL_POISON =
        ATTACHMENT_TYPES.register("entity_chemical_poison", () -> AttachmentType
            .builder(EntityChemicalPoisonAttachment::new)
            .serialize(EntityChemicalPoisonAttachment.CODEC)
            .build());

    // PLAYER_NOVEL_COMPOUNDS: per-Player set of FROWNS ids for every novel Molecule ever synthesized.
    // copyFrom(other) path.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerNovelCompoundsAttachment>> PLAYER_NOVEL_COMPOUNDS =
        ATTACHMENT_TYPES.register("player_novel_compounds", () -> AttachmentType
            .builder(PlayerNovelCompoundsAttachment::new)
            .serialize(PlayerNovelCompoundsAttachment.CODEC)
            .copyOnDeath()
            .build());

    // PLAYER_BABY_BLUE_ADDICTION: per-Player scalar addiction counter (0..maxAddictionLevel).
    // had a matching copyFrom(source) method invoked by the mod's death/respawn handler.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<petrolpark.mc.destroy.content.product.babyblue.PlayerBabyBlueAddictionAttachment>> PLAYER_BABY_BLUE_ADDICTION =
        ATTACHMENT_TYPES.register("player_baby_blue_addiction", () -> AttachmentType
            .builder(petrolpark.mc.destroy.content.product.babyblue.PlayerBabyBlueAddictionAttachment::new)
            .serialize(petrolpark.mc.destroy.content.product.babyblue.PlayerBabyBlueAddictionAttachment.CODEC)
            .copyOnDeath()
            .build());

    // CHUNK_CRUDE_OIL: per-chunk crude oil deposit (Perlin noise + random seismic-herring pattern).
    // Attaches to ChunkAccess — lookup via chunk.getData(CHUNK_CRUDE_OIL).
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<petrolpark.mc.destroy.content.oil.ChunkCrudeOil>> CHUNK_CRUDE_OIL =
        ATTACHMENT_TYPES.register("chunk_crude_oil", () -> AttachmentType
            .builder(petrolpark.mc.destroy.content.oil.ChunkCrudeOil::new)
            .serialize(petrolpark.mc.destroy.content.oil.ChunkCrudeOil.CODEC)
            .build());

    public static final void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    };
};
