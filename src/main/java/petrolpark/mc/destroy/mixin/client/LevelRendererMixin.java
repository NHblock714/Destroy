package petrolpark.mc.destroy.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.createmod.catnip.theme.Color;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;

import petrolpark.mc.destroy.config.DestroyConfigs;
import petrolpark.mc.destroy.core.pollution.PollutionHelper;
import petrolpark.mc.destroy.DestroyPollutionTypes;

/**
 * Tints vanilla rain rendering by the local Level's ACID_RAIN pollution proportion.
 *
 * <pre>
 * 727: fconst_1 // r = 1.0
 * 728: fconst_1 // g = 1.0
 * 729: fconst_1 // b = 1.0
 * 730: fload 44 // a = computed alpha
 * 732: invokeinterface VertexConsumer.setColor(FFFF)
 * </pre>
 *
 * <ol>
 * <li>{@code @Inject} brackets the rain section: the flag goes true before
 * {@code setShaderTexture(RAIN_LOCATION)} and false again before
 * {@code setShaderTexture(SNOW_LOCATION)}.</li>
 * <li>{@code @Redirect} on {@link VertexConsumer#setColor} swaps (r, g, b) for the pollution
 * colour inside the rain section only, leaving alpha alone.</li>
 * <li>The global {@code setShaderColor} is set as well, for shader packs which read that
 * instead of the per-vertex colour.</li>
 * </ol>
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {

    /** Whether rendering is currently in the rain branch (true = rain, false = snow / off). */
    @Unique
    private boolean destroy$inRainSection = false;

    /**
     * True when this mixin set the shader colour in the current rain section. Tracked separately
     * from {@link #destroy$inRainSection} so the shader colour is only restored if we changed it,
     * which keeps rain at 0% pollution from losing the implicit colour set by upstream vanilla
     * rendering (fog / sky tint).
     */
    @Unique
    private boolean destroy$shaderColorDirtied = false;

    /**
     * Marks the start of the rain section, just before vanilla binds the rain texture.
     */
    @Inject(
        method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V",
            ordinal = 0
        )
    )
    public void destroy$beginRain(LightTexture lightTexture, float partialTick,
                                  double camX, double camY, double camZ, CallbackInfo ci) {
        destroy$inRainSection = true;
        // The global shader colour may already carry a blue-grey fog tint set further up the
        // rendering pipeline (fog / sky / light texture). Forcing it to (1, 1, 1, 1) here would
        // wash that out and leave the rain pure white, so only set a shader colour once the ratio
        // is above zero.
        if (destroy$rainColorAffected()) {
            float ratio = destroy$getAcidRainRatio();
            if (ratio > 0f) {
                Color color = destroy$getRainColor();
                RenderSystem.setShaderColor(color.getRedAsFloat(), color.getGreenAsFloat(),
                                            color.getBlueAsFloat(), 1f);
                destroy$shaderColorDirtied = true;
            }
        }
    }

    /**
     * Ends the rain section just before vanilla binds the snow texture, restoring the shader colour
     * only if this mixin set one; if the ratio was zero this frame, vanilla's implicit state is
     * left in place.
     */
    @Inject(
        method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V",
            ordinal = 1
        )
    )
    public void destroy$endRainBeginSnow(LightTexture lightTexture, float partialTick,
                                         double camX, double camY, double camZ, CallbackInfo ci) {
        destroy$inRainSection = false;
        if (destroy$shaderColorDirtied) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            destroy$shaderColorDirtied = false;
        }
    }

    /** Clears the flag on return, again restoring the shader colour only if this mixin set one. */
    @Inject(
        method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
        at = @At("RETURN")
    )
    public void destroy$onReturn(LightTexture lightTexture, float partialTick,
                                 double camX, double camY, double camZ, CallbackInfo ci) {
        destroy$inRainSection = false;
        if (destroy$shaderColorDirtied) {
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            destroy$shaderColorDirtied = false;
        }
    }

    /**
     * Vanilla emits {@code vc.setColor(1f, 1f, 1f, fadeAlpha)} for all four vertices of every rain
     * quad, letting rain.png supply the colour itself. This lerps those vertices towards acid
     * green:
     *
     * <ul>
     * <li>{@code ratio = 0}: returns {@code (r, g, b, a)} untouched, identical to vanilla</li>
     * <li>{@code ratio = 1}: returns {@code (0, 1, 0, a)}, fully acidic</li>
     * <li>in between: linear lerp from vanilla towards acid green, alpha preserved</li>
     * </ul>
     */
    @Redirect(
        method = "renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;setColor(FFFF)Lcom/mojang/blaze3d/vertex/VertexConsumer;"
        )
    )
    public VertexConsumer destroy$tintRainVertex(VertexConsumer vc, float r, float g, float b, float a) {
        if (!(destroy$inRainSection && destroy$rainColorAffected())) {
            return vc.setColor(r, g, b, a);
        }
        float ratio = destroy$getAcidRainRatio();
        // Pass vanilla's colour straight through at 0%, so no floating-point drift is visible.
        if (ratio <= 0f) return vc.setColor(r, g, b, a);
        // Lerp from vanilla (r, g, b) toward (0, 1, 0) acid green by ratio.
        float nr = r + (0f - r) * ratio;
        float ng = g + (1f - g) * ratio;
        float nb = b + (0f - b) * ratio;
        return vc.setColor(nr, ng, nb, a);
    }

    /** 0..1 acid-rain pollution ratio with NaN/clamp guards.*/
    @Unique
    private float destroy$getAcidRainRatio() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return 0f;
        float ratio = PollutionHelper.getPollutionProportion(mc.level, DestroyPollutionTypes.ACID_RAIN.get());
        if (Float.isNaN(ratio) || ratio < 0f) return 0f;
        if (ratio > 1f) return 1f;
        return ratio;
    }

    /** Catnip {@link Color}-flavored variant — used by the {@code setShaderColor} fallback path
 * (some shader packs read the global shader color rather than per-vertex). Mirrors lerp logic
 * but anchors at vanilla white {@code 0xFFFFFFFF} since shader path doesn't have access to
 * the original (r, g, b) input args.*/
    @Unique
    private Color destroy$getRainColor() {
        float ratio = destroy$getAcidRainRatio();
        // 0xFFFFFFFF = vanilla white, 0xFF00FF00 = acid green, so ratio 0 gives vanilla white.
        return new Color(Color.mixColors(0xFFFFFFFF, 0xFF00FF00, ratio));
    }

    @Unique
    private static boolean destroy$rainColorAffected() {
        // rainColorChanges lives on the client pollution config — purely a cosmetic gate.
        return PollutionHelper.isPollutionEnabled()
            && DestroyConfigs.client().pollution.rainColorChanges.get();
    }
}
