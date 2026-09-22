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
        // FINE, not INFO: a server console should not be flooded by default.
        log.fine(message);
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
