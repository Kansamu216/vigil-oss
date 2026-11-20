package io.github.kansamu216.vigil.server.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public final class BanRepository {

    private static final long PERMANENT = 0L;

    private final Database database;

    public BanRepository(Database database) {
        this.database = database;
    }

    public long ban(
            String playerUuid, String playerName, String reason, long nowMillis, long expiresAtMillis) {
        if (playerUuid == null || playerUuid.isEmpty()) {
            throw new IllegalArgumentException("playerUuid is required");
        }
        String sql = "INSERT INTO bans"
                + " (player_uuid, player_name, reason, created_at, expires_at, active)"
                + " VALUES (?, ?, ?, ?, ?, 1)";
        try (PreparedStatement statement =
                database.connection().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerUuid);
            statement.setString(2, playerName == null ? "" : playerName);
            statement.setString(3, reason == null ? "" : reason);
            statement.setLong(4, nowMillis);
            statement.setLong(5, expiresAtMillis <= 0L ? PERMANENT : expiresAtMillis);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw new StorageException("failed to insert ban", e);
        }
    }

    public Optional<BanRecord> activeBan(String playerUuid, long nowMillis) {
        String sql = "SELECT id, player_uuid, player_name, reason, created_at, expires_at"
                + " FROM bans WHERE player_uuid = ? AND active = 1"
                + " AND (expires_at = 0 OR expires_at > ?)"
                + " ORDER BY id DESC LIMIT 1";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            statement.setLong(2, nowMillis);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                return Optional.of(new BanRecord(
                        result.getLong(1),
                        result.getString(2),
                        result.getString(3),
                        result.getString(4),
                        result.getLong(5),
                        result.getLong(6)));
            }
        } catch (SQLException e) {
            throw new StorageException("failed to query active ban", e);
        }
    }

    public int unban(String playerUuid) {
        String sql = "UPDATE bans SET active = 0 WHERE player_uuid = ? AND active = 1";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setString(1, playerUuid);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("failed to unban", e);
        }
    }

    public int activeCount(long nowMillis) {
        String sql = "SELECT COUNT(*) FROM bans WHERE active = 1 AND (expires_at = 0 OR expires_at > ?)";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, nowMillis);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new StorageException("failed to count bans", e);
        }
    }

    public int purgeExpired(long nowMillis) {
        String sql = "UPDATE bans SET active = 0 WHERE active = 1 AND expires_at > 0 AND expires_at <= ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, nowMillis);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("failed to purge expired bans", e);
        }
    }

    Connection connection() {
        return database.connection();
    }
}
