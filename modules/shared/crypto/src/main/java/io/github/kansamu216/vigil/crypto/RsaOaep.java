package io.github.kansamu216.vigil.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

public final class RsaOaep {

    public static final int KEY_BITS = 2048;

    private static final OAEPParameterSpec OAEP_SHA256 =
            new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private RsaOaep() {
    }

    public static KeyPair generateKeyPair() throws CryptoException {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(KEY_BITS, SecretsHolder.RANDOM);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new CryptoException(
                    CryptoException.Reason.INVALID_INPUT, "rsa keygen unavailable", e);
        }
    }

    public static byte[] wrap(PublicKey publicKey, byte[] sessionKey) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEP_SHA256);
            return cipher.doFinal(sessionKey);
        } catch (GeneralSecurityException e) {
            throw new CryptoException(
                    CryptoException.Reason.INVALID_INPUT, "wrap failed", e);
        }
    }

    public static byte[] unwrap(PrivateKey privateKey, byte[] wrappedKey) throws CryptoException {
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            cipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_SHA256);
            return cipher.doFinal(wrappedKey);
        } catch (GeneralSecurityException e) {
            throw new CryptoException(
                    CryptoException.Reason.KEY_UNWRAP_FAILED, "unwrap failed", e);
        }
    }

    private static final class SecretsHolder {
        private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();
    }
}
