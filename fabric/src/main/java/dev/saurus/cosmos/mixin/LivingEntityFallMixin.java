package dev.saurus.cosmos.mixin;

import dev.saurus.cosmos.world.Planets;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Impact energy is m g h, so a fall of h blocks on a body with gravity g hurts like a fall of h * g/9.81 on Earth. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityFallMixin {
    @ModifyVariable(method = "calculateFallDamage", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private double cosmos$scaleFall(double fallDistance) {
        return fallDistance * Planets.gravityFactor(((LivingEntity) (Object) this).level());
    }
}
