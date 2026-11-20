package io.github.kansamu216.vigil.server.runtime;

import io.github.kansamu216.vigil.model.Verdict;
import io.github.kansamu216.vigil.server.session.AuthOutcome;

/**
 * 一次认证应答的处理结果：握手结论 + 服务端判定。
 *
 * <p>握手失败时 {@code verdict} 为 null。</p>
 */
public final class AuthEvaluation {

    private final AuthOutcome outcome;
    private final Verdict verdict;

    AuthEvaluation(AuthOutcome outcome, Verdict verdict) {
        this.outcome = outcome;
        this.verdict = verdict;
    }

    public AuthOutcome outcome() {
        return outcome;
    }

    public Verdict verdict() {
        return verdict;
    }

    public boolean handshakeSucceeded() {
        return outcome.isSuccess();
    }
}
