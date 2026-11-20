package io.github.kansamu216.vigil.server.runtime;

import io.github.kansamu216.vigil.model.Verdict;
import io.github.kansamu216.vigil.server.session.AuthOutcome;

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
