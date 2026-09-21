package com.example.mcqq.core;

import com.example.mcqq.core.command.CommandTree;
import java.nio.file.Path;

/**
 * Everything the bridge needs from the server it runs inside, and nothing more.
 *
 * <p>This is the whole seam. {@code MinecraftServer}, {@code ServerPlayer} and {@code Component} never cross
 * it: an adapter hands over text that already carries {@code §} colour codes and decides for itself how to
 * render them, which is what keeps the core free of any Minecraft class and testable without a game.
 *
 * <p>The inbound direction (QQ → Minecraft) is {@link #broadcast}; the outbound direction (Minecraft → QQ) is
 * the adapter calling {@link Bridge#forward} from its own event listeners.
 */
public interface MinecraftPlatform {

    /** What {@code /qq status} prints first, e.g. {@code fabric-26.1.2} or {@code paper-26.1.2}. */
    String label();

    /** Where {@code mc-qq/config.yml} lives: {@code config/} on Fabric, the plugin's data folder on Bukkit. */
    Path configDir();

    /**
     * Puts one line in front of every player and in the console. The implementation is responsible for
     * reaching the main thread — a server tick owns a player's packet queue, and Folia has no single one.
     */
    void broadcast(String line);

    /** Runs work on the thread that owns the game state: {@code server.execute} or the platform's scheduler. */
    void onMainThread(Runnable task);

    /**
     * Registers {@code /qq} and its sub-commands in whatever way the platform knows how.
     *
     * <p>Everything the platform needs is on the tree: walk {@link CommandTree#root()}'s children to build the
     * literals, ask each node for its {@code name()}, {@code description()} and {@code permissionNode()}, and
     * hand raw argument lists to {@link CommandTree#execute} / {@link CommandTree#complete}. Because it is all
     * derived, adding a sub-command in the core changes nothing here.
     */
    void registerCommands(CommandTree tree);
}
