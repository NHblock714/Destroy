package petrolpark.mc.destroy.content.processing.ageing;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour.TankSegment;
import com.simibubi.create.foundation.blockEntity.renderer.SmartBlockEntityRenderer;

import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.math.VecHelper;
import net.createmod.catnip.platform.NeoForgeCatnipServices;
import net.createmod.catnip.render.FluidRenderHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemStackHandler;
import petrolpark.mc.destroy.Destroy;
import petrolpark.mc.destroy.DestroyTags;

/** Draws the barrel's contents only while it is open: the fluid box, then the items ringed around
 * the axis and bobbing at the fluid surface, and a yeast film as a tiled face just above it.
*/
public class AgeingBarrelRenderer extends SmartBlockEntityRenderer<AgeingBarrelBlockEntity> {

    private static final float MIN_Y = 2 / 16f;

    public AgeingBarrelRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected void renderSafe(AgeingBarrelBlockEntity barrel, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        super.renderSafe(barrel, partialTicks, ms, buffer, light, overlay);

        if (!barrel.getBlockState().getValue(AgingBarrelBlock.IS_OPEN)) return;

        float fluidLevel = renderFluid(barrel, partialTicks, ms, buffer, light, overlay);

        // Items
        ms.pushPose();
        ms.translate(0.5f, 0.0f, 0.5f);
        IItemHandlerModifiable inv = barrel.itemCapability != null ? barrel.itemCapability : new ItemStackHandler();
        List<ItemStack> itemStacks = new ArrayList<>();
        boolean renderYeast = false;
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            // If both items are yeast, only do the overlay render for one.
            if (stack.is(DestroyTags.Items.YEAST.tag) && !renderYeast) {
                renderYeast = true;
            } else {
                itemStacks.add(stack);
            }
        }

        if (!itemStacks.isEmpty()) {
            float anglePartition = 360f / itemStacks.size();
            for (int i = itemStacks.size(); i > 0; i--) {
                ItemStack stack = itemStacks.get(i - 1);
                if (stack.isEmpty()) continue;

                ms.pushPose();

                if (fluidLevel != MIN_Y) {
                    ms.translate(0,
                        (Mth.sin(AnimationTickHolder.getRenderTime(barrel.getLevel()) / 12f + anglePartition * i) + 1.5f) / 32f,
                        0);
                }

                Vec3 itemPosition = VecHelper.rotate(
                    new Vec3(itemStacks.size() == 1 ? 0f : 0.1f,
                        Mth.clamp(fluidLevel - 0.05f, 0.125f, 0.8f),
                        0f),
                    anglePartition * i, Axis.Y);
                ms.translate(itemPosition.x, itemPosition.y, itemPosition.z);
                TransformStack.of(ms)
                    .rotateYDegrees(anglePartition * i + 35)
                    .rotateXDegrees(65);
                renderItem(barrel, partialTicks, ms, buffer, light, overlay, stack);
                ms.popPose();
            }
        }

        ms.popPose();

        // Yeast top overlay
        ms.pushPose();
        if (renderYeast) {
            FluidRenderHelper.renderStillTiledFace(Direction.UP, 2 / 16f, 2 / 16f, 14 / 16f, 14 / 16f,
                fluidLevel + 0.01f, FluidRenderHelper.getFluidBuilder(buffer), ms, light, 0xFFFFFFFF,
                Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(Destroy.asResource("block/yeast_overlay")));
        }
        ms.popPose();
    }

    protected void renderItem(AgeingBarrelBlockEntity barrel, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay, ItemStack stack) {
        if (stack.isEmpty()) return;
        Minecraft.getInstance().getItemRenderer()
            .renderStatic(stack, ItemDisplayContext.GROUND, light, overlay, ms, buffer, barrel.getLevel(), 0);
    }

    protected float renderFluid(AgeingBarrelBlockEntity barrel, float partialTicks, PoseStack ms,
                                MultiBufferSource buffer, int light, int overlay) {
        TankSegment tank = barrel.getTankToRender();
        float units = tank.getTotalUnits(partialTicks);
        float maxY = MIN_Y + (Mth.clamp(units / barrel.getTank().getCapacity(), 0, 1) * 8 / 12f);
        if (units < 1 || tank.getRenderedFluid().isEmpty()) return MIN_Y;
        NeoForgeCatnipServices.FLUID_RENDERER.renderFluidBox(tank.getRenderedFluid(),
            2 / 16f, MIN_Y, 2 / 16f, 14 / 16f, maxY, 14 / 16f, buffer, ms, light, false, true);
        return maxY;
    }

    @Override
    public int getViewDistance() {
        return 16;
    }
}
