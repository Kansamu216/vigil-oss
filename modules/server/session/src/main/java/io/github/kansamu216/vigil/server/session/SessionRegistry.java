package io.github.kansamu216.vigil.server.session;

import io.github.kansamu216.vigil.model.Challenge;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话表。
 *
 * <p>有界 LRU：达到上限时淘汰最久未活动的会话，而不是无限增长。
 * 关服时必须调用 {@link #clear()} 释放引用。</p>
 */
public final class SessionRegistry {

    private final int maxSessions;
    private final LinkedHashMap<String, SessionRecord> sessions;

    public SessionRegistry(int maxSessions) {
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("maxSessions must be positive");
        }
        this.maxSessions = maxSessions;
        this.sessions = new LinkedHashMap<String, SessionRecord>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, SessionRecord> eldest) {
                return size() > SessionRegistry.this.maxSessions;
            }
        };
    }

    synchronized void put(SessionRecord record) {
        sessions.put(record.sessionId(), record);
    }

    synchronized SessionRecord get(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    public synchronized int size() {
        return sessions.size();
    }

    /**
     * 关闭超时会话，返回被关闭的会话 id，供上层记审计。
     */
    public synchronized List<String> closeIdle(long nowMillis, long idleTimeoutMillis) {
        List<String> closed = new ArrayList<String>();
        Iterator<Map.Entry<String, SessionRecord>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            SessionRecord record = iterator.next().getValue();
            if (nowMillis - record.lastActivityMillis() >= idleTimeoutMillis) {
                record.state(SessionState.CLOSED);
                closed.add(record.sessionId());
                iterator.remove();
            }
        }
        return closed;
    }

    public synchronized Challenge challengeOf(String sessionId) {
        SessionRecord record = get(sessionId);
        return record == null ? null : record.challenge();
    }

    public synchronized SessionState stateOf(String sessionId) {
        SessionRecord record = get(sessionId);
        return record == null ? SessionState.CLOSED : record.state();
    }

    /** 关服或断线时清理，密钥引用随记录一起被丢弃。 */
    public synchronized void clear() {
        sessions.clear();
    }
}
