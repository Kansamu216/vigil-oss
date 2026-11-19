package io.github.kansamu216.vigil.platform.api;

import java.nio.file.Path;

/**
 * 服务端上下文：数据目录、日志与关服钩子。
 */
public interface ServerContext {

    /** 插件/模组私有数据目录，数据库与临时文件只能落在这里。 */
    Path dataDirectory();

    boolean isRunning();

    /** 注册关服清理；实现方必须保证只执行一次。 */
    void onShutdown(Runnable hook);

    void info(String message);

    void warn(String message);
}
