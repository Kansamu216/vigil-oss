package io.github.kansamu216.vigil.server.session;

import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.model.ClientReport;

/**
 * 握手成功的结果。
 *
 * <p>会话密钥只保留在内存，方法返回副本供后续保活使用；用尽后调用 {@link #wipe()}。</p>
 */
public final class AuthResult {

    private final String sessionId;
    private final String playerId;
    private final ClientReport report;
    private final byte[] sessionKey;

    AuthResult(String sessionId, String playerId, ClientReport report, byte[] sessionKey) {
        this.sessionId = sessionId;
        this.playerId = playerId;
        this.report = report;
        this.sessionKey = sessionKey;
    }

    public String sessionId() {
        return sessionId;
    }

    public String playerId() {
        return playerId;
    }

    public ClientReport report() {
        return report;
    }

    public byte[] sessionKey() {
        return sessionKey.clone();
    }

    public void wipe() {
        io.github.kansamu216.vigil.crypto.Secrets.wipe(sessionKey);
    }

    static AuthResult of(Challenge challenge, ClientReport report, byte[] sessionKey) {
        return new AuthResult(challenge.sessionId(), report.playerId(), report, sessionKey);
    }
}
