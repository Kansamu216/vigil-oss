package io.github.kansamu216.vigil.server.session;

import io.github.kansamu216.vigil.protocol.ProtocolLimits;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Limits received work before it reaches the server's protocol and database. */
public final class InboundFrameQueue {

    private static final int MAX_QUEUED_FRAMES = 256;
    private static final int MAX_CONTROL_TASKS = 64;
    private final Map<String, Object> connections = new ConcurrentHashMap<String, Object>();
    private final RateLimiter limiter = new RateLimiter(8, 1000L, 4096);
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean maintenancePending = new AtomicBoolean();
    private final Semaphore frameSlots = new Semaphore(MAX_QUEUED_FRAMES);
    private final Semaphore controlSlots = new Semaphore(MAX_CONTROL_TASKS);
    private final ThreadPoolExecutor executor;
    private volatile Runnable afterStop = () -> {};

    public InboundFrameQueue(ThreadFactory threads) {
        executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(MAX_QUEUED_FRAMES + MAX_CONTROL_TASKS), threads) {
            @Override
            protected void terminated() {
                afterStop.run();
            }
        };
    }

    public void connected(String playerId, Object connection) {
        connections.put(playerId, connection);
    }

    public void disconnected(String playerId, Object connection) {
        connections.remove(playerId, connection);
    }

    public boolean isCurrent(String playerId, Object connection) {
        return connection != null && connections.get(playerId) == connection;
    }

    public void submit(String playerId, Object connection, byte[] frame, Runnable work) {
        if (!isCurrent(playerId, connection)
                || frame == null || frame.length > ProtocolLimits.MAX_FRAME_BYTES
                || !limiter.tryAcquire(playerId, TimeUnit.NANOSECONDS.toMillis(System.nanoTime()))) {
            dropped.incrementAndGet();
            return;
        }
        if (!enqueue(frameSlots, () -> {
            if (isCurrent(playerId, connection)) {
                work.run();
            }
        })) {
            dropped.incrementAndGet();
        }
    }

    /** Control work gets its own admission capacity while sharing the database worker. */
    public boolean control(Runnable work) {
        return enqueue(controlSlots, work);
    }

    private boolean enqueue(Semaphore slots, Runnable work) {
        if (!slots.tryAcquire()) {
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    work.run();
                } finally {
                    slots.release();
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            slots.release();
            return false;
        }
    }

    public long drainDroppedCount() {
        return dropped.getAndSet(0L);
    }

    public void maintain(Runnable work) {
        if (!maintenancePending.compareAndSet(false, true)) {
            return;
        }
        if (!control(() -> {
            try {
                work.run();
            } finally {
                maintenancePending.set(false);
            }
        })) {
            maintenancePending.set(false);
        }
    }

    public void stop(Runnable closeRuntime) {
        afterStop = closeRuntime;
        connections.clear();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(3L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
