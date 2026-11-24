package com.ethanpierce.cursedtech.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-player visual options (Mods > Cursed Techniques > Config). */
public final class ClientConfig {
    private ClientConfig() {}

    private static final ModConfigSpec.Builder B = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue CINEMATIC_CAMERA = B
            .comment("Third-person camera shots for Domain Expansions and Black Flash.")
            .define("cinematicCamera", true);
    public static final ModConfigSpec.BooleanValue IMPACT_FRAMES = B
            .comment("Flashing/inverted impact frames on big hits. Turn off if flashing lights bother you.")
            .define("impactFrames", true);
    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE = B
            .define("screenShake", true);
    public static final ModConfigSpec.BooleanValue SHOW_HUD = B
            .define("showHud", true);

    public static final ModConfigSpec SPEC = B.build();
}
