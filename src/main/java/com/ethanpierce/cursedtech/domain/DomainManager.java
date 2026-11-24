package com.ethanpierce.cursedtech.domain;

import com.ethanpierce.cursedtech.core.CursedData;
import com.ethanpierce.cursedtech.core.Fx;
import com.ethanpierce.cursedtech.core.ModRegistries;
import com.ethanpierce.cursedtech.core.Progression;
import com.ethanpierce.cursedtech.core.ServerConfig;
import com.ethanpierce.cursedtech.core.Technique;
import com.ethanpierce.cursedtech.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain Expansion: a sphere around the caster with a sure-hit effect.
 *
 * Timeline: 0-30 ticks the barrier builds from the ground up while viewers get
 * an orbiting camera shot, then the domain closes and the sure-hit runs for
 * the configured duration. The domain collapses early if the caster dies,
 * leaves, or loses a domain clash. Afterwards the caster's technique burns out.
 */
public final class DomainManager {
    private DomainManager() {}

    static final int BUILD_TICKS = 30;
    static final int CINEMATIC_TICKS = 50;
    private static int nextId = 1;

    static final class Domain {
        final int id = nextId++;
        final UUID caster;
        final ServerLevel level;
        final Vec3 center;
        final Technique tech;
        final int radius;
        final int lifetime;
        final List<BlockPos> shell;
        final List<BlockPos> placed = new ArrayList<>();
        /** Open domains (Calamity) have no barrier: bigger, but targets can run. */
        final boolean open;
        int age;
        boolean caught;

        Domain(ServerPlayer p, Technique tech, int radius, int lifetime) {
            this.caster = p.getUUID();
            this.level = p.serverLevel();
            this.center = p.position();
            this.tech = tech;
            this.open = tech == Technique.CALAMITY;
            this.radius = radius;
            this.lifetime = lifetime;
            this.shell = open ? List.of() : buildShell();
        }

        private List<BlockPos> buildShell() {
            List<BlockPos> out = new ArrayList<>();
            BlockPos c = BlockPos.containing(center);
            for (int x = -radius - 1; x <= radius + 1; x++)
                for (int y = -radius - 1; y <= radius + 1; y++)
                    for (int z = -radius - 1; z <= radius + 1; z++) {
                        double d = Math.sqrt(x * x + y * y + z * z);
                        if (d >= radius - 0.5 && d < radius + 0.5) out.add(c.offset(x, y, z));
                    }
            out.sort(Comparator.comparingInt(BlockPos::getY));
            return out;
        }

        boolean contains(Vec3 pos) {
            return pos.distanceTo(center) < radius - 0.5;
        }

        ServerPlayer casterPlayer() {
            return level.getServer().getPlayerList().getPlayer(caster);
        }
    }

    private static final Map<UUID, Domain> ACTIVE = new HashMap<>();

    /** True if p stands inside a domain cast by someone else (sure-hit pierces Infinity). */
    public static boolean isInEnemyDomain(ServerPlayer p) {
        for (Domain d : ACTIVE.values()) {
            if (!d.caster.equals(p.getUUID()) && d.level == p.serverLevel() && d.age >= BUILD_TICKS
                    && d.contains(p.position())) return true;
        }
        return false;
    }

    public static boolean isInOwnDomain(ServerPlayer p) {
        Domain d = ACTIVE.get(p.getUUID());
        return d != null && d.contains(p.position());
    }

    public static boolean open(ServerPlayer p, Technique tech) {
        if (ACTIVE.containsKey(p.getUUID())) return false;
        ServerLevel level = p.serverLevel();
        int radius = ServerConfig.DOMAIN_RADIUS.get();
        if (tech == Technique.UNBOUNDED) radius += 2;
        if (tech == Technique.CALAMITY) radius = Math.round(radius * 1.5f);
        int lifetime = BUILD_TICKS + ServerConfig.DOMAIN_DURATION_SECONDS.get() * 20;
        CursedData mine = p.getData(ModRegistries.CURSED);

        // Domain clash: overlapping domains fight; more remaining energy (plus luck) wins.
        for (Domain other : new ArrayList<>(ACTIVE.values())) {
            if (other.level != level || other.center.distanceTo(p.position()) > other.radius + radius) continue;
            ServerPlayer rival = other.casterPlayer();
            float rivalPower = rival == null ? 0 : rival.getData(ModRegistries.CURSED).energy;
            float myPower = mine.energy - tech.cost(2);
            float roll = level.random.nextFloat() * 25F;
            Vec3 mid = other.center.add(p.position()).scale(0.5);
            Fx.impactNear(level, mid, radius * 3, 1, 0xFFFFFF, "DOMAIN CLASH");
            level.playSound(null, mid.x, mid.y, mid.z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5F, 0.6F);
            if (myPower + roll >= rivalPower) {
                broadcast(level, mid, radius * 3, Component.literal(p.getName().getString() + "'s domain overwhelms "
                        + (rival == null ? "the rival" : rival.getName().getString()) + "'s!").withStyle(ChatFormatting.GOLD));
                end(other, true);
            } else {
                broadcast(level, mid, radius * 3, Component.literal(p.getName().getString()
                        + "'s domain shatters against the barrier!").withStyle(ChatFormatting.GOLD));
                burnout(p);
                return true; // energy is still spent
            }
        }

        Domain d = new Domain(p, tech, radius, lifetime);
        ACTIVE.put(p.getUUID(), d);
        com.ethanpierce.cursedtech.core.Advancements.award(p, "domain");

        int color = tech.color;
        Component title = Component.literal("Domain Expansion: ").withStyle(Style.EMPTY.withColor(color))
                .append(Component.literal(tech.domainName()).withStyle(Style.EMPTY.withColor(color).withBold(true)));
        for (ServerPlayer viewer : level.players()) {
            double dist = viewer.position().distanceTo(d.center);
            if (dist <= radius * 4) {
                PacketDistributor.sendToPlayer(viewer, new ModNetwork.DomainFxPayload(d.id, true,
                        d.center.x, d.center.y, d.center.z, radius, color));
            }
            if (dist <= radius + 6) {
                viewer.sendSystemMessage(Component.literal(p.getName().getString() + ": ").append(title));
                Fx.cinematic(viewer, ModNetwork.CinematicPayload.DOMAIN_ORBIT, p, null, CINEMATIC_TICKS,
                        tech.domainName(), color);
            }
        }
        level.playSound(null, d.center.x, d.center.y, d.center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.2F, 0.7F);
        level.playSound(null, d.center.x, d.center.y, d.center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5F, 0.5F);
        return true;
    }

    public static void tick() {
        Iterator<Domain> it = ACTIVE.values().iterator();
        List<Domain> ended = new ArrayList<>();
        while (it.hasNext()) {
            Domain d = it.next();
            ServerPlayer caster = d.casterPlayer();
            boolean casterGone = caster == null || !caster.isAlive() || caster.serverLevel() != d.level
                    || caster.position().distanceTo(d.center) > d.radius + 1.5;
            if (casterGone || d.age >= d.lifetime) {
                ended.add(d);
                continue;
            }
            tickDomain(d, caster);
            d.age++;
        }
        for (Domain d : ended) end(d, false);
    }

    private static void tickDomain(Domain d, ServerPlayer caster) {
        ServerLevel l = d.level;

        // Build the barrier from the ground up.
        if (d.age < BUILD_TICKS && !d.open && ServerConfig.DOMAIN_BARRIER.get()) {
            int per = (int) Math.ceil(d.shell.size() / (double) BUILD_TICKS);
            int from = d.age * per;
            for (int i = from; i < Math.min(d.shell.size(), from + per); i++) {
                BlockPos pos = d.shell.get(i);
                if (l.isOutsideBuildHeight(pos) || !l.isLoaded(pos)) continue;
                if (l.getBlockState(pos).isAir()) {
                    l.setBlock(pos, ModRegistries.DOMAIN_BARRIER.get().defaultBlockState(), 2);
                    d.placed.add(pos);
                }
            }
        }
        if (d.age < BUILD_TICKS) {
            Fx.sphere(l, Fx.dust(d.tech.dust(), 2.0F), d.center, d.radius * (d.age / (double) BUILD_TICKS), 40);
            return;
        }
        if (d.age == BUILD_TICKS) {
            Fx.impactNear(l, d.center, d.radius + 2, 0, d.tech.color, "");
            l.playSound(null, d.center.x, d.center.y, d.center.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.PLAYERS, 1.5F, 0.5F);
        }

        // Ambience.
        for (int i = 0; i < 6; i++) {
            Vec3 r = new Vec3(l.random.nextGaussian(), l.random.nextGaussian(), l.random.nextGaussian())
                    .normalize().scale(l.random.nextDouble() * d.radius);
            Vec3 pt = d.center.add(r);
            l.sendParticles(Fx.dust(d.tech.dust(), 1.5F), pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }

        int sinceClose = d.age - BUILD_TICKS;
        List<LivingEntity> inside = Fx.around(caster, d.center, d.radius - 0.5);
        if (!d.caught && !inside.isEmpty()) {
            d.caught = true;
            Progression.addXp(caster, caster.getData(ModRegistries.CURSED), 20);
        }

        switch (d.tech.base()) {
            case VORTEX -> {
                // Event Horizon: everything inside is caught by infinite pull. Frozen briefly, then crawling.
                // Unbounded Void holds them twice as long.
                int freeze = d.tech == Technique.UNBOUNDED ? 100 : 60;
                for (LivingEntity e : inside) {
                    boolean frozen = sinceClose < freeze;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, frozen ? 6 : 2, false, false));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0, false, false));
                    e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30, 2, false, false));
                    if (frozen) {
                        e.setDeltaMovement(0, Math.min(0, e.getDeltaMovement().y), 0);
                        e.hurtMarked = true;
                        if (e instanceof Mob m) m.getNavigation().stop();
                    }
                    if (sinceClose % 40 == 0) Fx.hit(caster, e, 4.0F);
                    if (l.random.nextInt(4) == 0) {
                        Fx.burst(l, ParticleTypes.END_ROD, e.position().add(0, 1, 0), 1, 0.5, 0.02);
                    }
                }
                if (sinceClose % 10 == 0) Fx.sphere(l, ParticleTypes.REVERSE_PORTAL, d.center, d.radius - 1, 30);
            }
            case BLADE -> {
                // Sanctum of a Thousand Edges: everything inside is cut once a second.
                if (sinceClose % 20 == 0) {
                    for (LivingEntity e : inside) {
                        Fx.hit(caster, e, 3.0F);
                        Fx.burst(l, ParticleTypes.SWEEP_ATTACK, e.position().add(0, 1, 0), 4, 0.6, 0);
                        Fx.burst(l, Fx.dust(d.tech.dust(), 1.0F), e.position().add(0, 1, 0), 10, 0.4, 0);
                    }
                    l.playSound(null, d.center.x, d.center.y, d.center.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                            SoundSource.PLAYERS, 1.5F, 0.6F + l.random.nextFloat() * 0.6F);
                }
                for (int i = 0; i < 4; i++) {
                    Vec3 pt = d.center.add((l.random.nextDouble() - 0.5) * d.radius * 1.6,
                            l.random.nextDouble() * d.radius * 0.7, (l.random.nextDouble() - 0.5) * d.radius * 1.6);
                    l.sendParticles(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                }
            }
            case FLAME -> {
                // Coffin of Embers: a furnace. Constant burning; the caster is immune.
                caster.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false));
                if (sinceClose % 20 == 0) {
                    for (LivingEntity e : inside) {
                        Fx.hit(caster, e, 2.0F);
                        e.igniteForSeconds(3);
                    }
                }
                for (int i = 0; i < 10; i++) {
                    Vec3 pt = d.center.add((l.random.nextDouble() - 0.5) * d.radius * 1.6, 0.1,
                            (l.random.nextDouble() - 0.5) * d.radius * 1.6);
                    l.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 1, 0.1, 0.4, 0.1, 0.03);
                }
                if (l.random.nextInt(3) == 0) Fx.sphere(l, ParticleTypes.LAVA, d.center, d.radius * 0.6, 2);
            }
            default -> {}
        }
    }

    private static void end(Domain d, boolean shattered) {
        ACTIVE.remove(d.caster);
        for (BlockPos pos : d.placed) {
            if (d.level.isLoaded(pos) && d.level.getBlockState(pos).is(ModRegistries.DOMAIN_BARRIER.get())) {
                d.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        Fx.sphere(d.level, ParticleTypes.LARGE_SMOKE, d.center, d.radius, 60);
        d.level.playSound(null, d.center.x, d.center.y, d.center.z,
                shattered ? SoundEvents.GLASS_BREAK : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.5F, 0.6F);
        for (ServerPlayer viewer : d.level.players()) {
            if (viewer.position().distanceTo(d.center) <= d.radius * 4) {
                PacketDistributor.sendToPlayer(viewer, new ModNetwork.DomainFxPayload(d.id, false,
                        d.center.x, d.center.y, d.center.z, d.radius, d.tech.color));
            }
        }
        ServerPlayer caster = d.casterPlayer();
        if (caster != null) burnout(caster);
    }

    private static void burnout(ServerPlayer p) {
        CursedData data = p.getData(ModRegistries.CURSED);
        data.burnoutUntil = p.serverLevel().getGameTime() + ServerConfig.BURNOUT_SECONDS.get() * 20L;
    }

    private static void broadcast(ServerLevel l, Vec3 at, double range, Component msg) {
        for (ServerPlayer sp : l.players()) if (sp.position().distanceTo(at) <= range) sp.sendSystemMessage(msg);
    }

    public static void endFor(UUID caster) {
        Domain d = ACTIVE.get(caster);
        if (d != null) end(d, false);
    }

    public static void endAll() {
        for (Domain d : new ArrayList<>(ACTIVE.values())) end(d, false);
    }
}
