package com.example.mcqq.bukkit.common;

import com.example.mcqq.core.Constants;
import com.example.mcqq.core.Log;
import com.example.mcqq.core.MinecraftPlatform;
import com.example.mcqq.core.command.CommandTree;
import java.nio.file.Path;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The half of a Bukkit-family adapter that is the same on every server: where the config lives, what the
 * platform is called, how a line reaches somebody, and how the command tree gets registered.
 *
 * <p>It is compiled against <b>spigot-api</b> — the lowest common denominator of the family — so the Paper
 * variant and the Spigot variant both depend on it unchanged. Everything Paper-only stays in the variant that
 * needs it: Adventure components (which Spigot does not ship), the Folia region schedulers, and
 * {@code AsyncChatEvent}.
 *
 * <p>The one thing a variant must supply is {@link #sendLine}: the core writes {@code §}-coloured strings, and
 * how those become a message is exactly what differs between a Paper server and a Spigot one.
 */
public abstract class BukkitPlatformBase implements MinecraftPlatform {

    /** A {@link JavaPlugin} rather than a {@code Plugin}: {@code getCommand} lives on the former now. */
    protected final JavaPlugin plugin;
    protected final Server server;

    protected BukkitPlatformBase(JavaPlugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
    }

    /** Hands one finished line to one recipient. Paper renders it as a component, Spigot as a legacy string. */
    protected abstract void sendLine(CommandSender target, String line);

    @Override
    public String label() {
        return server.getName().toLowerCase(Locale.ROOT) + "-" + minecraftVersion();
    }

    /**
     * The game version, from the classic Bukkit call — {@code Server.getMinecraftVersion()} is Paper-only and
     * would not compile against spigot-api. Paper overrides this with the nicer one.
     *
     * <p>{@code getBukkitVersion()} answers something like {@code 26.2-R0.1-SNAPSHOT}, so the version is the
     * part before the first dash.
     */
    protected String minecraftVersion() {
        String raw = Bukkit.getBukkitVersion();
        int dash = raw.indexOf('-');
        return dash < 0 ? raw : raw.substring(0, dash);
    }

    /**
     * The {@code plugins/} folder rather than the plugin's own data folder: the core appends {@code mcqq/}
     * itself, so the config lands in {@code plugins/mcqq/config.yml} — the same relative place it occupies as
     * {@code config/mcqq/config.yml} on Fabric.
     */
    @Override
    public Path configDir() {
        return plugin.getDataFolder().getParentFile().toPath();
    }

    @Override
    public void broadcast(String line) {
        // `broadcastMessage(String)` is deprecated on Paper, so the players are walked here instead — and a
        // variant that has to schedule per player (Folia) overrides this method rather than this loop.
        onMainThread(() -> {
            for (Player player : server.getOnlinePlayers()) {
                sendLine(player, line);
            }
        });
    }

    @Override
    public void onMainThread(Runnable task) {
        server.getScheduler().runTask(plugin, task);
    }

    @Override
    public void registerCommands(CommandTree tree) {
        // Declared in plugin.yml, so the server has already built the command by the time this runs.
        plugin.getCommand(Constants.COMMAND).setExecutor(new QqCommand(tree, this));
    }
}
