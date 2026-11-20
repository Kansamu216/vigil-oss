package io.github.kansamu216.vigil.platform.neoforge1211.network;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class VigilNetwork {

    private static volatile FrameSink sink;

    private VigilNetwork() {
    }

    public static void setSink(FrameSink frameSink) {
        sink = frameSink;
    }

    public static void handle(VigilFramePayload payload, IPayloadContext context) {
        FrameSink current = sink;
        if (current == null) {
            return;
        }
        current.onFrame(context.player(), payload.data());
    }

    public interface FrameSink {
        void onFrame(Player player, byte[] frame);
    }
}
