package com.ethanpierce.cursedtech.ability;

import com.ethanpierce.cursedtech.core.Fx;
import com.ethanpierce.cursedtech.core.Tasks;
import com.ethanpierce.cursedtech.core.Technique;
import com.ethanpierce.cursedtech.network.ModNetwork;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Maximum techniques: Special Grade finishers. Each is telegraphed (a charge
 * or a visible marker) so a target who's paying attention can get out of the
 * way, costs most of an energy bar and has a long cooldown. None break blocks.
 */
public final class Maximums {
    private Maximums() {}

    public static boolean use(ServerPlayer p, Technique t) {
        boolean ok = switch (t) {
            case VORTEX -> gravityWell(p, t);
            case BLADE -> worldRend(p, t);
            case FLAME -> meteor(p, t);
            case UNBOUNDED -> hollowAnnihilation(p, t);
            case CALAMITY -> divineFlame(p, t);
            default -> false;
        };
        if (ok) {
            Fx.cinematic(p, ModNetwork.CinematicPayload.FINISHER, p, null, 22, t.abilityName(3), t.color);
            Fx.impact(p, 0, t.color, t.abilityName(3).replace("Maximum: ", "").toUpperCase());
        }
        return ok;
    }

    // ------------------------------------------------------------- VORTEX

    /** A massive pull point that crushes everything dragged into its core. */
    private static boolean gravityWell(ServerPlayer p, Technique t) {
        Vec3 point = Fx.aim(p, 22, false);
        ServerLevel l = p.serverLevel();
        l.playSound(null, point.x, point.y, point.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.0F, 1.6F);
        Tasks.start(l, (level, age) -> {
            Fx.sphere(level, ParticleTypes.REVERSE_PORTAL, point, 3.5 - age * 0.05, 30);
            Fx.sphere(level, Fx.dust(t.dust(), 2.5F), point, 1.0 + level.random.nextDouble(), 8);
            for (LivingEntity e : Fx.around(p, point, 11)) {
                Vec3 to = point.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                Fx.push(e, to.normalize().scale(Math.min(0.45, 1.4 / Math.max(0.8, to.length()))));
                e.fallDistance = 0;
                if (age % 10 == 0 && to.length() < 4) Fx.hit(p, e, 2.0F);
            }
            if (age >= 50) {
                for (LivingEntity e : Fx.around(p, point, 5.5)) Fx.hit(p, e, 12.0F);
                Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, point, 1, 0, 0);
                Fx.burst(level, ParticleTypes.END_ROD, point, 80, 0.2, 0.6);
                level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 2.0F, 0.5F);
                Fx.impactNear(level, point, 30, 1, t.color, "");
                return true;
            }
            return false;
        });
        return true;
    }

    // -------------------------------------------------------------- BLADE

    /** One enormous cut along your aim line, 30 blocks long. */
    private static boolean worldRend(ServerPlayer p, Technique t) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double len = 30;
        HitResult wall = l.clip(new ClipContext(eye, eye.add(look.scale(len)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        if (wall.getType() != HitResult.Type.MISS) len = wall.getLocation().distanceTo(eye);
        final double reach = len;
        Set<LivingEntity> hit = new HashSet<>();
        // Short wind-up so it reads on screen, then the cut travels outward.
        Tasks.start(l, (level, age) -> {
            if (age < 8) {
                Fx.burst(level, Fx.dust(0xFFFFFF, 1.2F), eye.add(look.scale(1.5)), 10, 0.4, 0);
                return false;
            }
            double from = (age - 8) * 6, to = Math.min(reach, from + 6);
            for (double s = from; s <= to; s += 0.6) {
                Vec3 pt = eye.add(look.scale(s));
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 2, 0.6, 0.6, 0.6, 0);
                level.sendParticles(Fx.dust(t.dust(), 1.4F), pt.x, pt.y, pt.z, 3, 0.8, 0.8, 0.8, 0);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pt, pt).inflate(1.6),
                        e -> Fx.validTarget(p, e))) {
                    if (hit.add(e)) {
                        Fx.hit(p, e, 14.0F);
                        Fx.push(e, look.scale(0.8).add(0, 0.4, 0));
                    }
                }
            }
            if (age == 8) level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 2.0F, 0.4F);
            if (to >= reach) {
                if (!hit.isEmpty()) Fx.impactNear(level, eye, 30, 1, t.color, "");
                return true;
            }
            return false;
        });
        return true;
    }

    // -------------------------------------------------------------- FLAME

    /** Calls down a meteor. A ring marks where it lands 1.5s later. */
    private static boolean meteor(ServerPlayer p, Technique t) {
        ServerLevel l = p.serverLevel();
        Vec3 aim = Fx.aim(p, 32, true);
        HitResult ground = l.clip(new ClipContext(aim.add(0, 0.5, 0), aim.add(0, -10, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        Vec3 c = ground.getType() == HitResult.Type.MISS ? aim : ground.getLocation();
        l.playSound(null, c.x, c.y, c.z, SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 2.0F, 0.5F);
        Tasks.start(l, (level, age) -> {
            if (age < 30) {
                Fx.ring(level, ParticleTypes.FLAME, c.add(0, 0.1, 0), 6, 40);
                Vec3 rock = c.add(0, 22 - age * 0.73, 0);
                Fx.burst(level, ParticleTypes.LAVA, rock, 4, 0.6, 0);
                Fx.burst(level, ParticleTypes.LARGE_SMOKE, rock, 6, 0.8, 0.02);
                Fx.burst(level, Fx.dust(t.dust(), 3.0F), rock, 8, 0.8, 0);
                return false;
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(6, 4, 6),
                    e -> Fx.validTarget(p, e) && e.position().distanceTo(c) <= 6.5)) {
                Fx.hit(p, e, 14.0F);
                e.igniteForSeconds(6);
                Vec3 away = e.position().subtract(c).multiply(1, 0, 1);
                Fx.push(e, (away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 0) : away.normalize().scale(1.2)).add(0, 0.8, 0));
            }
            Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, c.add(0, 1, 0), 2, 1.5, 0);
            Fx.burst(level, ParticleTypes.FLAME, c.add(0, 1, 0), 120, 2.5, 0.25);
            Fx.burst(level, ParticleTypes.LAVA, c, 40, 2.0, 0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.6F);
            Fx.impactNear(level, c, 32, 1, 0xFF8A2A, "");
            return true;
        });
        return true;
    }

    // ---------------------------------------------------------- UNBOUNDED

    /**
     * Attraction and repulsion collide into an imaginary mass that tears
     * along your aim. It passes through walls (without breaking them).
     */
    private static boolean hollowAnnihilation(ServerPlayer p, Technique t) {
        ServerLevel l = p.serverLevel();
        Set<LivingEntity> hit = new HashSet<>();
        final Vec3[] pos = new Vec3[1];
        final Vec3[] dir = new Vec3[1];
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 2.0F, 0.5F);
        Tasks.start(l, (level, age) -> {
            if (!p.isAlive()) return true;
            if (age < 18) {
                // Blue and red converge in front of the caster.
                Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(2));
                Vec3 side = p.getLookAngle().cross(new Vec3(0, 1, 0)).normalize();
                double spread = 2.2 * (1 - age / 18.0);
                Fx.burst(level, Fx.dust(0x3A7BFF, 2.0F), front.add(side.scale(spread)), 6, 0.15, 0);
                Fx.burst(level, Fx.dust(0xFF3030, 2.0F), front.subtract(side.scale(spread)), 6, 0.15, 0);
                return false;
            }
            if (age == 18) {
                dir[0] = p.getLookAngle();
                pos[0] = p.getEyePosition().add(dir[0].scale(2));
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0F, 0.4F);
                Fx.impactNear(level, pos[0], 24, 1, t.color, "");
            }
            pos[0] = pos[0].add(dir[0].scale(1.1));
            Fx.sphere(level, Fx.dust(t.dust(), 3.0F), pos[0], 2.2, 30);
            Fx.burst(level, ParticleTypes.END_ROD, pos[0], 6, 0.8, 0.05);
            List<LivingEntity> in = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos[0], pos[0]).inflate(2.6),
                    e -> Fx.validTarget(p, e));
            for (LivingEntity e : in) {
                if (hit.add(e)) {
                    Fx.hit(p, e, 16.0F);
                    Fx.push(e, dir[0].scale(1.5).add(0, 0.3, 0));
                }
            }
            return age >= 18 + 45;
        });
        return true;
    }

    // ----------------------------------------------------------- CALAMITY

    /** Draws a bow of flame and looses a single arrow that detonates. */
    private static boolean divineFlame(ServerPlayer p, Technique t) {
        ServerLevel l = p.serverLevel();
        final Vec3[] pos = new Vec3[1];
        final Vec3[] vel = new Vec3[1];
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.5F, 0.5F);
        Tasks.start(l, (level, age) -> {
            if (!p.isAlive()) return true;
            if (age < 12) {
                Vec3 front = p.getEyePosition().add(p.getLookAngle().scale(1.2));
                Fx.burst(level, ParticleTypes.FLAME, front, 10, 0.3, 0.02);
                return false;
            }
            if (age == 12) {
                vel[0] = p.getLookAngle().scale(2.4);
                pos[0] = p.getEyePosition().add(p.getLookAngle());
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 2.0F, 0.6F);
            }
            for (int sub = 0; sub < 3; sub++) {
                Vec3 next = pos[0].add(vel[0].scale(1 / 3.0));
                HitResult wall = level.clip(new ClipContext(pos[0], next, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, p));
                boolean entity = !level.getEntitiesOfClass(LivingEntity.class, new AABB(pos[0], next).inflate(0.5),
                        e -> Fx.validTarget(p, e)).isEmpty();
                if (entity || wall.getType() != HitResult.Type.MISS || age >= 12 + 20) {
                    Vec3 at = wall.getType() != HitResult.Type.MISS ? wall.getLocation() : next;
                    for (LivingEntity e : Fx.around(p, at, 5.5)) {
                        Fx.hit(p, e, 14.0F);
                        e.igniteForSeconds(8);
                        Fx.push(e, e.position().subtract(at).normalize().scale(1.0).add(0, 0.5, 0));
                    }
                    Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, at, 1, 0, 0);
                    Fx.burst(level, ParticleTypes.FLAME, at, 150, 2.0, 0.3);
                    level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0F, 0.7F);
                    Fx.impactNear(level, at, 32, 1, t.color, "");
                    return true;
                }
                pos[0] = next;
                level.sendParticles(ParticleTypes.FLAME, next.x, next.y, next.z, 4, 0.05, 0.05, 0.05, 0.02);
                level.sendParticles(Fx.dust(t.dust(), 1.8F), next.x, next.y, next.z, 2, 0, 0, 0, 0);
            }
            return false;
        });
        return true;
    }
}
