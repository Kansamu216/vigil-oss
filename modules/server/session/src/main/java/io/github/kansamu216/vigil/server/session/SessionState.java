package io.github.kansamu216.vigil.server.session;

/**
 * 会话状态。状态只在会话所属线程推进，后台任务只读取快照。
 */
public enum SessionState {

    /** 已创建，尚未下发挑战。 */
    NEW,

    /** 挑战已下发，等待应答。 */
    CHALLENGED,

    /** 握手完成，会话密钥已建立。 */
    AUTHENTICATED,

    /** 超时或被主动关闭。 */
    CLOSED,

    /** 校验失败达到上限，等待处置。 */
    REJECTED
}
