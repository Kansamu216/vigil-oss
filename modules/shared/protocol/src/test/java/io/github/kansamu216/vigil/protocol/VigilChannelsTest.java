package io.github.kansamu216.vigil.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VigilChannelsTest {

    @Test
    void namespaceAndMessageIdAreStable() {
        assertEquals("vigil", VigilChannels.namespace());
        assertEquals("ping", VigilChannels.pingId());
        assertEquals("frame", VigilChannels.frameId());
    }

    @Test
    void protocolVersionIsPositiveAndStable() {
        assertTrue(VigilChannels.protocolVersion() > 0);
        assertEquals(1, VigilChannels.protocolVersion());
    }
}
