package com.example.mcqq.bukkit;

import com.example.mcqq.core.Constants;
import com.example.mcqq.core.Log;
import com.example.mcqq.core.MinecraftPlatform;
import com.example.mcqq.core.command.CommandTree;
import java.nio.file.Path;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Bukkit side of the seam, and the only file in this project that knows what a {@code Server} is on the
 * bridge's behalf.
 *
 * <p>Two things are worth knowing about a Bukkit plugin here:
 *
 * <ul>
 *   <li>{@link #configDir()} hands back the {@code plugins/} folder rather than the plugin's own data folder,
 *       because the core appends {@code mc-qq/} itself — so the config lands in {@code plugins/mc-qq/config.yml},
 *       the same relative place it occupies under {@code config/mc-qq/config.yml} on Fabric.
 *   <li>The main thread is not one thread on every server. Folia replaces it with region schedulers, so the
 *       global one is probed for once at startup and used when it is there; otherwise the classic scheduler
 *       does the same job on Paper and Spigot.
 * </ul>
 *
 * <p>The field is a {@link JavaPlugin} rather than a {@code Plugin} because that is where {@code getCommand}
 * lives now: the {@code Plugin} interface no longer carries it.
 */
final class BukkitPlatform implements MinecraftPlatform {

    private final JavaPlugin plugin;
    private final Server server;
    private final boolean folia;

    BukkitPlatform(JavaPlugin plugin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.folia = isFolia();
        Log.info("平台 " + label() + "，主线程调度走 " + (folia ? "区域调度器" : "经典调度器"));
    }

    /**
     * Whether this is Folia.
     *
     * <p>The obvious probe — asking for a region scheduler — does not work: Paper implements those too, so it
     * answers on both. What actually differs is the server jar, and Folia's is the only one carrying this class.
     */
    private static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException notFolia) {
            return false;
        }
    }

    @Override
    public String label() {
        return server.getName().toLowerCase(Locale.ROOT) + "-" + server.getMinecraftVersion();
    }

    @Override
    public Path configDir() {
        return plugin.getDataFolder().getParentFile().toPath();
    }

    @Override
    public void broadcast(String line) {
        // Paper deprecated the legacy `broadcastMessage(String)` in favour of Adventure, so the § codes the
        // core writes are turned into a real component here instead of being handed over as a legacy string.
        Component rendered = LegacyComponentSerializer.legacySection().deserialize(line);
        if (folia) {
            // Folia has no single main thread: every player belongs to a region, and touching one from another
            // region throws. So each player is handed the line on their own thread.
            for (Player player : server.getOnlinePlayers()) {
                player.getScheduler().execute(plugin, () -> player.sendMessage(rendered), null, 1);
            }
            return;
        }
        onMainThread(() -> server.sendMessage(rendered));
    }

    @Override
    public void onMainThread(Runnable task) {
        if (folia) {
            server.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            server.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void registerCommands(CommandTree tree) {
        // Declared in plugin.yml, so the server has already built the command by the time this runs.
        plugin.getCommand(Constants.COMMAND).setExecutor(new QqCommand(tree));
    }
}
