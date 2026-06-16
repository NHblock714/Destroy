package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.FireworkStarItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams.Builder;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.core.explosion.SmartExplosion;
import petrolpark.mc.destroy.core.explosion.mixedexplosive.ExplosiveProperties.ExplosiveProperty;

/**
 * {@link SmartExplosion} subclass driven by a {@link MixedExplosiveInventory}'s aggregated
 * {@link ExplosiveProperties}. Radius, fire, block-drop behavior, entity push strength,
 * silk-touch, XP drop, fluid interaction, and head-drop all derive from the property-condition
 * thresholds (CAN_EXPLODE / OBLITERATES / SILK_TOUCH / DROPS_HEADS / DROPS_EXPERIENCE /
 * ENTITIES_PUSHED / EVAPORATES_FLUIDS / UNDERWATER).
 *
 * <p><b>1.21 migrations applied</b>:</p>
 *
 * <p><b>SERIALIZER registration</b>: Wired in {@link SmartExplosion} static init block.</p>
*/
public class CustomExplosiveMixExplosion extends SmartExplosion {

    public static final Serializer SERIALIZER = new Serializer(Destroy.asResource("custom_mix"));

    protected final ExplosiveProperties properties;
    protected final List<ItemStack> specialItems;

    protected CustomExplosiveMixExplosion(Level level, ExplosiveProperties properties, List<ItemStack> specialItems, Entity source, DamageSource damageSource, ExplosionDamageCalculator damageCalculator, Vec3 position, float radius, float irregularity) {
        super(level, source, damageSource, damageCalculator, position, radius, irregularity);
        this.properties = properties;
        this.specialItems = specialItems;
    }

    public static CustomExplosiveMixExplosion create(Level level, MixedExplosiveInventory inv, @Nullable Entity source, Vec3 position) {
        return create(level, inv.getExplosiveProperties(), inv.getSpecialItems(), source, position);
    }

    protected static CustomExplosiveMixExplosion create(Level level, ExplosiveProperties properties, List<ItemStack> specialItems, @Nullable Entity source, Vec3 position) {
        float oxygenBalance = Math.abs(properties.get(ExplosiveProperty.OXYGEN_BALANCE).value) / 10f;
        if (properties.fulfils(ExplosiveProperties.DROPS_HEADS)) source = new DummyChargedCreeper(level, source);

        return new CustomExplosiveMixExplosion(level, properties, specialItems, source, null, new DamageCalculator(properties), position, (4f + (properties.get(ExplosiveProperty.ENERGY).value / 3f)) * (1f - oxygenBalance * oxygenBalance), 0.5f);
    }

    /**
 * Build an enchanted silk-touch NETHERITE_PICKAXE for loot-context TOOL parameter when the
 * SILK_TOUCH explosive-property-condition is fulfilled. No-arg
 * static Lazy pattern no longer works without Level context.
*/
    protected static ItemStack silkTouchTool(Level level) {
        ItemStack stack = new ItemStack(Items.NETHERITE_PICKAXE);
        Holder<Enchantment> silkTouch = level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.SILK_TOUCH);
        stack.enchant(silkTouch, 1);
        return stack;
    }

    @Override
    public boolean shouldDoObliterationDrops() {
        return properties.fulfils(ExplosiveProperties.OBLITERATES);
    }

    @Override
    public boolean shouldAlwaysDropExperience() {
        return properties.fulfils(ExplosiveProperties.DROPS_EXPERIENCE);
    }

    @Override
    public boolean shouldAlwaysDropExperienceFromMobs() {
        return properties.fulfils(ExplosiveProperties.DROPS_EXPERIENCE);
    }

    @Override
    public void modifyLoot(BlockPos pos, Builder builder) {
        if (properties.fulfils(ExplosiveProperties.SILK_TOUCH)) builder.withParameter(LootContextParams.TOOL, silkTouchTool(level));
    }

    @Override
    public void explodeEntity(Entity entity, float strength) {
        super.explodeEntity(entity, strength * (properties.hasCondition(ExplosiveProperties.ENTITIES_PUSHED) && !properties.fulfils(ExplosiveProperties.ENTITIES_PUSHED) ? 0.1f : 1f));
    }

    @Override
    public void effects(boolean clientSide) {
        super.effects(clientSide);
        List<FireworkExplosion> fireworkExplosions = new ArrayList<>();
        if (clientSide) for (ItemStack stack : specialItems) {
            if (stack.getItem() instanceof FireworkStarItem) {
                // 1.21: FireworkStarItem stores explosion in DataComponents.FIREWORK_EXPLOSION
                // (previously NBT "Explosion" CompoundTag). stack.get() returns the record directly.
                FireworkExplosion exp = stack.get(DataComponents.FIREWORK_EXPLOSION);
                if (exp != null) fireworkExplosions.add(exp);
            } else if (stack.getItem() instanceof ISpecialEffectExplosiveItem specialItem) {
                specialItem.explode(this, level, getToBlow(), stack);
            }
        }
        if (!fireworkExplosions.isEmpty()) {
            // 1.21 createFireworks signature: List<FireworkExplosion> (previously CompoundTag).
            // Explosion.x/y/z private in 1.21 → use SmartExplosion.getPosition() accessor.
            Vec3 pos = getPosition();
            level.createFireworks(pos.x, pos.y, pos.z, 0d, 0d, 0d, fireworkExplosions);
        }
    }

    public static class DamageCalculator extends ExplosionDamageCalculator {

        protected final ExplosiveProperties properties;

        public DamageCalculator(ExplosiveProperties properties) {
            this.properties = properties;
        }

        @Override
        public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid) {
            if (!fluid.isEmpty() && (properties.fulfils(ExplosiveProperties.EVAPORATES_FLUIDS) || properties.fulfils(ExplosiveProperties.UNDERWATER))) return Optional.of(0f);
            return super.getBlockExplosionResistance(explosion, level, pos, state, fluid);
        }

        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
            return level.getFluidState(pos).isEmpty() || !properties.fulfils(ExplosiveProperties.UNDERWATER) || properties.fulfils(ExplosiveProperties.EVAPORATES_FLUIDS);
        }

    }

    public static class DummyChargedCreeper extends Creeper {

        protected Entity trueSource;

        public DummyChargedCreeper(Level level, Entity trueSource) {
            super(EntityType.CREEPER, level);
            this.trueSource = trueSource;
        }

        @Override
        public Component getName() {
            return trueSource == null ? Component.translatable("entity.destroy.dummy_charged_creeper") : trueSource.getName();
        }

        @Override
        public boolean isPowered() {
            return true;
        }

        @Override
        public boolean canDropMobsSkull() {
            return true;
        }

    }

    @Override
    public Serializer getSerializer() {
        return SERIALIZER;
    }

    /**
 * Serialize explosion state over the wire. Caller must pass a {@link RegistryFriendlyByteBuf}
 * for {@link ItemStack#OPTIONAL_STREAM_CODEC} encoding
 *.
*/
    public void write(FriendlyByteBuf buffer) {
        properties.write(buffer);
        RegistryFriendlyByteBuf registryBuf = (RegistryFriendlyByteBuf) buffer;
        buffer.writeVarInt(specialItems.size());
        for (ItemStack stack : specialItems) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(registryBuf, stack);
        }
        buffer.writeDouble(getPosition().x());
        buffer.writeDouble(getPosition().y());
        buffer.writeDouble(getPosition().z());
    }

    public static class Serializer extends SmartExplosion.Serializer<CustomExplosiveMixExplosion> {

        public Serializer(ResourceLocation id) {
            super(id);
        }

        @OnlyIn(Dist.CLIENT)
        public SmartExplosion read(FriendlyByteBuf buffer) {
            Minecraft mc = Minecraft.getInstance();
            RegistryFriendlyByteBuf registryBuf = (RegistryFriendlyByteBuf) buffer;
            int size = buffer.readVarInt();
            List<ItemStack> stacks = new ArrayList<>(size);
            for (int i = 0; i < size; i++) stacks.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(registryBuf));
            return CustomExplosiveMixExplosion.create(mc.level, ExplosiveProperties.read(buffer), stacks, null, new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
        }

    }

}
