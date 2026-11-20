package io.github.kansamu216.vigil.platform.api;

import java.util.UUID;

public interface GuardChannel {

    String channelName();

    boolean sendToPlayer(UUID playerId, byte[] frame);

    void onReceive(FrameHandler handler);

    int maxFrameBytes();

    interface FrameHandler {
        void onFrame(UUID playerId, byte[] frame);
    }
}
