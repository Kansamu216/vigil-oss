package io.github.kansamu216.vigil.platform.neoforge1211.network;

import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import io.github.kansamu216.vigil.protocol.VigilChannels;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.Arrays;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 承载协议信封的会话帧。
 *
 * <p>只做字节搬运：不在这里解析业务字段，也不在客户端打印内容。长度上限与核心
 * 协议一致，超限在解码阶段直接拒绝。</p>
 */
public record VigilFramePayload(byte[] data) implements CustomPacketPayload {

    public static final Type<VigilFramePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(VigilChannels.namespace(), VigilChannels.frameId()));

    public static final StreamCodec<ByteBuf, VigilFramePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                byte[] bytes = payload.data;
                if (bytes.length > ProtocolLimits.MAX_FRAME_BYTES) {
                    throw new DecoderException("frame too large: " + bytes.length);
                }
                buffer.writeInt(bytes.length);
                buffer.writeBytes(bytes);
            },
            buffer -> {
                int length = buffer.readInt();
                if (length < 0 || length > ProtocolLimits.MAX_FRAME_BYTES) {
                    throw new DecoderException("invalid frame length: " + length);
                }
                byte[] bytes = new byte[length];
                buffer.readBytes(bytes);
                return new VigilFramePayload(bytes);
            });

    public VigilFramePayload {
        if (data.length > ProtocolLimits.MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("frame too large: " + data.length);
        }
        data = Arrays.copyOf(data, data.length);
    }

    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
