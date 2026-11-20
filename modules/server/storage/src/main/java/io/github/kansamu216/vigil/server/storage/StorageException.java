package io.github.kansamu216.vigil.server.storage;

/**
 * 存储层失败。上层应进入降级，停止依赖持久化数据的自动处置。
 */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public StorageException(String message) {
        super(message);
    }
}
