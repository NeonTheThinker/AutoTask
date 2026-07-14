package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpResponseEditingFilesPayload(String data) implements CustomPayload {
    public static final CustomPayload.Id<AtpResponseEditingFilesPayload> ID =
            new CustomPayload.Id<>(Identifier.of("autotaskp", "editing_files"));

    public static final PacketCodec<RegistryByteBuf, AtpResponseEditingFilesPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, AtpResponseEditingFilesPayload::data,
            AtpResponseEditingFilesPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
