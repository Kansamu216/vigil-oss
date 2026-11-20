package io.github.kansamu216.vigil.server.storage;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 审计仓储。
 *
 * <p>只写事件码与必要元数据：不写密钥、原始硬件值、进程清单或报告原文。
 * 保留期清理由 {@link #purgeOlderThan(long)} 负责，调用方定期执行。</p>
 */
public final class AuditRepository {

    private final Database database;

    public AuditRepository(Database database) {
        this.database = database;
    }

    /** 最近查询窗口的最大条数。 */
    public static int maxRecent() {
        return 500;
    }

    public long append(String eventCode, String playerId, String detail, long nowMillis) {
        if (eventCode == null || eventCode.isEmpty() || eventCode.length() > 64) {
            throw new IllegalArgumentException("invalid event code");
        }
        String sql = "INSERT INTO audit (event_code, player_id, detail, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement =
                database.connection().prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, eventCode);
            statement.setString(2, playerId);
            statement.setString(3, detail == null ? "" : truncate(detail, 512));
            statement.setLong(4, nowMillis);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (SQLException e) {
            throw new StorageException("failed to append audit", e);
        }
    }

    public List<AuditEntry> recent(int limit) {
        int bounded = Math.max(1, Math.min(limit, maxRecent()));
        String sql = "SELECT id, event_code, player_id, detail, created_at"
                + " FROM audit ORDER BY id DESC LIMIT ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setInt(1, bounded);
            try (ResultSet result = statement.executeQuery()) {
                List<AuditEntry> entries = new ArrayList<AuditEntry>();
                while (result.next()) {
                    entries.add(new AuditEntry(
                            result.getLong(1),
                            result.getString(2),
                            result.getString(3),
                            result.getString(4),
                            result.getLong(5)));
                }
                return Collections.unmodifiableList(entries);
            }
        } catch (SQLException e) {
            throw new StorageException("failed to read audit", e);
        }
    }

    public int purgeOlderThan(long cutoffMillis) {
        String sql = "DELETE FROM audit WHERE created_at < ?";
        try (PreparedStatement statement = database.connection().prepareStatement(sql)) {
            statement.setLong(1, cutoffMillis);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("failed to purge audit", e);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
