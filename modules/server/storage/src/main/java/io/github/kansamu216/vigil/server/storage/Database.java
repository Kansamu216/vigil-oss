package io.github.kansamu216.vigil.server.storage;

import java.io.Closeable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * SQLite 连接与 migration 执行器。
 *
 * <p>规则：单写线程使用、WAL、busy_timeout 有界、migration 只向前且每步独立事务。
 * 备份由调用方在 migration 前完成（本类只负责失败时回滚当前步骤）。</p>
 */
public final class Database implements Closeable {

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    public static Database open(Path file) throws SQLException {
        Path absolute = file.toAbsolutePath();
        Path parent = absolute.getParent();
        if (parent != null) {
            try {
                java.nio.file.Files.createDirectories(parent);
            } catch (java.io.IOException e) {
                throw new SQLException("failed to create database directory: " + parent, e);
            }
        }
        return open("jdbc:sqlite:" + absolute);
    }

    /** 测试用内存库：{@code jdbc:sqlite::memory:}。 */
    public static Database openInMemory() throws SQLException {
        return open("jdbc:sqlite::memory:");
    }

    private static Database open(String url) throws SQLException {
        // 驱动由本模块显式加载：Mod 环境下类可能来自重定位/展开后的目录，
        // 不能依赖 java.sql.Driver 的 ServiceLoader 自动发现。
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver is not available", e);
        }
        Connection connection = DriverManager.getConnection(url);
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 3000");
            }
            Database database = new Database(connection);
            database.migrate();
            return database;
        } catch (SQLException | RuntimeException e) {
            try {
                connection.close();
            } catch (SQLException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    public Connection connection() {
        return connection;
    }

    int schemaVersion() throws SQLException {
        try (Statement statement = connection.createStatement();
                java.sql.ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private void migrate() throws SQLException {
        int current = schemaVersion();
        for (Migration migration : Migrations.all()) {
            if (migration.version() <= current) {
                continue;
            }
            connection.setAutoCommit(false);
            try {
                migration.apply(connection);
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA user_version = " + migration.version());
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    @Override
    public void close() throws java.io.IOException {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new java.io.IOException("failed to close database", e);
        }
    }

    /** 单步 migration。版本号必须连续，不允许跳号。 */
    public interface Migration {
        int version();

        void apply(Connection connection) throws SQLException;
    }
}
