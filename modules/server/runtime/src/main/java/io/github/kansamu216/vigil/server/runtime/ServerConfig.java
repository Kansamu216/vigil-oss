package io.github.kansamu216.vigil.server.runtime;

import io.github.kansamu216.vigil.server.decision.DecisionPolicy;

public final class ServerConfig {

    private final long challengeTtlMillis;
    private final long sessionIdleTimeoutMillis;
    private final long auditRetentionMillis;
    private final int maxSessions;
    private final String databasePath;
    private final DecisionPolicy decisionPolicy;

    public ServerConfig(
            long challengeTtlMillis,
            long sessionIdleTimeoutMillis,
            long auditRetentionMillis,
            int maxSessions,
            String databasePath,
            DecisionPolicy decisionPolicy) {
        this.challengeTtlMillis = clamp(challengeTtlMillis, 1_000L, 120_000L);
        this.sessionIdleTimeoutMillis = clamp(sessionIdleTimeoutMillis, 5_000L, 900_000L);
        this.auditRetentionMillis = clamp(
                auditRetentionMillis, 24L * 60L * 60L * 1000L, 365L * 24L * 60L * 60L * 1000L);
        this.maxSessions = (int) clamp(maxSessions, 16L, 100_000L);
        if (databasePath == null || databasePath.isEmpty()) {
            throw new IllegalArgumentException("databasePath is required");
        }
        this.databasePath = databasePath;
        this.decisionPolicy = decisionPolicy == null ? DecisionPolicy.defaults() : decisionPolicy;
    }

    public static ServerConfig defaults(String databasePath) {
        return new ServerConfig(
                20_000L,
                120_000L,
                30L * 24L * 60L * 60L * 1000L,
                4096,
                databasePath,
                DecisionPolicy.defaults());
    }

    private static long clamp(long value, long min, long max) {
        return value < min ? min : (value > max ? max : value);
    }

    public long challengeTtlMillis() {
        return challengeTtlMillis;
    }

    public long sessionIdleTimeoutMillis() {
        return sessionIdleTimeoutMillis;
    }

    public long auditRetentionMillis() {
        return auditRetentionMillis;
    }

    public int maxSessions() {
        return maxSessions;
    }

    public String databasePath() {
        return databasePath;
    }

    public DecisionPolicy decisionPolicy() {
        return decisionPolicy;
    }
}
