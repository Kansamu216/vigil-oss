package io.github.kansamu216.vigil.crypto;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/**
 * 挑战签名与校验（SHA256withRSA）。
 *
 * <p>签名校验使用已核对指纹的服务器公钥。</p>
 */
public final class ChallengeSignature {

    private static final String ALGORITHM = "SHA256withRSA";

    private ChallengeSignature() {
    }

    public static byte[] sign(PrivateKey privateKey, byte[] payload) throws CryptoException {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey);
            signature.update(payload);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new CryptoException(
                    CryptoException.Reason.INVALID_INPUT, "sign failed", e);
        }
    }

    public static void verify(PublicKey publicKey, byte[] payload, byte[] signatureBytes)
            throws CryptoException {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(payload);
            if (!signature.verify(signatureBytes)) {
                throw new CryptoException(
                        CryptoException.Reason.SIGNATURE_INVALID, "signature mismatch");
            }
        } catch (CryptoException e) {
            throw e;
        } catch (GeneralSecurityException e) {
            throw new CryptoException(
                    CryptoException.Reason.SIGNATURE_INVALID, "verify failed", e);
        }
    }
}
