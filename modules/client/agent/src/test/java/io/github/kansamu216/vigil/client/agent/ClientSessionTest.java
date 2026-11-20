package io.github.kansamu216.vigil.client.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kansamu216.vigil.crypto.ChallengeSignature;
import io.github.kansamu216.vigil.crypto.KeyFingerprint;
import io.github.kansamu216.vigil.crypto.RsaOaep;
import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.protocol.Envelope;
import io.github.kansamu216.vigil.protocol.EnvelopeCodec;
import io.github.kansamu216.vigil.protocol.MessageType;
import io.github.kansamu216.vigil.protocol.payload.AuthPayload;
import java.security.KeyPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 客户端信任锚与挑战校验：错误指纹、坏签名、过期和未验签都必须拒绝。
 */
class ClientSessionTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String NONCE = "0123456789abcdef";
    private static final String SHA =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private KeyPair server;
    private String fingerprint;
    private ClientSession session;

    @BeforeEach
    void setUp() throws Exception {
        server = RsaOaep.generateKeyPair();
        fingerprint = KeyFingerprint.of(server.getPublic());
        session = new ClientSession(fingerprint);
    }

    private Challenge challenge(long expiresAt, String claimedFingerprint) {
        return new Challenge(
                "session-1",
                NONCE,
                claimedFingerprint,
                server.getPublic().getEncoded(),
                NOW,
                expiresAt);
    }

    private ClientReport report(String nonce) {
        return new ClientReport(
                1, nonce, "player-1", "tester",
                java.util.Collections.<io.github.kansamu216.vigil.model.ModEntry>emptyList(),
                java.util.Collections.<io.github.kansamu216.vigil.model.Observation>emptyList(),
                SHA,
                NOW);
    }

    @Test
    void acceptsTrustedChallengeAndBuildsAuthFrame() throws Exception {
        Challenge challenge = challenge(NOW + 20_000L, fingerprint);
        byte[] signature = ChallengeSignature.sign(server.getPrivate(), challenge.signedPayload());
        session.acceptChallenge(challenge, signature, NOW + 1_000L);

        byte[] frame = session.authFrame(challenge, report(NONCE), NOW + 2_000L);
        Envelope envelope = EnvelopeCodec.decode(frame);
        assertEquals(MessageType.AUTH, envelope.type());
        assertEquals("session-1", AuthPayload.decode(envelope.payload()).sessionId());
    }

    @Test
    void rejectsUnknownServerKey() throws Exception {
        KeyPair attacker = RsaOaep.generateKeyPair();
        Challenge challenge = challenge(NOW + 20_000L, fingerprint);
        byte[] signature = ChallengeSignature.sign(attacker.getPrivate(), challenge.signedPayload());

        ClientHandshakeException error = assertThrows(
                ClientHandshakeException.class,
                () -> session.acceptChallenge(challenge, signature, NOW));
        assertEquals(ClientHandshakeException.Reason.SIGNATURE_INVALID, error.reason());
    }

    @Test
    void rejectsWrongPinnedFingerprint() throws Exception {
        ClientSession other = new ClientSession(KeyFingerprint.of(RsaOaep.generateKeyPair().getPublic()));
        Challenge challenge = challenge(NOW + 20_000L, fingerprint);
        byte[] signature = ChallengeSignature.sign(server.getPrivate(), challenge.signedPayload());

        ClientHandshakeException error = assertThrows(
                ClientHandshakeException.class,
                () -> other.acceptChallenge(challenge, signature, NOW));
        assertEquals(ClientHandshakeException.Reason.UNTRUSTED_SERVER, error.reason());
    }

    @Test
    void rejectsExpiredChallenge() throws Exception {
        Challenge challenge = challenge(NOW + 1_000L, fingerprint);
        byte[] signature = ChallengeSignature.sign(server.getPrivate(), challenge.signedPayload());

        ClientHandshakeException error = assertThrows(
                ClientHandshakeException.class,
                () -> session.acceptChallenge(challenge, signature, NOW + 2_000L));
        assertEquals(ClientHandshakeException.Reason.EXPIRED, error.reason());
    }

    @Test
    void refusesToRespondBeforeVerification() throws Exception {
        Challenge challenge = challenge(NOW + 20_000L, fingerprint);
        ClientHandshakeException error = assertThrows(
                ClientHandshakeException.class,
                () -> session.authFrame(challenge, report(NONCE), NOW));
        assertEquals(ClientHandshakeException.Reason.NOT_VERIFIED, error.reason());
    }

    @Test
    void rejectsReportForAnotherChallenge() throws Exception {
        Challenge challenge = challenge(NOW + 20_000L, fingerprint);
        byte[] signature = ChallengeSignature.sign(server.getPrivate(), challenge.signedPayload());
        session.acceptChallenge(challenge, signature, NOW);

        ClientHandshakeException error = assertThrows(
                ClientHandshakeException.class,
                () -> session.authFrame(challenge, report("ffffffffffffffff"), NOW + 1L));
        assertEquals(ClientHandshakeException.Reason.REPORT_MISMATCH, error.reason());
    }

    @Test
    void rejectsMissingTrustAnchor() {
        assertThrows(ClientHandshakeException.class, () -> new ClientSession(""));
        assertThrows(ClientHandshakeException.class, () -> new ClientSession("not-a-fingerprint"));
    }

    @Test
    void challengeDeclaredFingerprintMustMatchKey() throws Exception {
        String otherFingerprint = KeyFingerprint.of(RsaOaep.generateKeyPair().getPublic());
        Challenge challenge = challenge(NOW + 20_000L, otherFingerprint);
        byte[] signature = ChallengeSignature.sign(server.getPrivate(), challenge.signedPayload());

        ClientHandshakeException error = assertThrows(
                ClientHandshakeException.class,
                () -> session.acceptChallenge(challenge, signature, NOW));
        assertEquals(ClientHandshakeException.Reason.UNTRUSTED_SERVER, error.reason());
    }
}
