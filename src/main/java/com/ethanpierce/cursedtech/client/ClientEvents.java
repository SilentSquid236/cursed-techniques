package com.ethanpierce.cursedtech.client;

import com.ethanpierce.cursedtech.network.ModNetwork;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

public final class ClientEvents {
    private ClientEvents() {}

    private static final String CATEGORY = "key.categories.cursedtech";
    public static final KeyMapping[] KEYS = {
            new KeyMapping("key.cursedtech.ability1", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY),
            new KeyMapping("key.cursedtech.ability2", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY),
            new KeyMapping("key.cursedtech.domain", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY),
    };

    public static void registerKeys(RegisterKeyMappingsEvent e) {
        for (KeyMapping k : KEYS) e.register(k);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        ClientState.ticks++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (int i = 0; i < KEYS.length; i++) {
            while (KEYS[i].consumeClick()) PacketDistributor.sendToServer(new ModNetwork.AbilityPayload(i));
        }
        CinematicCamera.tick();
        ImpactFrames.tick();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        CinematicCamera.stop();
        ImpactFrames.reset();
        ClientState.reset();
    }

    private static ClientState.Domain domainAroundPlayer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        Vec3 p = mc.player.getEyePosition();
        for (ClientState.Domain d : ClientState.DOMAINS.values()) if (d.contains(p)) return d;
        return null;
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor e) {
        ClientState.Domain d = domainAroundPlayer();
        if (d == null) return;
        float r = ((d.color() >> 16) & 0xFF) / 255f, g = ((d.color() >> 8) & 0xFF) / 255f, b = (d.color() & 0xFF) / 255f;
        e.setRed(r * 0.18F);
        e.setGreen(g * 0.18F);
        e.setBlue(b * 0.18F);
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog e) {
        ClientState.Domain d = domainAroundPlayer();
        if (d == null) return;
        e.setNearPlaneDistance(d.radius() * 0.3F);
        e.setFarPlaneDistance(d.radius() * 2.2F);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        float[] s = ImpactFrames.shake((float) e.getPartialTick());
        if (s == null) return;
        e.setYaw(e.getYaw() + s[0]);
        e.setPitch(e.getPitch() + s[1]);
        e.setRoll(e.getRoll() + s[2]);
    }
}
