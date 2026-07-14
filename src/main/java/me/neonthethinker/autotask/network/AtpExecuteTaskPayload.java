package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpExecuteTaskPayload(String taskName, long startOffset) implements CustomPayload {

    public static final Id<AtpExecuteTaskPayload> ID = new Id<>(Identifier.of("autotask_panel", "execute_task"));

    public static final PacketCodec<RegistryByteBuf, AtpExecuteTaskPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, AtpExecuteTaskPayload::taskName,
                    PacketCodecs.VAR_LONG, AtpExecuteTaskPayload::startOffset,
                    AtpExecuteTaskPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
