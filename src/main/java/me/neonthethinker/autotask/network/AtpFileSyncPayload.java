package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpFileSyncPayload(String relativePath, String content) implements CustomPayload {
    public static final CustomPayload.Id<AtpFileSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of("autotaskp", "file_sync"));

    public static final PacketCodec<RegistryByteBuf, AtpFileSyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, AtpFileSyncPayload::relativePath,
            PacketCodecs.STRING, AtpFileSyncPayload::content,
            AtpFileSyncPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
