package io.github.kansamu216.vigil.platform.api;

import java.util.UUID;

/**
 * 自定义通道。实现方负责网络线程与业务线程的边界，核心只看到字节。
 */
public interface GuardChannel {

    /** 通道标识，与协议层一致。 */
    String channelName();

    /** 发送一帧给指定玩家；返回 false 表示玩家已离线或通道未就绪。 */
    boolean sendToPlayer(UUID playerId, byte[] frame);

    /** 注册接收回调；回调必须切换到业务线程执行。 */
    void onReceive(FrameHandler handler);

    /** 单帧上限，供核心提前拒绝超大报文。 */
    int maxFrameBytes();

    interface FrameHandler {
        void onFrame(UUID playerId, byte[] frame);
    }
}
