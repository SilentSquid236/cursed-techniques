package com.ethanpierce.cursedtech.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Grade progression. XP comes from fights, not grinding farm animals:
 *   hostile kill    ~ max health / 4 (zombie 5, enderman 10)
 *   boss kill       max health x 2 (and counts toward Special Grade)
 *   player kill     40 (once per victim per 10 minutes)
 *   Black Flash     30
 *   domain that catches someone  20
 * Promotions also need Black Flashes (Grade 1, Special) and boss kills (Special).
 */
public final class Progression {
    private Progression() {}

    public static final float BOSS_HEALTH = 150f;
    private static final Map<String, Long> RECENT_PLAYER_KILLS = new HashMap<>();

    // ---------------------------------------------------------- awakening

    /** First join: roll grade and technique. Most people are Grade 2. */
    public static void awaken(ServerPlayer p, CursedData d) {
        double roll = p.getRandom().nextDouble();
        double special = ServerConfig.SPECIAL_SPAWN_CHANCE.get();
        double g1 = ServerConfig.GRADE1_SPAWN_CHANCE.get();
        if (roll < special) {
            Technique[] rare = Technique.rare();
            d.technique = rare[p.getRandom().nextInt(rare.length)];
            d.grade = Grade.SPECIAL;
        } else {
            Technique[] pool = Technique.playable();
            d.technique = pool[p.getRandom().nextInt(pool.length)];
            d.grade = roll < special + g1 ? Grade.GRADE_1 : Grade.GRADE_2;
        }
        d.assigned = true;
        d.energy = d.maxEnergy();

        p.sendSystemMessage(Component.literal("Your innate technique has awakened: ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(d.technique.displayName)
                        .withStyle(Style.EMPTY.withColor(d.technique.color).withBold(true)))
                .append(Component.literal("  [" + d.grade.displayName + "]")
                        .withStyle(Style.EMPTY.withColor(d.grade.color))));
        p.sendSystemMessage(Component.literal(controlsHint(d.grade)).withStyle(ChatFormatting.DARK_GRAY));

        if (d.grade == Grade.SPECIAL) {
            Fx.impact(p, 1, d.technique.color, "SPECIAL GRADE");
            for (ServerPlayer other : p.server.getPlayerList().getPlayers()) {
                if (other != p) {
                    other.sendSystemMessage(Component.literal("A Special Grade sorcerer has awakened somewhere in the world...")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
                }
            }
        }
    }

    public static String controlsHint(Grade g) {
        String s = "Z / X: abilities";
        s += g.atLeast(Grade.GRADE_1) ? "   V: Domain Expansion" : "   (Domain unlocks at Grade 1)";
        if (g == Grade.SPECIAL) s += "   G: Maximum";
        return s + "   /cursed info for progress";
    }

    // ---------------------------------------------------------------- XP

    public static void onKill(ServerPlayer killer, LivingEntity victim) {
        if (killer == victim) return;
        CursedData d = killer.getData(ModRegistries.CURSED);
        if (victim instanceof Player vp) {
            String key = killer.getUUID() + ">" + vp.getUUID();
            long now = killer.serverLevel().getGameTime();
            Long last = RECENT_PLAYER_KILLS.get(key);
            if (last != null && now - last < 20 * 60 * 10) return;
            RECENT_PLAYER_KILLS.put(key, now);
            addXp(killer, d, 40);
            return;
        }
        if (victim instanceof Animal || victim instanceof AbstractVillager) return;
        float hp = victim.getMaxHealth();
        if (hp >= BOSS_HEALTH) {
            d.bossKills++;
            killer.sendSystemMessage(Component.literal("Boss exorcised: " + victim.getName().getString())
                    .withStyle(ChatFormatting.GOLD));
            addXp(killer, d, (long) (hp * 2));
        } else {
            addXp(killer, d, Math.max(1, Math.round(hp / 4f)));
        }
    }

    public static void addXp(ServerPlayer p, CursedData d, long base) {
        if (d.technique == Technique.NONE) return;
        long gain = Math.round(base * ServerConfig.XP_MULTIPLIER.get());
        if (gain <= 0) return;
        d.xp += gain;
        tryPromote(p, d);
    }

    // --------------------------------------------------------- promotion

    public static long xpFor(Grade g) {
        return switch (g) {
            case GRADE_2 -> 0;
            case SEMI_GRADE_1 -> ServerConfig.SEMI_GRADE1_XP.get();
            case GRADE_1 -> ServerConfig.GRADE1_XP.get();
            case SPECIAL -> ServerConfig.SPECIAL_XP.get();
        };
    }

    private static int flashesFor(Grade g) {
        return switch (g) {
            case GRADE_1 -> ServerConfig.GRADE1_BLACK_FLASHES.get();
            case SPECIAL -> ServerConfig.SPECIAL_BLACK_FLASHES.get();
            default -> 0;
        };
    }

    private static int bossesFor(Grade g) {
        return g == Grade.SPECIAL ? ServerConfig.SPECIAL_BOSS_KILLS.get() : 0;
    }

    public static boolean meets(CursedData d, Grade g) {
        return d.xp >= xpFor(g) && d.blackFlashes >= flashesFor(g) && d.bossKills >= bossesFor(g);
    }

    /** Human-readable list of what's left for the next grade. */
    public static String requirements(CursedData d) {
        Grade next = d.grade.next();
        if (next == null) return "You stand at the peak.";
        StringBuilder s = new StringBuilder("Next: " + next.displayName + " -  XP " + d.xp + "/" + xpFor(next));
        if (flashesFor(next) > 0) s.append("  Black Flashes ").append(d.blackFlashes).append("/").append(flashesFor(next));
        if (bossesFor(next) > 0) s.append("  Bosses ").append(d.bossKills).append("/").append(bossesFor(next));
        return s.toString();
    }

    public static void tryPromote(ServerPlayer p, CursedData d) {
        Grade next = d.grade.next();
        while (next != null && meets(d, next)) {
            d.grade = next;
            announce(p, d);
            next = d.grade.next();
        }
    }

    public static void setGrade(ServerPlayer p, CursedData d, Grade g) {
        d.grade = g;
        d.energy = Math.min(d.energy, d.maxEnergy());
    }

    private static void announce(ServerPlayer p, CursedData d) {
        Grade g = d.grade;
        Component msg = Component.literal(p.getName().getString() + " has been promoted to ")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(g.displayName).withStyle(Style.EMPTY.withColor(g.color).withBold(true)));
        for (ServerPlayer sp : p.server.getPlayerList().getPlayers()) sp.sendSystemMessage(msg);

        String unlock = switch (g) {
            case SEMI_GRADE_1 -> "Your reserves of cursed energy deepen.";
            case GRADE_1 -> "You can now use Domain Expansion (V).";
            case SPECIAL -> "Maximum technique (G) and Reverse Cursed Technique unlocked.";
            default -> "";
        };
        if (!unlock.isEmpty()) p.sendSystemMessage(Component.literal(unlock).withStyle(Style.EMPTY.withColor(g.color)));
        d.energy = d.maxEnergy();
        Fx.impact(p, g == Grade.SPECIAL ? 1 : 0, g.color, g.displayName.toUpperCase());
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    /** Sent with the HUD sync: XP needed for the next grade (0 at the top). */
    public static long nextXp(CursedData d) {
        Grade next = d.grade.next();
        return next == null ? 0 : xpFor(next);
    }
}
