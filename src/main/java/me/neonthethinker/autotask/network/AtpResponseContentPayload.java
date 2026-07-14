package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpResponseContentPayload(String relativePath, String content) implements CustomPayload {

    public static final Id<AtpResponseContentPayload> ID = new Id<>(Identifier.of("autotask_panel", "response_content"));

    public static final PacketCodec<RegistryByteBuf, AtpResponseContentPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, AtpResponseContentPayload::relativePath,
                    PacketCodecs.STRING, AtpResponseContentPayload::content,
                    AtpResponseContentPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
