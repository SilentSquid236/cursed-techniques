package com.ethanpierce.cursedtech.core;

import com.ethanpierce.cursedtech.CursedTech;
import com.ethanpierce.cursedtech.domain.DomainManager;
import com.ethanpierce.cursedtech.network.ModNetwork;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CursedTech.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    /** Players whose current swing rolled a Black Flash (uuid -> game tick). */
    private static final Map<UUID, Long> PENDING_FLASH = new HashMap<>();

    // ------------------------------------------------------------ lifecycle

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CursedData d = p.getData(ModRegistries.CURSED);
        if (!d.assigned) {
            Technique[] pool = Technique.playable();
            d.technique = pool[p.getRandom().nextInt(pool.length)];
            d.assigned = true;
            d.energy = maxEnergy();
            p.sendSystemMessage(Component.literal("Your innate technique has awakened: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(d.technique.displayName)
                            .withStyle(Style.EMPTY.withColor(d.technique.color).withBold(true))));
            p.sendSystemMessage(Component.literal("Z / X: abilities   V: Domain Expansion   (rebind in Controls)")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        DomainManager.endFor(e.getEntity().getUUID());
        PENDING_FLASH.remove(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) DomainManager.endFor(p.getUUID());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        DomainManager.endAll();
        Tasks.clear();
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        Tasks.tickAll();
        DomainManager.tick();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CursedData d = p.getData(ModRegistries.CURSED);
        float max = maxEnergy();
        if (d.energy < max) d.energy = Math.min(max, d.energy + ServerConfig.REGEN_PER_SECOND.get().floatValue() / 20f);
        if (d.energy > max) d.energy = max;
        if (p.tickCount % 4 == 0) sync(p, d);
    }

    public static void sync(ServerPlayer p, CursedData d) {
        long now = p.serverLevel().getGameTime();
        int[] left = new int[3];
        for (int i = 0; i < 3; i++) left[i] = (int) Math.max(0, d.cooldownUntil[i] - now);
        PacketDistributor.sendToPlayer(p, new ModNetwork.SyncPayload(d.technique.id, d.energy, maxEnergy(), left,
                Arrays.copyOf(d.cooldownMax, 3), (int) Math.max(0, d.burnoutUntil - now), d.zoneUntil > now));
    }

    private static float maxEnergy() {
        return ServerConfig.MAX_ENERGY.get().floatValue();
    }

    // ---------------------------------------------------------- Black Flash

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof LivingEntity)) return;
        CursedData d = p.getData(ModRegistries.CURSED);
        if (d.energy < 10 || p.getAttackStrengthScale(0.5F) < 0.95F) return;
        long now = p.serverLevel().getGameTime();
        double chance = ServerConfig.BLACK_FLASH_CHANCE.get();
        if (d.zoneUntil > now) chance *= ServerConfig.BLACK_FLASH_ZONE_MULTIPLIER.get();
        if (p.getRandom().nextDouble() < chance) PENDING_FLASH.put(p.getUUID(), now);
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (!(e.getSource().getDirectEntity() instanceof ServerPlayer p) || e.getSource().getEntity() != p) return;
        if (!e.getSource().is(DamageTypes.PLAYER_ATTACK)) return;
        Long tick = PENDING_FLASH.remove(p.getUUID());
        ServerLevel l = p.serverLevel();
        if (tick == null || tick != l.getGameTime()) return;

        LivingEntity target = e.getEntity();
        CursedData d = p.getData(ModRegistries.CURSED);
        d.energy -= 10;
        d.zoneUntil = l.getGameTime() + 200;

        // Big but not one-shot: roughly 1.8x a full swing plus a flat bonus.
        e.setAmount(e.getAmount() * 1.8F + 2.0F);
        Vec3 dir = target.position().subtract(p.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 1e-4) dir = p.getLookAngle();
        Fx.push(target, dir.normalize().scale(1.3).add(0, 0.35, 0));

        Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
        Fx.burst(l, Fx.dust(0x000000, 2.5F), at, 40, 0.4, 0);
        Fx.burst(l, Fx.dust(0xD01020, 1.6F), at, 30, 0.6, 0);
        Fx.burst(l, ParticleTypes.CRIT, at, 30, 0.2, 0.8);
        Fx.burst(l, ParticleTypes.SQUID_INK, at, 15, 0.3, 0.15);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.5F, 0.5F);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 0.8F, 1.8F);
        l.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6F, 1.4F);

        Fx.impactNear(l, at, 24, 1, 0xD01020, "BLACK FLASH");
        Fx.cinematic(p, ModNetwork.CinematicPayload.BLACK_FLASH, p, target, 16, "BLACK FLASH", 0xD01020);
    }

    // ------------------------------------------------------------- commands

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("cursed")
                .then(Commands.literal("info").executes(ServerEvents::info))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("technique", StringArgumentType.word())
                                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(Technique.values()).map(t -> t.id), b))
                                        .executes(ServerEvents::set))))
                .then(Commands.literal("reroll").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players()).executes(ServerEvents::reroll)))
                .then(Commands.literal("refill").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(c -> refill(c, Float.MAX_VALUE))
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0))
                                        .executes(c -> refill(c, FloatArgumentType.getFloat(c, "amount")))))));
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        CursedData d = p.getData(ModRegistries.CURSED);
        Technique t = d.technique;
        c.getSource().sendSuccess(() -> Component.literal(t.displayName)
                .withStyle(Style.EMPTY.withColor(t.color).withBold(true))
                .append(Component.literal(String.format("  %.0f/%.0f CE", d.energy, maxEnergy()))
                        .withStyle(ChatFormatting.GRAY)), false);
        if (t != Technique.NONE) {
            c.getSource().sendSuccess(() -> Component.literal("[1] " + t.abilityName(0) + "   [2] " + t.abilityName(1)
                    + "   [Domain] " + t.domainName()).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        Technique t = Technique.byId(StringArgumentType.getString(c, "technique"));
        for (ServerPlayer p : targets) assign(p, t);
        c.getSource().sendSuccess(() -> Component.literal("Set " + targets.size() + " player(s) to " + t.displayName), true);
        return targets.size();
    }

    private static int reroll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        Technique[] pool = Technique.playable();
        for (ServerPlayer p : targets) assign(p, pool[p.getRandom().nextInt(pool.length)]);
        c.getSource().sendSuccess(() -> Component.literal("Rerolled " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static int refill(CommandContext<CommandSourceStack> c, float amount) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : targets) {
            CursedData d = p.getData(ModRegistries.CURSED);
            d.energy = Math.min(maxEnergy(), amount);
            Arrays.fill(d.cooldownUntil, 0);
            d.burnoutUntil = 0;
        }
        c.getSource().sendSuccess(() -> Component.literal("Refilled " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static void assign(ServerPlayer p, Technique t) {
        DomainManager.endFor(p.getUUID());
        CursedData d = p.getData(ModRegistries.CURSED);
        d.technique = t;
        d.assigned = true;
        Arrays.fill(d.cooldownUntil, 0);
        d.burnoutUntil = 0;
        p.sendSystemMessage(Component.literal("Your technique is now ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(t.displayName).withStyle(Style.EMPTY.withColor(t.color).withBold(true))));
    }
}
