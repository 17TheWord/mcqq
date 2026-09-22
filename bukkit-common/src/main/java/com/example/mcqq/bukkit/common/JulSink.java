package com.example.mcqq.bukkit.common;

import com.example.mcqq.core.Log;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The bridge's log lines, on the logger a Bukkit server hands its plugins: they land in the server console and in
 * {@code logs/latest.log} with the plugin's prefix, and nothing extra has to be embedded in the jar.
 */
public final class JulSink implements Log.Sink {

    private final Logger log;

    public JulSink(Logger log) {
        this.log = log;
    }

    @Override
    public void info(String message) {
        log.info(message);
    }

    @Override
    public void warn(String message, Throwable cause) {
        if (cause == null) {
            log.warning(message);
        } else {
            log.log(Level.WARNING, message, cause);
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
            log.severe(message);
        } else {
            log.log(Level.SEVERE, message, cause);
        }
    }
}
