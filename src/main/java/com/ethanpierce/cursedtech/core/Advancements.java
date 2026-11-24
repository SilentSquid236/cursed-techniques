package com.ethanpierce.cursedtech.core;

import com.ethanpierce.cursedtech.CursedTech;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * Awards the mod's advancements (data/cursedtech/advancement). They use the
 * "impossible" trigger and are granted from code, so quest books and other
 * mods can track grade progress, Black Flashes, domains and Maximums.
 */
public final class Advancements {
    private Advancements() {}

    public static void award(ServerPlayer p, String name) {
        AdvancementHolder h = p.server.getAdvancements().get(CursedTech.id(name));
        if (h != null && !p.getAdvancements().getOrStartProgress(h).isDone()) {
            p.getAdvancements().award(h, "done");
        }
    }

    /** Grants everything the player has already earned (used on login for older saves). */
    public static void catchUp(ServerPlayer p, CursedData d) {
        if (!d.assigned || d.technique == Technique.NONE) return;
        award(p, "awakened");
        if (d.grade.atLeast(Grade.SEMI_GRADE_1)) award(p, "semi_grade_1");
        if (d.grade.atLeast(Grade.GRADE_1)) award(p, "grade_1");
        if (d.grade == Grade.SPECIAL) award(p, "special");
        if (d.blackFlashes > 0) award(p, "black_flash");
        if (d.bossKills > 0) award(p, "boss");
    }

    public static String forGrade(Grade g) {
        return switch (g) {
            case SEMI_GRADE_1 -> "semi_grade_1";
            case GRADE_1 -> "grade_1";
            case SPECIAL -> "special";
            default -> null;
        };
    }
}
