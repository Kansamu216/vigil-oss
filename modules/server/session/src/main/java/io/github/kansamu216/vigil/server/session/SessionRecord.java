package io.github.kansamu216.vigil.server.session;

import io.github.kansamu216.vigil.model.Challenge;

final class SessionRecord {

    private final String sessionId;
    private final String playerId;
    private final Challenge challenge;

    private SessionState state = SessionState.CHALLENGED;
    private long lastActivityMillis;
    private int failedAttempts;
    private long lastSequence = -1L;

    SessionRecord(String sessionId, String playerId, Challenge challenge, long nowMillis) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.challenge = challenge;
        this.lastActivityMillis = nowMillis;
    }

    String sessionId() {
        return sessionId;
    }

    String playerId() {
        return playerId;
    }

    Challenge challenge() {
        return challenge;
    }

    SessionState state() {
        return state;
    }

    void state(SessionState state) {
        this.state = state;
    }

    long lastActivityMillis() {
        return lastActivityMillis;
    }

    void touch(long nowMillis) {
        this.lastActivityMillis = nowMillis;
    }

    int failedAttempts() {
        return failedAttempts;
    }

    int recordFailure() {
        return ++failedAttempts;
    }

    boolean acceptSequence(long sequence) {
        if (sequence <= lastSequence) {
            return false;
        }
        lastSequence = sequence;
        return true;
    }

    long lastSequence() {
        return lastSequence;
    }
}
