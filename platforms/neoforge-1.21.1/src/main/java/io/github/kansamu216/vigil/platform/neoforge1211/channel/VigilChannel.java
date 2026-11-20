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

    private static void handlePing(VigilPingPayload payload, IPayloadContext context) {
        RECEIVED.incrementAndGet();
        if (payload.protocolVersion() != VigilChannels.protocolVersion()) {
            LOGGER.warn("[VIGIL-CHANNEL-002] protocol mismatch local={} remote={}",
                    VigilChannels.protocolVersion(), payload.protocolVersion());
        }
    }

    public static long receivedCount() {
        return RECEIVED.get();
    }
}
