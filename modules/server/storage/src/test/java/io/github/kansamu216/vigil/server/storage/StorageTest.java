package io.github.kansamu216.vigil.server.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StorageTest {

    private static final long NOW = 1_700_000_000_000L;
    private static final String UUID = "2f8a0f52-0000-4000-8000-000000000001";

    private File file;
    private Database database;
    private BanRepository bans;
    private AuditRepository audit;

    @BeforeEach
    void setUp() throws Exception {
        file = Files.createTempFile("vigil-test", ".db").toFile();
        database = Database.open(file.toPath());
        bans = new BanRepository(database);
        audit = new AuditRepository(database);
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
        assertTrue(file.delete() || !file.exists());
    }

    @Test
    void migratesToCurrentVersion() throws Exception {
        assertEquals(1, database.schemaVersion());
    }

    @Test
    void permanentBanIsActive() {
        bans.ban(UUID, "tester", "integrity", NOW, 0L);
        assertTrue(bans.activeBan(UUID, NOW + 10_000L).isPresent());
        assertEquals(1, bans.activeCount(NOW));
    }

    @Test
    void temporaryBanExpires() {
        bans.ban(UUID, "tester", "integrity", NOW, NOW + 1_000L);
        assertTrue(bans.activeBan(UUID, NOW + 500L).isPresent());
        assertFalse(bans.activeBan(UUID, NOW + 1_500L).isPresent());
        assertEquals(0, bans.activeCount(NOW + 1_500L));
    }

    @Test
    void unbanRemovesActiveBan() {
        bans.ban(UUID, "tester", "integrity", NOW, 0L);
        assertEquals(1, bans.unban(UUID));
        assertFalse(bans.activeBan(UUID, NOW).isPresent());
        assertEquals(0, bans.unban(UUID));
    }

    @Test
    void purgeExpiredOnlyTouchesExpiredRows() {
        bans.ban(UUID, "a", "reason", NOW, NOW + 1_000L);
        bans.ban("other-uuid", "b", "reason", NOW, 0L);
        assertEquals(1, bans.purgeExpired(NOW + 2_000L));
        assertEquals(1, bans.activeCount(NOW + 2_000L));
    }

    @Test
    void auditAppendsAndReadsBack() {
        audit.append("VIGIL-AUTH-OK", UUID, "mods=12", NOW);
        audit.append("VIGIL-VERDICT", UUID, "score=30", NOW + 1L);

        assertEquals(2, audit.recent(10).size());
        assertEquals("VIGIL-VERDICT", audit.recent(1).get(0).eventCode());
    }

    @Test
    void auditPurgesOldEntries() {
        audit.append("VIGIL-AUTH-OK", UUID, null, NOW);
        audit.append("VIGIL-AUTH-OK", UUID, null, NOW + 10_000L);

        assertEquals(1, audit.purgeOlderThan(NOW + 5_000L));
        assertEquals(1, audit.recent(10).size());
    }

    @Test
    void reopeningKeepsDataAndSchemaVersion() throws Exception {
        bans.ban(UUID, "tester", "integrity", NOW, 0L);
        database.close();

        database = Database.open(file.toPath());
        bans = new BanRepository(database);
        assertEquals(1, database.schemaVersion());
        assertTrue(new BanRepository(database).activeBan(UUID, NOW).isPresent());
    }
}
