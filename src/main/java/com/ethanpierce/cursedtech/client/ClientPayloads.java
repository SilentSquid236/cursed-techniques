package com.ethanpierce.cursedtech.client;

import com.ethanpierce.cursedtech.core.Technique;
import com.ethanpierce.cursedtech.network.ModNetwork;
import net.minecraft.world.phys.Vec3;

public final class ClientPayloads {
    private ClientPayloads() {}

    public static void sync(ModNetwork.SyncPayload p) {
        ClientState.technique = Technique.byId(p.technique());
        ClientState.energy = p.energy();
        ClientState.maxEnergy = p.maxEnergy();
        ClientState.cdLeft = p.cdLeft().length == 3 ? p.cdLeft() : new int[3];
        ClientState.cdMax = p.cdMax().length == 3 ? p.cdMax() : new int[3];
        ClientState.burnoutLeft = p.burnoutLeft();
        ClientState.inZone = p.inZone();
        ClientState.syncTick = ClientState.ticks;
    }

    public static void cinematic(ModNetwork.CinematicPayload p) {
        CinematicCamera.start(p.shot(), p.subjectId(), p.targetId(), p.duration(), p.label(), p.color());
    }

    public static void impact(ModNetwork.ImpactPayload p) {
        ImpactFrames.trigger(p.strength(), p.color(), p.label());
    }

    public static void domain(ModNetwork.DomainFxPayload p) {
        if (p.open()) {
            ClientState.DOMAINS.put(p.id(), new ClientState.Domain(new Vec3(p.x(), p.y(), p.z()), p.radius(), p.color()));
        } else {
            ClientState.DOMAINS.remove(p.id());
        }
    }
}
