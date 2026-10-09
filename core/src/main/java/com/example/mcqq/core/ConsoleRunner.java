package com.example.mcqq.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * RCON 命令的那一条专用线程。
 *
 * <p>单线程是刻意的：RCON 是请求/响应一一配对的协议，服务端的命令队列本来就得排队被主线程消化 ——
 * 并行不会更快，只会把"谁在等谁"变成"好几个一起等"。
 *
 * <p>要躲开的又是"等"本身：两个调用方 —— MC 主线程（游戏内 {@code /qq run}）与 SDK 的事件线程
 * （QQ 来的命令）—— 都不能等。RCON 收下的命令要等服务端主线程去执行，主线程同步等 RCON 就是
 * 自己等自己，必然挂满超时。
 *
 * <p>队列有上限是另一半要躲开的：服务端没开 RCON 时每条任务都要烧满超时才失败，无界队列会让
 * 后面的命令排队几分钟，而结果早已没有意义。满了就当场拒，让调用方立刻说"忙"。
 */
final class ConsoleRunner {

    /** 最多同时排几条命令（含正在跑的那条）。 */
    static final int QUEUE_LIMIT = 16;

    private final Executor queue;

    ConsoleRunner() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_LIMIT),
                runnable -> {
                    Thread thread = new Thread(runnable, Constants.MOD_ID + "-rcon");
                    thread.setDaemon(true);
                    return thread;
                });
        // 闲置时连唯一那条线程也退掉（跑完一批命令后不留驻线程），下次提交再起来。
        pool.allowCoreThreadTimeOut(true);
        this.queue = pool;
    }

    /** 测试缝：传 {@code Runnable::run} 时"提交"就是"就地执行"，同步断言照写。 */
    ConsoleRunner(Executor direct) {
        this.queue = direct;
    }

    /** 提交；false = 队列满了被拒 —— 拒绝的话得由调用方当场说。 */
    boolean submit(Runnable task) {
        try {
            queue.execute(task);
            return true;
        } catch (RejectedExecutionException e) {
            return false;
        }
    }
}
