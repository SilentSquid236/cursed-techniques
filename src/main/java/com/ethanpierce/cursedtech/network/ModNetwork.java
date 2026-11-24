package com.ethanpierce.cursedtech.network;

import com.ethanpierce.cursedtech.CursedTech;
import com.ethanpierce.cursedtech.ability.Abilities;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(AbilityPayload.TYPE, AbilityPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) Abilities.use(sp, p.slot());
        }));
        // Client handlers are referenced only inside lambda bodies so the
        // dedicated server never loads client classes.
        r.playToClient(SyncPayload.TYPE, SyncPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() ->
                com.ethanpierce.cursedtech.client.ClientPayloads.sync(p)));
        r.playToClient(CinematicPayload.TYPE, CinematicPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() ->
                com.ethanpierce.cursedtech.client.ClientPayloads.cinematic(p)));
        r.playToClient(ImpactPayload.TYPE, ImpactPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() ->
                com.ethanpierce.cursedtech.client.ClientPayloads.impact(p)));
        r.playToClient(DomainFxPayload.TYPE, DomainFxPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() ->
                com.ethanpierce.cursedtech.client.ClientPayloads.domain(p)));
    }

    // ---------------------------------------------------------------- C2S

    public record AbilityPayload(int slot) implements CustomPacketPayload {
        public static final Type<AbilityPayload> TYPE = new Type<>(CursedTech.id("ability"));
        public static final StreamCodec<FriendlyByteBuf, AbilityPayload> CODEC =
                StreamCodec.ofMember((p, b) -> b.writeVarInt(p.slot), b -> new AbilityPayload(b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- S2C

    /** Player HUD state. Cooldowns are remaining ticks. */
    public record SyncPayload(String technique, float energy, float maxEnergy,
                              int[] cdLeft, int[] cdMax, int burnoutLeft, boolean inZone) implements CustomPacketPayload {
        public static final Type<SyncPayload> TYPE = new Type<>(CursedTech.id("sync"));
        public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = StreamCodec.ofMember(SyncPayload::write, SyncPayload::read);

        private void write(FriendlyByteBuf b) {
            b.writeUtf(technique);
            b.writeFloat(energy);
            b.writeFloat(maxEnergy);
            b.writeVarIntArray(cdLeft);
            b.writeVarIntArray(cdMax);
            b.writeVarInt(burnoutLeft);
            b.writeBoolean(inZone);
        }

        private static SyncPayload read(FriendlyByteBuf b) {
            return new SyncPayload(b.readUtf(), b.readFloat(), b.readFloat(),
                    b.readVarIntArray(), b.readVarIntArray(), b.readVarInt(), b.readBoolean());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Ask the client to play a camera shot. */
    public record CinematicPayload(int shot, int subjectId, int targetId, int duration, String label, int color)
            implements CustomPacketPayload {
        public static final int DOMAIN_ORBIT = 1;
        public static final int BLACK_FLASH = 2;
        public static final int FINISHER = 3;

        public static final Type<CinematicPayload> TYPE = new Type<>(CursedTech.id("cinematic"));
        public static final StreamCodec<FriendlyByteBuf, CinematicPayload> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    b.writeVarInt(p.shot); b.writeVarInt(p.subjectId); b.writeVarInt(p.targetId);
                    b.writeVarInt(p.duration); b.writeUtf(p.label); b.writeInt(p.color);
                },
                b -> new CinematicPayload(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readUtf(), b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Impact frame. strength 0 = light white flash, 1 = heavy inverted frame. */
    public record ImpactPayload(int strength, int color, String label) implements CustomPacketPayload {
        public static final Type<ImpactPayload> TYPE = new Type<>(CursedTech.id("impact"));
        public static final StreamCodec<FriendlyByteBuf, ImpactPayload> CODEC = StreamCodec.ofMember(
                (p, b) -> { b.writeVarInt(p.strength); b.writeInt(p.color); b.writeUtf(p.label); },
                b -> new ImpactPayload(b.readVarInt(), b.readInt(), b.readUtf()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Domain opened/closed, for fog tint inside it. */
    public record DomainFxPayload(int id, boolean open, double x, double y, double z, float radius, int color)
            implements CustomPacketPayload {
        public static final Type<DomainFxPayload> TYPE = new Type<>(CursedTech.id("domain_fx"));
        public static final StreamCodec<FriendlyByteBuf, DomainFxPayload> CODEC = StreamCodec.ofMember(
                (p, b) -> {
                    b.writeVarInt(p.id); b.writeBoolean(p.open);
                    b.writeDouble(p.x); b.writeDouble(p.y); b.writeDouble(p.z);
                    b.writeFloat(p.radius); b.writeInt(p.color);
                },
                b -> new DomainFxPayload(b.readVarInt(), b.readBoolean(), b.readDouble(), b.readDouble(), b.readDouble(),
                        b.readFloat(), b.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
