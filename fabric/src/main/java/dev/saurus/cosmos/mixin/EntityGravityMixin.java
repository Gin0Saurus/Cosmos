package dev.saurus.cosmos.mixin;

import dev.saurus.cosmos.world.Planets;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Real surface gravity: every entity's per-tick gravity (players, mobs, items, arrows, falling sand) is scaled by
 * g_body / 9.81, with g_body = GM / R^2 from the body's measured mass and radius. Runs on client and server so player
 * movement prediction matches.
 */
@Mixin(Entity.class)
public abstract class EntityGravityMixin {
    @Inject(method = "getGravity", at = @At("RETURN"), cancellable = true)
    private void cosmos$bodyGravity(CallbackInfoReturnable<Double> cir) {
        double f = Planets.gravityFactor(((Entity) (Object) this).level());
        if (f != 1.0) cir.setReturnValue(cir.getReturnValue() * f);
    }
}
