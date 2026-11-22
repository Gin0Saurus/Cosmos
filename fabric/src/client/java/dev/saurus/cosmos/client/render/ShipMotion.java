package dev.saurus.cosmos.client.render;

import dev.saurus.cosmos.client.CosmosState;
import dev.saurus.cosmos.core.Universe;
import dev.saurus.cosmos.core.math.Vec3d;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Free flight in space mode. Speed scales with the distance to the nearest surface (so you can creep up to a planet
 * and still cross the galaxy): v = speedFactor x distance per second, x20 while sprinting. Anything above c is the
 * labelled "warp" gameplay convenience, not physics.
 */
public final class ShipMotion {
    private ShipMotion() {}
    public static double lastSpeed = 0, lastNearest = 0;

    public static void apply(Universe u, double dt, float yRot, float xRot, double t) {
        Minecraft mc = Minecraft.getInstance();
        if (dt <= 0 || mc.player == null) return;
        Options o = mc.options;
        double fwd = (o.keyUp.isDown() ? 1 : 0) - (o.keyDown.isDown() ? 1 : 0);
        double side = (o.keyRight.isDown() ? 1 : 0) - (o.keyLeft.isDown() ? 1 : 0);
        double vert = (o.keyJump.isDown() ? 1 : 0) - (o.keyShift.isDown() ? 1 : 0);
        double nearest = CosmosState.nearestSurfaceDistance(u, t);
        lastNearest = nearest;
        double speed = Math.max(5.0, CosmosState.speedFactor * nearest) * (o.keySprint.isDown() ? 20 : 1);
        if (fwd == 0 && side == 0 && vert == 0) { lastSpeed = 0; return; }
        Vec3d f = CosmosState.forward(yRot, xRot);
        Vec3d upHint = CosmosState.upHint();
        Vec3d right = f.cross(upHint).normalize(), up = right.cross(f);
        Vec3d dir = f.mul(fwd).add(right.mul(side)).add(up.mul(vert)).normalize();
        // never step through more than half the remaining distance in one frame
        double step = Math.min(speed * dt, 0.5 * nearest + 1);
        lastSpeed = step / dt;
        CosmosState.shipPos = CosmosState.shipPos.add(dir.mul(step));
    }
}
