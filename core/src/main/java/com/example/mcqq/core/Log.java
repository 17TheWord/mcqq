package com.example.mcqq.core;

/**
 * The bridge's logging seam.
 *
 * <p>The platforms do not agree on a logging library — Minecraft ships slf4j, a Bukkit server hands a plugin a
 * {@code java.util.logging.Logger} — so the core talks to this instead of picking one of them. An adapter
 * installs its own sink at startup.
 *
 * <p>Until then lines go nowhere: a library that decides on its own to write to stdout would surprise whoever
 * dropped it into a server directory.
 */
public final class Log {

    /** What an adapter implements; three levels are all the bridge uses. {@code cause} may be null. */
    public interface Sink {
        void info(String message);

        void warn(String message, Throwable cause);

        void error(String message, Throwable cause);

        void debug(String message);
    }

    private static final Sink SILENT = new Sink() {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message, Throwable cause) {
        }

        @Override
        public void error(String message, Throwable cause) {
        }

        @Override
        public void debug(String message) {
        }
    };

    private static volatile Sink sink = SILENT;

    /**
     * Whether {@link #debug} says anything. Off by default, and turned on from the config — the lines it
     * carries are the ones an operator needs to answer "why did this message not arrive".
     */
    private static volatile boolean debugEnabled = false;

    private Log() {
    }

    /** Called once by an adapter, before anything else. A null sink restores silence. */
    public static void install(Sink replacement) {
        sink = replacement == null ? SILENT : replacement;
    }

    public static void info(String message) {
        sink.info(message);
    }

    public static void warn(String message) {
        sink.warn(message, null);
    }

    public static void warn(String message, Throwable cause) {
        sink.warn(message, cause);
    }

    public static void error(String message, Throwable cause) {
        sink.error(message, cause);
    }

    /** Set from the config; see {@code debug:} in {@code config.example.yml}. */
    public static void debugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    /** Only worth calling for something an operator would have to ask about; it is dropped when off. */
    public static void debug(String message) {
        if (debugEnabled) {
            sink.debug(message);
        }
    }
}
