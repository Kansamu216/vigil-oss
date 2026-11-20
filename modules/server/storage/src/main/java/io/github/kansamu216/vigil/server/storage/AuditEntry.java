package io.github.kansamu216.vigil.server.storage;

public final class AuditEntry {

    private final long id;
    private final String eventCode;
    private final String playerId;
    private final String detail;
    private final long createdAtMillis;

    AuditEntry(long id, String eventCode, String playerId, String detail, long createdAtMillis) {
        this.id = id;
        this.eventCode = eventCode;
        this.playerId = playerId;
        this.detail = detail;
        this.createdAtMillis = createdAtMillis;
    }

    public long id() {
        return id;
    }

    public String eventCode() {
        return eventCode;
    }

    public String playerId() {
        return playerId;
    }

    public String detail() {
        return detail;
    }

    public long createdAtMillis() {
        return createdAtMillis;
    }
}
