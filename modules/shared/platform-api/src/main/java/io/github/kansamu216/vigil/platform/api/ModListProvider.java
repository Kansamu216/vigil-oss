package io.github.kansamu216.vigil.platform.api;

import io.github.kansamu216.vigil.model.ModEntry;
import java.nio.file.Path;
import java.util.List;

public interface ModListProvider {

    List<ModEntry> entries();

    Path fileOf(String modId);

    boolean isLoaded(String modId);
}
