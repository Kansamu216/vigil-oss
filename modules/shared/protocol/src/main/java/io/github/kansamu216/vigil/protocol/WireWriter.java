package io.github.kansamu216.vigil.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;

/**
 * 手写大端编码器。
 *
 * <p>所有写入都检查上限，避免构造出超长帧再在解码端拒绝。UTF-8 字符串先写 u16 长度。</p>
 */
public final class WireWriter {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final ByteArrayOutputStream out;
    private final int limit;

    public WireWriter(int expectedSize, int limit) {
        this.out = new ByteArrayOutputStream(expectedSize < 32 ? 32 : expectedSize);
        this.limit = limit;
    }

    public static WireWriter of(int limit) {
        return new WireWriter(64, limit);
    }

    private void ensure(int extra) {
        if (out.size() + extra > limit) {
            throw new IllegalStateException(
                    "wire writer overflow: " + (out.size() + extra) + " > " + limit);
        }
    }

    public WireWriter u8(int value) {
        ensure(1);
        out.write(value & 0xFF);
        return this;
    }

    public WireWriter u16(int value) {
        ensure(2);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
        return this;
    }

    public WireWriter i32(int value) {
        ensure(4);
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
        return this;
    }

    public WireWriter i64(long value) {
        ensure(8);
        for (int shift = 56; shift >= 0; shift -= 8) {
            out.write((int) (value >>> shift) & 0xFF);
        }
        return this;
    }

    public WireWriter raw(byte[] bytes) {
        ensure(bytes.length);
        out.write(bytes, 0, bytes.length);
        return this;
    }

    public WireWriter bytes(byte[] bytes) {
        if (bytes.length > 0xFFFF) {
            throw new IllegalArgumentException("byte array too long: " + bytes.length);
        }
        u16(bytes.length);
        raw(bytes);
        return this;
    }

    public WireWriter string(String value) {
        byte[] encoded = value.getBytes(UTF_8);
        if (encoded.length > ProtocolLimits.MAX_STRING_BYTES) {
            throw new IllegalArgumentException("string too long: " + encoded.length);
        }
        return bytes(encoded);
    }

    public int size() {
        return out.size();
    }

    public byte[] toByteArray() {
        return out.toByteArray();
    }
}
