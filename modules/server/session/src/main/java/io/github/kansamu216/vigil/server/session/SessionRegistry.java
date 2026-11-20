package io.github.kansamu216.vigil.server.session;

import io.github.kansamu216.vigil.model.Challenge;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final Map<String, SessionRecord> players = new HashMap<String, SessionRecord>();

    public SessionRegistry(int maxSessions) {
        if (maxSessions <= 0) {
            throw new IllegalArgumentException("maxSessions must be positive");
        }
        this.maxSessions = maxSessions;
        this.sessions = new LinkedHashMap<String, SessionRecord>(16, 0.75f, true);
    }

    synchronized void put(SessionRecord record) {
        SessionRecord previous = players.put(record.playerId(), record);
        if (previous != null) {
            sessions.remove(previous.sessionId());
            close(previous);
        }
        sessions.put(record.sessionId(), record);
        if (sessions.size() > maxSessions) {
            Iterator<SessionRecord> iterator = sessions.values().iterator();
            SessionRecord oldest = iterator.next();
            iterator.remove();
            players.remove(oldest.playerId());
            close(oldest);
        }
    }

    synchronized SessionRecord get(String sessionId) {
        return sessionId == null ? null : sessions.get(sessionId);
    }

    public synchronized int size() {
        return sessions.size();
    }

    synchronized void closeConnection(String playerId, Object connection) {
        SessionRecord record = players.get(playerId);
        if (record != null && record.belongsTo(playerId, connection)) {
            players.remove(playerId);
            sessions.remove(record.sessionId());
            close(record);
        }
    }

    private static void close(SessionRecord record) {
        synchronized (record) {
            record.state(SessionState.CLOSED);
        }
    }

    /**
     * 关闭超时会话，返回被关闭的会话 id，供上层记审计。
     */
    public synchronized List<String> closeIdle(long nowMillis, long idleTimeoutMillis) {
        List<String> closed = new ArrayList<String>();
        Iterator<Map.Entry<String, SessionRecord>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            SessionRecord record = iterator.next().getValue();
            synchronized (record) {
                if (nowMillis - record.lastActivityMillis() >= idleTimeoutMillis) {
                    record.state(SessionState.CLOSED);
                    closed.add(record.sessionId());
                    iterator.remove();
                    players.remove(record.playerId());
                }
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
        if (record == null) {
            return SessionState.CLOSED;
        }
        synchronized (record) {
            return record.state();
        }
    }

    /** 关服或断线时清理，密钥引用随记录一起被丢弃。 */
    public synchronized void clear() {
        for (SessionRecord record : sessions.values()) {
            close(record);
        }
        sessions.clear();
        players.clear();
    }
}
