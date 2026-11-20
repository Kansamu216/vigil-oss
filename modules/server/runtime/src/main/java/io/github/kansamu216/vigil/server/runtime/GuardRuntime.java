package io.github.kansamu216.vigil.server.runtime;

import io.github.kansamu216.vigil.model.AuthRequest;
import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.Verdict;
import io.github.kansamu216.vigil.model.VerdictAction;
import io.github.kansamu216.vigil.protocol.Direction;
import io.github.kansamu216.vigil.protocol.Envelope;
import io.github.kansamu216.vigil.protocol.EnvelopeCodec;
import io.github.kansamu216.vigil.protocol.MessageType;
import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.payload.AuthPayload;
import io.github.kansamu216.vigil.protocol.payload.ChallengePayload;
import io.github.kansamu216.vigil.server.decision.DecisionEngine;
import io.github.kansamu216.vigil.server.session.AuthFailure;
import io.github.kansamu216.vigil.server.session.AuthOutcome;
import io.github.kansamu216.vigil.server.session.HandshakeService;
import io.github.kansamu216.vigil.server.session.SessionRegistry;
import io.github.kansamu216.vigil.server.session.SignedChallenge;
import io.github.kansamu216.vigil.server.storage.AuditRepository;
import io.github.kansamu216.vigil.server.storage.BanRecord;
import io.github.kansamu216.vigil.server.storage.BanRepository;
import io.github.kansamu216.vigil.server.storage.Database;
import io.github.kansamu216.vigil.server.storage.StorageException;
import java.io.Closeable;
import java.io.IOException;
import java.security.KeyPair;
import java.util.Optional;

/**
 * 服务端运行时装配。
 *
 * <p>链路：进服检查 → 挑战下发 → 应答校验 → 判定 → 审计 → 处置回调。
 * 平台适配层只负责字节收发与动作执行。</p>
 */
public final class GuardRuntime implements Closeable {

    private static final String EVENT_CHALLENGE = "VIGIL-CHALLENGE";
    private static final String EVENT_AUTH_FAILED = "VIGIL-AUTH-FAILED";
    private static final String EVENT_AUTH_OK = "VIGIL-AUTH-OK";
    private static final String EVENT_VERDICT = "VIGIL-VERDICT";
    private static final String EVENT_BAN = "VIGIL-BAN";

    private final ServerConfig config;
    private final SessionRegistry sessions;
    private final HandshakeService handshake;
    private final DecisionEngine decision;
    private final Database database;
    private final BanRepository bans;
    private final AuditRepository audit;
    private final RuntimeListener listener;

    private GuardRuntime(
            ServerConfig config,
            SessionRegistry sessions,
            HandshakeService handshake,
            DecisionEngine decision,
            Database database,
            RuntimeListener listener) {
        this.config = config;
        this.sessions = sessions;
        this.handshake = handshake;
        this.decision = decision;
        this.database = database;
        this.bans = new BanRepository(database);
        this.audit = new AuditRepository(database);
        this.listener = listener;
    }

    public static GuardRuntime open(
            ServerConfig config, KeyPair serverKeyPair, RuntimeListener listener) throws IOException {
        try {
            Database database = Database.open(java.nio.file.Paths.get(config.databasePath()));
            SessionRegistry sessions = new SessionRegistry(config.maxSessions());
            HandshakeService handshake =
                    new HandshakeService(serverKeyPair, config.challengeTtlMillis(), sessions);
            DecisionEngine decision = new DecisionEngine(config.decisionPolicy());
            return new GuardRuntime(
                    config, sessions, handshake, decision, database,
                    listener == null ? RuntimeListener.NOOP : listener);
        } catch (java.sql.SQLException e) {
            throw new IOException("failed to open vigil database", e);
        }
    }

    public String publicKeyFingerprint() {
        return handshake.keyFingerprint();
    }

    /**
     * 玩家进服。已在封禁中的玩家直接拒绝。
     */
    public JoinOutcome onPlayerJoin(String playerId, long nowMillis) {
        Optional<BanRecord> active = safeActiveBan(playerId, nowMillis);
        if (active.isPresent()) {
            BanRecord record = active.get();
            return JoinOutcome.denied("banned: " + record.reason());
        }
        SignedChallenge signed = handshake.issueChallenge(playerId, nowMillis);
        byte[] payload = ChallengePayload.encode(signed.challenge(), signed.signature());
        byte[] frame = EnvelopeCodec.encode(
                Envelope.of(MessageType.CHALLENGE, Direction.SERVER_TO_CLIENT, 0L, payload));
        appendAudit(EVENT_CHALLENGE, playerId, "session=" + signed.challenge().sessionId(), nowMillis);
        listener.onChallengeIssued(playerId, signed.challenge().sessionId());
        return JoinOutcome.challenge(signed.challenge().sessionId(), frame);
    }

    /**
     * 处理一帧认证应答。失败只记审计，不产生处置。
     */
    public AuthEvaluation onAuthFrame(String playerId, byte[] frame, long nowMillis) {
        Envelope envelope;
        try {
            envelope = EnvelopeCodec.decode(frame);
        } catch (ProtocolException e) {
            appendAudit(EVENT_AUTH_FAILED, playerId, "protocol=" + e.code(), nowMillis);
            listener.onHandshakeFailed(playerId, AuthFailure.REPORT_MALFORMED);
            return new AuthEvaluation(AuthOutcome.failed(AuthFailure.REPORT_MALFORMED), null);
        }
        if (envelope.type() != MessageType.AUTH
                || envelope.direction() != Direction.CLIENT_TO_SERVER) {
            appendAudit(EVENT_AUTH_FAILED, playerId, "unexpected=" + envelope.type(), nowMillis);
            listener.onHandshakeFailed(playerId, AuthFailure.REPORT_MALFORMED);
            return new AuthEvaluation(AuthOutcome.failed(AuthFailure.REPORT_MALFORMED), null);
        }

        AuthRequest request;
        try {
            request = AuthPayload.decode(envelope.payload());
        } catch (ProtocolException e) {
            appendAudit(EVENT_AUTH_FAILED, playerId, "auth-payload=" + e.code(), nowMillis);
            listener.onHandshakeFailed(playerId, AuthFailure.REPORT_MALFORMED);
            return new AuthEvaluation(AuthOutcome.failed(AuthFailure.REPORT_MALFORMED), null);
        }

        AuthOutcome outcome = handshake.authenticate(request, nowMillis);
        if (!outcome.isSuccess()) {
            appendAudit(EVENT_AUTH_FAILED, playerId, "reason=" + outcome.failure(), nowMillis);
            listener.onHandshakeFailed(playerId, outcome.failure());
            return new AuthEvaluation(outcome, null);
        }

        ClientReport report = outcome.result().report();
        Verdict verdict = decision.evaluate(report);
        appendAudit(
                EVENT_AUTH_OK,
                playerId,
                "mods=" + report.mods().size() + " obs=" + report.observations().size(),
                nowMillis);
        appendAudit(EVENT_VERDICT, playerId, verdict.toString(), nowMillis);
        listener.onVerdict(playerId, verdict);

        if (verdict.action() == VerdictAction.BAN) {
            applyBan(playerId, report.playerName(), verdict.reason(), nowMillis);
        } else if (verdict.requiresAction()) {
            listener.onActionRequired(playerId, verdict.action(), verdict.reason());
        }
        return new AuthEvaluation(outcome, verdict);
    }

    private void applyBan(String playerId, String playerName, String reason, long nowMillis) {
        try {
            bans.ban(playerId, playerName, reason, nowMillis, 0L);
            appendAudit(EVENT_BAN, playerId, reason, nowMillis);
            listener.onActionRequired(playerId, VerdictAction.BAN, reason);
        } catch (StorageException e) {
            // 存储故障进入降级，不产生封禁。
            appendAudit(EVENT_BAN, playerId, "storage-failure", nowMillis);
            listener.onActionRequired(playerId, VerdictAction.ALERT, "ban storage failure");
        }
    }

    public void onPlayerQuit(String playerId, long nowMillis) {
        appendAudit("VIGIL-QUIT", playerId, null, nowMillis);
    }

    /** 周期维护：关闭超时会话、清理过期封禁与审计。 */
    public void tick(long nowMillis) {
        sessions.closeIdle(nowMillis, config.sessionIdleTimeoutMillis());
        try {
            bans.purgeExpired(nowMillis);
            audit.purgeOlderThan(nowMillis - config.auditRetentionMillis());
        } catch (StorageException e) {
            // 维护失败只影响清理。
            appendAudit("VIGIL-MAINTENANCE-FAILED", null, e.getMessage(), nowMillis);
        }
    }

    public Optional<BanRecord> activeBan(String playerId, long nowMillis) {
        return bans.activeBan(playerId, nowMillis);
    }

    public int activeBanCount(long nowMillis) {
        return bans.activeCount(nowMillis);
    }

    private Optional<BanRecord> safeActiveBan(String playerId, long nowMillis) {
        try {
            return bans.activeBan(playerId, nowMillis);
        } catch (StorageException e) {
            // 存储不可用时放行进服，并记录审计。
            appendAudit("VIGIL-STORAGE-DEGRADED", playerId, "join-check-skipped", nowMillis);
            return Optional.empty();
        }
    }

    private void appendAudit(String eventCode, String playerId, String detail, long nowMillis) {
        try {
            audit.append(eventCode, playerId, detail, nowMillis);
        } catch (StorageException e) {
            // 审计写失败不中断判定链路。
            listener.onActionRequired(playerId, VerdictAction.ALERT, "audit storage failure");
        }
    }

    @Override
    public void close() throws IOException {
        handshake.shutdown();
        database.close();
    }
}
