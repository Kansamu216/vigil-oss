package io.github.kansamu216.vigil.protocol.payload;

import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import io.github.kansamu216.vigil.protocol.WireReader;
import io.github.kansamu216.vigil.protocol.WireWriter;

/**
 * 挑战报文编解码。
 *
 * <p>签名覆盖 u16 版本 + 五个字段，编码顺序固定，客户端不得自行重排。
 * 公钥本身不上线，只传指纹，避免自证信任锚。</p>
 */
public final class ChallengePayload {

    private ChallengePayload() {
    }

    public static byte[] encode(Challenge challenge, byte[] signature) {
        WireWriter writer = new WireWriter(256, ProtocolLimits.MAX_PAYLOAD_BYTES);
        writer.u16(1);
        writer.string(challenge.sessionId());
        writer.string(challenge.nonce());
        writer.string(challenge.serverKeyFingerprint());
        writer.i64(challenge.issuedAtMillis());
        writer.i64(challenge.expiresAtMillis());
        writer.bytes(signature);
        return writer.toByteArray();
    }

    public static Decoded decode(byte[] payload) throws ProtocolException {
        WireReader reader = new WireReader(payload);
        int schema = reader.u16();
        if (schema != 1) {
            throw new ProtocolException(
                    ProtocolException.Code.UNSUPPORTED_VERSION, "challenge schema: " + schema);
        }
        String sessionId = reader.string();
        String nonce = reader.string();
        String fingerprint = reader.string();
        long issuedAt = reader.i64();
        long expiresAt = reader.i64();
        byte[] signature = reader.bytes();
        if (reader.remaining() != 0) {
            throw new ProtocolException(
                    ProtocolException.Code.LENGTH_MISMATCH, "trailing bytes in challenge payload");
        }
        Challenge challenge;
        try {
            challenge = new Challenge(sessionId, nonce, fingerprint, issuedAt, expiresAt);
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(ProtocolException.Code.FIELD_OUT_OF_RANGE, e.getMessage());
        }
        return new Decoded(challenge, signature);
    }

    /** 解码结果：挑战本体与签名分开返回，便于先验签再使用。 */
    public static final class Decoded {
        private final Challenge challenge;
        private final byte[] signature;

        Decoded(Challenge challenge, byte[] signature) {
            this.challenge = challenge;
            this.signature = signature;
        }

        public Challenge challenge() {
            return challenge;
        }

        public byte[] signature() {
            return signature.clone();
        }
    }
}
