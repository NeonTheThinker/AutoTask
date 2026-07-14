package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpRequestFilesPayload() implements CustomPayload {

    public static final Id<AtpRequestFilesPayload> ID = new Id<>(Identifier.of("autotask_panel", "request_files"));

    public static final PacketCodec<RegistryByteBuf, AtpRequestFilesPayload> CODEC =
            PacketCodec.unit(new AtpRequestFilesPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
