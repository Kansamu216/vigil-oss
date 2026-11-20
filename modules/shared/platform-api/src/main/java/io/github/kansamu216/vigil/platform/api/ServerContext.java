package io.github.kansamu216.vigil.platform.api;

import java.nio.file.Path;

public interface ServerContext {

    Path dataDirectory();

    boolean isRunning();

    void onShutdown(Runnable hook);

    void info(String message);

    void warn(String message);
}
