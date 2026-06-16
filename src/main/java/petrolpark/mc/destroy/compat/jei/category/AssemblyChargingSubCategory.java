package petrolpark.mc.destroy.compat.jei.category;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.minecraft.client.gui.GuiGraphics;

import petrolpark.mc.destroy.compat.jei.animation.AnimatedDynamo;

/**
 * JEI Sequenced-Assembly sub-category for the "charging via Dynamo" step. Used by
 * {@link petrolpark.mc.destroy.content.processing.dynamo.ChargingRecipe ChargingRecipe}'s
 * {@code getJEISubCategory()} when a Sequenced Assembly recipe chains a dynamo-charging step.
 *
 * <p>Note: {@code ChargingRecipe.getJEISubCategory} currently
 * returns {@code SequencedAssemblySubCategory.AssemblyPressing::new} as a placeholder; it can now
 * be switched to {@code AssemblyChargingSubCategory::new} for proper visual fidelity.</p>
*/
public class AssemblyChargingSubCategory extends SequencedAssemblySubCategory {

    private final AnimatedDynamo dynamo;

    public AssemblyChargingSubCategory() {
        super(25);
        dynamo = new AnimatedDynamo(false, false);
    }

    @Override
    public void draw(SequencedRecipe<?> recipe, GuiGraphics graphics, double mouseX, double mouseY, int index) {
        PoseStack ms = graphics.pose();
        ms.pushPose();
        ms.translate(-5, 50, 0);
        ms.scale(.6f, .6f, .6f);
        dynamo.draw(graphics, getWidth() / 2, 0);
        ms.popPose();
    }
}
