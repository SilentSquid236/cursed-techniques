package com.ethanpierce.cursedtech.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Balance knobs. Lives in world/serverconfig/cursedtech-server.toml. */
public final class ServerConfig {
    private ServerConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MAX_ENERGY = B
            .comment("Cursed energy pool at Grade 2. Higher grades multiply this.").defineInRange("maxEnergy", 100.0, 10.0, 10000.0);
    public static final ModConfigSpec.DoubleValue REGEN_PER_SECOND = B
            .comment("Cursed energy regained per second at Grade 2. Higher grades multiply this.").defineInRange("regenPerSecond", 3.0, 0.0, 1000.0);
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER = B
            .comment("Multiplies all technique and domain damage. Armor still applies.")
            .defineInRange("damageMultiplier", 1.0, 0.0, 20.0);

    public static final ModConfigSpec.IntValue DOMAIN_RADIUS = B
            .comment("Domain sphere radius in blocks.").defineInRange("domainRadius", 10, 5, 24);
    public static final ModConfigSpec.IntValue DOMAIN_DURATION_SECONDS = B
            .comment("How long a domain lasts once it closes.").defineInRange("domainDurationSeconds", 12, 3, 120);
    public static final ModConfigSpec.IntValue DOMAIN_COOLDOWN_SECONDS = B
            .defineInRange("domainCooldownSeconds", 120, 0, 3600);
    public static final ModConfigSpec.DoubleValue DOMAIN_COST = B
            .comment("Energy needed (and spent) to open a domain.").defineInRange("domainCost", 80.0, 0.0, 10000.0);
    public static final ModConfigSpec.IntValue BURNOUT_SECONDS = B
            .comment("After a domain ends, the caster's technique is unusable for this long.")
            .defineInRange("burnoutSeconds", 10, 0, 300);
    public static final ModConfigSpec.BooleanValue DOMAIN_BARRIER = B
            .comment("Build a temporary barrier shell out of blocks (only replaces air, removed afterwards).")
            .define("domainBarrierBlocks", true);

    public static final ModConfigSpec.DoubleValue BLACK_FLASH_CHANCE = B
            .comment("Chance per fully charged melee hit to land a Black Flash.")
            .defineInRange("blackFlashChance", 0.06, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue BLACK_FLASH_ZONE_MULTIPLIER = B
            .comment("Black Flash chance multiplier for 10s after landing one.")
            .defineInRange("blackFlashZoneMultiplier", 2.5, 1.0, 20.0);

    // ------------------------------------------------------------ progression

    static { B.push("progression"); }

    public static final ModConfigSpec.DoubleValue SPECIAL_SPAWN_CHANCE = B
            .comment("Chance a new player awakens as a Special Grade with a rare innate technique (Unbounded / Calamity Sovereign).")
            .defineInRange("specialSpawnChance", 0.01, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue GRADE1_SPAWN_CHANCE = B
            .comment("Chance a new player awakens directly at Grade 1. Everyone else starts at Grade 2.")
            .defineInRange("grade1SpawnChance", 0.04, 0.0, 1.0);
    public static final ModConfigSpec.DoubleValue XP_MULTIPLIER = B
            .comment("Multiplies all grade XP gained. Raise to make leveling faster.")
            .defineInRange("xpMultiplier", 1.0, 0.0, 100.0);

    public static final ModConfigSpec.IntValue SEMI_GRADE1_XP = B
            .comment("Total XP for Semi-Grade 1.").defineInRange("semiGrade1Xp", 2500, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue GRADE1_XP = B
            .comment("Total XP for Grade 1 (unlocks Domain Expansion).").defineInRange("grade1Xp", 8000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue GRADE1_BLACK_FLASHES = B
            .comment("Black Flashes landed needed for Grade 1.").defineInRange("grade1BlackFlashes", 3, 0, 1000);
    public static final ModConfigSpec.IntValue SPECIAL_XP = B
            .comment("Total XP for Special Grade (unlocks Maximum and Reverse Cursed Technique).")
            .defineInRange("specialXp", 30000, 0, Integer.MAX_VALUE);
    public static final ModConfigSpec.IntValue SPECIAL_BLACK_FLASHES = B
            .comment("Black Flashes landed needed for Special Grade.").defineInRange("specialBlackFlashes", 12, 0, 1000);
    public static final ModConfigSpec.IntValue SPECIAL_BOSS_KILLS = B
            .comment("Boss kills (mobs with 150+ max health) needed for Special Grade.")
            .defineInRange("specialBossKills", 2, 0, 1000);

    static { B.pop(); }

    public static final ModConfigSpec SPEC = B.build();
}
