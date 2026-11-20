package io.github.kansamu216.vigil.protocol;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

class EnvelopeFuzzTest {

    private static final int ITERATIONS = 10_000;

    @Test
    void randomBytesNeverEscapeAsUncheckedException() {
        Random random = new Random(20261119L);
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] input = new byte[random.nextInt(64)];
            random.nextBytes(input);
            assertOnlyProtocolFailure(input);
        }
    }

    @Test
    void mutatedValidFramesNeverEscapeAsUncheckedException() {
        Random random = new Random(7717L);
        byte[] valid = EnvelopeCodec.encode(
                Envelope.of(MessageType.AUTH, Direction.CLIENT_TO_SERVER, 9L, new byte[] {7, 8, 9, 10}));
        for (int i = 0; i < ITERATIONS; i++) {
            byte[] mutated = valid.clone();
            int mutations = 1 + random.nextInt(3);
            for (int m = 0; m < mutations; m++) {
                mutated[random.nextInt(mutated.length)] = (byte) random.nextInt(256);
            }
            assertOnlyProtocolFailure(mutated);
        }
    }

    private static void assertOnlyProtocolFailure(byte[] input) {
        try {
            Envelope envelope = EnvelopeCodec.decode(input);
            assertNotNull(envelope, "decoded envelope must not be null");
            assertTrue(envelope.payloadLength() <= ProtocolLimits.MAX_PAYLOAD_BYTES);
        } catch (ProtocolException expected) {
            assertNotNull(expected.code());
        }
    }
}
