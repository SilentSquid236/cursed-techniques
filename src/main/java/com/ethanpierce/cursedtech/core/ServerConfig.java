package com.ethanpierce.cursedtech.core;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Balance knobs. Lives in world/serverconfig/cursedtech-server.toml. */
public final class ServerConfig {
    private ServerConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue MAX_ENERGY = B
            .comment("Cursed energy pool size.").defineInRange("maxEnergy", 100.0, 10.0, 10000.0);
    public static final ModConfigSpec.DoubleValue REGEN_PER_SECOND = B
            .comment("Cursed energy regained per second.").defineInRange("regenPerSecond", 3.0, 0.0, 1000.0);
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

    public static final ModConfigSpec SPEC = B.build();
}
