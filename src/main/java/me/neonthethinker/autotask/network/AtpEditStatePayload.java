package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpEditStatePayload(String relativePath, boolean isEditing) implements CustomPayload {
    public static final CustomPayload.Id<AtpEditStatePayload> ID =
            new CustomPayload.Id<>(Identifier.of("autotaskp", "edit_state"));

    public static final PacketCodec<RegistryByteBuf, AtpEditStatePayload> CODEC = PacketCodec.tuple(
            PacketCodecs.STRING, AtpEditStatePayload::relativePath,
            PacketCodecs.BOOL, AtpEditStatePayload::isEditing,
            AtpEditStatePayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() { return ID; }
}
