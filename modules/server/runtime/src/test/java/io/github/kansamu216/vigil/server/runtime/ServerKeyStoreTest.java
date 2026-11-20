package io.github.kansamu216.vigil.server.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 密钥生命周期：首次生成、重启稳定、文件缺失或损坏时终止启动。
 */
class ServerKeyStoreTest {

    @Test
    void createsAndReloadsSameKey(@TempDir Path dir) throws Exception {
        ServerKeyStore first = ServerKeyStore.loadOrCreate(dir);
        assertEquals(64, first.fingerprint().length());
        assertTrue(Files.exists(dir.resolve(ServerKeyStore.PRIVATE_KEY_FILE)));
        assertTrue(Files.exists(dir.resolve(ServerKeyStore.PUBLIC_KEY_FILE)));

        ServerKeyStore second = ServerKeyStore.loadOrCreate(dir);
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(
                first.keyPair().getPrivate().getEncoded().length,
                second.keyPair().getPrivate().getEncoded().length);
    }

    @Test
    void privateKeyIsOwnerOnlyOnPosix(@TempDir Path dir) throws Exception {
        ServerKeyStore.loadOrCreate(dir);
        Set<PosixFilePermission> permissions;
        try {
            permissions = Files.getPosixFilePermissions(dir.resolve(ServerKeyStore.PRIVATE_KEY_FILE));
        } catch (UnsupportedOperationException e) {
            return;
        }
        assertTrue(permissions.contains(PosixFilePermission.OWNER_READ));
        assertTrue(!permissions.contains(PosixFilePermission.GROUP_READ));
        assertTrue(!permissions.contains(PosixFilePermission.OTHERS_READ));
    }

    @Test
    void refusesPartialKeyMaterial(@TempDir Path dir) throws Exception {
        ServerKeyStore.loadOrCreate(dir);
        Files.delete(dir.resolve(ServerKeyStore.PUBLIC_KEY_FILE));

        IOException error = assertThrows(IOException.class, () -> ServerKeyStore.loadOrCreate(dir));
        assertTrue(error.getMessage().contains("incomplete"));
    }

    @Test
    void refusesCorruptedKeyMaterial(@TempDir Path dir) throws Exception {
        ServerKeyStore.loadOrCreate(dir);
        Files.write(dir.resolve(ServerKeyStore.PRIVATE_KEY_FILE), new byte[] {1, 2, 3, 4});

        assertThrows(IOException.class, () -> ServerKeyStore.loadOrCreate(dir));
    }

    @Test
    void refusesMismatchedKeyPair(@TempDir Path dir) throws Exception {
        ServerKeyStore first = ServerKeyStore.loadOrCreate(dir.resolve("a"));
        ServerKeyStore second = ServerKeyStore.loadOrCreate(dir.resolve("b"));
        Files.copy(
                dir.resolve("a").resolve(ServerKeyStore.PRIVATE_KEY_FILE),
                dir.resolve("b").resolve(ServerKeyStore.PRIVATE_KEY_FILE),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        IOException error = assertThrows(
                IOException.class, () -> ServerKeyStore.loadOrCreate(dir.resolve("b")));
        assertTrue(error.getMessage().contains("do not match"));
        assertEquals(64, first.fingerprint().length());
        assertEquals(64, second.fingerprint().length());
    }
}
