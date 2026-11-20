package io.github.kansamu216.vigil.server.storage;

import java.io.Closeable;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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

    public static Database openInMemory() throws SQLException {
        return open("jdbc:sqlite::memory:");
    }

    private static Database open(String url) throws SQLException {

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite JDBC driver is not available", e);
        }
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 3000");
        }
        Database database = new Database(connection);
        database.migrate();
        return database;
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

    public interface Migration {
        int version();

        void apply(Connection connection) throws SQLException;
    }
}
