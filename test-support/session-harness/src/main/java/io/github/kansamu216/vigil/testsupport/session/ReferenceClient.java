package io.github.kansamu216.vigil.testsupport.session;

import io.github.kansamu216.vigil.crypto.AeadCipher;
import io.github.kansamu216.vigil.crypto.ChallengeSignature;
import io.github.kansamu216.vigil.crypto.CryptoException;
import io.github.kansamu216.vigil.crypto.KeyFingerprint;
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
 * 参考客户端实现，只覆盖客户端在握手中的必要动作：
 *
 * <ol>
 *   <li>用预置指纹校验服务器公钥，校验失败直接中止；</li>
 *   <li>校验挑战签名；</li>
 *   <li>生成一次性会话密钥，用服务器公钥 OAEP 包裹；</li>
 *   <li>把报告按 AUTH 帧的 AAD/序号加密。</li>
 * </ol>
 *
 * <p>它不是产品客户端：没有传感器、没有采集、没有 UI。真实 Agent 复用同样的
 * 协议与密码学调用，但不得绕过这里的顺序。</p>
 */
public final class ReferenceClient {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private final PublicKey serverPublicKey;
    private final String expectedFingerprint;

    public ReferenceClient(PublicKey serverPublicKey, String expectedFingerprint) {
        this.serverPublicKey = serverPublicKey;
        this.expectedFingerprint = expectedFingerprint;
    }

    /** 客户端侧信任锚校验：公钥指纹必须与预置值一致。 */
    public boolean verifyTrustAnchor() {
        return expectedFingerprint.equals(KeyFingerprint.of(serverPublicKey));
    }

    public void verifyChallenge(Challenge challenge, byte[] signature) throws CryptoException {
        if (!verifyTrustAnchor()) {
            throw new CryptoException(
                    CryptoException.Reason.SIGNATURE_INVALID, "trust anchor mismatch");
        }
        if (!expectedFingerprint.equals(challenge.serverKeyFingerprint())) {
            throw new CryptoException(
                    CryptoException.Reason.SIGNATURE_INVALID, "challenge fingerprint mismatch");
        }
        ChallengeSignature.verify(serverPublicKey, challenge.signedPayload(), signature);
    }

    /**
     * 生成认证应答。报告必须使用本次挑战的 nonce，否则服务端会拒绝。
     */
    public AuthRequest respond(Challenge challenge, ClientReport report, long sequence)
            throws CryptoException {
        if (!challenge.nonce().equals(report.nonce())) {
            throw new IllegalArgumentException("report nonce does not match challenge");
        }
        byte[] sessionKey = AeadCipher.newSessionKey();
        byte[] wrappedKey = RsaOaep.wrap(serverPublicKey, sessionKey);

        byte[] sessionIdBytes = challenge.sessionId().getBytes(UTF_8);
        byte[] aad = AssociatedData.build(
                ProtocolVersion.MAJOR,
                sessionIdBytes,
                MessageType.AUTH.id(),
                Direction.CLIENT_TO_SERVER,
                sequence);
        byte[] plain = ReportPayload.encode(report);
        byte[] encrypted = AeadCipher.seal(
                sessionKey, sessionIdBytes, Direction.CLIENT_TO_SERVER.wireValue(), sequence, aad, plain);

        io.github.kansamu216.vigil.crypto.Secrets.wipe(plain);
        io.github.kansamu216.vigil.crypto.Secrets.wipe(sessionKey);
        return new AuthRequest(challenge.sessionId(), challenge.nonce(), wrappedKey, encrypted);
    }
}
