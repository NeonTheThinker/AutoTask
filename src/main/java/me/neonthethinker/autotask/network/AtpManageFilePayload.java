package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpManageFilePayload(String action, String sourcePath, String targetPath) implements CustomPayload {
    public static final CustomPayload.Id<AtpManageFilePayload> ID = new CustomPayload.Id<>(Identifier.of("autotaskp", "manage_file"));

    public static final PacketCodec<RegistryByteBuf, AtpManageFilePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, AtpManageFilePayload::action,
            PacketCodecs.STRING, AtpManageFilePayload::sourcePath,
            PacketCodecs.STRING, AtpManageFilePayload::targetPath,
            AtpManageFilePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
