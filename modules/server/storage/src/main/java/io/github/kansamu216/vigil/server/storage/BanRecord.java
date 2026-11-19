package io.github.kansamu216.vigil.server.storage;

/**
 * 一条封禁记录。UUID 为主键语义，玩家改名不影响封禁。
 */
public final class BanRecord {

    private final long id;
    private final String playerUuid;
    private final String playerName;
    private final String reason;
    private final long createdAtMillis;
    private final long expiresAtMillis;

    BanRecord(
            long id,
            String playerUuid,
            String playerName,
            String reason,
            long createdAtMillis,
            long expiresAtMillis) {
        this.id = id;
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.reason = reason;
        this.createdAtMillis = createdAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public long id() {
        return id;
    }

    public String playerUuid() {
        return playerUuid;
    }

    public String playerName() {
        return playerName;
    }

    public String reason() {
        return reason;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }

    public long expiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired(long nowMillis) {
        return expiresAtMillis > 0L && nowMillis >= expiresAtMillis;
    }
}
