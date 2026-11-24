package com.ethanpierce.cursedtech.client;

import com.ethanpierce.cursedtech.CursedTech;
import com.ethanpierce.cursedtech.core.Technique;
import com.ethanpierce.cursedtech.network.ModNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Energy HUD, cinematic letterbox/titles and impact-frame overlays. */
public final class Hud {
    private Hud() {}

    public static void register(RegisterGuiLayersEvent e) {
        e.registerAboveAll(CursedTech.id("energy_hud"), Hud::renderHud);
        e.registerAboveAll(CursedTech.id("cinematic"), Hud::renderCinematic);
    }

    // --------------------------------------------------------------- HUD

    private static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ClientConfig.SHOW_HUD.get() || CinematicCamera.active()) return;
        Technique t = ClientState.technique;
        if (t == Technique.NONE) return;
        Font font = mc.font;
        int x = 8;
        int y = g.guiHeight() - 62;

        g.drawString(font, t.displayName, x, y, t.color, true);
        if (ClientState.inZone) g.drawString(font, " ⚡", x + font.width(t.displayName), y, 0xFFD01020, true);

        // Energy bar
        int w = 100, by = y + 11;
        float frac = ClientState.maxEnergy <= 0 ? 0 : Mth.clamp(ClientState.energy / ClientState.maxEnergy, 0, 1);
        g.fill(x - 1, by - 1, x + w + 1, by + 5, 0xC0000000);
        g.fill(x, by, x + (int) (w * frac), by + 4, 0xFF000000 | t.color);
        g.fill(x, by, x + (int) (w * frac), by + 1, 0x60FFFFFF);
        String ce = String.format("%.0f", ClientState.energy);
        g.drawString(font, ce, x + w + 4, by - 2, 0xFFCCCCCC, true);

        if (ClientState.burnoutLeft > 0) {
            int left = Math.max(0, ClientState.burnoutLeft - (int) (ClientState.ticks - ClientState.syncTick));
            g.drawString(font, "BURNOUT " + (left / 20 + 1) + "s", x, by + 8, 0xFFDD3333, true);
            return;
        }

        for (int i = 0; i < 3; i++) {
            int ly = by + 8 + i * 10;
            int cd = ClientState.cooldownLeft(i);
            String key = ClientEvents.KEYS[i].getTranslatedKeyMessage().getString();
            String name = i == 2 ? "Domain: " + t.domainName() : t.abilityName(i);
            boolean ready = cd <= 0;
            int col = ready ? 0xFFFFFFFF : 0xFF777777;
            String text = "[" + key + "] " + name + (ready ? "" : String.format("  %.1fs", cd / 20f));
            g.drawString(font, text, x, ly, col, true);
            if (!ready && ClientState.cdMax[i] > 0) {
                float f = 1 - cd / (float) ClientState.cdMax[i];
                g.fill(x, ly + 8, x + (int) (font.width(text) * f), ly + 9, 0x80FFFFFF);
            }
        }
    }

    // --------------------------------------------------------- cinematics

    private static void renderCinematic(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float pt = delta.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();

        // Letterbox and domain title.
        float lb = CinematicCamera.letterbox(pt);
        if (lb > 0) {
            int bar = (int) (h * 0.11F * lb);
            g.fill(0, 0, w, bar, 0xFF000000);
            g.fill(0, h - bar, w, h, 0xFF000000);
            if (CinematicCamera.shot() == ModNetwork.CinematicPayload.DOMAIN_ORBIT) {
                float p = CinematicCamera.progress(pt);
                if (p > 0.3F) {
                    int alpha = (int) (255 * Mth.clamp((p - 0.3F) / 0.15F, 0, 1) * lb);
                    if (alpha > 8) {
                        bigText(g, mc.font, "DOMAIN EXPANSION", w / 2, (int) (h * 0.62F), 1.4F,
                                (alpha << 24) | 0xDDDDDD);
                        bigText(g, mc.font, CinematicCamera.label, w / 2, (int) (h * 0.62F) + 16, 2.4F,
                                (alpha << 24) | (CinematicCamera.color & 0xFFFFFF));
                    }
                }
            }
        }

        // Impact frames.
        float age = ImpactFrames.age(pt);
        if (age >= 0 && age < 8) {
            int s = ImpactFrames.strength();
            int rgb = ImpactFrames.color() & 0xFFFFFF;
            if (s == 1) {
                if (age < 2) {
                    g.fill(0, 0, w, h, (0x55 << 24) | rgb);           // inverted frame, tinted
                } else if (age < 3.5F) {
                    g.fill(0, 0, w, h, 0xE0000000);                    // hard black frame
                    g.fill(0, h / 2 - 1, w, h / 2 + 1, (0xFF << 24) | rgb); // slash line
                } else {
                    int a = (int) (0xA0 * (1 - (age - 3.5F) / 4.5F));
                    if (a > 0) vignette(g, w, h, (a << 24) | rgb);
                }
            } else if (s == 0 && age < 3) {
                int a = (int) (0x90 * (1 - age / 3));
                if (a > 0) g.fill(0, 0, w, h, (a << 24) | rgb);
            }
        }
        // Impact title ("BLACK FLASH", "DOMAIN CLASH").
        if (age >= 1 && age < 24 && !ImpactFrames.label.isEmpty()) {
            float k = Mth.clamp((24 - age) / 6F, 0, 1);
            int alpha = (int) (255 * k);
            if (alpha > 8) {
                float scale = 3.2F - Mth.clamp((age - 1) / 4F, 0, 1) * 0.8F;
                int col = ImpactFrames.strength() == 1 || ImpactFrames.strength() == -1 ? 0xD01020 : 0xFFFFFF;
                bigText(g, mc.font, ImpactFrames.label, w / 2 + 1, h / 3 + 1, scale, (alpha << 24));
                bigText(g, mc.font, ImpactFrames.label, w / 2, h / 3, scale, (alpha << 24) | col);
            }
        } else if (age >= 24) {
            ImpactFrames.label = "";
        }
    }

    private static void vignette(GuiGraphics g, int w, int h, int argb) {
        int t = Math.max(6, h / 10);
        g.fill(0, 0, w, t, argb);
        g.fill(0, h - t, w, h, argb);
        g.fill(0, t, t, h - t, argb);
        g.fill(w - t, t, w, h - t, argb);
    }

    private static void bigText(GuiGraphics g, Font font, String text, int cx, int cy, float scale, int argb) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, text, -font.width(text) / 2, -4, argb, false);
        g.pose().popPose();
    }
}
