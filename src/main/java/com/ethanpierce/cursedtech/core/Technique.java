package com.ethanpierce.cursedtech.core;

import org.joml.Vector3f;

/**
 * Innate techniques. Names are original; the mechanics are inspired by the
 * attraction/repulsion, slashing and fire users of the genre.
 *
 * Slots: 0 and 1 are abilities, 2 is the Domain Expansion, 3 is the
 * Maximum technique (Special Grade only).
 *
 * UNBOUNDED and CALAMITY are rare innate techniques that only appear at
 * first awakening (Special Grade spawns). They reuse the base kits at higher
 * power and add signature traits.
 */
public enum Technique {
    NONE("none", "No Technique", 0xAAAAAA, false, null,
            new String[]{"-", "-", "-", "-"}, new float[]{0, 0, 0, 0}, new int[]{0, 0, 0, 0}),
    VORTEX("vortex", "Vortex Manipulation", 0x6FA8FF, false, null,
            new String[]{"Collapse Point", "Repulsion Burst", "Event Horizon", "Maximum: Gravity Well"},
            new float[]{15, 30, 0, 60}, new int[]{60, 160, 0, 1200}),
    BLADE("blade", "Edge Weaving", 0xE04848, false, null,
            new String[]{"Cleave", "Dismantle Storm", "Sanctum of a Thousand Edges", "Maximum: World Rend"},
            new float[]{15, 30, 0, 60}, new int[]{50, 160, 0, 1200}),
    FLAME("flame", "Ember Calling", 0xFF8A2A, false, null,
            new String[]{"Ember Shot", "Eruption", "Coffin of Embers", "Maximum: Meteor"},
            new float[]{12, 30, 0, 60}, new int[]{40, 160, 0, 1200}),
    UNBOUNDED("unbounded", "Unbounded", 0xA98BFF, true, "vortex",
            new String[]{"Collapse Point", "Repulsion Burst", "Unbounded Void", "Hollow Annihilation"},
            new float[]{15, 30, 0, 70}, new int[]{50, 140, 0, 1200}),
    CALAMITY("calamity", "Calamity Sovereign", 0xC81E3C, true, "blade",
            new String[]{"Cleave", "Dismantle Storm", "Shrine of Calamity", "Divine Flame"},
            new float[]{12, 28, 0, 70}, new int[]{40, 140, 0, 1200});

    public final String id;
    public final String displayName;
    public final int color;
    /** Rare innate technique, only rolled for Special Grade spawns. */
    public final boolean special;
    /** Base kit this technique's abilities and domain style come from. */
    private final String baseId;
    private final String[] abilityNames;
    private final float[] costs;
    private final int[] cooldowns;

    Technique(String id, String displayName, int color, boolean special, String baseId,
              String[] abilityNames, float[] costs, int[] cooldowns) {
        this.id = id;
        this.displayName = displayName;
        this.color = color;
        this.special = special;
        this.baseId = baseId;
        this.abilityNames = abilityNames;
        this.costs = costs;
        this.cooldowns = cooldowns;
    }

    /** VORTEX/BLADE/FLAME: the kit whose abilities this technique uses. */
    public Technique base() {
        return baseId == null ? this : byId(baseId);
    }

    /** Ability power relative to the base kit. */
    public float power() {
        return special ? 1.3f : 1.0f;
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

    /** Common techniques anyone can awaken with. */
    public static Technique[] playable() {
        return new Technique[]{VORTEX, BLADE, FLAME};
    }

    /** Rare innate techniques. */
    public static Technique[] rare() {
        return new Technique[]{UNBOUNDED, CALAMITY};
    }
}
