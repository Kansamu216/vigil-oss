package io.github.kansamu216.vigil.platform.neoforge1211.client;

import io.github.kansamu216.vigil.client.mod.sensor.ModFileHasher;
import io.github.kansamu216.vigil.model.ModEntry;
import io.github.kansamu216.vigil.model.Observation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;

/**
 * 在后台线程构建 Mod 清单快照。
 *
 * <p>在后台线程计算摘要；单个文件失败只记录观测。</p>
 */
final class ModInventory {

    private ModInventory() {
    }

    static Snapshot collect() {
        List<ModEntry> entries = new ArrayList<ModEntry>();
        List<Observation> failures = new ArrayList<Observation>();
        for (IModInfo info : ModList.get().getMods()) {
            if (entries.size() >= 2048) {
                failures.add(new Observation("COLLECT_MOD_LIST_TRUNCATED", "", 0));
                break;
            }
            if (info.getOwningFile() == null || info.getOwningFile().getFile() == null) {
                continue;
            }
            Path file = info.getOwningFile().getFile().getFilePath();
            if (file == null || !Files.isRegularFile(file)) {
                continue;
            }
            try {
                entries.add(new ModEntry(
                        info.getModId(),
                        info.getVersion().toString(),
                        file.getFileName().toString(),
                        Files.size(file),
                        ModFileHasher.sha256(file)));
            } catch (Exception e) {
                failures.add(new Observation("COLLECT_MOD_HASH_FAILED", info.getModId(), 0));
            }
        }
        return new Snapshot(entries, failures);
    }

    static final class Snapshot {
        private final List<ModEntry> mods;
        private final List<Observation> observations;

        Snapshot(List<ModEntry> mods, List<Observation> observations) {
            this.mods = Collections.unmodifiableList(new ArrayList<ModEntry>(mods));
            this.observations = Collections.unmodifiableList(new ArrayList<Observation>(observations));
        }

        List<ModEntry> mods() {
            return mods;
        }

        List<Observation> observations() {
            return observations;
        }
    }
}
