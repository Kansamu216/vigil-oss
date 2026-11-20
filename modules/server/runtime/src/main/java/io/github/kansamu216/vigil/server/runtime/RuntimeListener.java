package io.github.kansamu216.vigil.server.runtime;

import io.github.kansamu216.vigil.model.Verdict;
import io.github.kansamu216.vigil.model.VerdictAction;
import io.github.kansamu216.vigil.server.session.AuthFailure;

public interface RuntimeListener {

    default void onChallengeIssued(String playerId, String sessionId) {
    }

    default void onHandshakeFailed(String playerId, AuthFailure failure) {
    }

    default void onVerdict(String playerId, Verdict verdict) {
    }

    default void onActionRequired(String playerId, VerdictAction action, String reason) {
    }

    RuntimeListener NOOP = new RuntimeListener() {
    };
}
