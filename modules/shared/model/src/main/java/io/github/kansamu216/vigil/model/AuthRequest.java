package io.github.kansamu216.vigil.model;

public final class AuthRequest {

    private final String sessionId;
    private final String nonce;
    private final byte[] wrappedSessionKey;
    private final byte[] encryptedReport;

    public AuthRequest(String sessionId, String nonce, byte[] wrappedSessionKey, byte[] encryptedReport) {
        if (sessionId == null || sessionId.isEmpty() || sessionId.length() > 64) {
            throw new IllegalArgumentException("invalid session id");
        }
        if (nonce == null || nonce.length() < 16 || nonce.length() > 128) {
            throw new IllegalArgumentException("invalid nonce");
        }
        if (wrappedSessionKey.length == 0 || wrappedSessionKey.length > 1024) {
            throw new IllegalArgumentException("invalid wrapped key length");
        }
        if (encryptedReport.length == 0 || encryptedReport.length > 64 * 1024) {
            throw new IllegalArgumentException("invalid encrypted report length");
        }
        this.sessionId = sessionId;
        this.nonce = nonce;
        this.wrappedSessionKey = wrappedSessionKey.clone();
        this.encryptedReport = encryptedReport.clone();
    }

    public String sessionId() {
        return sessionId;
    }

    public String nonce() {
        return nonce;
    }

    public byte[] wrappedSessionKey() {
        return wrappedSessionKey.clone();
    }

    public byte[] encryptedReport() {
        return encryptedReport.clone();
    }
}
