package dev.saurus.cosmos.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client/server messages for leaving and reaching planetary surfaces. */
public final class CosmosPayloads {
    private CosmosPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
        return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("cosmos", path));
    }

    /** Server -> client: you flew out of the top of a body's atmosphere at (lat, lon); continue in space. */
    public record Launch(String body, double latDeg, double lonDeg, double upSpeed) implements CustomPacketPayload {
        public static final Type<Launch> TYPE = typeOf("launch");
        public static final StreamCodec<ByteBuf, Launch> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Launch::body, ByteBufCodecs.DOUBLE, Launch::latDeg, ByteBufCodecs.DOUBLE, Launch::lonDeg,
                ByteBufCodecs.DOUBLE, Launch::upSpeed, Launch::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Client -> server: put me on body's surface at (lat, lon). Empty body = drop back down where I launched from. */
    public record Land(String body, double latDeg, double lonDeg) implements CustomPacketPayload {
        public static final Type<Land> TYPE = typeOf("land");
        public static final StreamCodec<ByteBuf, Land> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Land::body, ByteBufCodecs.DOUBLE, Land::latDeg, ByteBufCodecs.DOUBLE, Land::lonDeg, Land::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Server -> client on join and after landing: which body the player is standing on ("" for none). */
    public record WorldInfo(boolean overworldIsEarth, String body) implements CustomPacketPayload {
        public static final Type<WorldInfo> TYPE = typeOf("world_info");
        public static final StreamCodec<ByteBuf, WorldInfo> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, WorldInfo::overworldIsEarth, ByteBufCodecs.STRING_UTF8, WorldInfo::body, WorldInfo::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
