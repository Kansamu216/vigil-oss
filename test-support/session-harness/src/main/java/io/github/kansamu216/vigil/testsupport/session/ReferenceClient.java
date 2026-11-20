package io.github.kansamu216.vigil.testsupport.session;

import io.github.kansamu216.vigil.client.agent.ClientHandshakeException;
import io.github.kansamu216.vigil.client.agent.ClientSession;
import io.github.kansamu216.vigil.crypto.AeadCipher;
import io.github.kansamu216.vigil.crypto.CryptoException;
import io.github.kansamu216.vigil.crypto.KeyFingerprint;
import io.github.kansamu216.vigil.crypto.PublicKeys;
import io.github.kansamu216.vigil.crypto.RsaOaep;
import io.github.kansamu216.vigil.model.AuthRequest;
import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.model.ClientReport;
import io.github.kansamu216.vigil.protocol.AssociatedData;
import io.github.kansamu216.vigil.protocol.Direction;
import io.github.kansamu216.vigil.protocol.MessageType;
import io.github.kansamu216.vigil.protocol.ProtocolVersion;
import io.github.kansamu216.vigil.protocol.payload.ReportPayload;
import java.nio.charset.Charset;
import java.security.PublicKey;

/**
 * 测试用参考客户端：薄封装 {@link ClientSession}，并额外断言服务端公钥与
 * 测试持有的密钥一致。产品客户端不使用本类。
 */
public final class ReferenceClient {

    private final PublicKey expectedPublicKey;
    private final String expectedFingerprint;
    private final ClientSession session;

    public ReferenceClient(PublicKey expectedPublicKey, String expectedFingerprint)
            throws ClientHandshakeException {
        this.expectedPublicKey = expectedPublicKey;
        this.expectedFingerprint = expectedFingerprint;
        this.session = new ClientSession(expectedFingerprint);
    }

    public boolean verifyTrustAnchor() {
        return expectedFingerprint.equals(KeyFingerprint.of(expectedPublicKey));
    }

    public void verifyChallenge(Challenge challenge, byte[] signature, long nowMillis)
            throws ClientHandshakeException {
        if (!verifyTrustAnchor()) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.UNTRUSTED_SERVER,
                    "test trust anchor does not match the expected server key");
        }
        try {
            PublicKey presented = PublicKeys.decodeRsa(challenge.serverPublicKey());
            if (!expectedPublicKey.equals(presented)) {
                throw new ClientHandshakeException(
                        ClientHandshakeException.Reason.UNTRUSTED_SERVER,
                        "challenge key differs from the expected server key");
            }
        } catch (CryptoException e) {
            throw new ClientHandshakeException(
                    ClientHandshakeException.Reason.INVALID_KEY, "invalid challenge key", e);
        }
        session.acceptChallenge(challenge, signature, nowMillis);
    }

    /**
     * 测试用应答：使用挑战签发时间作为“当前时间”，避免使用真实时钟导致
     * 固定测试时间戳被判定过期。
     */
    public AuthRequest respond(Challenge challenge, ClientReport report, long sequence)
            throws ClientHandshakeException {
        return session.createAuthRequest(challenge, report, challenge.issuedAtMillis());
    }

    /**
     * 测试专用：不经过信任锚校验，直接用给定公钥构造应答，
     * 用于构造“跨交付/错误公钥”这类服务端必须拒绝的样本。
     */
    public static AuthRequest respondWithKey(PublicKey key, Challenge challenge, ClientReport report)
            throws CryptoException {
        if (!challenge.nonce().equals(report.nonce())) {
            throw new IllegalArgumentException("report nonce does not match challenge");
        }
        Charset utf8 = Charset.forName("UTF-8");
        byte[] sessionKey = AeadCipher.newSessionKey();
        byte[] wrappedKey = RsaOaep.wrap(key, sessionKey);
        byte[] sessionIdBytes = challenge.sessionId().getBytes(utf8);
        byte[] aad = AssociatedData.build(
                ProtocolVersion.MAJOR,
                sessionIdBytes,
                MessageType.AUTH.id(),
                Direction.CLIENT_TO_SERVER,
                0L);
        byte[] plain = ReportPayload.encode(report);
        byte[] encrypted = AeadCipher.seal(
                sessionKey,
                sessionIdBytes,
                Direction.CLIENT_TO_SERVER.wireValue(),
                0L,
                aad,
                plain);
        io.github.kansamu216.vigil.crypto.Secrets.wipe(plain);
        io.github.kansamu216.vigil.crypto.Secrets.wipe(sessionKey);
        return new AuthRequest(challenge.sessionId(), challenge.nonce(), wrappedKey, encrypted);
    }
}
