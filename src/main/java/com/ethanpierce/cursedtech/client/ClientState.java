package com.ethanpierce.cursedtech.client;

import com.ethanpierce.cursedtech.core.Grade;
import com.ethanpierce.cursedtech.core.Technique;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Everything the client knows about its own cursed energy and nearby domains. */
public final class ClientState {
    private ClientState() {}

    public static long ticks;

    public static Technique technique = Technique.NONE;
    public static Grade grade = Grade.GRADE_2;
    public static long xp, xpFloor, xpNext;
    public static float energy, maxEnergy = 100;
    public static int[] cdLeft = new int[4];
    public static int[] cdMax = new int[4];
    public static int burnoutLeft;
    public static boolean inZone;
    /** Client tick the last sync arrived, to count cooldowns down smoothly between syncs. */
    public static long syncTick;

    public record Domain(Vec3 center, float radius, int color) {
        public boolean contains(Vec3 p) { return p.distanceTo(center) < radius - 0.3; }
    }

    public static final Map<Integer, Domain> DOMAINS = new HashMap<>();

    public static void reset() {
        technique = Technique.NONE;
        energy = 0;
        cdLeft = new int[4];
        cdMax = new int[4];
        grade = Grade.GRADE_2;
        burnoutLeft = 0;
        DOMAINS.clear();
    }

    public static int cooldownLeft(int slot) {
        return (int) Math.max(0, cdLeft[slot] - (ticks - syncTick));
    }
}
