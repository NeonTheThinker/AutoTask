package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpResponseFilesPayload(String fileData) implements CustomPayload {

    public static final Id<AtpResponseFilesPayload> ID = new Id<>(Identifier.of("autotask_panel", "response_files"));

    public static final PacketCodec<RegistryByteBuf, AtpResponseFilesPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, AtpResponseFilesPayload::fileData,
                    AtpResponseFilesPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
