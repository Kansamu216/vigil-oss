package io.github.kansamu216.vigil.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kansamu216.vigil.crypto.RsaOaep;
import io.github.kansamu216.vigil.model.AuthRequest;
import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.Observation;
import io.github.kansamu216.vigil.model.Verdict;
import io.github.kansamu216.vigil.model.VerdictAction;
import io.github.kansamu216.vigil.protocol.EnvelopeCodec;
import io.github.kansamu216.vigil.protocol.MessageType;
import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.payload.ChallengePayload;
import io.github.kansamu216.vigil.server.decision.DecisionPolicy;
import io.github.kansamu216.vigil.testsupport.session.ReferenceClient;
import io.github.kansamu216.vigil.testsupport.session.Reports;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;

class GuardRuntimeTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String PLAYER = "player-uuid-1";

    private static ServerConfig configFor(Path file, DecisionPolicy policy) {
        return new ServerConfig(20_000L, 120_000L, 30L * 24 * 3600 * 1000, 128, file.toString(), policy);
    }

    @Test
    void cleanClientProducesNoAction() throws Exception {
        Path file = Files.createTempDirectory("vigil-rt").resolve("vigil.db");
        KeyPair keys = RsaOaep.generateKeyPair();
        RecordingListener listener = new RecordingListener();

        try (GuardRuntime runtime = GuardRuntime.open(configFor(file, DecisionPolicy.defaults()), keys, listener)) {
            JoinOutcome join = runtime.onPlayerJoin(PLAYER, NOW);
            assertTrue(join.allowed());

            Challenge challenge = challengeOf(join.challengeFrame());
            ReferenceClient client = new ReferenceClient(keys.getPublic(), runtime.publicKeyFingerprint());
            verifyClient(client, challenge, join.challengeFrame());
            AuthRequest request = client.respond(challenge, Reports.clean(challenge.nonce(), PLAYER), 0L);

            AuthEvaluation evaluation = runtime.onAuthFrame(
                    PLAYER,
                    EnvelopeCodec.encode(io.github.kansamu216.vigil.protocol.Envelope.of(
                            MessageType.AUTH,
                            io.github.kansamu216.vigil.protocol.Direction.CLIENT_TO_SERVER,
                            0L,
                            io.github.kansamu216.vigil.protocol.payload.AuthPayload.encode(request))),
                    NOW + 500L);

            assertTrue(evaluation.handshakeSucceeded());
            Verdict verdict = evaluation.verdict();
            assertNotNull(verdict);
            assertEquals(VerdictAction.NONE, verdict.action());
            assertEquals(0, listener.actions.size());
        }
    }

    @Test
    void statisticsOnlyRaisesAlert() throws Exception {
        Path file = Files.createTempDirectory("vigil-rt").resolve("vigil.db");
        KeyPair keys = RsaOaep.generateKeyPair();
        RecordingListener listener = new RecordingListener();

        try (GuardRuntime runtime = GuardRuntime.open(configFor(file, DecisionPolicy.defaults()), keys, listener)) {
            JoinOutcome join = runtime.onPlayerJoin(PLAYER, NOW);
            Challenge challenge = challengeOf(join.challengeFrame());
            ReferenceClient client = new ReferenceClient(keys.getPublic(), runtime.publicKeyFingerprint());
            ClientReport report = Reports.withObservations(
                    challenge.nonce(), PLAYER, new Observation("STATS_CPS_HIGH", "cps=20", 90));
            verifyClient(client, challenge, join.challengeFrame());
            AuthRequest request = client.respond(challenge, report, 0L);

            AuthEvaluation evaluation = runtime.onAuthFrame(PLAYER, authFrame(request), NOW + 100L);
            assertEquals(VerdictAction.ALERT, evaluation.verdict().action());
            assertEquals(1, listener.actions.size());
            assertEquals(VerdictAction.ALERT, listener.actions.get(0));
        }
    }

    @Test
    void clientSuppliedIntegrityClaimNeverBans() throws Exception {
        Path file = Files.createTempDirectory("vigil-rt").resolve("vigil.db");
        KeyPair keys = RsaOaep.generateKeyPair();
        RecordingListener listener = new RecordingListener();
        DecisionPolicy aggressive = new DecisionPolicy(10, 40, 60, 90, true, true);

        try (GuardRuntime runtime = GuardRuntime.open(configFor(file, aggressive), keys, listener)) {
            JoinOutcome join = runtime.onPlayerJoin(PLAYER, NOW);
            Challenge challenge = challengeOf(join.challengeFrame());
            ReferenceClient client = new ReferenceClient(keys.getPublic(), runtime.publicKeyFingerprint());
            ClientReport report = Reports.withObservations(
                    challenge.nonce(), PLAYER,
                    new Observation("INTEGRITY_HASH_MISMATCH", "mod foo", 100));
            verifyClient(client, challenge, join.challengeFrame());
            AuthRequest request = client.respond(challenge, report, 0L);

            AuthEvaluation evaluation = runtime.onAuthFrame(PLAYER, authFrame(request), NOW + 100L);
            assertEquals(VerdictAction.ALERT, evaluation.verdict().action());
            assertEquals(0, runtime.activeBanCount(NOW + 200L));

            JoinOutcome second = runtime.onPlayerJoin(PLAYER, NOW + 300L);
            assertTrue(second.allowed());
        }
    }

    @Test
    void malformedFrameIsAuditedButNeverJudged() throws Exception {
        Path file = Files.createTempDirectory("vigil-rt").resolve("vigil.db");
        KeyPair keys = RsaOaep.generateKeyPair();
        RecordingListener listener = new RecordingListener();

        try (GuardRuntime runtime = GuardRuntime.open(configFor(file, DecisionPolicy.defaults()), keys, listener)) {
            runtime.onPlayerJoin(PLAYER, NOW);
            AuthEvaluation evaluation = runtime.onAuthFrame(PLAYER, new byte[] {1, 2, 3}, NOW + 10L);
            assertFalse(evaluation.handshakeSucceeded());
            assertEquals(null, evaluation.verdict());
            assertEquals(0, listener.actions.size());
        }
    }

    private static void verifyClient(ReferenceClient client, Challenge challenge, byte[] frame)
            throws Exception {
        io.github.kansamu216.vigil.protocol.Envelope envelope = EnvelopeCodec.decode(frame);
        io.github.kansamu216.vigil.protocol.payload.ChallengePayload.Decoded decoded =
                io.github.kansamu216.vigil.protocol.payload.ChallengePayload.decode(envelope.payload());
        client.verifyChallenge(challenge, decoded.signature(), NOW);
    }

    private static byte[] authFrame(AuthRequest request) {
        try {
            return EnvelopeCodec.encode(io.github.kansamu216.vigil.protocol.Envelope.of(
                    MessageType.AUTH,
                    io.github.kansamu216.vigil.protocol.Direction.CLIENT_TO_SERVER,
                    0L,
                    io.github.kansamu216.vigil.protocol.payload.AuthPayload.encode(request)));
        } catch (RuntimeException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Challenge challengeOf(byte[] frame) throws ProtocolException {
        io.github.kansamu216.vigil.protocol.Envelope envelope = EnvelopeCodec.decode(frame);
        return ChallengePayload.decode(envelope.payload()).challenge();
    }

    private static final class RecordingListener implements RuntimeListener {
        private final java.util.List<VerdictAction> actions = new java.util.ArrayList<VerdictAction>();

        @Override
        public void onActionRequired(String playerId, VerdictAction action, String reason) {
            actions.add(action);
        }
    }
}
