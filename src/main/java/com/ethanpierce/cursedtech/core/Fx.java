package com.ethanpierce.cursedtech.core;

import com.ethanpierce.cursedtech.network.ModNetwork;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.List;

/** Shared helpers for damage, targeting, particles and client effects. */
public final class Fx {
    private Fx() {}

    // ----------------------------------------------------------- damage

    public static DamageSource source(ServerPlayer p) {
        return new DamageSource(p.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ModRegistries.TECHNIQUE_DAMAGE), p);
    }

    /** Technique damage, scaled by config, the caster's grade and their technique's power. */
    public static boolean hit(ServerPlayer p, LivingEntity target, float amount) {
        CursedData d = p.getData(ModRegistries.CURSED);
        target.invulnerableTime = 0;
        float scaled = amount * ServerConfig.DAMAGE_MULTIPLIER.get().floatValue() * d.grade.damageMult * d.technique.power();
        return target.hurt(source(p), scaled);
    }

    public static void push(Entity e, Vec3 v) {
        e.setDeltaMovement(e.getDeltaMovement().add(v));
        e.hurtMarked = true;
    }

    // -------------------------------------------------------- targeting

    public static boolean validTarget(ServerPlayer caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || !e.isAlive() || e.isSpectator()) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof net.minecraft.world.entity.TamableAnimal t && t.isOwnedBy(caster)) return false;
        return !caster.isAlliedTo(e) && !(le instanceof ServerPlayer sp && sp.isCreative());
    }

    public static List<LivingEntity> around(ServerPlayer caster, Vec3 center, double radius) {
        return caster.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> validTarget(caster, e) && e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(center) <= radius);
    }

    /** Aim point: first living entity or block along the look ray, else the max-range point. */
    public static Vec3 aim(ServerPlayer p, double range, boolean feetOfEntity) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        HitResult block = p.pick(range, 1.0F, false);
        Vec3 end = block.getType() == HitResult.Type.MISS ? eye.add(look.scale(range)) : block.getLocation();
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(p, eye, end,
                p.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0), e -> validTarget(p, e),
                eye.distanceToSqr(end));
        if (ent != null) {
            Entity e = ent.getEntity();
            return feetOfEntity ? e.position() : e.position().add(0, e.getBbHeight() / 2, 0);
        }
        return end;
    }

    // -------------------------------------------------------- particles

    public static DustParticleOptions dust(Vector3f rgb, float scale) {
        return new DustParticleOptions(rgb, scale);
    }

    public static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f), scale);
    }

    public static void burst(ServerLevel l, ParticleOptions p, Vec3 at, int count, double spread, double speed) {
        l.sendParticles(p, at.x, at.y, at.z, count, spread, spread, spread, speed);
    }

    public static void ring(ServerLevel l, ParticleOptions p, Vec3 c, double r, int points) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            l.sendParticles(p, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    public static void sphere(ServerLevel l, ParticleOptions p, Vec3 c, double r, int points) {
        for (int i = 0; i < points; i++) {
            double u = l.random.nextDouble() * 2 - 1;
            double a = l.random.nextDouble() * Math.PI * 2;
            double s = Math.sqrt(1 - u * u);
            l.sendParticles(p, c.x + s * Math.cos(a) * r, c.y + u * r, c.z + s * Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------ client cinematics

    public static void impactNear(ServerLevel l, Vec3 at, double range, int strength, int color, String label) {
        for (ServerPlayer sp : l.players()) {
            if (sp.position().distanceTo(at) <= range) {
                PacketDistributor.sendToPlayer(sp, new ModNetwork.ImpactPayload(strength, color, label));
            }
        }
    }

    public static void impact(ServerPlayer sp, int strength, int color, String label) {
        PacketDistributor.sendToPlayer(sp, new ModNetwork.ImpactPayload(strength, color, label));
    }

    public static void cinematic(ServerPlayer viewer, int shot, Entity subject, Entity target, int duration,
                                 String label, int color) {
        PacketDistributor.sendToPlayer(viewer, new ModNetwork.CinematicPayload(shot, subject.getId(),
                target == null ? -1 : target.getId(), duration, label, color));
    }
}
