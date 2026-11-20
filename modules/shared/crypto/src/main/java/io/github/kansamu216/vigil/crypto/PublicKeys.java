package io.github.kansamu216.vigil.crypto;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

public final class PublicKeys {

    private PublicKeys() {
    }

    public static PublicKey decodeRsa(byte[] encoded) throws CryptoException {
        if (encoded == null || encoded.length < 64 || encoded.length > 1024) {
            throw new CryptoException(CryptoException.Reason.INVALID_INPUT, "invalid public key length");
        }
        try {
            PublicKey key = KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(encoded));
            if (!"RSA".equals(key.getAlgorithm())) {
                throw new CryptoException(
                        CryptoException.Reason.INVALID_INPUT, "public key is not RSA");
            }
            return key;
        } catch (GeneralSecurityException e) {
            throw new CryptoException(CryptoException.Reason.INVALID_INPUT, "invalid public key", e);
        }
    }
}
