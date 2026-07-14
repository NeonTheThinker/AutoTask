package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpResponseExecutedTasksPayload(String executedTasksData) implements CustomPayload {

    public static final Id<AtpResponseExecutedTasksPayload> ID = new Id<>(Identifier.of("autotask_panel", "response_executed_tasks"));

    public static final PacketCodec<RegistryByteBuf, AtpResponseExecutedTasksPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.STRING, AtpResponseExecutedTasksPayload::executedTasksData,
                    AtpResponseExecutedTasksPayload::new
            );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
