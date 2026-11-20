package io.github.kansamu216.vigil.server.storage;

/**
 * 存储层失败。上层应进入降级（保留内存态、停止依赖持久证据的自动处置），
 * 不得把存储故障当成作弊证据。
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
