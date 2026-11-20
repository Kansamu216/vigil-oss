package io.github.kansamu216.vigil.model;

public final class Challenge {

    private final String sessionId;
    private final String nonce;
    private final String serverKeyFingerprint;
    private final byte[] serverPublicKey;
    private final long issuedAtMillis;
    private final long expiresAtMillis;

    public Challenge(
            String sessionId,
            String nonce,
            String serverKeyFingerprint,
            byte[] serverPublicKey,
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
        if (serverPublicKey == null || serverPublicKey.length < 64 || serverPublicKey.length > 1024) {
            throw new IllegalArgumentException("invalid server public key");
        }
        if (expiresAtMillis <= issuedAtMillis) {
            throw new IllegalArgumentException("challenge must expire after issue");
        }
        this.sessionId = sessionId;
        this.nonce = nonce;
        this.serverKeyFingerprint = serverKeyFingerprint;
        this.serverPublicKey = serverPublicKey.clone();
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

    public byte[] serverPublicKey() {
        return serverPublicKey.clone();
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

    public byte[] signedPayload() {
        String key = java.util.Base64.getEncoder().encodeToString(serverPublicKey);
        return (sessionId + "|" + nonce + "|" + serverKeyFingerprint + "|"
                + issuedAtMillis + "|" + expiresAtMillis + "|" + key)
                .getBytes(java.nio.charset.Charset.forName("UTF-8"));
    }
}
