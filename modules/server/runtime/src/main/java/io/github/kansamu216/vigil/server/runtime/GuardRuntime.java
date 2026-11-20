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
import io.github.kansamu216.vigil.server.decision.ModPolicy;
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

public final class GuardRuntime implements Closeable {

    private static final String EVENT_CHALLENGE = "VIGIL-CHALLENGE";
    private static final String EVENT_AUTH_FAILED = "VIGIL-AUTH-FAILED";
    private static final String EVENT_AUTH_OK = "VIGIL-AUTH-OK";
    private static final String EVENT_MOD_POLICY = "VIGIL-MOD-POLICY";
    private static final String EVENT_VERDICT = "VIGIL-VERDICT";
    private static final String EVENT_BAN = "VIGIL-BAN";
    private static final String EVENT_UNBAN = "VIGIL-UNBAN";

    private final ServerConfig config;
    private final SessionRegistry sessions;
    private final HandshakeService handshake;
    private final DecisionEngine decision;
    private final Database database;
    private final BanRepository bans;
    private final AuditRepository audit;
    private final RuntimeListener listener;
    private final ModPolicy modPolicy;

    private GuardRuntime(
            ServerConfig config,
            SessionRegistry sessions,
            HandshakeService handshake,
            DecisionEngine decision,
            Database database,
            RuntimeListener listener,
            ModPolicy modPolicy) {
        this.config = config;
        this.sessions = sessions;
        this.handshake = handshake;
        this.decision = decision;
        this.database = database;
        this.bans = new BanRepository(database);
        this.audit = new AuditRepository(database);
        this.listener = listener;
        this.modPolicy = modPolicy == null ? ModPolicy.disabled() : modPolicy;
    }

    public static GuardRuntime open(
            ServerConfig config, KeyPair serverKeyPair, RuntimeListener listener) throws IOException {
        return open(config, serverKeyPair, listener, ModPolicy.disabled());
    }

    public static GuardRuntime open(
            ServerConfig config,
            KeyPair serverKeyPair,
            RuntimeListener listener,
            ModPolicy modPolicy) throws IOException {
        try {
            Database database = Database.open(java.nio.file.Paths.get(config.databasePath()));
            SessionRegistry sessions = new SessionRegistry(config.maxSessions());
            HandshakeService handshake =
                    new HandshakeService(serverKeyPair, config.challengeTtlMillis(), sessions);
            DecisionEngine decision = new DecisionEngine(config.decisionPolicy());
            return new GuardRuntime(
                    config, sessions, handshake, decision, database,
                    listener == null ? RuntimeListener.NOOP : listener, modPolicy);
        } catch (java.sql.SQLException e) {
            throw new IOException("failed to open vigil database", e);
        }
    }

    public String publicKeyFingerprint() {
        return handshake.keyFingerprint();
    }

    public int modPolicyRuleCount() {
        return modPolicy.ruleCount();
    }

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
        ModPolicy.Result modResult = modPolicy.evaluate(report.mods());
        Verdict verdict = decision.evaluate(report, modResult.observations());
        appendAudit(
                EVENT_AUTH_OK,
                playerId,
                "mods=" + report.mods().size() + " obs=" + report.observations().size(),
                nowMillis);
        if (!modResult.clean()) {
            appendAudit(EVENT_MOD_POLICY, playerId, modResult.summary(), nowMillis);
        }
        appendAudit(EVENT_VERDICT, playerId, verdict.toString(), nowMillis);
        listener.onVerdict(playerId, verdict);

        if (verdict.action() == VerdictAction.BAN) {
            applyBan(playerId, report.playerName(), verdict.reason(), nowMillis);
        } else if (verdict.requiresAction()) {
            listener.onActionRequired(playerId, verdict.action(), verdict.reason());
        }
        return new AuthEvaluation(outcome, verdict);
    }

    public boolean ban(
            String playerId,
            String playerName,
            String reason,
            long durationMillis,
            long nowMillis) {
        if (playerId == null || playerId.isEmpty() || playerId.length() > 64) {
            throw new IllegalArgumentException("playerId is required and bounded");
        }
        if (durationMillis < 0L) {
            throw new IllegalArgumentException("duration must not be negative");
        }
        String safeReason = reason == null ? "" : reason;
        if (safeReason.length() > 256) {
            safeReason = safeReason.substring(0, 256);
        }
        long expiresAt = 0L;
        if (durationMillis > 0L) {
            expiresAt = nowMillis + durationMillis;
            if (expiresAt < nowMillis) {
                expiresAt = Long.MAX_VALUE;
            }
        }
        try {
            bans.ban(playerId, playerName, safeReason, nowMillis, expiresAt);
            appendAudit(EVENT_BAN, playerId, safeReason, nowMillis);
            listener.onActionRequired(playerId, VerdictAction.BAN, safeReason);
            return true;
        } catch (StorageException e) {
            appendAudit(EVENT_BAN, playerId, "storage-failure", nowMillis);
            listener.onActionRequired(playerId, VerdictAction.ALERT, "ban storage failure");
            return false;
        }
    }

    public int unban(String playerId, long nowMillis) {
        if (playerId == null || playerId.isEmpty() || playerId.length() > 64) {
            throw new IllegalArgumentException("playerId is required and bounded");
        }
        try {
            int changed = bans.unban(playerId);
            appendAudit(EVENT_UNBAN, playerId, "count=" + changed, nowMillis);
            return changed;
        } catch (StorageException e) {
            appendAudit(EVENT_UNBAN, playerId, "storage-failure", nowMillis);
            listener.onActionRequired(playerId, VerdictAction.ALERT, "unban storage failure");
            return 0;
        }
    }

    private void applyBan(String playerId, String playerName, String reason, long nowMillis) {
        ban(playerId, playerName, reason, 0L, nowMillis);
    }

    public void onPlayerQuit(String playerId, long nowMillis) {
        appendAudit("VIGIL-QUIT", playerId, null, nowMillis);
    }

    public void tick(long nowMillis) {
        sessions.closeIdle(nowMillis, config.sessionIdleTimeoutMillis());
        try {
            bans.purgeExpired(nowMillis);
            audit.purgeOlderThan(nowMillis - config.auditRetentionMillis());
        } catch (StorageException e) {

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

            appendAudit("VIGIL-STORAGE-DEGRADED", playerId, "join-check-skipped", nowMillis);
            return Optional.empty();
        }
    }

    private void appendAudit(String eventCode, String playerId, String detail, long nowMillis) {
        try {
            audit.append(eventCode, playerId, detail, nowMillis);
        } catch (StorageException e) {

            listener.onActionRequired(playerId, VerdictAction.ALERT, "audit storage failure");
        }
    }

    @Override
    public void close() throws IOException {
        handshake.shutdown();
        database.close();
    }
}
