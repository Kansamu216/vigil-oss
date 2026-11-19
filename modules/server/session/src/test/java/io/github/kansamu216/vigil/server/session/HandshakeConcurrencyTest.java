package io.github.kansamu216.vigil.server.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kansamu216.vigil.crypto.RsaOaep;
import io.github.kansamu216.vigil.model.AuthRequest;
import io.github.kansamu216.vigil.model.Challenge;
import io.github.kansamu216.vigil.testsupport.session.ReferenceClient;
import io.github.kansamu216.vigil.testsupport.session.Reports;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * 并发重放回归：同一认证帧被多个线程同时提交时只能成功一次。
 */
class HandshakeConcurrencyTest {

    @Test
    void concurrentReplaysAuthenticateOnlyOnce() throws Exception {
        KeyPair server = RsaOaep.generateKeyPair();
        HandshakeService service = new HandshakeService(server, 30_000L, new SessionRegistry(64));
        ReferenceClient client = new ReferenceClient(server.getPublic(), service.keyFingerprint());
        SignedChallenge signed = service.issueChallenge("player-uuid-1", 1_700_000_000_000L);
        Challenge challenge = signed.challenge();
        AuthRequest request = client.respond(
                challenge, Reports.clean(challenge.nonce(), "player-uuid-1"), 0L);

        int workers = 4;
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            CountDownLatch ready = new CountDownLatch(workers);
            CountDownLatch start = new CountDownLatch(1);
            List<Callable<AuthOutcome>> tasks = new ArrayList<Callable<AuthOutcome>>();
            for (int i = 0; i < workers; i++) {
                tasks.add(new Callable<AuthOutcome>() {
                    @Override
                    public AuthOutcome call() throws Exception {
                        ready.countDown();
                        start.await();
                        return service.authenticate(request, 1_700_000_001_000L);
                    }
                });
            }
            List<Future<AuthOutcome>> futures = new ArrayList<Future<AuthOutcome>>();
            for (Callable<AuthOutcome> task : tasks) {
                futures.add(executor.submit(task));
            }
            ready.await();
            start.countDown();

            int successes = 0;
            for (Future<AuthOutcome> future : futures) {
                if (future.get().isSuccess()) {
                    successes++;
                }
            }
            assertEquals(1, successes, "only one replay may establish the session");
            assertTrue(service.keepalive(challenge.sessionId(), 1L, 1_700_000_002_000L));
        } finally {
            executor.shutdownNow();
        }
    }
}
