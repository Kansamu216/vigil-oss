package io.github.kansamu216.vigil.server.session;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RateLimiter {

    private final int maxEvents;
    private final long windowMillis;
    private final int maxKeys;
    private final LinkedHashMap<String, Window> windows;

    public RateLimiter(int maxEvents, long windowMillis, int maxKeys) {
        if (maxEvents <= 0 || windowMillis <= 0 || maxKeys <= 0) {
            throw new IllegalArgumentException("rate limiter bounds must be positive");
        }
        this.maxEvents = maxEvents;
        this.windowMillis = windowMillis;
        this.maxKeys = maxKeys;
        this.windows = new LinkedHashMap<String, Window>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
                return size() > RateLimiter.this.maxKeys;
            }
        };
    }

    public synchronized boolean tryAcquire(String key, long nowMillis) {
        Window window = windows.get(key);
        if (window == null || nowMillis - window.startedAtMillis >= windowMillis) {
            window = new Window(nowMillis);
            windows.put(key, window);
        }
        window.count++;
        return window.count <= maxEvents;
    }

    public synchronized int trackedKeys() {
        return windows.size();
    }

    public synchronized void evictExpired(long nowMillis) {
        Iterator<Map.Entry<String, Window>> iterator = windows.entrySet().iterator();
        while (iterator.hasNext()) {
            if (nowMillis - iterator.next().getValue().startedAtMillis >= windowMillis) {
                iterator.remove();
            }
        }
    }

    private static final class Window {
        private final long startedAtMillis;
        private int count;

        Window(long startedAtMillis) {
            this.startedAtMillis = startedAtMillis;
        }
    }
}
