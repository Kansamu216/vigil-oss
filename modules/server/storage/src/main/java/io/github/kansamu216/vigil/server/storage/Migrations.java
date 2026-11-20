package io.github.kansamu216.vigil.server.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Schema 迁移表。
 *
 * <p>只向前：已发布的迁移不再修改，修正必须新增版本。
 * 新增表或索引时同时考虑查询路径，避免全表扫描。</p>
 */
final class Migrations {

    private static final List<Database.Migration> MIGRATIONS;

    static {
        List<Database.Migration> list = new ArrayList<Database.Migration>();
        list.add(new V1InitialSchema());
        MIGRATIONS = Collections.unmodifiableList(list);
    }

    private Migrations() {
    }

    static List<Database.Migration> all() {
        return MIGRATIONS;
    }

    /** V1：封禁与审计。仅存判定所需最小字段，不存报告原文。 */
    static final class V1InitialSchema implements Database.Migration {

        @Override
        public int version() {
            return 1;
        }

        @Override
        public void apply(Connection connection) throws SQLException {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS bans ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "player_uuid TEXT NOT NULL,"
                        + "player_name TEXT NOT NULL,"
                        + "reason TEXT NOT NULL,"
                        + "created_at INTEGER NOT NULL,"
                        + "expires_at INTEGER NOT NULL,"
                        + "active INTEGER NOT NULL DEFAULT 1)");
                statement.execute(
                        "CREATE INDEX IF NOT EXISTS idx_bans_uuid_active ON bans(player_uuid, active)");
                statement.execute("CREATE TABLE IF NOT EXISTS audit ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "event_code TEXT NOT NULL,"
                        + "player_id TEXT,"
                        + "detail TEXT,"
                        + "created_at INTEGER NOT NULL)");
                statement.execute(
                        "CREATE INDEX IF NOT EXISTS idx_audit_created ON audit(created_at)");
            }
        }
    }
}
