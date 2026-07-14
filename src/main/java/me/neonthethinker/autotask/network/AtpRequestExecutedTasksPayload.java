package me.neonthethinker.autotask.network;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record AtpRequestExecutedTasksPayload() implements CustomPayload {

    public static final Id<AtpRequestExecutedTasksPayload> ID = new Id<>(Identifier.of("autotask_panel", "request_executed_tasks"));

    public static final PacketCodec<RegistryByteBuf, AtpRequestExecutedTasksPayload> CODEC =
            PacketCodec.unit(new AtpRequestExecutedTasksPayload());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
