package io.github.kansamu216.vigil.client.agent;

import io.github.kansamu216.vigil.crypto.AeadCipher;
import io.github.kansamu216.vigil.crypto.ChallengeSignature;
import io.github.kansamu216.vigil.crypto.CryptoException;
import io.github.kansamu216.vigil.crypto.KeyFingerprint;
import io.github.kansamu216.vigil.crypto.PublicKeys;
import io.github.kansamu216.vigil.crypto.RsaOaep;
import io.github.kansamu216.vigil.crypto.Secrets;
import io.github.kansamu216.vigil.model.AuthRequest;
import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.protocol.AssociatedData;
import io.github.kansamu216.vigil.protocol.Direction;
import io.github.kansamu216.vigil.protocol.Envelope;
import io.github.kansamu216.vigil.protocol.EnvelopeCodec;
import io.github.kansamu216.vigil.protocol.MessageType;
import io.github.kansamu216.vigil.protocol.ProtocolVersion;
import io.github.kansamu216.vigil.protocol.payload.AuthPayload;
import io.github.kansamu216.vigil.protocol.payload.ReportPayload;
import java.nio.charset.Charset;
import java.security.PublicKey;

/**
 * 客户端侧会话状态机。
 *
 * <p>按固定顺序校验挑战并建立会话：信任锚、签名、有效期、应答。</p>
 */
public final class ClientSession {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final String pinnedFingerprint;

    private PublicKey serverPublicKey;
    private String verifiedSessionId;
    private String verifiedNonce;
    private long verifiedExpiresAt;

    public ClientSession(String pinnedFingerprint) throws ClientHandshakeException {
        if (pinnedFingerprint == null || pinnedFingerprint.length() != 64) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.NO_TRUST_ANCHOR,
                    "client trust anchor must be a 64 character fingerprint");
        }
        this.pinnedFingerprint = pinnedFingerprint.toLowerCase(java.util.Locale.ROOT);
    }

    public String pinnedFingerprint() {
        return pinnedFingerprint;
    }

    /**
     * 校验挑战并建立会话；只有成功后才能构造应答。
     */
    public void acceptChallenge(Challenge challenge, byte[] signature, long nowMillis)
            throws ClientHandshakeException {
        if (challenge.isExpired(nowMillis)) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.EXPIRED, "server challenge expired");
        }
        PublicKey key;
        try {
            key = PublicKeys.decodeRsa(challenge.serverPublicKey());
        } catch (CryptoException e) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.INVALID_KEY, "invalid server public key", e);
        }
        String actual = KeyFingerprint.of(key);
        if (!pinnedFingerprint.equals(actual)
                || !pinnedFingerprint.equals(challenge.serverKeyFingerprint())) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.UNTRUSTED_SERVER,
                    "server fingerprint does not match the pinned trust anchor");
        }
        try {
            ChallengeSignature.verify(key, challenge.signedPayload(), signature);
        } catch (CryptoException e) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.SIGNATURE_INVALID, "challenge signature invalid", e);
        }
        this.serverPublicKey = key;
        this.verifiedSessionId = challenge.sessionId();
        this.verifiedNonce = challenge.nonce();
        this.verifiedExpiresAt = challenge.expiresAtMillis();
    }

    /**
     * 构造认证应答。调用方负责把它编码进 AUTH 信封。
     */
    public AuthRequest createAuthRequest(Challenge challenge, ClientReport report, long nowMillis)
            throws ClientHandshakeException {
        if (serverPublicKey == null
                || !challenge.sessionId().equals(verifiedSessionId)
                || !challenge.nonce().equals(verifiedNonce)) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.NOT_VERIFIED,
                    "challenge has not been verified in this session");
        }
        if (nowMillis >= challenge.expiresAtMillis()) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.EXPIRED, "server challenge expired");
        }
        if (!report.nonce().equals(challenge.nonce())) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.REPORT_MISMATCH,
                    "report does not belong to this challenge");
        }

        byte[] sessionKey = AeadCipher.newSessionKey();
        byte[] wrappedKey;
        try {
            wrappedKey = RsaOaep.wrap(serverPublicKey, sessionKey);
        } catch (CryptoException e) {
            Secrets.wipe(sessionKey);
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.INVALID_KEY, "failed to wrap session key", e);
        }

        byte[] sessionIdBytes = challenge.sessionId().getBytes(UTF_8);
        byte[] aad = AssociatedData.build(
                ProtocolVersion.MAJOR,
                sessionIdBytes,
                MessageType.AUTH.id(),
                Direction.CLIENT_TO_SERVER,
                0L);
        byte[] plain = ReportPayload.encode(report);
        byte[] encrypted;
        try {
            encrypted = AeadCipher.seal(
                    sessionKey,
                    sessionIdBytes,
                    Direction.CLIENT_TO_SERVER.wireValue(),
                    0L,
                    aad,
                    plain);
        } catch (CryptoException e) {
            Secrets.wipe(sessionKey);
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.INVALID_KEY, "failed to seal report", e);
        } finally {
            Secrets.wipe(plain);
            Secrets.wipe(sessionKey);
        }

        return new AuthRequest(challenge.sessionId(), challenge.nonce(), wrappedKey, encrypted);
    }

    /**
     * 构造 AUTH 帧（含信封），供平台层直接发送。
     */
    public byte[] authFrame(Challenge challenge, ClientReport report, long nowMillis)
            throws ClientHandshakeException {
        AuthRequest request = createAuthRequest(challenge, report, nowMillis);
        return EnvelopeCodec.encode(Envelope.of(
                MessageType.AUTH,
                Direction.CLIENT_TO_SERVER,
                0L,
                AuthPayload.encode(request)));
    }

    public long verifiedExpiresAt() {
        return verifiedExpiresAt;
    }
}
