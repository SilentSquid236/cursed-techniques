package com.ethanpierce.cursedtech.core;

import org.joml.Vector3f;

/**
 * Innate techniques. Names are original; the mechanics are inspired by the
 * attraction/repulsion, slashing and fire users of the genre.
 *
 * Slot 0 and 1 are abilities, slot 2 is the Domain Expansion.
 */
public enum Technique {
    NONE("none", "No Technique", 0xAAAAAA,
            new String[]{"-", "-", "-"}, new float[]{0, 0, 0}, new int[]{0, 0, 0}),
    VORTEX("vortex", "Vortex Manipulation", 0x6FA8FF,
            new String[]{"Collapse Point", "Repulsion Burst", "Event Horizon"},
            new float[]{15, 30, 0}, new int[]{60, 160, 0}),
    BLADE("blade", "Edge Weaving", 0xE04848,
            new String[]{"Cleave", "Dismantle Storm", "Sanctum of a Thousand Edges"},
            new float[]{15, 30, 0}, new int[]{50, 160, 0}),
    FLAME("flame", "Ember Calling", 0xFF8A2A,
            new String[]{"Ember Shot", "Eruption", "Coffin of Embers"},
            new float[]{12, 30, 0}, new int[]{40, 160, 0});

    public final String id;
    public final String displayName;
    public final int color;
    private final String[] abilityNames;
    private final float[] costs;
    private final int[] cooldowns;

    Technique(String id, String displayName, int color, String[] abilityNames, float[] costs, int[] cooldowns) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.abilityNames = abilityNames;
        this.costs = costs;
        this.cooldowns = cooldowns;
    }

    public String abilityName(int slot) { return abilityNames[slot]; }
    public String domainName() { return abilityNames[2]; }

    /** Energy cost; the domain slot reads from the server config. */
    public float cost(int slot) {
        return slot == 2 ? ServerConfig.DOMAIN_COST.get().floatValue() : costs[slot];
    }

    /** Cooldown in ticks; the domain slot reads from the server config. */
    public int cooldown(int slot) {
        return slot == 2 ? ServerConfig.DOMAIN_COOLDOWN_SECONDS.get() * 20 : cooldowns[slot];
    }

    public Vector3f dust() {
        return new Vector3f(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f);
    }

    public static Technique byId(String id) {
        for (Technique t : values()) if (t.id.equals(id)) return t;
        return NONE;
    }

    public static Technique[] playable() {
        return new Technique[]{VORTEX, BLADE, FLAME};
    }
}
