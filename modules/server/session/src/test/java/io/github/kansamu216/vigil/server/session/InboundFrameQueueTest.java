package io.github.kansamu216.vigil.server.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InboundFrameQueueTest {
    @Test
    void skipsStaleFrameAndDrainsAcceptedControlWorkBeforeClosing() throws InterruptedException {
        InboundFrameQueue queue = new InboundFrameQueue(Thread::new);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> events = new ArrayList<String>();
        Object oldConnection = new Object();
        Object newConnection = new Object();
        try {
            assertTrue(queue.control(() -> {
                started.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
            assertTrue(started.await(2L, TimeUnit.SECONDS));
            queue.connected("player", oldConnection);
            queue.submit("player", oldConnection, new byte[16], () -> events.add("stale"));
            queue.disconnected("player", oldConnection);
            queue.connected("player", newConnection);
            assertTrue(queue.control(() -> events.add("control")));
        } finally {
            release.countDown();
            queue.stop(() -> events.add("closed"));
        }
        assertEquals(Arrays.asList("control", "closed"), events);
    }
}
