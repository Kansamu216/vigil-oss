package io.github.kansamu216.vigil.server.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kansamu216.vigil.crypto.RsaOaep;
import io.github.kansamu216.vigil.model.AuthRequest;
import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.model.Observation;
import io.github.kansamu216.vigil.testsupport.session.ReferenceClient;
import io.github.kansamu216.vigil.testsupport.session.Reports;
import java.security.KeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 握手状态机的正常路径与全部失败分支。
 *
 * <p>失败分支必须能区分原因：这些原因会进入审计与指标，
 * 但绝不能被当成作弊证据。</p>
 */
class HandshakeServiceTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final long TTL = 30_000L;
    private static final String PLAYER = "player-uuid-1";

    private KeyPair serverKeyPair;
    private HandshakeService service;
    private ReferenceClient client;

    @BeforeEach
    void setUp() throws Exception {
        serverKeyPair = RsaOaep.generateKeyPair();
        service = new HandshakeService(serverKeyPair, TTL, new SessionRegistry(64));
        client = new ReferenceClient(serverKeyPair.getPublic(), service.keyFingerprint());
    }

    private SignedChallenge issue(long now) {
        return service.issueChallenge(PLAYER, now);
    }

    @Test
    void completesHandshake() throws Exception {
        SignedChallenge signed = issue(NOW);
        Challenge challenge = signed.challenge();
        client.verifyChallenge(challenge, signed.signature(), NOW);

        ClientReport report = Reports.clean(challenge.nonce(), PLAYER);
        client.verifyChallenge(challenge, signed.signature(), NOW);
        AuthRequest request = client.respond(challenge, report, 0L);

        AuthOutcome outcome = service.authenticate(request, NOW + 1_000L);
        assertTrue(outcome.isSuccess());
        assertEquals(PLAYER, outcome.result().playerId());
        assertEquals(1, outcome.result().report().mods().size());
        assertEquals(SessionState.AUTHENTICATED, service.sessions().stateOf(challenge.sessionId()));
        outcome.result().wipe();
    }

    @Test
    void rejectsWrongNonce() throws Exception {
        SignedChallenge signed = issue(NOW);
        Challenge challenge = signed.challenge();
        client.verifyChallenge(challenge, signed.signature(), NOW);
        AuthRequest valid = client.respond(challenge, Reports.clean(challenge.nonce(), PLAYER), 0L);
        AuthRequest forged = new AuthRequest(
                valid.sessionId(), "0000000000000000", valid.wrappedSessionKey(), valid.encryptedReport());

        AuthOutcome outcome = service.authenticate(forged, NOW + 10L);
        assertFalse(outcome.isSuccess());
        assertEquals(AuthFailure.NONCE_MISMATCH, outcome.failure());
    }

    @Test
    void rejectsExpiredChallenge() throws Exception {
        SignedChallenge signed = issue(NOW);
        Challenge challenge = signed.challenge();
        client.verifyChallenge(challenge, signed.signature(), NOW);
        AuthRequest request = client.respond(challenge, Reports.clean(challenge.nonce(), PLAYER), 0L);

        AuthOutcome outcome = service.authenticate(request, NOW + TTL + 1L);
        assertFalse(outcome.isSuccess());
        assertEquals(AuthFailure.CHALLENGE_EXPIRED, outcome.failure());
        assertEquals(SessionState.CLOSED, service.sessions().stateOf(challenge.sessionId()));
    }

    @Test
    void rejectsRepeatedAuth() throws Exception {
        SignedChallenge signed = issue(NOW);
        Challenge challenge = signed.challenge();
        client.verifyChallenge(challenge, signed.signature(), NOW);
        AuthRequest request = client.respond(challenge, Reports.clean(challenge.nonce(), PLAYER), 0L);

        assertTrue(service.authenticate(request, NOW + 1L).isSuccess());
        AuthOutcome second = service.authenticate(request, NOW + 2L);
        assertFalse(second.isSuccess());
        assertEquals(AuthFailure.ALREADY_AUTHENTICATED, second.failure());
    }

    @Test
    void rejectsRequestFromAnotherDelivery() throws Exception {
        KeyPair otherDelivery = RsaOaep.generateKeyPair();
        SignedChallenge signed = issue(NOW);
        Challenge challenge = signed.challenge();
        AuthRequest request = ReferenceClient.respondWithKey(
                otherDelivery.getPublic(), challenge, Reports.clean(challenge.nonce(), PLAYER));

        AuthOutcome outcome = service.authenticate(request, NOW + 1L);
        assertFalse(outcome.isSuccess());
        assertEquals(AuthFailure.KEY_UNWRAP_FAILED, outcome.failure());
    }

    @Test
    void rejectsUnknownSession() throws Exception {
        SignedChallenge signed = issue(NOW);
        Challenge challenge = signed.challenge();
        client.verifyChallenge(challenge, signed.signature(), NOW);
        AuthRequest request = client.respond(challenge, Reports.clean(challenge.nonce(), PLAYER), 0L);
        AuthRequest unknown = new AuthRequest(
                "no-such-session", request.nonce(), request.wrappedSessionKey(), request.encryptedReport());

        AuthOutcome outcome = service.authenticate(unknown, NOW + 1L);
        assertFalse(outcome.isSuccess());
        assertEquals(AuthFailure.UNKNOWN_SESSION, outcome.failure());
    }

    @Test
    void rateLimitsRepeatedFailures() throws Exception {
        SignedChallenge signed = issue(NOW);
        Challenge challenge = signed.challenge();
        client.verifyChallenge(challenge, signed.signature(), NOW);
        AuthRequest valid = client.respond(challenge, Reports.clean(challenge.nonce(), PLAYER), 0L);
        AuthRequest forged = new AuthRequest(
                valid.sessionId(), "0000000000000000", valid.wrappedSessionKey(), valid.encryptedReport());

        for (int i = 0; i < 5; i++) {
            assertFalse(service.authenticate(forged, NOW + i).isSuccess());
        }
        AuthOutcome limited = service.authenticate(forged, NOW + 6L);
        assertEquals(AuthFailure.RATE_LIMITED, limited.failure());
    }

    @Test
    void keepsMonotonicSequenceOnKeepalive() throws Exception {
        SignedChallenge signed = issue(NOW);
        Challenge challenge = signed.challenge();
        client.verifyChallenge(challenge, signed.signature(), NOW);
        AuthRequest request = client.respond(challenge, Reports.clean(challenge.nonce(), PLAYER), 0L);
        service.authenticate(request, NOW + 1L);

        assertTrue(service.keepalive(challenge.sessionId(), 1L, NOW + 2L));
        assertFalse(service.keepalive(challenge.sessionId(), 1L, NOW + 3L));
        assertFalse(service.keepalive(challenge.sessionId(), 0L, NOW + 4L));
    }

    @Test
    void reportsObservationWithoutJudging() throws Exception {
        SignedChallenge signed = issue(NOW);
        Challenge challenge = signed.challenge();
        ClientReport report = Reports.withObservations(
                challenge.nonce(), PLAYER, new Observation("INTEGRITY_HASH_MISMATCH", "mod foo", 60));
        client.verifyChallenge(challenge, signed.signature(), NOW);
        AuthRequest request = client.respond(challenge, report, 0L);

        AuthOutcome outcome = service.authenticate(request, NOW + 1L);
        assertTrue(outcome.isSuccess());
        // 握手层不产生结论：报告里只有事实。
        assertEquals(1, outcome.result().report().observations().size());
        outcome.result().wipe();
    }
}
