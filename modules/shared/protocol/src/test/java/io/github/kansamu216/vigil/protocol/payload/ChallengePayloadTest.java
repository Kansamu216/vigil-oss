package io.github.kansamu216.vigil.protocol.payload;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.protocol.ProtocolException;
import io.github.kansamu216.vigil.protocol.WireWriter;
import org.junit.jupiter.api.Test;

/**
 * 挑战负载：公钥必须随签名一起往返，并在解码后仍可校验。
 */
class ChallengePayloadTest {

    private static final byte[] PUBLIC_KEY = new byte[294];
    private static final byte[] SIGNATURE = new byte[] {1, 2, 3, 4};

    private static Challenge challenge() {
        for (int i = 0; i < PUBLIC_KEY.length; i++) {
            PUBLIC_KEY[i] = (byte) i;
        }
        return new Challenge(
                "session-1",
                "0123456789abcdef",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                PUBLIC_KEY,
                1_700_000_000_000L,
                1_700_000_020_000L);
    }

    @Test
    void roundTripsPublicKeyAndSignature() throws Exception {
        Challenge original = challenge();
        ChallengePayload.Decoded decoded =
                ChallengePayload.decode(ChallengePayload.encode(original, SIGNATURE));

        assertEquals(original.sessionId(), decoded.challenge().sessionId());
        assertEquals(original.nonce(), decoded.challenge().nonce());
        assertArrayEquals(original.serverPublicKey(), decoded.challenge().serverPublicKey());
        assertArrayEquals(SIGNATURE, decoded.signature());
        assertArrayEquals(original.signedPayload(), decoded.challenge().signedPayload());
    }

    @Test
    void rejectsLegacySchemaWithoutKey() {
        WireWriter writer = new WireWriter(64, 256);
        writer.u16(1);
        writer.string("session-1");
        writer.string("0123456789abcdef");
        writer.string("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        writer.i64(1L);
        writer.i64(2L);
        writer.bytes(SIGNATURE);

        ProtocolException error = assertThrows(
                ProtocolException.class, () -> ChallengePayload.decode(writer.toByteArray()));
        assertEquals(ProtocolException.Code.UNSUPPORTED_VERSION, error.code());
    }

    @Test
    void rejectsTrailingBytes() {
        byte[] valid = ChallengePayload.encode(challenge(), SIGNATURE);
        byte[] withTail = new byte[valid.length + 1];
        System.arraycopy(valid, 0, withTail, 0, valid.length);

        ProtocolException error = assertThrows(
                ProtocolException.class, () -> ChallengePayload.decode(withTail));
        assertEquals(ProtocolException.Code.LENGTH_MISMATCH, error.code());
    }
}
