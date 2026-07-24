package petrolpark.mc.destroy.content.processing.extrusion;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;
import petrolpark.mc.library.compat.create.core.world.block.entity.behaviour.AbstractRememberPlacerBehaviour;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import petrolpark.mc.destroy.DestroyBlockEntityTypes;
import petrolpark.mc.destroy.DestroyDamageSources;
import petrolpark.mc.destroy.DestroyVoxelShapes;

/**
 * Extrusion Die — a directional (rotated-pillar) block that extrudes blocks pushed through its
 * axis. Entities caught inside take damage (SweetBerryBush-style). The heavy lifting happens in
 * {@link ExtrudableMovementBehaviour} attached to the pushed block.
*/
public class ExtrusionDieBlock extends RotatedPillarBlock implements IBE<ExtrusionDieBlockEntity>, IWrenchable {

    public static final MapCodec<ExtrusionDieBlock> CODEC = simpleCodec(ExtrusionDieBlock::new);

    public ExtrusionDieBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<ExtrusionDieBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public <S extends BlockEntity> BlockEntityTicker<S> getTicker(Level level, BlockState state, BlockEntityType<S> type) {
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return DestroyVoxelShapes.EXTRUSION_DIE.get(state.getValue(AXIS));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        AbstractRememberPlacerBehaviour.setPlacedBy(level, pos, placer);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    /** Copied from SweetBerryBushBlock 1.21 — entities inside get stuck + hurt on movement.*/
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (entity instanceof LivingEntity) {
            entity.makeStuckInBlock(state, new Vec3(0.8d, 0.75d, 0.8d));
            if (!level.isClientSide() && (entity.xOld != entity.getX() || entity.zOld != entity.getZ())) {
                double d0 = Math.abs(entity.getX() - entity.xOld);
                double d1 = Math.abs(entity.getZ() - entity.zOld);
                if (d0 >= 0.003d || d1 >= 0.003d) {
                    entity.hurt(DestroyDamageSources.extrusionDie(level), 3f);
                }
            }
        }
    }

    @Override
    public Class<ExtrusionDieBlockEntity> getBlockEntityClass() {
        return ExtrusionDieBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ExtrusionDieBlockEntity> getBlockEntityType() {
        return DestroyBlockEntityTypes.EXTRUSION_DIE.get();
    }
}
