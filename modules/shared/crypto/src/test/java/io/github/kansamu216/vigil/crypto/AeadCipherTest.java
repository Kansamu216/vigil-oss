package io.github.kansamu216.vigil.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;

/**
 * AES-GCM 正常路径与攻击路径。
 */
class AeadCipherTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final byte[] SESSION = "session-1234-abcd".getBytes(UTF_8);

    private final byte[] key = AeadCipher.newSessionKey();
    private final byte[] aad = "aad".getBytes(UTF_8);
    private final byte[] plain = "report-body".getBytes(UTF_8);

    @Test
    void sealsAndOpens() throws CryptoException {
        byte[] sealed = AeadCipher.seal(key, SESSION, 0, 7L, aad, plain);
        assertArrayEquals(plain, AeadCipher.unseal(key, SESSION, 0, 7L, aad, sealed));
    }

    @Test
    void rejectsTamperedCiphertext() throws CryptoException {
        byte[] sealed = AeadCipher.seal(key, SESSION, 0, 7L, aad, plain);
        sealed[sealed.length - 1] ^= 0x01;
        CryptoException error = assertThrows(
                CryptoException.class, () -> AeadCipher.unseal(key, SESSION, 0, 7L, aad, sealed));
        assertEquals(CryptoException.Reason.AUTHENTICATION_FAILED, error.reason());
    }

    @Test
    void rejectsReplayedSequence() throws CryptoException {
        byte[] sealed = AeadCipher.seal(key, SESSION, 0, 7L, aad, plain);
        // 攻击者把同一密文挂到新序号上重放。
        CryptoException error = assertThrows(
                CryptoException.class, () -> AeadCipher.unseal(key, SESSION, 0, 8L, aad, sealed));
        assertEquals(CryptoException.Reason.AUTHENTICATION_FAILED, error.reason());
    }

    @Test
    void rejectsReflectedDirection() throws CryptoException {
        byte[] sealed = AeadCipher.seal(key, SESSION, 0, 7L, aad, plain);
        CryptoException error = assertThrows(
                CryptoException.class, () -> AeadCipher.unseal(key, SESSION, 1, 7L, aad, sealed));
        assertEquals(CryptoException.Reason.AUTHENTICATION_FAILED, error.reason());
    }

    @Test
    void rejectsDifferentAssociatedData() throws CryptoException {
        byte[] sealed = AeadCipher.seal(key, SESSION, 0, 7L, aad, plain);
        CryptoException error = assertThrows(
                CryptoException.class,
                () -> AeadCipher.unseal(key, SESSION, 0, 7L, "other".getBytes(UTF_8), sealed));
        assertEquals(CryptoException.Reason.AUTHENTICATION_FAILED, error.reason());
    }

    @Test
    void rejectsShortSealedData() {
        CryptoException error = assertThrows(
                CryptoException.class,
                () -> AeadCipher.unseal(key, SESSION, 0, 7L, aad, new byte[] {1, 2, 3}));
        assertEquals(CryptoException.Reason.INVALID_INPUT, error.reason());
    }

    @Test
    void rejectsWrongKeyLength() {
        CryptoException error = assertThrows(
                CryptoException.class,
                () -> AeadCipher.seal(new byte[16], SESSION, 0, 7L, aad, plain));
        assertEquals(CryptoException.Reason.INVALID_INPUT, error.reason());
    }

    @Test
    void rejectsShortSessionId() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> AeadCipher.seal(key, new byte[] {1, 2}, 0, 7L, aad, plain));
    }
}
