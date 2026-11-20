package io.github.kansamu216.vigil.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ObservationCodesTest {

    @Test
    void isStatsAcceptsOnlyStatsPrefixedCodes() {
        assertTrue(ObservationCodes.isStats("STATS_"));
        assertTrue(ObservationCodes.isStats("STATS_SPEED"));

        assertFalse(ObservationCodes.isStats(null));
        assertFalse(ObservationCodes.isStats(""));
        assertFalse(ObservationCodes.isStats("COLLECT_SPEED"));
        assertFalse(ObservationCodes.isStats("SPEED"));
        assertFalse(ObservationCodes.isStats("stats_speed"));
    }

    @Test
    void isCollectAcceptsOnlyCollectPrefixedCodes() {
        assertTrue(ObservationCodes.isCollect("COLLECT_"));
        assertTrue(ObservationCodes.isCollect("COLLECT_TIMEOUT"));

        assertFalse(ObservationCodes.isCollect(null));
        assertFalse(ObservationCodes.isCollect(""));
        assertFalse(ObservationCodes.isCollect("STATS_TIMEOUT"));
        assertFalse(ObservationCodes.isCollect("TIMEOUT"));
        assertFalse(ObservationCodes.isCollect("collect_timeout"));
    }

    @Test
    void toStatsKeepsExistingPrefixAsIs() {
        assertEquals("STATS_SPEED", ObservationCodes.toStats("STATS_SPEED"));
        assertEquals("STATS_", ObservationCodes.toStats("STATS_"));
    }

    @Test
    void toStatsAddsPrefixForPlainCode() {
        assertEquals("STATS_SPEED", ObservationCodes.toStats("SPEED"));
    }

    @Test
    void toStatsFitsAtBoundary() {
        String base58 = repeat('x', 58);
        String result = ObservationCodes.toStats(base58);
        assertEquals(64, result.length());
        assertEquals("STATS_" + base58, result);
    }

    @Test
    void toStatsTruncatesOverlongBaseTo58Chars() {
        String base59 = repeat('y', 59);
        String result = ObservationCodes.toStats(base59);
        assertEquals(64, result.length());
        assertEquals("STATS_" + repeat('y', 58), result);

        String base100 = repeat('z', 100);
        String result100 = ObservationCodes.toStats(base100);
        assertEquals(64, result100.length());
        assertEquals("STATS_" + repeat('z', 58), result100);
    }

    @Test
    void toStatsRejectsNullOrEmpty() {
        assertThrows(IllegalArgumentException.class, () -> ObservationCodes.toStats(null));
        assertThrows(IllegalArgumentException.class, () -> ObservationCodes.toStats(""));
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }
}
