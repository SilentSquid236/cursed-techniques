package com.ethanpierce.cursedtech.ability;

import com.ethanpierce.cursedtech.core.CursedData;
import com.ethanpierce.cursedtech.core.Fx;
import com.ethanpierce.cursedtech.core.ModRegistries;
import com.ethanpierce.cursedtech.core.Tasks;
import com.ethanpierce.cursedtech.core.Technique;
import com.ethanpierce.cursedtech.domain.DomainManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
 * Server-side ability logic. Numbers are tuned so a technique hit is roughly
 * a sword hit: an average player in iron/diamond can trade, dodge and recover.
 */
public final class Abilities {
    private Abilities() {}

    public static void use(ServerPlayer p, int slot) {
        if (slot < 0 || slot > 2 || p.isSpectator() || !p.isAlive()) return;
        CursedData d = p.getData(ModRegistries.CURSED);
        Technique t = d.technique;
        if (t == Technique.NONE) {
            bar(p, "You have no innate technique.", ChatFormatting.GRAY);
            return;
        }
        long now = p.serverLevel().getGameTime();
        if (d.burnoutUntil > now) {
            bar(p, "Technique burnt out (" + (d.burnoutUntil - now) / 20 + "s)", ChatFormatting.DARK_RED);
            return;
        }
        if (d.cooldownUntil[slot] > now) return;
        float cost = t.cost(slot);
        if (d.energy < cost && !p.isCreative()) {
            bar(p, "Not enough cursed energy", ChatFormatting.DARK_PURPLE);
            return;
        }

        boolean ok = switch (slot) {
            case 0 -> ability1(p, t);
            case 1 -> ability2(p, t);
            default -> DomainManager.open(p, t);
        };
        if (!ok) return;

        if (!p.isCreative()) d.energy -= cost;
        int cd = t.cooldown(slot);
        if (slot < 2 && DomainManager.isInOwnDomain(p)) cd /= 2; // techniques flow inside your own domain
        d.cooldownUntil[slot] = now + cd;
        d.cooldownMax[slot] = cd;
    }

    private static void bar(ServerPlayer p, String msg, ChatFormatting color) {
        p.displayClientMessage(Component.literal(msg).withStyle(color), true);
    }

    private static boolean ability1(ServerPlayer p, Technique t) {
        return switch (t) {
            case VORTEX -> collapsePoint(p, t);
            case BLADE -> cleave(p, t);
            case FLAME -> emberShot(p, t);
            default -> false;
        };
    }

    private static boolean ability2(ServerPlayer p, Technique t) {
        return switch (t) {
            case VORTEX -> repulsionBurst(p, t);
            case BLADE -> dismantleStorm(p, t);
            case FLAME -> eruption(p, t);
            default -> false;
        };
    }

    // ================================================================ VORTEX

    /** Opens a pull point where you aim; it drags enemies in for 1.2s, then implodes. */
    private static boolean collapsePoint(ServerPlayer p, Technique t) {
        ServerLevel l = p.serverLevel();
        Vec3 point = Fx.aim(p, 16, false);
        l.playSound(null, point.x, point.y, point.z, SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.6F, 1.8F);
        Tasks.start(l, (level, age) -> {
            Fx.sphere(level, ParticleTypes.REVERSE_PORTAL, point, 1.2 - age * 0.04, 10);
            Fx.burst(level, Fx.dust(t.dust(), 1.4F), point, 4, 0.25, 0);
            for (LivingEntity e : Fx.around(p, point, 6.5)) {
                Vec3 to = point.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                double dist = Math.max(0.6, to.length());
                Fx.push(e, to.normalize().scale(Math.min(0.32, 0.9 / dist)));
                e.fallDistance = 0;
            }
            if (age >= 24) {
                for (LivingEntity e : Fx.around(p, point, 3.2)) Fx.hit(p, e, 5.0F);
                Fx.burst(level, ParticleTypes.EXPLOSION, point, 2, 0.3, 0);
                Fx.burst(level, ParticleTypes.END_ROD, point, 30, 0.1, 0.35);
                level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.9F, 1.6F);
                return true;
            }
            return false;
        });
        return true;
    }

    /** A shockwave around you that blasts everyone back. */
    private static boolean repulsionBurst(ServerPlayer p, Technique t) {
        ServerLevel l = p.serverLevel();
        Vec3 c = p.position().add(0, 1, 0);
        List<LivingEntity> hits = Fx.around(p, c, 5.5);
        for (LivingEntity e : hits) {
            Vec3 away = e.position().subtract(p.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1e-4) away = p.getLookAngle().multiply(1, 0, 1);
            Fx.hit(p, e, 6.0F);
            Fx.push(e, away.normalize().scale(1.7).add(0, 0.55, 0));
        }
        l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.0F, 1.4F);
        Tasks.start(l, (level, age) -> {
            double r = 1 + age * 1.3;
            Fx.ring(level, Fx.dust(t.dust(), 2.0F), c.add(0, -0.6, 0), r, 28 + age * 6);
            Fx.ring(level, ParticleTypes.CLOUD, c.add(0, -0.8, 0), r, 12);
            return age >= 4;
        });
        Fx.burst(l, ParticleTypes.SONIC_BOOM, c, 1, 0, 0);
        if (!hits.isEmpty()) Fx.impact(p, 0, t.color, "");
        return true;
    }

    // ================================================================= BLADE

    /** A long invisible cut along your aim line. */
    private static boolean cleave(ServerPlayer p, Technique t) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        double len = 14;
        HitResult wall = l.clip(new ClipContext(eye, eye.add(look.scale(len)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        if (wall.getType() != HitResult.Type.MISS) len = wall.getLocation().distanceTo(eye);

        Set<LivingEntity> hit = new HashSet<>();
        for (double s = 1; s <= len; s += 0.5) {
            Vec3 pt = eye.add(look.scale(s));
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(pt, pt).inflate(0.7),
                    e -> Fx.validTarget(p, e))) hit.add(e);
            if (((int) (s * 2)) % 2 == 0) {
                l.sendParticles(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
            l.sendParticles(Fx.dust(0xFFFFFF, 0.8F), pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0);
        }
        for (LivingEntity e : hit) {
            Fx.hit(p, e, 7.0F);
            Fx.burst(l, Fx.dust(t.dust(), 1.2F), e.position().add(0, e.getBbHeight() / 2, 0), 12, 0.3, 0);
        }
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.7F);
        if (!hit.isEmpty()) {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.6F);
        }
        return true;
    }

    /** Three waves of slashes across a cone in front of you. */
    private static boolean dismantleStorm(ServerPlayer p, Technique t) {
        Tasks.start(p.serverLevel(), (level, age) -> {
            if (!p.isAlive()) return true;
            if (age % 6 == 0) {
                Vec3 eye = p.getEyePosition();
                Vec3 look = p.getLookAngle();
                boolean any = false;
                for (LivingEntity e : Fx.around(p, eye, 9)) {
                    Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
                    if (to.normalize().dot(look) > Math.cos(Math.toRadians(50)) && p.hasLineOfSight(e)) {
                        Fx.hit(p, e, 3.5F);
                        Fx.burst(level, ParticleTypes.SWEEP_ATTACK, e.position().add(0, 1, 0), 3, 0.4, 0);
                        any = true;
                    }
                }
                for (int i = 0; i < 14; i++) {
                    double d = 1.5 + level.random.nextDouble() * 7.5;
                    double yaw = Math.toRadians((level.random.nextDouble() - 0.5) * 90);
                    Vec3 dir = look.yRot((float) yaw);
                    Vec3 pt = eye.add(dir.scale(d)).add(0, (level.random.nextDouble() - 0.5) * 1.5, 0);
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                    level.sendParticles(Fx.dust(t.dust(), 0.9F), pt.x, pt.y, pt.z, 2, 0.2, 0.2, 0.2, 0);
                }
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
                        1.0F, 0.9F + level.random.nextFloat() * 0.5F);
                if (any) Fx.impact(p, 0, 0xFFFFFF, "");
            }
            return age >= 12;
        });
        return true;
    }

    // ================================================================= FLAME

    /** A fast fire bolt. Doesn't grief: no blocks are lit. */
    private static boolean emberShot(ServerPlayer p, Technique t) {
        ServerLevel l = p.serverLevel();
        final Vec3[] pos = {p.getEyePosition().add(p.getLookAngle().scale(0.8))};
        final Vec3 vel = p.getLookAngle().scale(1.6);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.9F, 1.2F);
        Tasks.start(l, (level, age) -> {
            for (int sub = 0; sub < 2; sub++) {
                Vec3 next = pos[0].add(vel.scale(0.5));
                HitResult wall = level.clip(new ClipContext(pos[0], next, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, p));
                List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(pos[0], next).inflate(0.45), e -> Fx.validTarget(p, e));
                if (!hits.isEmpty()) {
                    LivingEntity e = hits.get(0);
                    Fx.hit(p, e, 5.0F);
                    e.igniteForSeconds(4);
                    Fx.burst(level, ParticleTypes.LAVA, next, 6, 0.2, 0);
                    Fx.burst(level, ParticleTypes.FLAME, next, 20, 0.2, 0.08);
                    level.playSound(null, next.x, next.y, next.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
                    return true;
                }
                if (wall.getType() != HitResult.Type.MISS) {
                    Fx.burst(level, ParticleTypes.FLAME, wall.getLocation(), 12, 0.15, 0.05);
                    Fx.burst(level, ParticleTypes.SMOKE, wall.getLocation(), 6, 0.15, 0.02);
                    return true;
                }
                pos[0] = next;
                level.sendParticles(ParticleTypes.FLAME, next.x, next.y, next.z, 3, 0.05, 0.05, 0.05, 0.01);
                level.sendParticles(Fx.dust(t.dust(), 1.5F), next.x, next.y, next.z, 1, 0, 0, 0, 0);
            }
            return age >= 20;
        });
        return true;
    }

    /** Telegraphed eruption under your target. Readable, so it can be dodged. */
    private static boolean eruption(ServerPlayer p, Technique t) {
        ServerLevel l = p.serverLevel();
        Vec3 aim = Fx.aim(p, 18, true);
        // Snap to the ground below the aim point.
        HitResult ground = l.clip(new ClipContext(aim.add(0, 0.5, 0), aim.add(0, -6, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, p));
        Vec3 c = ground.getType() == HitResult.Type.MISS ? aim : ground.getLocation();
        l.playSound(null, c.x, c.y, c.z, SoundEvents.LAVA_AMBIENT, SoundSource.PLAYERS, 1.5F, 0.7F);
        Tasks.start(l, (level, age) -> {
            if (age < 14) {
                Fx.ring(level, ParticleTypes.FLAME, c.add(0, 0.1, 0), 3.5, 24);
                Fx.ring(level, Fx.dust(t.dust(), 1.0F), c.add(0, 0.1, 0), 3.5 * age / 14.0, 16);
                if (age % 3 == 0) Fx.burst(level, ParticleTypes.LAVA, c, 2, 1.2, 0);
                return false;
            }
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(c.x - 3.5, c.y - 1, c.z - 3.5, c.x + 3.5, c.y + 4, c.z + 3.5),
                    e -> Fx.validTarget(p, e) && e.position().multiply(1, 0, 1).distanceTo(c.multiply(1, 0, 1)) <= 3.5)) {
                Fx.hit(p, e, 8.0F);
                e.igniteForSeconds(3);
                Fx.push(e, new Vec3(0, 0.9, 0));
            }
            for (int i = 0; i < 5; i++) {
                Vec3 col = c.add(0, i * 0.8, 0);
                Fx.burst(level, ParticleTypes.FLAME, col, 25, 0.8, 0.12);
            }
            Fx.burst(level, ParticleTypes.LAVA, c, 20, 1.0, 0);
            Fx.burst(level, ParticleTypes.EXPLOSION, c.add(0, 1, 0), 3, 1.0, 0);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2F, 0.8F);
            Fx.impactNear(level, c, 14, 0, 0xFFB060, "");
            return true;
        });
        return true;
    }
}
