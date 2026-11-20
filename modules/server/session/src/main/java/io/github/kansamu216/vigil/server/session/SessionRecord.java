package io.github.kansamu216.vigil.server.session;

import io.github.kansamu216.vigil.model.Challenge;

/**
 * 会话记录。只在 session 模块内部修改，对外暴露只读快照。
 */
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

    /**
     * 记录并校验单调序号。返回 false 表示序号被复用或回退，应拒绝该报文。
     */
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
