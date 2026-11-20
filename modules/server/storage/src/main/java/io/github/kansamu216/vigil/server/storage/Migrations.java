package io.github.kansamu216.vigil.server.storage;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
