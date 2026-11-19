package io.github.kansamu216.vigil.platform.neoforge1211.channel;

import io.github.kansamu216.vigil.protocol.VigilChannels;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 通道上最小的事实报文，用于 M0 验证双端注册与收发链路。
 *
 * <p>只携带协议版本与单调序号，不携带玩家标识、文件路径或环境数据。
 * 报文长度由字段类型固定，没有可变长字符串或列表，因此不存在解码放大面。</p>
 */
public record VigilPingPayload(int protocolVersion, long sequence) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<VigilPingPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(VigilChannels.namespace(), VigilChannels.pingId()));

    public static final StreamCodec<ByteBuf, VigilPingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, VigilPingPayload::protocolVersion,
                    ByteBufCodecs.VAR_LONG, VigilPingPayload::sequence,
                    VigilPingPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
