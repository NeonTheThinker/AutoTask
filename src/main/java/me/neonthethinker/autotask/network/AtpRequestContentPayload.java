package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpRequestContentPayload(String relativePath) implements CustomPayload {

    public static final Id<AtpRequestContentPayload> ID = new Id<>(Identifier.of("autotask_panel", "request_content"));

    public static final PacketCodec<RegistryByteBuf, AtpRequestContentPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, AtpRequestContentPayload::relativePath,
                    AtpRequestContentPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
