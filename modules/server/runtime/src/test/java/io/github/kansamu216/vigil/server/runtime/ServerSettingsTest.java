package io.github.kansamu216.vigil.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerSettingsTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Test
    void createsDefaultFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("config").resolve(ServerSettings.FILE_NAME);
        ServerSettings settings = ServerSettings.load(file);

        assertTrue(Files.exists(file));
        ServerConfig config = settings.toServerConfig(dir.resolve("data"));
        assertEquals(20_000L, config.challengeTtlMillis());
        assertEquals(120_000L, config.sessionIdleTimeoutMillis());
        assertTrue(config.databasePath().endsWith("vigil.db"));
        assertTrue(settings.warnings().isEmpty());
    }

    @Test
    void clampsOutOfRangeValues(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        Files.write(file, (
                "challenge.ttlSeconds=0\n"
                        + "session.idleSeconds=99999\n"
                        + "session.maxSessions=1\n"
                        + "policy.banScore=500\n").getBytes(UTF_8));

        ServerSettings settings = ServerSettings.load(file);
        ServerConfig config = settings.toServerConfig(dir);

        assertEquals(1_000L, config.challengeTtlMillis());
        assertEquals(900_000L, config.sessionIdleTimeoutMillis());
        assertEquals(16, config.maxSessions());
        assertEquals(100, config.decisionPolicy().banScore());
        assertEquals(4, settings.warnings().size());
    }

    @Test
    void fallsBackOnInvalidNumber(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        Files.write(file, "challenge.ttlSeconds=abc\n".getBytes(UTF_8));

        ServerConfig config = ServerSettings.load(file).toServerConfig(dir);
        assertEquals(20_000L, config.challengeTtlMillis());
    }

    @Test
    void resetsNonMonotonicThresholds(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        Files.write(file, (
                "policy.alertScore=50\n"
                        + "policy.kickScore=20\n").getBytes(UTF_8));

        ServerSettings settings = ServerSettings.load(file);
        ServerConfig config = settings.toServerConfig(dir);
        assertEquals(20, config.decisionPolicy().alertScore());
        assertEquals(50, config.decisionPolicy().kickScore());
        assertTrue(settings.warnings().stream().anyMatch(w -> w.contains("non-decreasing")));
    }

    @Test
    void rejectsDatabasePathTraversal(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        Files.write(file, "database.file=../outside.db\n".getBytes(UTF_8));

        ServerSettings settings = ServerSettings.load(file);
        Path dataDir = dir.resolve("data");
        ServerConfig config = settings.toServerConfig(dataDir);

        Path database = java.nio.file.Paths.get(config.databasePath());
        assertTrue(database.normalize().startsWith(dataDir.toAbsolutePath().normalize()));
        assertTrue(settings.warnings().stream().anyMatch(w -> w.contains("inside the data directory")));
    }

    @Test
    void rejectsAbsoluteDatabasePath(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ServerSettings.FILE_NAME);
        Files.write(file, "database.file=/tmp/evil.db\n".getBytes(UTF_8));

        ServerSettings settings = ServerSettings.load(file);
        Path dataDir = dir.resolve("data");
        ServerConfig config = settings.toServerConfig(dataDir);
        assertTrue(java.nio.file.Paths.get(config.databasePath()).startsWith(dataDir.toAbsolutePath()));
        assertTrue(!settings.warnings().isEmpty());
    }
}
