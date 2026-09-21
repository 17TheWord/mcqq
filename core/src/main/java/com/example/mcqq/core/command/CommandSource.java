package com.example.mcqq.core.command;

/**
 * Who asked, and how to answer them — the two things only the platform knows.
 *
 * <p>It exists so the <em>policy</em> can live in the core instead of being repeated in every adapter: whether
 * a sub-command may run, what a refusal says, what prefix a line carries and what happens when a command
 * throws. An adapter builds one of these per invocation and hands it to {@link CommandTree#run}.
 *
 * <p>QueQiao does the same thing with a plain {@code Object} sender; a two-method interface gets the same
 * thinness without the casts.
 */
public interface CommandSource {

    /**
     * Whether the caller holds {@code node}. The platform decides what that means: Bukkit looks the node up,
     * a mod platform has no nodes and answers with its op level instead.
     */
    boolean hasPermission(String node);

    /** Prints one finished line — the core has already prefixed it with {@code Constants.PREFIX}. */
    void reply(String line);
}
