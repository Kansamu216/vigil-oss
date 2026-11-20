package io.github.kansamu216.vigil.platform.neoforge1211.channel;

import java.util.concurrent.atomic.AtomicLong;
import io.github.kansamu216.vigil.platform.neoforge1211.network.VigilFramePayload;
import io.github.kansamu216.vigil.platform.neoforge1211.network.VigilNetwork;
import io.github.kansamu216.vigil.protocol.VigilChannels;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 协议通道注册与最小收发链路。
 *
 * <p>只处理加载器注册与线程边界；通道标识以 {@link VigilChannels} 为准。</p>
 */
public final class VigilChannel {

    private static final Logger LOGGER = LoggerFactory.getLogger("vigil");

    private static final AtomicLong RECEIVED = new AtomicLong();

    private VigilChannel() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar =
                event.registrar(String.valueOf(VigilChannels.protocolVersion()));
        registrar.playBidirectional(
                VigilPingPayload.TYPE,
                VigilPingPayload.STREAM_CODEC,
                VigilChannel::handlePing);
        registrar.playBidirectional(
                VigilFramePayload.TYPE,
                VigilFramePayload.STREAM_CODEC,
                VigilNetwork::handle);
    }

    /**
     * 收到对端事实报文，只做版本校验与计数。
     */
    private static void handlePing(VigilPingPayload payload, IPayloadContext context) {
        RECEIVED.incrementAndGet();
        if (payload.protocolVersion() != VigilChannels.protocolVersion()) {
            LOGGER.warn("[VIGIL-CHANNEL-002] protocol mismatch local={} remote={}",
                    VigilChannels.protocolVersion(), payload.protocolVersion());
        }
    }

    /** 只读计数，供测试使用。 */
    public static long receivedCount() {
        return RECEIVED.get();
    }
}
