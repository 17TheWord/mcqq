package com.example.mcqq.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The bridge's log lines on a loader that ships slf4j — which is both of the mod loaders, since Minecraft
 * itself logs through it. They land in {@code logs/latest.log} next to everything else the server says, with
 * nothing extra embedded in the jar.
 *
 * <p>It lives in the core because it is the same thirty lines on every mod platform; only Bukkit differs
 * (it hands a plugin a {@code java.util.logging.Logger} instead).
 */
public final class Slf4jSink implements Log.Sink {

    private final Logger log;

    public Slf4jSink() {
        this(LoggerFactory.getLogger(Constants.MOD_ID));
    }

    public Slf4jSink(Logger log) {
        this.log = log;
    }

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        if (cause == null) {
            log.warn(message);
        } else {
            log.warn(message, cause);
        }
    }

    @Override
    public void debug(String message) {
        // INFO 而不是 DEBUG/FINE：这个方法的门是配置里的 debug 开关（Log.debug 已经判过），
        // 而服务端的 logger 级别默认是 INFO —— 再按 DEBUG 输出的话，那些行在真机上一句都看不到，
        // 开关就等于没作用。宁可让打开开关的人看到几条带 [debug] 的 INFO 行。
        log.info("[debug] " + message);
    }

    @Override
    public void error(String message, Throwable cause) {
        if (cause == null) {
            log.error(message);
        } else {
            log.error(message, cause);
        }
    }
}
