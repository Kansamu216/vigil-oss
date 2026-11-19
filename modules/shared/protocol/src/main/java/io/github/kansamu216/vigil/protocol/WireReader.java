package io.github.kansamu216.vigil.protocol;

import java.nio.charset.Charset;

/**
 * 手写大端读取器。
 *
 * <p>每次读取都检查剩余长度，任何越界都转成 {@link ProtocolException.Code#TRUNCATED}，
 * 而不是抛 {@link ArrayIndexOutOfBoundsException}：解码器必须能被畸形输入安全喂入。</p>
 */
public final class WireReader {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final byte[] buffer;
    private final int limit;
    private int cursor;

    public WireReader(byte[] buffer) {
        this(buffer, 0, buffer.length);
    }

    public WireReader(byte[] buffer, int offset, int length) {
        this.buffer = buffer;
        this.cursor = offset;
        this.limit = offset + length;
    }

    private void require(int count) throws ProtocolException {
        if (count < 0 || cursor + count > limit) {
            throw new ProtocolException(
                    ProtocolException.Code.TRUNCATED,
                    "need " + count + " bytes at " + cursor + ", limit " + limit);
        }
    }

    public int u8() throws ProtocolException {
        require(1);
        return buffer[cursor++] & 0xFF;
    }

    public int u16() throws ProtocolException {
        require(2);
        int value = ((buffer[cursor] & 0xFF) << 8) | (buffer[cursor + 1] & 0xFF);
        cursor += 2;
        return value;
    }

    public int i32() throws ProtocolException {
        require(4);
        int value = ((buffer[cursor] & 0xFF) << 24)
                | ((buffer[cursor + 1] & 0xFF) << 16)
                | ((buffer[cursor + 2] & 0xFF) << 8)
                | (buffer[cursor + 3] & 0xFF);
        cursor += 4;
        return value;
    }

    public long i64() throws ProtocolException {
        require(8);
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (buffer[cursor + i] & 0xFFL);
        }
        cursor += 8;
        return value;
    }

    public byte[] raw(int count) throws ProtocolException {
        require(count);
        byte[] out = new byte[count];
        System.arraycopy(buffer, cursor, out, 0, count);
        cursor += count;
        return out;
    }

    public byte[] bytes() throws ProtocolException {
        int length = u16();
        if (length > ProtocolLimits.MAX_PAYLOAD_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.TOO_LARGE, "byte field too long: " + length);
        }
        return raw(length);
    }

    public String string() throws ProtocolException {
        byte[] raw = bytes();
        if (raw.length > ProtocolLimits.MAX_STRING_BYTES) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE, "string too long: " + raw.length);
        }
        return new String(raw, UTF_8);
    }

    /** 读取一个数量字段，超上限直接拒绝，避免用它去预分配数组。 */
    public int count(int maxAllowed, String field) throws ProtocolException {
        int value = u16();
        if (value > maxAllowed) {
            throw new ProtocolException(
                    ProtocolException.Code.FIELD_OUT_OF_RANGE,
                    field + " count " + value + " > " + maxAllowed);
        }
        return value;
    }

    public int remaining() {
        return limit - cursor;
    }

    public int position() {
        return cursor;
    }
}
