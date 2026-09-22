package com.example.mcqq.spigot;

import com.example.mcqq.bukkit.common.BukkitPlatformBase;
import com.example.mcqq.core.Log;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Spigot variant of the seam. Everything it does comes from {@code bukkit-common}; the one thing it has to
 * say for itself is how a line reaches somebody.
 *
 * <p>Spigot has no Adventure, so the {@code §} codes the core writes are handed over as the legacy string they
 * are — the server renders them. That is the whole difference from the Paper variant.
 */
final class SpigotPlatform extends BukkitPlatformBase {

    SpigotPlatform(JavaPlugin plugin) {
        super(plugin);
        Log.info("平台 " + label() + "，主线程调度走 经典调度器");
    }

    @Override
    protected void sendLine(CommandSender target, String line) {
        target.sendMessage(line);
    }
}
