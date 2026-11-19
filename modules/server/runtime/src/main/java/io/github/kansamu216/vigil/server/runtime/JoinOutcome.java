package io.github.kansamu216.vigil.server.runtime;

/**
 * 玩家进服的两种结果：下发挑战，或直接拒绝（已在封禁中）。
 */
public final class JoinOutcome {

    private final boolean allowed;
    private final String sessionId;
    private final byte[] challengeFrame;
    private final String denyReason;

    private JoinOutcome(boolean allowed, String sessionId, byte[] challengeFrame, String denyReason) {
        this.allowed = allowed;
        this.sessionId = sessionId;
        this.challengeFrame = challengeFrame == null ? null : challengeFrame.clone();
        this.denyReason = denyReason;
    }

    static JoinOutcome challenge(String sessionId, byte[] frame) {
        return new JoinOutcome(true, sessionId, frame, null);
    }

    static JoinOutcome denied(String reason) {
        return new JoinOutcome(false, null, null, reason);
    }

    public boolean allowed() {
        return allowed;
    }

    public String sessionId() {
        return sessionId;
    }

    public byte[] challengeFrame() {
        return challengeFrame == null ? null : challengeFrame.clone();
    }

    public String denyReason() {
        return denyReason;
    }
}
