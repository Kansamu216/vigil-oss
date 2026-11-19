package io.github.kansamu216.vigil.platform.neoforge1211.network;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 会话帧转交点。
 *
 * <p>客户端与服务端共用同一个 payload 注册，但只有服务端会安装 {@link FrameSink}。
 * 客户端未接入 Agent 时收到帧会被忽略，不会误解析或误判。</p>
 */
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
