package com.ethanpierce.cursedtech.entity;

import com.ethanpierce.cursedtech.core.Advancements;
import com.ethanpierce.cursedtech.core.Fx;
import com.ethanpierce.cursedtech.core.ModRegistries;
import com.ethanpierce.cursedtech.core.ServerConfig;
import com.ethanpierce.cursedtech.core.Tasks;
import com.ethanpierce.cursedtech.domain.DomainManager;
import com.ethanpierce.cursedtech.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The Calamity: the sorcerer sealed beneath the world a thousand years ago, and the
 * final boss of Cursed Arts. Summoned at a Sealing Altar.
 *
 * Phase 1 (100-50%): cleaves, ember volleys, collapse points and blinks.
 * Phase 2 (50-20%): opens his own domain. Players standing in their own domain
 *   are shielded from its sure-hit. Afterwards he burns out and takes extra damage.
 * Phase 3 (under 20%): heals once with Reverse Cursed Technique and fights faster.
 *
 * Every special attack is telegraphed, and none of them break or place blocks.
 */
public class CalamityEntity extends Monster {
    public static final int COLOR = 0x9B30FF;
    public static final int DOMAIN_RADIUS = 12;
    private static final int DOMAIN_BUILD = 30;
    private static final int DOMAIN_LENGTH = 200;
    private static final int BURNOUT = 120;

    /** Calamities whose domain is currently closed (for Infinity's sure-hit rule). */
    private static final Set<CalamityEntity> DOMAINS = Collections.newSetFromMap(new WeakHashMap<>());

    private final ServerBossEvent bossBar = (ServerBossEvent) new ServerBossEvent(
            Component.literal("The Calamity").withStyle(Style.EMPTY.withColor(COLOR)),
            BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10).setDarkenScreen(true);

    private int intro = 60;
    private int attackCooldown = 50;
    private int domainCooldown = 0;
    private int domainAge = -1;
    private Vec3 domainCenter = Vec3.ZERO;
    private int burnout = 0;
    private boolean phase2, phase3;

    public CalamityEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 600.0)
                .add(Attributes.ARMOR, 14.0)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0)
                .add(Attributes.ATTACK_DAMAGE, 12.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.9)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** True if p is inside a Calamity domain's sure-hit (pierces Infinity). */
    public static boolean inSureHit(ServerPlayer p) {
        for (CalamityEntity c : DOMAINS) {
            if (c.isAlive() && c.level() == p.level() && c.domainAge >= DOMAIN_BUILD
                    && p.position().distanceTo(c.domainCenter) < DOMAIN_RADIUS) return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- basics

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (intro > 0 && !source.is(DamageTypes.GENERIC_KILL)) return false;
        if (source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN) || source.is(DamageTypes.CRAMMING)) return false;
        if (burnout > 0) amount *= 1.25F;
        // No single hit takes off more than 8% of his health: no one-shot cheese.
        amount = Math.min(amount, getMaxHealth() * 0.08F);
        return super.hurt(source, amount);
    }

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        bossBar.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        bossBar.removePlayer(p);
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        if (name != null) bossBar.setName(name);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Intro", intro);
        tag.putBoolean("Phase2", phase2);
        tag.putBoolean("Phase3", phase3);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        intro = tag.getInt("Intro");
        phase2 = tag.getBoolean("Phase2");
        phase3 = tag.getBoolean("Phase3");
        if (hasCustomName()) bossBar.setName(getDisplayName());
    }

    @Override
    public void die(DamageSource source) {
        if (level() instanceof ServerLevel l && !isRemoved()) {
            endDomain(l);
            say(l, "So this is the age that finally seals me. ...Remember my name, sorcerer.");
            for (ServerPlayer p : l.players()) {
                if (p.distanceTo(this) <= 64) {
                    Advancements.award(p, "calamity");
                    Fx.impact(p, 2, COLOR, "");
                    Fx.cinematic(p, ModNetwork.CinematicPayload.FINISHER, this, null, 40, "SEALED", COLOR);
                }
            }
            l.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2.0F, 0.6F);
            Fx.sphere(l, Fx.dust(COLOR, 2.5F), position().add(0, 1, 0), 3, 200);
        }
        super.die(source);
    }

    @Override
    public void remove(RemovalReason reason) {
        DOMAINS.remove(this);
        super.remove(reason);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        boolean ok = super.doHurtTarget(target);
        if (ok && target instanceof LivingEntity le && !le.isAlive() && level() instanceof ServerLevel l
                && target instanceof Player) {
            say(l, "Next.");
        }
        return ok;
    }

    // ------------------------------------------------------------------- AI

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel l = (ServerLevel) level();
        bossBar.setProgress(getHealth() / getMaxHealth());

        if (intro > 0) {
            if (intro == 60) introduce(l);
            intro--;
            getNavigation().stop();
            Fx.sphere(l, Fx.dust(COLOR, 1.6F), position().add(0, 1.1, 0), 1.6, 8);
            return;
        }
        if (domainCooldown > 0) domainCooldown--;
        if (burnout > 0) {
            burnout--;
            if (tickCount % 5 == 0) Fx.burst(l, ParticleTypes.LARGE_SMOKE, position().add(0, 2, 0), 2, 0.3, 0.01);
        }
        tickDomain(l);

        float frac = getHealth() / getMaxHealth();
        if (!phase2 && frac <= 0.5F) {
            phase2 = true;
            say(l, "Enough playing. Let me show you what a real domain looks like.");
            domainCooldown = 0;
            attackCooldown = 20;
        }
        if (!phase3 && frac <= 0.2F) {
            phase3 = true;
            say(l, "You'd seal me again? Then bleed for it.");
            heal(getMaxHealth() * 0.1F);
            Fx.burst(l, Fx.dust(0xE8FFF0, 1.5F), position().add(0, 1, 0), 60, 0.8, 0);
            l.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.HOSTILE, 2.0F, 0.5F);
            addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 0, false, false));
            addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 0, false, false));
        }

        LivingEntity t = getTarget();
        if (t == null || !t.isAlive() || burnout > 0 || domainAge >= 0) return;
        if (--attackCooldown > 0) return;

        int phase = phase3 ? 3 : phase2 ? 2 : 1;
        double dist = distanceTo(t);
        if (phase >= 2 && domainCooldown <= 0 && dist < DOMAIN_RADIUS) {
            openDomain(l);
            attackCooldown = 40;
            return;
        }
        int roll = random.nextInt(phase == 3 ? 5 : 4);
        if (dist > 12 && random.nextBoolean()) roll = 3;
        switch (roll) {
            case 0 -> cleave(l, t);
            case 1 -> emberVolley(l, t);
            case 2 -> collapsePoint(l, t);
            case 3 -> blink(l, t);
            default -> dismantleStorm(l);
        }
        attackCooldown = switch (phase) {
            case 1 -> 70;
            case 2 -> 55;
            default -> 40;
        };
    }

    private void introduce(ServerLevel l) {
        l.playSound(null, getX(), getY(), getZ(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 2.0F, 0.7F);
        for (ServerPlayer p : l.players()) {
            if (p.distanceTo(this) <= 48) {
                Fx.cinematic(p, ModNetwork.CinematicPayload.DOMAIN_ORBIT, this, null, 60, "The Calamity", COLOR);
                Fx.impact(p, 1, COLOR, "");
            }
        }
        say(l, "A thousand years under stone, and the first thing I see is another sorcerer. Good. I'm hungry.");
    }

    private void say(ServerLevel l, String line) {
        Component msg = Component.literal("<").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal("The Calamity").withStyle(Style.EMPTY.withColor(COLOR)))
                .append(Component.literal("> ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.literal(line).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        for (ServerPlayer p : l.players()) if (p.distanceTo(this) <= 64) p.sendSystemMessage(msg);
    }

    // -------------------------------------------------------------- damage

    private boolean strike(LivingEntity e, float amount) {
        DamageSource src = new DamageSource(level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(ModRegistries.TECHNIQUE_DAMAGE), this);
        e.invulnerableTime = 0;
        return e.hurt(src, amount * ServerConfig.CALAMITY_DAMAGE.get().floatValue());
    }

    private boolean valid(LivingEntity e) {
        if (e == this || !e.isAlive() || e.isSpectator() || e instanceof Enemy) return false;
        return !(e instanceof Player p && (p.isCreative() || p.isSpectator()));
    }

    private List<LivingEntity> around(ServerLevel l, Vec3 c, double r) {
        return l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> valid(e) && e.position().add(0, e.getBbHeight() / 2, 0).distanceTo(c) <= r);
    }

    // ------------------------------------------------------------- attacks

    /** A telegraphed line cut: a thin purple line shows where it will land, then it slices. */
    private void cleave(ServerLevel l, LivingEntity t) {
        swing(InteractionHand.MAIN_HAND);
        Vec3 from = getEyePosition();
        Vec3 dir = t.getEyePosition().subtract(from).normalize();
        double len = 20;
        HitResult wall = l.clip(new ClipContext(from, from.add(dir.scale(len)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        if (wall.getType() != HitResult.Type.MISS) len = wall.getLocation().distanceTo(from);
        final double length = len;
        l.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5F, 0.6F);
        Tasks.start(l, (level, age) -> {
            if (!isAlive()) return true;
            if (age < 14) {
                for (double s = 1; s <= length; s += 0.8) {
                    Vec3 pt = from.add(dir.scale(s));
                    level.sendParticles(Fx.dust(COLOR, 0.6F), pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
                }
                return false;
            }
            for (double s = 1; s <= length; s += 0.5) {
                Vec3 pt = from.add(dir.scale(s));
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(pt, pt).inflate(0.9), this::valid)) {
                    if (strike(e, 14.0F)) Fx.burst(level, Fx.dust(0xFFFFFF, 1.2F), e.position().add(0, 1, 0), 12, 0.3, 0);
                }
                if (((int) (s * 2)) % 2 == 0) level.sendParticles(ParticleTypes.SWEEP_ATTACK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
            level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.5F, 0.5F);
            return true;
        });
    }

    /** Three fire bolts, each aimed at where the target is when it fires. */
    private void emberVolley(ServerLevel l, LivingEntity t) {
        swing(InteractionHand.MAIN_HAND);
        Tasks.start(l, (level, age) -> {
            if (!isAlive() || !t.isAlive()) return true;
            if (age % 8 == 0) bolt(level, t.getEyePosition().subtract(0, 0.4, 0));
            return age >= 16;
        });
    }

    private void bolt(ServerLevel l, Vec3 at) {
        final Vec3[] pos = {getEyePosition()};
        final Vec3 vel = at.subtract(pos[0]).normalize().scale(1.1);
        l.playSound(null, getX(), getY(), getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 0.8F);
        Tasks.start(l, (level, age) -> {
            for (int sub = 0; sub < 2; sub++) {
                Vec3 next = pos[0].add(vel.scale(0.5));
                HitResult wall = level.clip(new ClipContext(pos[0], next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                List<LivingEntity> hits = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos[0], next).inflate(0.5), this::valid);
                if (!hits.isEmpty()) {
                    LivingEntity e = hits.get(0);
                    if (strike(e, 8.0F)) e.igniteForSeconds(4);
                    Fx.burst(level, ParticleTypes.FLAME, next, 20, 0.2, 0.08);
                    level.playSound(null, next.x, next.y, next.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 1.0F, 0.8F);
                    return true;
                }
                if (wall.getType() != HitResult.Type.MISS) {
                    Fx.burst(level, ParticleTypes.FLAME, wall.getLocation(), 12, 0.15, 0.05);
                    return true;
                }
                pos[0] = next;
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, next.x, next.y, next.z, 3, 0.05, 0.05, 0.05, 0.01);
                level.sendParticles(Fx.dust(COLOR, 1.4F), next.x, next.y, next.z, 1, 0, 0, 0, 0);
            }
            return age >= 40;
        });
    }

    /** A pull point under the target: it drags you in for a second, then implodes. Run! */
    private void collapsePoint(ServerLevel l, LivingEntity t) {
        swing(InteractionHand.MAIN_HAND);
        Vec3 point = t.position().add(0, 1, 0);
        l.playSound(null, point.x, point.y, point.z, SoundEvents.PORTAL_TRIGGER, SoundSource.HOSTILE, 0.8F, 1.6F);
        Tasks.start(l, (level, age) -> {
            if (!isAlive()) return true;
            Fx.sphere(level, ParticleTypes.REVERSE_PORTAL, point, 1.5 - age * 0.04, 12);
            Fx.ring(level, Fx.dust(COLOR, 1.0F), point.subtract(0, 0.9, 0), 4.0, 24);
            for (LivingEntity e : around(level, point, 7)) {
                Vec3 to = point.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                Fx.push(e, to.normalize().scale(Math.min(0.22, 0.7 / Math.max(0.6, to.length()))));
            }
            if (age >= 26) {
                for (LivingEntity e : around(level, point, 4.0)) strike(e, 14.0F);
                Fx.burst(level, ParticleTypes.EXPLOSION, point, 3, 0.4, 0);
                Fx.burst(level, ParticleTypes.END_ROD, point, 30, 0.1, 0.35);
                level.playSound(null, point.x, point.y, point.z, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 1.0F, 1.4F);
                Fx.impactNear(level, point, 10, 0, COLOR, "");
                return true;
            }
            return false;
        });
    }

    /** Vanishes in smoke and reappears behind the target. */
    private void blink(ServerLevel l, LivingEntity t) {
        Vec3 back = t.getLookAngle().multiply(1, 0, 1);
        if (back.lengthSqr() < 1e-4) back = new Vec3(1, 0, 0);
        Vec3 dest = t.position().subtract(back.normalize().scale(2.5));
        Fx.burst(l, ParticleTypes.LARGE_SMOKE, position().add(0, 1, 0), 30, 0.4, 0.02);
        if (randomTeleport(dest.x, dest.y, dest.z, true)) {
            l.playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0F, 0.6F);
            Fx.burst(l, Fx.dust(COLOR, 1.5F), position().add(0, 1, 0), 30, 0.4, 0);
            lookAt(t, 180, 180);
            doHurtTarget(t);
            swing(InteractionHand.MAIN_HAND);
        }
    }

    /** Phase 3: three rings of slashes around him. Back off. */
    private void dismantleStorm(ServerLevel l) {
        l.playSound(null, getX(), getY(), getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.HOSTILE, 1.5F, 0.4F);
        Tasks.start(l, (level, age) -> {
            if (!isAlive()) return true;
            Vec3 c = position().add(0, 1, 0);
            if (age < 12) {
                Fx.ring(level, Fx.dust(COLOR, 0.8F), c.subtract(0, 0.9, 0), 6.0, 30);
                return false;
            }
            if ((age - 12) % 6 == 0) {
                for (LivingEntity e : around(level, c, 6.5)) strike(e, 6.0F);
                for (int i = 0; i < 20; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2, r = 1 + level.random.nextDouble() * 5.5;
                    level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x + Math.cos(a) * r, c.y + level.random.nextDouble() - 0.5,
                            c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                level.playSound(null, getX(), getY(), getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.HOSTILE, 1.2F,
                        0.7F + level.random.nextFloat() * 0.5F);
            }
            return age >= 24;
        });
    }

    // -------------------------------------------------------------- domain

    private void openDomain(ServerLevel l) {
        domainAge = 0;
        domainCenter = position();
        DOMAINS.add(this);
        say(l, "Domain Expansion.");
        Component title = Component.literal("Domain Expansion: ").withStyle(Style.EMPTY.withColor(COLOR))
                .append(Component.literal("Abyssal Throne").withStyle(Style.EMPTY.withColor(COLOR).withBold(true)));
        for (ServerPlayer p : l.players()) {
            double d = p.position().distanceTo(domainCenter);
            if (d <= DOMAIN_RADIUS * 4) {
                PacketDistributor.sendToPlayer(p, new ModNetwork.DomainFxPayload(-getId(), true,
                        domainCenter.x, domainCenter.y, domainCenter.z, DOMAIN_RADIUS, COLOR));
            }
            if (d <= DOMAIN_RADIUS + 8) {
                p.sendSystemMessage(Component.literal("The Calamity: ").withStyle(ChatFormatting.GRAY).append(title));
                Fx.cinematic(p, ModNetwork.CinematicPayload.DOMAIN_ORBIT, this, null, 50, "Abyssal Throne", COLOR);
            }
        }
        l.playSound(null, getX(), getY(), getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.HOSTILE, 1.5F, 0.5F);
        l.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.4F);
    }

    private void tickDomain(ServerLevel l) {
        if (domainAge < 0) return;
        int age = domainAge++;
        if (age < DOMAIN_BUILD) {
            getNavigation().stop();
            Fx.sphere(l, Fx.dust(COLOR, 2.0F), domainCenter, DOMAIN_RADIUS * (age / (double) DOMAIN_BUILD), 50);
            return;
        }
        if (age == DOMAIN_BUILD) {
            Fx.impactNear(l, domainCenter, DOMAIN_RADIUS + 2, 1, COLOR, "");
            l.playSound(null, domainCenter.x, domainCenter.y, domainCenter.z, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(),
                    SoundSource.HOSTILE, 2.0F, 0.4F);
        }
        Fx.sphere(l, Fx.dust(COLOR, 1.8F), domainCenter, DOMAIN_RADIUS, 24);
        for (int i = 0; i < 6; i++) {
            Vec3 pt = domainCenter.add((random.nextDouble() - 0.5) * DOMAIN_RADIUS * 1.6, 0.1,
                    (random.nextDouble() - 0.5) * DOMAIN_RADIUS * 1.6);
            l.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pt.x, pt.y, pt.z, 1, 0.1, 0.4, 0.1, 0.02);
        }
        if ((age - DOMAIN_BUILD) % 20 == 0) {
            for (LivingEntity e : around(l, domainCenter, DOMAIN_RADIUS - 0.5)) {
                if (e instanceof ServerPlayer sp && DomainManager.isInOwnDomain(sp)) {
                    // Your own domain holds his sure-hit back.
                    sp.displayClientMessage(Component.literal("Your domain holds back the sure-hit!")
                            .withStyle(ChatFormatting.GOLD), true);
                    Fx.burst(l, ParticleTypes.ENCHANTED_HIT, sp.position().add(0, 1, 0), 10, 0.4, 0.1);
                    continue;
                }
                if (strike(e, 5.0F)) {
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 50, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false));
                    Fx.burst(l, ParticleTypes.SWEEP_ATTACK, e.position().add(0, 1, 0), 3, 0.5, 0);
                }
            }
            l.playSound(null, domainCenter.x, domainCenter.y, domainCenter.z, SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 1.5F, 0.5F + random.nextFloat() * 0.4F);
        }
        if (age >= DOMAIN_BUILD + DOMAIN_LENGTH || position().distanceTo(domainCenter) > DOMAIN_RADIUS + 2) {
            endDomain(l);
            burnout = BURNOUT;
            domainCooldown = 20 * 30;
            say(l, "...Tch.");
        }
    }

    private void endDomain(ServerLevel l) {
        if (domainAge < 0) return;
        domainAge = -1;
        DOMAINS.remove(this);
        Fx.sphere(l, ParticleTypes.LARGE_SMOKE, domainCenter, DOMAIN_RADIUS, 60);
        l.playSound(null, domainCenter.x, domainCenter.y, domainCenter.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 2.0F, 0.5F);
        for (ServerPlayer p : l.players()) {
            if (p.position().distanceTo(domainCenter) <= DOMAIN_RADIUS * 4) {
                PacketDistributor.sendToPlayer(p, new ModNetwork.DomainFxPayload(-getId(), false,
                        domainCenter.x, domainCenter.y, domainCenter.z, DOMAIN_RADIUS, COLOR));
            }
        }
    }
}
