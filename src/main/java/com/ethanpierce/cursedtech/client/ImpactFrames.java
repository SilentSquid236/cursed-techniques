package com.ethanpierce.cursedtech.client;

import com.ethanpierce.cursedtech.mixin.GameRendererInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/**
 * Anime-style impact frames.
 *
 * Heavy (Black Flash, domain clash): one inverted-color frame burst with a
 * tinted overlay, then a hard black frame, then fade, plus strong shake.
 * Light (big technique hits): a quick white flash and a small shake.
 */
public final class ImpactFrames {
    private ImpactFrames() {}

    private static final ResourceLocation INVERT = ResourceLocation.withDefaultNamespace("shaders/post/invert.json");
    private static final RandomSource RNG = RandomSource.create();

    private static long start = -100;
    private static int strength;
    private static int color;
    public static String label = "";
    private static boolean inverted;

    private static long shakeStart = -100;
    private static float shakePower;
    private static int shakeTicks;

    public static void trigger(int str, int rgb, String text) {
        if (ClientConfig.SCREEN_SHAKE.get()) {
            shakeStart = ClientState.ticks;
            shakePower = str == 1 ? 3.0F : 1.0F;
            shakeTicks = str == 1 ? 10 : 5;
        }
        label = text;
        if (!ClientConfig.IMPACT_FRAMES.get()) {
            if (!text.isEmpty()) start = ClientState.ticks; // still show the title text
            strength = -1;
            return;
        }
        start = ClientState.ticks;
        strength = str;
        color = rgb;
        if (str == 1) setInvert(true);
    }

    public static void tick() {
        if (inverted && ClientState.ticks - start >= 2) setInvert(false);
    }

    private static void setInvert(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        try {
            if (on) {
                ((GameRendererInvoker) mc.gameRenderer).cursedtech$loadEffect(INVERT);
                inverted = true;
            } else if (inverted) {
                mc.gameRenderer.shutdownEffect();
                mc.gameRenderer.checkEntityPostEffect(mc.getCameraEntity());
                inverted = false;
            }
        } catch (Exception ignored) {
            inverted = false;
        }
    }

    public static void reset() {
        setInvert(false);
        start = -100;
        shakeStart = -100;
    }

    /** Ticks since the last impact, including partial ticks. */
    public static float age(float pt) {
        return ClientState.ticks - start + pt;
    }

    public static int strength() { return strength; }
    public static int color() { return color; }

    /** Returns {yaw, pitch, roll} offsets in degrees. */
    public static float[] shake(float pt) {
        float t = ClientState.ticks - shakeStart + pt;
        if (t < 0 || t > shakeTicks) return null;
        float k = shakePower * (1 - t / shakeTicks);
        return new float[]{(RNG.nextFloat() - 0.5F) * k, (RNG.nextFloat() - 0.5F) * k, (RNG.nextFloat() - 0.5F) * k * 1.5F};
    }
}
