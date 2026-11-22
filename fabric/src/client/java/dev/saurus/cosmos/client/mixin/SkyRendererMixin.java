package dev.saurus.cosmos.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import dev.saurus.cosmos.client.render.CosmosRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Real stars drawn right after Minecraft's own sky pass has closed (so no render pass is open), before terrain. */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    @Shadow @Final private RenderTarget renderTarget;

    @Inject(method = "render", at = @At("RETURN"))
    private void cosmos$realStars(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.level.dimensionType().hasSkyLight()) return;
        Matrix4f viewProj = mc.gameRenderer.mainCamera().getViewRotationProjectionMatrix(new Matrix4f());
        CosmosRenderer.INSTANCE.renderOverworldSky(renderTarget, viewProj, new Matrix4f(),
                mc.level.getOverworldClockTime(), mc.level.getRainLevel(1.0f));
    }
}
