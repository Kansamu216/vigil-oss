package io.github.kansamu216.vigil.client.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 客户端配置：缺少信任锚时不自动接受任何服务器。
 */
class ClientSettingsTest {

    @Test
    void createsTemplateWithoutTrustAnchor(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ClientSettings.FILE_NAME);
        ClientSettings settings = ClientSettings.load(file);

        assertTrue(Files.exists(file));
        assertTrue(settings.enabled());
        assertFalse(settings.hasTrustAnchor());
        assertTrue(settings.problem().contains("empty"));
    }

    @Test
    void acceptsValidFingerprint(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ClientSettings.FILE_NAME);
        Files.write(file, (
                "trust.enabled=true\n"
                        + "trust.fingerprint=0123456789ABCDEF0123456789abcdef0123456789abcdef0123456789ABCDEF\n")
                .getBytes(java.nio.charset.Charset.forName("UTF-8")));

        ClientSettings settings = ClientSettings.load(file);
        assertTrue(settings.hasTrustAnchor());
        assertEquals(64, settings.fingerprint().length());
        assertEquals(settings.fingerprint().toLowerCase(java.util.Locale.ROOT), settings.fingerprint());
    }

    @Test
    void invalidFingerprintDisablesHandshake(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ClientSettings.FILE_NAME);
        Files.write(file, "trust.fingerprint=xyz\n".getBytes(java.nio.charset.Charset.forName("UTF-8")));

        ClientSettings settings = ClientSettings.load(file);
        assertFalse(settings.hasTrustAnchor());
        assertTrue(settings.problem().contains("64 hex"));
    }

    @Test
    void explicitDisableWins(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(ClientSettings.FILE_NAME);
        Files.write(file, (
                "trust.enabled=false\n"
                        + "trust.fingerprint=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef\n")
                .getBytes(java.nio.charset.Charset.forName("UTF-8")));

        assertFalse(ClientSettings.load(file).hasTrustAnchor());
    }
}
