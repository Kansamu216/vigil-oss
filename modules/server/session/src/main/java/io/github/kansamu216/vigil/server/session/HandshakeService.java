package io.github.kansamu216.vigil.server.session;

import io.github.kansamu216.vigil.crypto.AeadCipher;
import io.github.kansamu216.vigil.crypto.ChallengeSignature;
import io.github.kansamu216.vigil.crypto.CryptoException;
import io.github.kansamu216.vigil.crypto.KeyFingerprint;
import io.github.kansamu216.vigil.crypto.RsaOaep;
import io.github.kansamu216.vigil.crypto.Secrets;
import io.github.kansamu216.vigil.model.AuthRequest;
import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.protocol.AssociatedData;
import io.github.kansamu216.vigil.protocol.Direction;
import io.github.kansamu216.vigil.protocol.MessageType;
import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolVersion;
import io.github.kansamu216.vigil.protocol.payload.ReportPayload;
import java.nio.charset.Charset;
import java.security.KeyPair;
import java.util.UUID;

public final class HandshakeService {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int MAX_FAILED_ATTEMPTS = 5;

    private final KeyPair serverKeyPair;
    private final String keyFingerprint;
    private final long challengeTtlMillis;
    private final SessionRegistry sessions;
    private final RateLimiter authLimiter;

    public HandshakeService(
            KeyPair serverKeyPair, long challengeTtlMillis, SessionRegistry sessions) {
        if (challengeTtlMillis <= 0L) {
            throw new IllegalArgumentException("challenge ttl must be positive");
        }
        this.serverKeyPair = serverKeyPair;
        this.keyFingerprint = KeyFingerprint.of(serverKeyPair.getPublic());
        this.challengeTtlMillis = challengeTtlMillis;
        this.sessions = sessions;

        this.authLimiter = new RateLimiter(5, 10_000L, 4096);
    }

    public String keyFingerprint() {
        return keyFingerprint;
    }

    public SessionRegistry sessions() {
        return sessions;
    }

    public SignedChallenge issueChallenge(String playerId, long nowMillis) {
        if (playerId == null || playerId.isEmpty()) {
            throw new IllegalArgumentException("playerId is required");
        }
        String sessionId = UUID.randomUUID().toString();
        String nonce = KeyFingerprint.hex(Secrets.randomBytes(16));
        Challenge challenge = new Challenge(
                sessionId,
                nonce,
                keyFingerprint,
                serverKeyPair.getPublic().getEncoded(),
                nowMillis,
                nowMillis + challengeTtlMillis);
        byte[] signature;
        try {
            signature = ChallengeSignature.sign(serverKeyPair.getPrivate(), challenge.signedPayload());
        } catch (CryptoException e) {
            throw new IllegalStateException("server key cannot sign challenge", e);
        }
        sessions.put(new SessionRecord(sessionId, playerId, challenge, nowMillis));
        return new SignedChallenge(challenge, signature);
    }

    public AuthOutcome authenticate(AuthRequest request, long nowMillis) {
        if (!authLimiter.tryAcquire(request.sessionId(), nowMillis)) {
            return AuthOutcome.failed(AuthFailure.RATE_LIMITED);
        }
        SessionRecord record = sessions.get(request.sessionId());
        if (record == null) {
            return AuthOutcome.failed(AuthFailure.UNKNOWN_SESSION);
        }

        synchronized (record) {
            return authenticateLocked(record, request, nowMillis);
        }
    }

    private AuthOutcome authenticateLocked(
            SessionRecord record, AuthRequest request, long nowMillis) {
        if (record.state() == SessionState.AUTHENTICATED) {
            return AuthOutcome.failed(AuthFailure.ALREADY_AUTHENTICATED);
        }
        if (record.state() != SessionState.CHALLENGED) {
            return AuthOutcome.failed(AuthFailure.UNKNOWN_SESSION);
        }
        if (record.failedAttempts() >= MAX_FAILED_ATTEMPTS) {
            record.state(SessionState.REJECTED);
            return AuthOutcome.failed(AuthFailure.RATE_LIMITED);
        }

        Challenge challenge = record.challenge();
        if (!Secrets.constantTimeEquals(
                challenge.nonce().getBytes(UTF_8), request.nonce().getBytes(UTF_8))) {
            record.recordFailure();
            return AuthOutcome.failed(AuthFailure.NONCE_MISMATCH);
        }
        if (challenge.isExpired(nowMillis)) {
            record.state(SessionState.CLOSED);
            return AuthOutcome.failed(AuthFailure.CHALLENGE_EXPIRED);
        }

        byte[] sessionKey;
        try {
            sessionKey = RsaOaep.unwrap(serverKeyPair.getPrivate(), request.wrappedSessionKey());
        } catch (CryptoException e) {
            record.recordFailure();
            return AuthOutcome.failed(AuthFailure.KEY_UNWRAP_FAILED);
        }
        if (sessionKey.length != AeadCipher.KEY_BYTES) {
            Secrets.wipe(sessionKey);
            record.recordFailure();
            return AuthOutcome.failed(AuthFailure.KEY_UNWRAP_FAILED);
        }

        byte[] sessionIdBytes = record.sessionId().getBytes(UTF_8);
        byte[] aad = AssociatedData.build(
                ProtocolVersion.MAJOR,
                sessionIdBytes,
                MessageType.AUTH.id(),
                Direction.CLIENT_TO_SERVER,
                0L);
        byte[] plain;
        try {
            plain = AeadCipher.unseal(
                    sessionKey,
                    sessionIdBytes,
                    Direction.CLIENT_TO_SERVER.wireValue(),
                    0L,
                    aad,
                    request.encryptedReport());
        } catch (CryptoException e) {
            Secrets.wipe(sessionKey);
            record.recordFailure();
            return AuthOutcome.failed(AuthFailure.REPORT_DECRYPT_FAILED);
        }

        ClientReport report;
        try {
            report = ReportPayload.decode(plain);
        } catch (ProtocolException e) {
            Secrets.wipe(plain);
            Secrets.wipe(sessionKey);
            record.recordFailure();
            return AuthOutcome.failed(AuthFailure.REPORT_MALFORMED);
        }
        Secrets.wipe(plain);

        if (!report.nonce().equals(challenge.nonce())
                || !report.playerId().equals(record.playerId())) {
            Secrets.wipe(sessionKey);
            record.recordFailure();
            return AuthOutcome.failed(AuthFailure.NONCE_MISMATCH);
        }

        record.state(SessionState.AUTHENTICATED);
        record.touch(nowMillis);
        return AuthOutcome.success(AuthResult.of(challenge, report, sessionKey));
    }

    public boolean keepalive(String sessionId, long sequence, long nowMillis) {
        SessionRecord record = sessions.get(sessionId);
        if (record == null) {
            return false;
        }
        synchronized (record) {
            if (record.state() != SessionState.AUTHENTICATED) {
                return false;
            }
            if (!record.acceptSequence(sequence)) {
                return false;
            }
            record.touch(nowMillis);
            return true;
        }
    }

    public void shutdown() {
        sessions.clear();
        authLimiter.evictExpired(Long.MAX_VALUE);
    }
}
