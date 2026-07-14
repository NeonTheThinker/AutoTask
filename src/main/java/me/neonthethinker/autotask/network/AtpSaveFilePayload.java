package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpSaveFilePayload(String relativePath, String content) implements CustomPayload {

    public static final Id<AtpSaveFilePayload> ID = new Id<>(Identifier.of("autotask_panel", "save_file"));

    public static final PacketCodec<RegistryByteBuf, AtpSaveFilePayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, AtpSaveFilePayload::relativePath,
                    PacketCodecs.STRING, AtpSaveFilePayload::content,
                    AtpSaveFilePayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
