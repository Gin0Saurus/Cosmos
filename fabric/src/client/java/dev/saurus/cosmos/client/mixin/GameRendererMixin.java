package dev.saurus.cosmos.client.mixin;

import dev.saurus.cosmos.client.CosmosState;
import dev.saurus.cosmos.client.render.CosmosRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Space mode replaces the frame once the whole level has been rendered (all level passes closed), before the HUD. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void cosmos$space(CallbackInfo ci) {
        if (!CosmosState.spaceMode) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GameRenderer self = (GameRenderer) (Object) this;
        CosmosRenderer.INSTANCE.renderSpace(self.mainRenderTarget(), mc.player.getYRot(), mc.player.getXRot(), self.mainCamera().getFov());
    }
}
