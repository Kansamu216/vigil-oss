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
import java.util.Objects;
import java.util.UUID;

/**
 * 挑战下发与握手校验。
 *
 * <p>顺序固定：限流 → 会话存在 → 状态 → 失败次数 → nonce → 有效期 → 解会话密钥
 * → 解报告 → 报告与挑战绑定校验。任何一步失败都只增加失败计数，不进入判定。</p>
 *
 * <p>本类不做判定、不落库、不踢人：它只回答“这次握手是否成立”。</p>
 */
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
        // 同一会话 10 秒内最多 5 次应答，表上限 4096 个会话 id。
        this.authLimiter = new RateLimiter(5, 10_000L, 4096);
    }

    public String keyFingerprint() {
        return keyFingerprint;
    }

    public SessionRegistry sessions() {
        return sessions;
    }

    /**
     * 为玩家签发一次性挑战。玩家重复进服会关闭旧会话。
     */
    public SignedChallenge issueChallenge(String playerId, long nowMillis) {
        return issueChallenge(playerId, playerId, nowMillis);
    }

    public SignedChallenge issueChallenge(String playerId, Object connection, long nowMillis) {
        if (playerId == null || playerId.isEmpty()) {
            throw new IllegalArgumentException("playerId is required");
        }
        Objects.requireNonNull(connection, "connection");
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
        sessions.put(new SessionRecord(sessionId, playerId, connection, challenge, nowMillis));
        return new SignedChallenge(challenge, signature);
    }

    public AuthOutcome authenticate(String playerId, AuthRequest request, long nowMillis) {
        return authenticate(playerId, playerId, request, nowMillis);
    }

    public AuthOutcome authenticate(String playerId, Object connection, AuthRequest request, long nowMillis) {
        SessionRecord record = sessions.get(request.sessionId());
        if (record == null) {
            return AuthOutcome.failed(AuthFailure.UNKNOWN_SESSION);
        }
        if (!record.belongsTo(playerId, connection)) {
            return AuthOutcome.failed(AuthFailure.CONNECTION_MISMATCH);
        }
        if (!authLimiter.tryAcquire(record.sessionId(), nowMillis)) {
            return AuthOutcome.failed(AuthFailure.RATE_LIMITED);
        }
        // 同一时刻只有一个线程能推进某个会话的状态与序号；其他会话仍可并行。
        // 并发重放同一 AUTH 帧时，只有第一个能通过状态检查。
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
        byte[] plain = null;
        boolean authenticated = false;
        try {
            plain = AeadCipher.unseal(sessionKey, sessionIdBytes, Direction.CLIENT_TO_SERVER.wireValue(),
                    0L, aad, request.encryptedReport());
            ClientReport report = ReportPayload.decode(plain);
            if (!ProtocolVersion.isSupported(report.protocolVersion())) {
                record.recordFailure();
                return AuthOutcome.failed(AuthFailure.REPORT_MALFORMED);
            }
            if (!report.nonce().equals(challenge.nonce()) || !report.playerId().equals(record.playerId())) {
                record.recordFailure();
                return AuthOutcome.failed(AuthFailure.NONCE_MISMATCH);
            }
            AuthOutcome outcome = AuthOutcome.success(AuthResult.of(challenge, report, sessionKey));
            record.state(SessionState.AUTHENTICATED);
            record.touch(nowMillis);
            authenticated = true;
            return outcome;
        } catch (CryptoException e) {
            record.recordFailure();
            return AuthOutcome.failed(AuthFailure.REPORT_DECRYPT_FAILED);
        } catch (ProtocolException e) {
            record.recordFailure();
            return AuthOutcome.failed(AuthFailure.REPORT_MALFORMED);
        } finally {
            Secrets.wipe(plain);
            if (!authenticated) {
                Secrets.wipe(sessionKey);
            }
        }
    }

    public void closeConnection(String playerId, Object connection) {
        sessions.closeConnection(playerId, connection);
    }

    /** 保活：只刷新活动时间，序号必须单调，复用序号视为重放。 */
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

    /** 关服清理：释放会话与限流表。 */
    public void shutdown() {
        sessions.clear();
        authLimiter.evictExpired(Long.MAX_VALUE);
    }
}
