package io.github.kansamu216.vigil.platform.api;

import io.github.kansamu216.vigil.model.ModEntry;
import java.nio.file.Path;
import java.util.List;

/**
 * Mod 清单来源。业务代码不得直接访问 ModList 或 FabricLoader。
 */
public interface ModListProvider {

    /** 当前加载的 Mod 列表；条目数受协议上限约束。 */
    List<ModEntry> entries();

    /** Mod 文件位置，仅用于流式哈希，不得整包读入内存。 */
    Path fileOf(String modId);

    boolean isLoaded(String modId);
}
