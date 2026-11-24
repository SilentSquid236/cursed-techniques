package com.ethanpierce.cursedtech.client;

import com.ethanpierce.cursedtech.network.ModNetwork;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Short scripted camera shots. While a shot plays the game is put in
 * third person (so your own model renders) and {@code CameraMixin} overrides
 * the camera position/rotation with {@link #pose}. Control stays with the
 * player the whole time; shots are under 2.5 seconds.
 */
public final class CinematicCamera {
    private CinematicCamera() {}

    private static int shot;
    private static int subjectId, targetId;
    private static long startTick;
    private static int duration;
    private static float baseYaw;
    private static CameraType previous;
    public static String label = "";
    public static int color;

    public record Pose(double x, double y, double z, float yaw, float pitch) {}

    public static boolean active() {
        return shot != 0;
    }

    public static int shot() {
        return shot;
    }

    public static void start(int newShot, int subject, int target, int ticks, String text, int rgb) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientConfig.CINEMATIC_CAMERA.get() || mc.level == null) return;
        Entity s = mc.level.getEntity(subject);
        if (s == null) return;
        // A domain shot outranks a Black Flash shot already playing.
        if (active() && shot == ModNetwork.CinematicPayload.DOMAIN_ORBIT && newShot != shot) return;
        if (!active()) previous = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        shot = newShot;
        subjectId = subject;
        targetId = target;
        duration = ticks;
        startTick = ClientState.ticks;
        baseYaw = s.getYRot();
        label = text;
        color = rgb;
    }

    public static void tick() {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ClientState.ticks - startTick >= duration || mc.level.getEntity(subjectId) == null) {
            stop();
        }
    }

    public static void stop() {
        if (!active()) return;
        shot = 0;
        Minecraft mc = Minecraft.getInstance();
        if (previous != null) mc.options.setCameraType(previous);
        previous = null;
    }

    /** 0..1 progress through the current shot. */
    public static float progress(float partialTick) {
        if (!active()) return 0;
        return Mth.clamp((ClientState.ticks - startTick + partialTick) / duration, 0, 1);
    }

    /** Letterbox amount 0..1, eased in and out. */
    public static float letterbox(float partialTick) {
        if (!active()) return 0;
        float t = ClientState.ticks - startTick + partialTick;
        float in = Mth.clamp(t / 5f, 0, 1);
        float out = Mth.clamp((duration - t) / 5f, 0, 1);
        return smooth(Math.min(in, out));
    }

    public static Pose pose(float partialTick) {
        if (!active()) return null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        Entity subject = mc.level.getEntity(subjectId);
        if (subject == null) return null;
        float t = progress(partialTick);
        Vec3 base = subject.getPosition(partialTick);
        Vec3 focus = base.add(0, subject.getBbHeight() * 0.85, 0);

        Vec3 cam;
        Vec3 look;
        if (shot == ModNetwork.CinematicPayload.DOMAIN_ORBIT) {
            if (t < 0.25f) {
                // Low, close front shot that pushes in on the caster.
                float k = smooth(t / 0.25f);
                double dist = Mth.lerp(k, 3.2, 1.9);
                Vec3 fwd = Vec3.directionFromRotation(0, baseYaw);
                cam = focus.add(fwd.scale(dist)).add(0, Mth.lerp(k, -0.6, -0.2), 0);
                look = focus;
            } else {
                // Rising, widening orbit to reveal the domain closing.
                float k = smooth((t - 0.25f) / 0.75f);
                double angle = Math.toRadians(baseYaw + 180 + k * 220);
                double r = Mth.lerp(k, 2.5, 6.5);
                double h = Mth.lerp(k, 0.2, 3.2);
                cam = focus.add(-Math.sin(angle) * r, h, Math.cos(angle) * r);
                look = focus.add(0, -0.3 * k, 0);
            }
        } else {
            // Black Flash: side-on shot of both fighters, snapping in.
            Entity target = targetId >= 0 ? mc.level.getEntity(targetId) : null;
            Vec3 other = target != null ? target.getPosition(partialTick).add(0, target.getBbHeight() * 0.6, 0)
                    : focus.add(Vec3.directionFromRotation(0, baseYaw).scale(2));
            Vec3 mid = focus.add(other).scale(0.5);
            Vec3 axis = other.subtract(focus).multiply(1, 0, 1);
            if (axis.lengthSqr() < 1e-4) axis = Vec3.directionFromRotation(0, baseYaw);
            Vec3 side = new Vec3(-axis.z, 0, axis.x).normalize();
            float k = smooth(Math.min(1, t * 1.6f));
            double dist = Mth.lerp(k, 4.2, 2.8) + axis.length() * 0.4;
            cam = mid.add(side.scale(dist)).add(0, Mth.lerp(k, 0.9, 0.3), 0);
            look = mid;
        }

        cam = clip(look, cam);
        Vec3 d = look.subtract(cam);
        double horiz = Math.sqrt(d.x * d.x + d.z * d.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(Math.atan2(-d.y, horiz));
        return new Pose(cam.x, cam.y, cam.z, yaw, pitch);
    }

    /** Keep the camera out of walls. */
    private static Vec3 clip(Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (hit.getType() == HitResult.Type.MISS) return to;
        Vec3 h = hit.getLocation();
        return h.add(from.subtract(h).normalize().scale(0.35));
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
