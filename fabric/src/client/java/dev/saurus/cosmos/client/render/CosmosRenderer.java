package dev.saurus.cosmos.client.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.saurus.cosmos.client.CosmosState;
import dev.saurus.cosmos.core.Universe;
import dev.saurus.cosmos.core.blackhole.BlackHole;
import dev.saurus.cosmos.core.body.Body;
import dev.saurus.cosmos.core.catalog.DeepSkyCatalog;
import dev.saurus.cosmos.core.coords.Frames;
import dev.saurus.cosmos.core.coords.UniversePos;
import dev.saurus.cosmos.core.coords.Units;
import dev.saurus.cosmos.core.galaxy.MilkyWayModel;
import dev.saurus.cosmos.core.math.Mat3;
import dev.saurus.cosmos.core.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;

/**
 * Draws the universe with Minecraft's RenderPearl GPU API.
 * Overworld: real catalogue stars behind the terrain, oriented for the observer's latitude and Minecraft's time of day.
 * Space mode: the whole frame - star sprites, then one fullscreen pass with Milky Way light, true-scale bodies,
 * atmospheres, rings, galaxies and black holes.
 */
public final class CosmosRenderer {
    private static final int SPACE_UBO_SIZE = 2048, STAR_UBO_SIZE = 128, MAX_BODIES = 16;
    private static final int SKY_W = 512, SKY_H = 256;

    private GpuBuffer fullscreenTri, spaceUbo, starUbo;
    // star geometry (space mode, observer-dependent) and overworld geometry (from Earth)
    private GpuBuffer starVb, starIb; private int starIndices; private Vec3d starObserver = null;
    private CompletableFuture<StarField.Geometry> starJob; private Vec3d starJobObserver;
    private GpuBuffer skyStarVb, skyStarIb; private int skyStarIndices; private CompletableFuture<StarField.Geometry> skyStarJob;
    // Milky Way glow texture
    private GpuTexture skyTex; private GpuTextureView skyView; private GpuSampler sampler; private Vec3d skyObserver = null;
    private CompletableFuture<int[]> skyJob; private Vec3d skyJobObserver;
    private long lastFrameNanos = 0;

    public static final CosmosRenderer INSTANCE = new CosmosRenderer();

    private void ensureStatic(GpuDevice dev) {
        if (fullscreenTri != null) return;
        ByteBuffer tri = MemoryUtil.memAlloc(3 * 12);
        tri.putFloat(-1).putFloat(-1).putFloat(0).putFloat(3).putFloat(-1).putFloat(0).putFloat(-1).putFloat(3).putFloat(0).flip();
        fullscreenTri = dev.createBuffer(() -> "cosmos fullscreen", GpuBuffer.USAGE_VERTEX, tri);
        MemoryUtil.memFree(tri);
        spaceUbo = dev.createBuffer(() -> "cosmos space ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, SPACE_UBO_SIZE);
        starUbo = dev.createBuffer(() -> "cosmos star ubo", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, STAR_UBO_SIZE);
        sampler = dev.createSampler(AddressMode.REPEAT, AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.LINEAR, 1, OptionalDouble.empty());
        skyTex = dev.createTexture(() -> "cosmos milky way", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, GpuFormat.RGBA8_UNORM, SKY_W, SKY_H, 1, 1);
        skyView = dev.createTextureView(skyTex);
        bodyTex = dev.createTexture(() -> "cosmos body surface", GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_COPY_DST, GpuFormat.RGBA8_UNORM, BODY_W, BODY_H, 1, 1);
        bodyView = dev.createTextureView(bodyTex);
    }

    // ------------------------------------------------------------------ surface map of the walkable body you're nearest
    private static final int BODY_W = 1024, BODY_H = 512;
    private GpuTexture bodyTex; private GpuTextureView bodyView;
    private String bodyTexId = null, bodyJobId = null;
    private CompletableFuture<int[]> bodyJob;

    /** Same terrain model as the chunks, rendered to a map once per body (~1-3 s on a worker thread). */
    private void updateBodyMapAsync(String focus) {
        if (bodyJob != null && bodyJob.isDone()) {
            try {
                int[] px = bodyJob.join();
                try (NativeImage img = new NativeImage(BODY_W, BODY_H, false)) {
                    for (int y = 0; y < BODY_H; y++) for (int x = 0; x < BODY_W; x++) img.setPixelABGR(x, y, px[y * BODY_W + x]);
                    RenderSystem.getDevice().createCommandEncoder().writeToTexture(bodyTex, img);
                }
                bodyTexId = bodyJobId;
            } catch (Exception e) { bodyTexId = null; }
            bodyJob = null;
        }
        if (focus != null && !focus.equals(bodyTexId) && bodyJob == null) {
            bodyJobId = focus;
            bodyJob = CompletableFuture.supplyAsync(() -> dev.saurus.cosmos.core.terrain.SurfaceMap.render(dev.saurus.cosmos.world.Planets.terrain(focus), BODY_W, BODY_H));
        }
    }

    // ------------------------------------------------------------------ async content
    private void updateStarsAsync(Universe u, Vec3d obsPc, double t) {
        if (starJob != null && starJob.isDone()) {
            StarField.Geometry g = starJob.join();
            GpuDevice dev = RenderSystem.getDevice();
            if (starVb != null) { starVb.close(); starIb.close(); }
            starVb = dev.createBuffer(() -> "cosmos stars", GpuBuffer.USAGE_VERTEX, g.vertices());
            starIb = dev.createBuffer(() -> "cosmos star indices", GpuBuffer.USAGE_INDEX, g.indices());
            starIndices = g.starCount() * 6;
            starObserver = starJobObserver;
            g.free();
            starJob = null;
        }
        boolean stale = starObserver == null || starObserver.distance(obsPc) > Math.max(0.002, 0.01 * nearestStarPc(u, obsPc, t));
        if (stale && starJob == null) {
            starJobObserver = obsPc;
            starJob = CompletableFuture.supplyAsync(() -> StarField.build(u, obsPc, t, 9.0, StarField.IDENTITY));
        }
    }

    private void updateSkyAsync(Vec3d obsPc) {
        if (skyJob != null && skyJob.isDone()) {
            int[] px = skyJob.join();
            try (NativeImage img = new NativeImage(SKY_W, SKY_H, false)) {
                for (int y = 0; y < SKY_H; y++) for (int x = 0; x < SKY_W; x++) img.setPixelABGR(x, y, px[y * SKY_W + x]);
                RenderSystem.getDevice().createCommandEncoder().writeToTexture(skyTex, img);
            }
            skyObserver = skyJobObserver;
            skyJob = null;
        }
        if ((skyObserver == null || skyObserver.distance(obsPc) > 150) && skyJob == null) {
            skyJobObserver = obsPc;
            skyJob = CompletableFuture.supplyAsync(() -> MilkyWayGlow.render(SKY_W, SKY_H, obsPc));
        }
    }

    private static double nearestStarPc(Universe u, Vec3d obsPc, double t) {
        int i = u.stars.nearest(obsPc, t);
        return i < 0 ? 1 : u.stars.positionPc(i, t).distance(obsPc);
    }

    // ------------------------------------------------------------------ overworld sky
    /** Real stars before terrain is drawn (terrain then covers them). Visibility follows Minecraft's night. */
    public void renderOverworldSky(RenderTarget target, Matrix4f projection, Matrix4f viewRotation, long dayTime, float rain) {
        Universe u = CosmosState.universe();
        if (u == null || !CosmosState.overworldSky || CosmosState.spaceMode) return;
        GpuDevice dev = RenderSystem.getDevice();
        ensureStatic(dev);
        if (skyStarJob == null && skyStarVb == null) skyStarJob = CompletableFuture.supplyAsync(() -> StarField.build(u, Vec3d.ZERO, CosmosState.time.seconds(), 6.8, StarField.IDENTITY));
        if (skyStarJob != null && skyStarJob.isDone()) {
            StarField.Geometry g = skyStarJob.join();
            skyStarVb = dev.createBuffer(() -> "cosmos sky stars", GpuBuffer.USAGE_VERTEX, g.vertices());
            skyStarIb = dev.createBuffer(() -> "cosmos sky star indices", GpuBuffer.USAGE_INDEX, g.indices());
            skyStarIndices = g.starCount() * 6;
            g.free(); skyStarJob = null;
        }
        if (skyStarVb == null) return;
        // night factor from Minecraft's clock (dusk at 12500, full night 13500..22500, dawn at 23500)
        double tod = Math.floorMod(dayTime, 24000L);
        double night = tod < 12000 ? 0 : tod < 13500 ? (tod - 12000) / 1500 : tod < 22500 ? 1 : tod < 24000 ? (24000 - tod) / 1500 : 0;
        night *= 1 - rain;
        if (CosmosState.vacuumSky) night = 1;           // no atmosphere: the stars are out in daylight too
        if (night < 0.01) return;
        // Earth's rotation: Minecraft midnight (18000) = local sidereal time RA_sun + 12h, so the real stars that are
        // opposite the Sun today are overhead at midnight.
        Vec3d sun = u.solarSystem.position(u.solarSystem.get("earth"), CosmosState.time.seconds()).negate();
        double raSun = Math.atan2(sun.y(), sun.x());
        double lst = raSun + Math.PI + (tod - 18000) / 24000.0 * 2 * Math.PI + Math.toRadians(CosmosState.observerLonDeg) * 0;
        Matrix4f icrsToWorld = icrsToWorld(lst, Math.toRadians(CosmosState.observerLatDeg));
        Matrix4f vp = new Matrix4f(projection).mul(viewRotation).mul(icrsToWorld);
        CommandEncoder enc = dev.createCommandEncoder();
        writeStarUbo(enc, vp, target.width, target.height, (float) (night * 1.4));
        try (RenderPass pass = enc.createRenderPass(() -> "cosmos overworld stars", target.getColorTextureView(), Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(CosmosPipelines.STARS));
            pass.setUniform("CosmosStars", starUbo);
            pass.setVertexBuffer(0, skyStarVb.slice());
            pass.setIndexBuffer(skyStarIb, IndexType.INT);
            pass.drawIndexed(0, 0, skyStarIndices, 1, 0);
        }
    }

    /** ICRS -> Minecraft world (X east, Y up, Z south) for local sidereal time lst and latitude lat. */
    static Matrix4f icrsToWorld(double lst, double lat) {
        double c = Math.cos(lst), s = Math.sin(lst), sl = Math.sin(lat), cl = Math.cos(lat);
        // v' = Rz(-lst) v : x' towards the meridian, y' towards east, z' towards the celestial pole
        // up = (cl, 0, sl).v', north = (-sl, 0, cl).v', east = y'
        double[][] rz = {{c, s, 0}, {-s, c, 0}, {0, 0, 1}};
        double[] up = {cl, 0, sl}, north = {-sl, 0, cl}, east = {0, 1, 0};
        double[][] w = new double[3][3];
        double[][] rows = {east, up, {sl, 0, -cl}}; // world X = east, Y = up, Z = south = -north
        for (int r = 0; r < 3; r++) for (int k = 0; k < 3; k++) {
            double v = 0; for (int j = 0; j < 3; j++) v += rows[r][j] * rz[j][k]; w[r][k] = v;
        }
        Matrix4f m = new Matrix4f();
        m.set((float) w[0][0], (float) w[1][0], (float) w[2][0], 0, (float) w[0][1], (float) w[1][1], (float) w[2][1], 0,
              (float) w[0][2], (float) w[1][2], (float) w[2][2], 0, 0, 0, 0, 1); // column-major
        return m;
    }

    private void writeStarUbo(CommandEncoder enc, Matrix4f vp, int w, int h, float brightness) {
        ByteBuffer bb = MemoryUtil.memAlloc(STAR_UBO_SIZE);
        Std140Builder.intoBuffer(bb).putMat4f(vp).putVec4(w, h, brightness, 0);
        bb.position(0).limit(STAR_UBO_SIZE);
        enc.writeToBuffer(starUbo.slice(), bb);
        MemoryUtil.memFree(bb);
    }

    // ------------------------------------------------------------------ space mode
    public void renderSpace(RenderTarget target, float yRot, float xRot, double fovDeg) {
        Universe u = CosmosState.universe();
        if (u == null || !CosmosState.spaceMode) return;
        GpuDevice dev = RenderSystem.getDevice();
        ensureStatic(dev);
        long now = System.nanoTime();
        double dtReal = lastFrameNanos == 0 ? 0 : Math.min(0.25, (now - lastFrameNanos) / 1e9);
        lastFrameNanos = now;
        CosmosState.time.tick(dtReal);
        double t = CosmosState.time.seconds();
        ShipMotion.apply(u, dtReal, yRot, xRot, t);

        Vec3d obsPc = CosmosState.shipPos.approxMetres().mul(1 / Units.PARSEC);
        updateStarsAsync(u, obsPc, t);
        updateSkyAsync(obsPc);

        // camera basis (ICRS)
        Vec3d f = CosmosState.forward(yRot, xRot);
        Vec3d upHint = CosmosState.upHint();
        if (Math.abs(f.dot(upHint)) > 0.999) upHint = Frames.ECLIPTIC_TO_EQUATORIAL.apply(Vec3d.X);
        Vec3d right = f.cross(upHint).normalize(), up = right.cross(f);
        float aspect = (float) target.width / target.height;
        double tanHalf = Math.tan(Math.toRadians(fovDeg) / 2);

        Matrix4f camToWorld = new Matrix4f().set(
                (float) right.x(), (float) right.y(), (float) right.z(), 0,
                (float) up.x(), (float) up.y(), (float) up.z(), 0,
                (float) -f.x(), (float) -f.y(), (float) -f.z(), 0,
                0, 0, 0, 1);
        Matrix4f view = new Matrix4f(camToWorld).transpose();
        Matrix4f proj = new Matrix4f().setPerspective((float) (2 * Math.atan(tanHalf)), aspect, 0.05f, 1000f);
        Matrix4f starVp = new Matrix4f(proj).mul(view);

        CommandEncoder enc = dev.createCommandEncoder();
        writeStarUbo(enc, starVp, target.width, target.height, 1.0f);
        writeSpaceUbo(enc, u, t, camToWorld, target.width, target.height, (float) tanHalf);

        try (RenderPass pass = enc.createRenderPass(() -> "cosmos space", target.getColorTextureView(), Optional.of(new Vector4f(0, 0, 0, 1)))) {
            if (starVb != null && starIndices > 0) {
                pass.setPipeline(RenderSystem.getCompiledPipeline(CosmosPipelines.STARS));
                pass.setUniform("CosmosStars", starUbo);
                pass.setVertexBuffer(0, starVb.slice());
                pass.setIndexBuffer(starIb, IndexType.INT);
                pass.drawIndexed(0, 0, starIndices, 1, 0);
            }
            pass.setPipeline(RenderSystem.getCompiledPipeline(CosmosPipelines.SPACE));
            pass.setUniform("CosmosSpace", spaceUbo);
            pass.setUniform("SkySampler", skyView, sampler);
            pass.setUniform("BodySampler", bodyView, sampler);
            pass.setVertexBuffer(0, fullscreenTri.slice());
            pass.draw(3, 1, 0, 0);
        }
    }

    private void writeSpaceUbo(CommandEncoder enc, Universe u, double t, Matrix4f camToWorld, int w, int h, float tanHalf) {
        UniversePos cam = CosmosState.shipPos;
        // ---- bodies: pick the 16 with the largest apparent size (stars always qualify)
        List<CosmosState.SceneBody> all = CosmosState.sceneBodies(u, t);
        all.sort(Comparator.comparingDouble((CosmosState.SceneBody s) -> -(s.radiusM() / Math.max(cam.distanceTo(s.pos()), 1)) * (s.type() == 2 ? 1e3 : 1)));
        List<CosmosState.SceneBody> bodies = new ArrayList<>(all.subList(0, Math.min(MAX_BODIES, all.size())));
        int ringIndex = -1;
        for (int i = 0; i < bodies.size(); i++) if (bodies.get(i).hasRings()) { ringIndex = i; break; }

        // ---- black hole within 3000 gravitational radii
        BlackHole bh = null; Vec3d bhCam = null;
        for (BlackHole b : u.blackHoles) {
            double rg = b.gravitationalRadiusM();
            Vec3d rel = b.position().to(cam);
            if (rel.length() < 3000 * rg) { bh = b; bhCam = rel.mul(1 / rg); break; }
        }
        // ---- galaxy seen from outside
        Vec3d obsPc = cam.approxMetres().mul(1 / Units.PARSEC);
        Vec3d gc = MilkyWayModel.toGalactocentric(obsPc);
        double gcDist = gc.length();
        boolean galaxyOn = false; Vec3d galCam = Vec3d.ZERO; double galScale = 1, galBar = MilkyWayModel.BAR_ANGLE; Mat3 worldToGal = Mat3.IDENTITY;
        double skyGain = 1.0;
        if (gcDist > 14_000 || Math.abs(gc.z()) > 2_500) {
            galaxyOn = true; galCam = gc.mul(1e-3);
            Mat3 g = Frames.EQUATORIAL_TO_GALACTIC;
            worldToGal = new Mat3(-g.m00(), -g.m01(), -g.m02(), -g.m10(), -g.m11(), -g.m12(), g.m20(), g.m21(), g.m22());
            skyGain = Math.max(0, 1 - (gcDist - 14_000) / 8_000);
        }
        // a catalogued galaxy nearby takes over the volume pass (e.g. Andromeda)
        DeepSkyCatalog.Dso near = nearestBigGalaxy(u, obsPc);
        if (near != null) {
            galaxyOn = true; skyGain = 0;
            double dKpc = near.distKpc();
            Vec3d centre = near.direction().mul(dKpc * 1000);
            galScale = Math.max(0.2, near.diameterPc() / 50_000);
            worldToGal = galaxyFrame(near);
            galCam = worldToGal.apply(obsPc.sub(centre)).mul(1e-3);
            galBar = 0.5;
        }
        Mat3 frameMat = bh != null ? bhFrame(bh) : worldToGal;
        Vec3d bhInFrame = bh != null ? frameMat.apply(bhCam) : Vec3d.ZERO;

        ByteBuffer bb = MemoryUtil.memAlloc(SPACE_UBO_SIZE);
        Std140Builder s = Std140Builder.intoBuffer(bb);
        s.putMat4f(camToWorld);
        s.putVec4(w, h, tanHalf, 1.0f);
        s.putVec4(bodies.size(), (float) (t % 1e6), (float) skyGain, ringIndex);
        s.putVec4((float) bhInFrame.x(), (float) bhInFrame.y(), (float) bhInFrame.z(), bh != null ? 1 : 0);
        boolean disc = bh != null && !bh.name().startsWith("Gaia");
        s.putVec4(bh != null ? (float) bh.spin() : 0, disc ? 1 : 0, 9000f, 18f);
        s.putVec4((float) galCam.x(), (float) galCam.y(), (float) galCam.z(), galaxyOn && bh == null ? 1 : 0);
        s.putVec4((float) galScale, (float) galBar, 1.0f, 0);
        s.putMat4f(toMatrix4f(frameMat));
        // ring (Saturn): plane normal = planet spin axis, radii from the NASA ring fact sheet
        if (ringIndex >= 0) {
            Body sat = bodies.get(ringIndex).body();
            Vec3d nrm = sat.rotation.spinAxis(t);
            double inner = 66_900, outer = 139_826;
            for (Body.Ring r : sat.rings) { inner = Math.min(inner, r.innerM() / 1e3); outer = Math.max(outer, r.outerM() / 1e3); }
            s.putVec4((float) nrm.x(), (float) nrm.y(), (float) nrm.z(), (float) inner);
            s.putVec4((float) outer, ringIndex, 0, 0);
        } else { s.putVec4(0, 0, 1, 0); s.putVec4(0, -1, 0, 0); }
        // per-body arrays (each a vec4[16])
        float[][] pos = new float[MAX_BODIES][4], col = new float[MAX_BODIES][4], atm = new float[MAX_BODIES][4], atm2 = new float[MAX_BODIES][4], light = new float[MAX_BODIES][4];
        for (int i = 0; i < bodies.size(); i++) {
            CosmosState.SceneBody b = bodies.get(i);
            Vec3d rel = cam.to(b.pos()).mul(1e-3); // km
            pos[i] = new float[]{(float) rel.x(), (float) rel.y(), (float) rel.z(), (float) (b.radiusM() / 1e3)};
            col[i] = new float[]{b.colour()[0], b.colour()[1], b.colour()[2], b.type()};
            Vec3d toLight = b.pos().to(b.lightPos());
            double dl = toLight.length();
            Vec3d ld = dl > 1 ? toLight.mul(1 / dl) : new Vec3d(0, 0, 1);
            double intensity = b.lightLumSun() * Math.pow(Units.AU / Math.max(dl, 1e6), 2) * 12.0;
            light[i] = new float[]{(float) ld.x(), (float) ld.y(), (float) ld.z(), (float) Math.min(intensity, 1e4)};
            if (b.atmosphere() != null && !Double.isNaN(b.atmosphere().surfacePressureBar())) {
                Body.Atmosphere a = b.atmosphere();
                atm[i] = new float[]{(float) (a.rayleigh()[0] * 1e3), (float) (a.rayleigh()[1] * 1e3), (float) (a.rayleigh()[2] * 1e3), (float) (a.rayleighH() / 1e3)};
                atm2[i] = new float[]{(float) (a.mieScatter() * 1e3), (float) a.mieG(), (float) (a.topM() / 1e3), 0};
            }
            if (b.type() == 2) atm2[i][3] = (float) (60.0 * Math.min(10, Math.max(0.3, Math.cbrt(b.emissive()))));
        }
        for (float[][] arr : new float[][][]{pos, col, atm, atm2, light})
            for (float[] v : arr) s.putVec4(v[0], v[1], v[2], v[3]);
        // the nearest walkable body (within 60 radii) is drawn with its real surface map
        int texIndex = -1; String focus = null; double bestRatio = 60;
        for (int i = 0; i < bodies.size(); i++) {
            Body bd = bodies.get(i).body();
            if (bd == null || !dev.saurus.cosmos.core.terrain.PlanetTerrains.isWalkable(bd.id)) continue;
            double ratio = cam.distanceTo(bodies.get(i).pos()) / bodies.get(i).radiusM();
            if (ratio < bestRatio) { bestRatio = ratio; focus = bd.id; texIndex = i; }
        }
        updateBodyMapAsync(focus);
        Body fb = texIndex >= 0 ? bodies.get(texIndex).body() : null;
        boolean ready = fb != null && fb.id.equals(bodyTexId);
        Mat3 toBodyFixed = ready && fb.rotation != null ? fb.rotation.orientation(t).transpose() : Mat3.IDENTITY;
        s.putMat4f(toMatrix4f(toBodyFixed));
        s.putVec4(ready ? texIndex : -1, 0, 0, 0);
        bb.position(0).limit(SPACE_UBO_SIZE);
        enc.writeToBuffer(spaceUbo.slice(), bb);
        MemoryUtil.memFree(bb);
    }

    private static DeepSkyCatalog.Dso nearestBigGalaxy(Universe u, Vec3d obsPc) {
        DeepSkyCatalog.Dso best = null; double bestRatio = 4;
        for (DeepSkyCatalog.Dso d : u.deepSky.all()) {
            if (d.kind() != DeepSkyCatalog.Kind.GALAXY || !d.hasDistance() || Double.isNaN(d.diameterPc()) || d.diameterPc() < 3000) continue;
            double dist = d.direction().mul(d.distKpc() * 1000).distance(obsPc);
            double ratio = dist / d.diameterPc();
            if (ratio < bestRatio) { bestRatio = ratio; best = d; }
        }
        return best;
    }

    /** Galaxy frame from catalogue position angle and axis ratio (inclination = acos(minor/major)). */
    private static Mat3 galaxyFrame(DeepSkyCatalog.Dso d) {
        Vec3d los = d.direction();
        Vec3d north = Vec3d.Z.sub(los.mul(los.z())).normalize();
        Vec3d east = north.cross(los).normalize();
        double pa = Math.toRadians(d.paDeg());
        Vec3d major = north.mul(Math.cos(pa)).add(east.mul(Math.sin(pa)));
        double ratio = Double.isNaN(d.minArcmin()) || Double.isNaN(d.majArcmin()) ? 0.5 : d.minArcmin() / d.majArcmin();
        double inc = Math.acos(Math.max(0.15, Math.min(1, ratio)));
        Vec3d minor = los.cross(major).normalize();
        Vec3d normal = los.mul(-Math.cos(inc)).add(minor.mul(Math.sin(inc))).normalize();
        Vec3d x = major.normalize(), y = normal.cross(x).normalize();
        return new Mat3(x.x(), x.y(), x.z(), y.x(), y.y(), y.z(), normal.x(), normal.y(), normal.z());
    }

    /** Black hole disc frame: Sgr A* disc in the Galactic plane (illustrative), others in the ecliptic. */
    private static Mat3 bhFrame(BlackHole b) {
        if (b.name().startsWith("Sagittarius")) return Frames.EQUATORIAL_TO_GALACTIC;
        return Frames.EQUATORIAL_TO_ECLIPTIC;
    }

    private static Matrix4f toMatrix4f(Mat3 m) {
        // GLSL mat3(mat4) * v must equal m.apply(v): store m column-major
        return new Matrix4f().set(
                (float) m.m00(), (float) m.m10(), (float) m.m20(), 0,
                (float) m.m01(), (float) m.m11(), (float) m.m21(), 0,
                (float) m.m02(), (float) m.m12(), (float) m.m22(), 0,
                0, 0, 0, 1);
    }
}
