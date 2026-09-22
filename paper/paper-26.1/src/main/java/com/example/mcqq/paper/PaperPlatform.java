package com.example.mcqq.paper;

import com.example.mcqq.bukkit.common.BukkitPlatformBase;
import com.example.mcqq.core.Log;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Paper variant of the seam: everything shared lives in {@code bukkit-common}, and what is left here is
 * exactly what Paper adds over plain Bukkit — Adventure components, and Folia's region schedulers.
 */
final class PaperPlatform extends BukkitPlatformBase {

    private final boolean folia;

    PaperPlatform(JavaPlugin plugin) {
        super(plugin);
        this.folia = isFolia();
        Log.info("平台 " + label() + "，主线程调度走 " + (folia ? "区域调度器" : "经典调度器"));
    }

    /** Paper has a direct answer for this, so it does not have to be picked out of the classic string. */
    @Override
    protected String minecraftVersion() {
        return server.getMinecraftVersion();
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

    /**
     * Paper deprecated the legacy strings, so the {@code §} codes the core writes are turned into a real
     * component here. That is the only difference from the shared implementation.
     */
    @Override
    protected void sendLine(CommandSender target, String line) {
        target.sendMessage(LegacyComponentSerializer.legacySection().deserialize(line));
    }

    @Override
    public void broadcast(String line) {
        if (!folia) {
            super.broadcast(line);
            return;
        }
        // Folia has no single main thread: every player belongs to a region, and touching one from another
        // region throws. So each player is handed the line on their own thread.
        Component rendered = LegacyComponentSerializer.legacySection().deserialize(line);
        for (Player player : server.getOnlinePlayers()) {
            player.getScheduler().execute(plugin, () -> player.sendMessage(rendered), null, 1);
        }
    }

    @Override
    public void onMainThread(Runnable task) {
        if (folia) {
            server.getGlobalRegionScheduler().execute(plugin, task);
        } else {
            super.onMainThread(task);
        }
    }
}
