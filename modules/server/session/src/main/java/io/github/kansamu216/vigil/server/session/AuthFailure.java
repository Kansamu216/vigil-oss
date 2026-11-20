package io.github.kansamu216.vigil.server.session;

/**
 * 握手失败原因。
 *
 * <p>对外只用于审计与指标聚合，不下发给玩家，避免暴露服务端校验细节。</p>
 */
public enum AuthFailure {

    /** 会话不存在或已关闭。 */
    UNKNOWN_SESSION,

    /** 报文来源连接与挑战不匹配。 */
    CONNECTION_MISMATCH,

    /** nonce 与挑战不一致（重放、串包或旧挑战）。 */
    NONCE_MISMATCH,

    /** 挑战已过期。 */
    CHALLENGE_EXPIRED,

    /** 会话密钥包裹解不开（跨交付、密钥轮换或伪造）。 */
    KEY_UNWRAP_FAILED,

    /** 报告解密失败（密文被改、AAD 不匹配或序号错）。 */
    REPORT_DECRYPT_FAILED,

    /** 报告编码非法。 */
    REPORT_MALFORMED,

    /** 触发限流。 */
    RATE_LIMITED,

    /** 已认证，重复提交应答。 */
    ALREADY_AUTHENTICATED,

    /** 内部故障：不得作为作弊证据。 */
    INTERNAL_ERROR
}
