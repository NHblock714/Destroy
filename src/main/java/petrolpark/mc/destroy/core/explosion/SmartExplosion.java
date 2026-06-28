package petrolpark.mc.destroy.core.explosion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyAdvancementTrigger;

/**
 * Flexible drop-in replacement for vanilla {@link Explosion} — custom raycast-driven block /
 * entity selection with {@code irregularity} shape parameter.
*/
@EventBusSubscriber
public class SmartExplosion extends Explosion {

    private static final Map<ResourceLocation, SmartExplosion.Serializer<?>> SERIALIZERS = new HashMap<>();

    public static void register(SmartExplosion.Serializer<?> type) {
        SERIALIZERS.put(type.id, type);
    }

    @SuppressWarnings("unchecked")
    public static <E extends SmartExplosion> SmartExplosion.Serializer<E> getType(ResourceLocation typeId) {
        return (SmartExplosion.Serializer<E>) SERIALIZERS.get(typeId);
    }

    public static final SmartExplosion.Serializer<SmartExplosion> DEFAULT_SERIALIZER =
        new SmartExplosion.Serializer<>(Destroy.asResource("default"));

    static {
        register(DEFAULT_SERIALIZER);
        // T2b: CustomExplosiveMixExplosion.SERIALIZER wired (supports the PrimedBomb family).
        register(petrolpark.mc.destroy.core.explosion.mixedexplosive.CustomExplosiveMixExplosion.SERIALIZER);
    }

    /**
 * Trigger a {@link SmartExplosion}. Respects {@code EventHooks.onExplosionStart} cancellation. 4096) for client particles/sound/knockback.
*/
    public static Explosion explode(Level level, SmartExplosion explosion) {
        if (EventHooks.onExplosionStart(level, explosion)) return explosion;
        explosion.explode();
        explosion.finalizeExplosion(level.isClientSide());

        if (level instanceof ServerLevel serverLevel) {
            Vec3 pos = explosion.getPosition();
            Map<Player, Vec3> knockbacks = explosion.getHitPlayers();
            // Broadcast the visual/sound to a wide radius: a launched munition can detonate far from
            // any player, and the effect must still reach viewers. Sound self-attenuates by distance,
            // and knockback stays gated to hit players via the knockbacks map (others receive Vec3.ZERO).
            final double effectRangeSq = 1024d * 1024d;
            for (net.minecraft.server.level.ServerPlayer player : serverLevel.getPlayers(p -> p.distanceToSqr(pos) < effectRangeSq)) {
                Vec3 kb = knockbacks.getOrDefault(player, Vec3.ZERO);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new SmartExplosionS2CPacket(pos,
                        explosion.radius(), explosion.irregularity,
                        List.copyOf(explosion.getToBlow()), kb));
            }
        }

        return explosion;
    }

    /** Center (global coordinates) of this Explosion.*/
    protected final Vec3 position;
    /** Drops this Explosion will generate.*/
    protected Map<BlockPos, List<ItemStack>> stacksToCreate;
    /**
 * How spherical this Explosion isn't: {@code 0} perfectly spherical, {@code 1} very irregular.
 * Vanilla default is about {@code 0.6}.
*/
    public final float irregularity;

    public SmartExplosion(Level level, @Nullable Entity source, @Nullable DamageSource damageSource,
                          @Nullable ExplosionDamageCalculator damageCalculator,
                          Vec3 position, float radius, float irregularity) {
        super(level, source, damageSource, damageCalculator,
            position.x, position.y, position.z, radius, false, Explosion.BlockInteraction.KEEP,
            ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER, SoundEvents.GENERIC_EXPLODE);
        this.position = position;
        this.irregularity = irregularity > 1f ? 1f : irregularity;
        this.stacksToCreate = new HashMap<>();
        // 1.21 Explosion.damageSource is final, so install the custom SmartExplosionDamageSource
        // post-super. It lets consumers (mob-drop XP handler / future obliteration hooks) detect the
        // explosion source via instanceof. Only override when the caller did not pass an explicit
        // damageSource; an explicitly supplied one takes priority.
        if (damageSource == null) {
            this.damageSource = new SmartExplosionDamageSource(
                level.registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(DamageTypes.EXPLOSION),
                this);
        }
    }

    @Override
    public void explode() {
        level.gameEvent(getDirectSourceEntity(), GameEvent.EXPLODE, position);

        ExplosionResult result = getExplosionResult();
        getToBlow().addAll(result.blocksToDestroy());

        List<Entity> entities = new ArrayList<>(result.entities().keySet());
        EventHooks.onExplosionDetonate(level, this, entities, radius() * 2);
        for (Entity entity : entities) {
            explodeEntity(entity, result.entities().get(entity));
        }
    }

    @Override
    public void finalizeExplosion(boolean clientSide) {
        boolean createExperience = getDirectSourceEntity() instanceof Player || shouldAlwaysDropExperience();

        // capture pre-onBlockExploded BlockState per pos so the {@link
        // BlockBehaviour.BlockStateBase#canDropFromExplosion} check below tests the original
        // block's setting, not whatever {@code state.onBlockExploded} replaced it with (always
        // {@code Blocks.AIR} via NeoForge's default {@link
        // net.neoforged.neoforge.common.extensions.IBlockExtension#onBlockExploded
        // IBlockExtension.onBlockExploded}).
        // Without this snapshot, TNT-style blocks that explicitly opt out of explosion-drops
        // (vanilla {@code TntBlock.dropFromExplosion → false}, and the
        // {@link petrolpark.mc.destroy.core.explosion.PrimeableBombBlock} family inherits this)
        // would still drop their loot-table items because by the time the drop-check runs the
        // block at {@code pos} is already {@code AIR}, and {@code Block.dropFromExplosion}
        // defaults to {@code true}. Combined with {@code PrimeableBombBlock.onBlockExploded}
        // also spawning a primed entity, this duplicated the explosive: chain-exploded
        // {@code custom_explosive_mix} blocks dropped both their item form (with NBT-preserved
        // inventory) AND a primed entity → infinite explosive refill on chain reactions
        // (a mixed-explosive detonation activating another both dropped the block item and
        // spawned the primed entity, endlessly replenishing the explosive).
        java.util.Map<BlockPos, BlockState> preExplosionStates =
            new java.util.HashMap<>(getToBlow().size());

        for (BlockPos pos : getToBlow()) {
            BlockState state = level.getBlockState(pos);
            preExplosionStates.put(pos, state);
            explodeBlock(pos);
            if (level instanceof ServerLevel serverLevel) {
                state.spawnAfterBreak(serverLevel, pos, ItemStack.EMPTY, createExperience);
            }
            state.onBlockExploded(level, pos, this);
        }

        for (Map.Entry<BlockPos, List<ItemStack>> entry : stacksToCreate.entrySet()) {
            BlockPos pos = entry.getKey();
            // use captured pre-explosion state for the drop gate, falling back to
            // current state (already AIR for explosions but accurate for non-explosion call
            // sites if any) if no snapshot exists.
            BlockState dropCheckState = preExplosionStates.getOrDefault(pos, level.getBlockState(pos));
            if (dropCheckState.canDropFromExplosion(level, pos, this)) {
                for (ItemStack stack : entry.getValue()) {
                    Block.popResource(level, pos, stack);
                }
            }
        }

        if (getIndirectSourceEntity() instanceof Player player) {
            DestroyAdvancementTrigger.DETONATE.award(level, player);
        }

        effects(clientSide);
    }

    /**
 * Cube-grid raycast to determine affected blocks + entities.
*/
    public ExplosionResult getExplosionResult() {
        Set<BlockPos> blocks = new HashSet<>();
        Map<Entity, Float> entities = new HashMap<>();

        int resolution = 8;
        float r = radius();
        float maxMomentum = r * (1f + irregularity / 2f);

        for (int i = -resolution; i <= resolution; i++) {
            for (int j = -resolution; j <= resolution; j++) {
                for (int k = -resolution; k <= resolution; k++) {
                    if (i == -resolution || i == resolution
                        || j == -resolution || j == resolution
                        || k == -resolution || k == resolution) {
                        Vec3 direction = new Vec3(i, j, k).normalize();
                        float momentum = r * ((1f - irregularity / 2f) + random.nextFloat() * irregularity);
                        Vec3 positionToExplode = position;

                        while (momentum > 0f) {
                            EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(level, null, position,
                                positionToExplode, new AABB(position, positionToExplode),
                                entity -> !entity.ignoreExplosion(this));
                            if (hitResult != null) {
                                Entity entity = hitResult.getEntity();
                                entities.merge(entity, momentum / maxMomentum, Math::max);
                                if (entity instanceof LivingEntity livingEntity) {
                                    double resistance = livingEntity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
                                    double kbRes = livingEntity.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
                                    momentum -= 0.1f + (0.125f * resistance * kbRes);
                                } else {
                                    momentum -= 0.1f;
                                }
                            }

                            BlockPos blockPosToExplode = BlockPos.containing(positionToExplode);
                            BlockState blockState = level.getBlockState(blockPosToExplode);
                            FluidState fluidState = level.getFluidState(blockPosToExplode);

                            if (!level.isInWorldBounds(blockPosToExplode)) break;

                            Optional<Float> optional = damageCalculator.getBlockExplosionResistance(this, level,
                                blockPosToExplode, blockState, fluidState);
                            if (optional.isPresent()) {
                                momentum -= (optional.get() + 0.3f) * 0.3f;
                            }

                            if (momentum > 0.0f
                                && damageCalculator.shouldBlockExplode(this, level, blockPosToExplode, blockState, momentum)) {
                                blocks.add(blockPosToExplode);
                            }

                            positionToExplode = positionToExplode.add(direction.scale(0.3f));
                            momentum -= 0.225f;
                        }
                    }
                }
            }
        }

        return new ExplosionResult(blocks, entities);
    }

    public void explodeBlock(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (level instanceof ServerLevel serverLevel) {
            BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
            LootParams.Builder builder = new LootParams.Builder(serverLevel)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY)
                .withParameter(LootContextParams.EXPLOSION_RADIUS, radius())
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, getDirectSourceEntity())
                .withOptionalParameter(petrolpark.mc.destroy.DestroyLootContextParams.SMART_EXPLOSION, this);
            if (getDirectSourceEntity() instanceof Player player) builder.withLuck(player.getLuck());
            modifyLoot(pos, builder);
            addBlockDrops(pos, state.getDrops(builder));
        }
    }

    /** Subclass hook to alter drops per exploded block.*/
    public void modifyLoot(BlockPos pos, LootParams.Builder builder) {}

    public void explodeEntity(Entity entity, float strength) {
        Vec3 knockback = entity.position().subtract(position).normalize().scale(strength * 5f);
        if (entity instanceof LivingEntity livingEntity) {
            // Damage: let vanilla entity.hurt apply blast protection internally via 1.21's
            // data-component enchantment system. No manual damage dampener multiplier.
            livingEntity.hurt(damageSource, radius() * strength * 5f);
            if (entity instanceof Player player
                && !player.isSpectator()
                && (!player.isCreative() || !player.getAbilities().flying)) {
                getHitPlayers().put(player, knockback);
            }
            // Knockback: scale by (1 - EXPLOSION_KNOCKBACK_RESISTANCE attribute).
            double kbScale = 1.0 - livingEntity.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE);
            knockback = knockback.scale(kbScale);
        }
        entity.setDeltaMovement(entity.getDeltaMovement().add(knockback));
    }

    /**
 * Sound + particle effects. Called both sides; client-side portion runs only when
 * {@code clientSide} is true.
*/
    public void effects(boolean clientSide) {
        if (level.isClientSide()) {
            level.playLocalSound(position.x(), position.y(), position.z(), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 4.0f, (1.0f + (random.nextFloat() * 0.4f)) * 0.7f, false);
        }
        if (clientSide) {
            if (radius() > 2f) {
                level.addParticle(ParticleTypes.EXPLOSION_EMITTER, position.x, position.y, position.z, 1d, 0d, 0d);
            } else {
                level.addParticle(ParticleTypes.EXPLOSION, position.x, position.y, position.z, 1d, 0d, 0d);
            }
        }
    }

    /** Whether this Explosion's loot should use the Obliteration LootItemCondition path.*/
    public boolean shouldDoObliterationDrops() {
        return false;
    }

    /** Whether experience should drop regardless of whether a Player caused the Explosion.*/
    public boolean shouldAlwaysDropExperience() {
        return false;
    }

    /** Whether mobs killed by this Explosion should always drop XP (independent of kill attribution).*/
    public boolean shouldAlwaysDropExperienceFromMobs() {
        return false;
    }

    
    @SubscribeEvent
    public static void onMobDrops(LivingDropsEvent event) {
        if (event.getEntity().wasExperienceConsumed()) return;
        if (!(event.getEntity().level() instanceof ServerLevel serverLevel)) return;
        if (!(event.getSource() instanceof SmartExplosionDamageSource ds
            && ds.explosion.shouldAlwaysDropExperienceFromMobs())) return;
        Player attacker = event.getSource().getEntity() instanceof Player p ? p : null;
        int xp = EventHooks.getExperienceDrop(event.getEntity(), attacker, event.getEntity().getExperienceReward(serverLevel, attacker));
        ExperienceOrb.award(serverLevel, event.getEntity().position(), xp);
    }

    public float getRadius() {
        return radius();
    }

    private void addBlockDrops(BlockPos pos, List<ItemStack> stacks) {
        stacksToCreate.merge(pos, stacks, (existing, next) -> {
            existing.addAll(next);
            return existing;
        });
    }

    public SmartExplosion.Serializer<?> getSerializer() {
        return DEFAULT_SERIALIZER;
    }

    public Vec3 getPosition() {
        return position;
    }

    public static class Serializer<E extends SmartExplosion> {

        public final ResourceLocation id;

        public Serializer(ResourceLocation id) {
            this.id = id;
        }

    }

    /**
 * @param blocksToDestroy Blocks this Explosion should remove.
 * @param entities Entities this Explosion affects, mapped to their effect strength [0,1].
*/
    public record ExplosionResult(Collection<BlockPos> blocksToDestroy, Map<Entity, Float> entities) {
        public ExplosionResult {
            Objects.requireNonNullElse(blocksToDestroy, List.of());
            Objects.requireNonNullElse(entities, Map.of());
        }
    }
}
