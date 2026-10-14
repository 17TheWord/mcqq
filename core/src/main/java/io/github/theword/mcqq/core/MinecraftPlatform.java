package io.github.theword.mcqq.core;

import io.github.theword.mcqq.core.command.CommandTree;
import java.nio.file.Path;

/**
 * 桥接需要服务端提供的全部东西 —— 一条缝，MC 的类一个都不跨过来。
 *
 * <p>adapter 交进来的是已带 {@code §} 颜色码的文本，所以 core 不 import 任何 MC 类、离线可测。
 * 入站（QQ → MC）走 {@link #broadcast}；出站由 adapter 在自己的监听里调 {@link Bridge#forward}。
 */
public interface MinecraftPlatform {

    /** What {@code /qq status} prints first, e.g. {@code fabric-26.1} or {@code paper-1.20.1}. */
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
