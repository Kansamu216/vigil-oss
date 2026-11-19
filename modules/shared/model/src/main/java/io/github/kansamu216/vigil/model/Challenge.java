package io.github.kansamu216.vigil.model;

/**
 * 服务端签发的挑战。
 *
 * <p>单次使用：nonce 被消费后立即失效；过期同样失效，不因重连延长。</p>
 */
public final class Challenge {

    private final String sessionId;
    private final String nonce;
    private final String serverKeyFingerprint;
    private final long issuedAtMillis;
    private final long expiresAtMillis;

    public Challenge(
            String sessionId,
            String nonce,
            String serverKeyFingerprint,
            long issuedAtMillis,
            long expiresAtMillis) {
        if (sessionId == null || sessionId.length() < 8 || sessionId.length() > 64) {
            throw new IllegalArgumentException("invalid session id");
        }
        if (nonce == null || nonce.length() < 16 || nonce.length() > 128) {
            throw new IllegalArgumentException("invalid nonce");
        }
        if (serverKeyFingerprint == null || serverKeyFingerprint.length() != 64) {
            throw new IllegalArgumentException("fingerprint must be 64 hex chars");
        }
        if (expiresAtMillis <= issuedAtMillis) {
            throw new IllegalArgumentException("challenge must expire after issue");
        }
        this.sessionId = sessionId;
        this.nonce = nonce;
        this.serverKeyFingerprint = serverKeyFingerprint;
        this.issuedAtMillis = issuedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String sessionId() {
        return sessionId;
    }

    public String nonce() {
        return nonce;
    }

    public String serverKeyFingerprint() {
        return serverKeyFingerprint;
    }

    public long issuedAtMillis() {
        return issuedAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    /** 签名覆盖的字节：sessionId|nonce|fingerprint|issuedAt|expiresAt。 */
    public byte[] signedPayload() {
        return (sessionId + "|" + nonce + "|" + serverKeyFingerprint + "|"
                + issuedAtMillis + "|" + expiresAtMillis).getBytes(java.nio.charset.Charset.forName("UTF-8"));
    }
}
