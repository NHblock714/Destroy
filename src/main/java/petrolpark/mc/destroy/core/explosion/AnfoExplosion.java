package petrolpark.mc.destroy.core.explosion;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import petrolpark.mc.destroy.DestroyTags;

/**
 * A controlled blast for miners: it breaks only fluids and gangue, leaving Ores intact, and hits
 * Entities with 30% of the usual knockback and damage.
 */
public class AnfoExplosion extends SmartExplosion {

    public static class NaturalBlockOnlyDamageCalculator extends ExplosionDamageCalculator {

        @Override
        public boolean shouldBlockExplode(Explosion explosion, BlockGetter reader, BlockPos pos, BlockState state, float power) {
            return (!reader.getFluidState(pos).isEmpty() || state.is(DestroyTags.Blocks.GANGUE.tag))
                && !state.is(Tags.Blocks.ORES);
        }
    }

    public AnfoExplosion(Level level, Entity source, Vec3 position, float radius, float irregularity) {
        super(level, source, null, new NaturalBlockOnlyDamageCalculator(), position, radius, irregularity);
    }

    @Override
    public void explodeEntity(Entity entity, float strength) {
        super.explodeEntity(entity, strength * 0.3f);
    }

    @Override
    public void explodeBlock(BlockPos pos) {
        // Do nothing — this explosion does not drop block items
    }
}
