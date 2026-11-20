package io.github.kansamu216.vigil.platform.api;

/**
 * 平台能力项。
 *
 * <p>适配层显式声明支持的能力；不支持的能力进入降级路径。</p>
 */
public enum Capability {

    /** 双向自定义通道。 */
    CHANNEL,

    /** 主线程与后台调度。 */
    SCHEDULER,

    /** 游戏事件。 */
    GAME_EVENTS,

    /** 命令与权限节点。 */
    COMMANDS,

    /** Mod 清单枚举。 */
    MOD_LIST,

    /** 仅游戏窗口截图。 */
    WINDOW_CAPTURE,

    /** 停止服务器时的清理回调。 */
    SHUTDOWN_HOOK
}
