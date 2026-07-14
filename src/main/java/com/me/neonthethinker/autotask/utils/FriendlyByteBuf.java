package com.me.neonthethinker.autotask.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FriendlyByteBuf {
    private final DataInputStream in;
    private final DataOutputStream out;
    private final ByteArrayOutputStream baos;

    public FriendlyByteBuf(byte[] bytes) {
        this.in = new DataInputStream(new ByteArrayInputStream(bytes));
        this.out = null;
        this.baos = null;
    }

    public FriendlyByteBuf() {
        this.in = null;
        this.baos = new ByteArrayOutputStream();
        this.out = new DataOutputStream(baos);
    }

    public String readString() throws IOException {
        int length = readVarInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public void writeString(String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(bytes.length);
        out.write(bytes);
    }

    public boolean readBoolean() throws IOException {
        return in.readBoolean();
    }

    public void writeBoolean(boolean b) throws IOException {
        out.writeBoolean(b);
    }

    public long readVarLong() throws IOException {
        long value = 0;
        int position = 0;
        byte currentByte;
        while (true) {
            currentByte = in.readByte();
            value |= (long) (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 64) throw new RuntimeException("VarLong is too big");
        }
        return value;
    }

    public void writeVarLong(long value) throws IOException {
        while ((value & 0xFFFFFFFFFFFFFF80L) != 0L) {
            out.writeByte((int)((value & 0x7FL) | 0x80L));
            value >>>= 7;
        }
        out.writeByte((int)(value & 0x7FL));
    }

    public int readVarInt() throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;
        while (true) {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 32) throw new RuntimeException("VarInt is too big");
        }
        return value;
    }

    public void writeVarInt(int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0L) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value & 0x7F);
    }

    public byte[] toBytes() {
        return baos.toByteArray();
    }
}
