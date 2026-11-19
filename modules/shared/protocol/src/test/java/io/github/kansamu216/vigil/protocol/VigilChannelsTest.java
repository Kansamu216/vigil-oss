package io.github.kansamu216.vigil.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 通道标识的黄金基线。
 *
 * <p>改动这些值会让已发布的客户端失效，因此用测试显式拦住无意的改名；
 * 有意变更必须先升协议版本并更新本测试和发布说明。</p>
 */
class VigilChannelsTest {

    @Test
    void namespaceAndMessageIdAreStable() {
        assertEquals("vigil", VigilChannels.namespace());
        assertEquals("ping", VigilChannels.pingId());
    }

    @Test
    void protocolVersionIsPositiveAndStable() {
        assertTrue(VigilChannels.protocolVersion() > 0);
        assertEquals(1, VigilChannels.protocolVersion());
    }
}
