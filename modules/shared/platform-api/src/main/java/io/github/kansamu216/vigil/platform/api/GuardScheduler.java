package io.github.kansamu216.vigil.platform.api;

/**
 * 调度出口。
 *
 * <p>主线程只做轻量状态变更；哈希、截图、压缩、数据库写入必须走 {@link #runAsync}。
 * 关服时必须取消全部任务，实现方负责不留下悬挂线程。</p>
 */
public interface GuardScheduler {

    /** 在主线程执行；实现方保证异常不会冒泡到游戏循环。 */
    void runOnMainThread(Runnable task);

    /** 在受控后台线程池执行，线程数与队列上限由实现方固定。 */
    void runAsync(Runnable task);

    /** 延迟执行一次，返回可用于取消的句柄。 */
    Cancellable scheduleOnce(long delayMillis, Runnable task);

    /** 周期执行，返回可用于取消的句柄。 */
    Cancellable scheduleRepeating(long periodMillis, Runnable task);

    /** 取消所有未执行任务，用于关服。 */
    void shutdown();

    interface Cancellable {
        void cancel();
    }
}
