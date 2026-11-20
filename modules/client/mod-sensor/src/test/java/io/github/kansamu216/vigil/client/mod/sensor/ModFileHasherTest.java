package io.github.kansamu216.vigil.client.mod.sensor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Mod 文件哈希：与标准测试向量一致，不存在文件时报错。
 */
class ModFileHasherTest {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    @Test
    void hashesKnownContent(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("mod.jar");
        Files.write(file, "abc".getBytes(UTF_8));
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                ModFileHasher.sha256(file));
    }

    @Test
    void hashesEmptyFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("empty.jar");
        Files.write(file, new byte[0]);
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                ModFileHasher.sha256(file));
    }

    @Test
    void failsOnMissingFile(@TempDir Path dir) {
        assertThrows(IOException.class, () -> ModFileHasher.sha256(dir.resolve("missing.jar")));
    }
}
