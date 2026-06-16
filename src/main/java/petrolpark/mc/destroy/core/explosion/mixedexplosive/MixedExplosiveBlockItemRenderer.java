package petrolpark.mc.destroy.core.explosion.mixedexplosive;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * ItemStack renderer for {@link MixedExplosiveBlockItem} — draws the base vanilla item model +
 * the {@link MixedExplosiveBlockEntityRenderer#renderTruncated} 4-direction uppercase label when
 * the stack has a custom name (pick-block / user-renamed).
 *
 * <p>One of the renderer trio: MixedExplosiveEntityRenderer +
 * MixedExplosiveBlockEntityRenderer + MixedExplosiveBlockItemRenderer.</p>
*/
public class MixedExplosiveBlockItemRenderer extends CustomRenderedItemModelRenderer {

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer, ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay, model.getOriginalModel());
        if (!stack.has(DataComponents.CUSTOM_NAME)) return;
        ms.popPose();
        MixedExplosiveBlockEntityRenderer.renderTruncated(ms, buffer, d -> light, stack.getHoverName().getString());
        ms.pushPose();
    }
}
