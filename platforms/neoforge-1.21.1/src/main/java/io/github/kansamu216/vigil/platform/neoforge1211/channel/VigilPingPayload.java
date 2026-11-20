package io.github.kansamu216.vigil.platform.neoforge1211.channel;

import io.github.kansamu216.vigil.protocol.VigilChannels;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 通道上的连通性报文，只携带协议版本与单调序号；长度固定。
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
