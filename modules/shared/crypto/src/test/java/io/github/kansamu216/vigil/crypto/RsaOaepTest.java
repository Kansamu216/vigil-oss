package io.github.kansamu216.vigil.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.Charset;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;

/**
 * RSA 包裹、签名与交付隔离。
 *
 * <p>“交付 A 的私钥解不开交付 B 的密钥包”是交付隔离的核心性质，
 * 这里用两个独立密钥对显式验证，而不是只在文档里承诺。</p>
 */
class RsaOaepTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Test
    void wrapsAndUnwrapsSessionKey() throws CryptoException {
        KeyPair server = RsaOaep.generateKeyPair();
        byte[] sessionKey = AeadCipher.newSessionKey();
        byte[] wrapped = RsaOaep.wrap(server.getPublic(), sessionKey);
        assertArrayEquals(sessionKey, RsaOaep.unwrap(server.getPrivate(), wrapped));
    }

    @Test
    void otherDeliveryCannotUnwrap() throws CryptoException {
        KeyPair deliveryA = RsaOaep.generateKeyPair();
        KeyPair deliveryB = RsaOaep.generateKeyPair();
        byte[] wrapped = RsaOaep.wrap(deliveryA.getPublic(), AeadCipher.newSessionKey());

        CryptoException error = assertThrows(
                CryptoException.class, () -> RsaOaep.unwrap(deliveryB.getPrivate(), wrapped));
        assertEquals(CryptoException.Reason.KEY_UNWRAP_FAILED, error.reason());
    }

    @Test
    void fingerprintsDifferPerDelivery() throws CryptoException {
        KeyPair deliveryA = RsaOaep.generateKeyPair();
        KeyPair deliveryB = RsaOaep.generateKeyPair();
        String fingerprintA = KeyFingerprint.of(deliveryA.getPublic());
        String fingerprintB = KeyFingerprint.of(deliveryB.getPublic());
        assertNotEquals(fingerprintA, fingerprintB);
        assertEquals(64, fingerprintA.length());
    }

    @Test
    void signsAndVerifiesChallenge() throws CryptoException {
        KeyPair server = RsaOaep.generateKeyPair();
        byte[] payload = "session|nonce".getBytes(UTF_8);
        byte[] signature = ChallengeSignature.sign(server.getPrivate(), payload);

        ChallengeSignature.verify(server.getPublic(), payload, signature);
    }

    @Test
    void rejectsTamperedChallengePayload() throws CryptoException {
        KeyPair server = RsaOaep.generateKeyPair();
        byte[] signature = ChallengeSignature.sign(server.getPrivate(), "a|b".getBytes(UTF_8));

        CryptoException error = assertThrows(
                CryptoException.class,
                () -> ChallengeSignature.verify(server.getPublic(), "a|c".getBytes(UTF_8), signature));
        assertEquals(CryptoException.Reason.SIGNATURE_INVALID, error.reason());
    }
}
