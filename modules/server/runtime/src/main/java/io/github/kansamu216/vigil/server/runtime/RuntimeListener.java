package io.github.kansamu216.vigil.server.runtime;

import io.github.kansamu216.vigil.model.Verdict;
import io.github.kansamu216.vigil.model.VerdictAction;
import io.github.kansamu216.vigil.server.session.AuthFailure;

/**
 * 运行时事件回调。
 *
 * <p>平台适配层通过它把结果落到具体动作（踢人、通知管理员），
 * 核心运行时自己不做任何平台相关操作。</p>
 */
public interface RuntimeListener {

    default void onChallengeIssued(String playerId, String sessionId) {
    }

    default void onHandshakeFailed(String playerId, AuthFailure failure) {
    }

    default void onVerdict(String playerId, Verdict verdict) {
    }

    default void onActionRequired(String playerId, VerdictAction action, String reason) {
    }

    /** 什么都不做的实现，供测试与未接通知渠道的部署使用。 */
    RuntimeListener NOOP = new RuntimeListener() {
    };
}
