package io.github.kansamu216.vigil.model;

/**
 * 处置动作，由服务端判定流程产生。
 */
public enum VerdictAction {

    /** 仅记录。 */
    NONE,

    /** 通知管理员，玩家无感。 */
    ALERT,

    /** 踢出当前连接，允许立即重连。 */
    KICK,

    /** 限制玩法（只读、限制交互），需要人工解除。 */
    RESTRICT,

    /** 封禁。 */
    BAN
}
