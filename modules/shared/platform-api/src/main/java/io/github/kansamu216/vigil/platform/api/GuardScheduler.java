package io.github.kansamu216.vigil.platform.api;

public interface GuardScheduler {

    void runOnMainThread(Runnable task);

    void runAsync(Runnable task);

    Cancellable scheduleOnce(long delayMillis, Runnable task);

    Cancellable scheduleRepeating(long periodMillis, Runnable task);

    void shutdown();

    interface Cancellable {
        void cancel();
    }
}
