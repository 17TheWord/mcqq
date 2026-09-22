package com.example.mcqq.bukkit.common;

import com.example.mcqq.core.command.CommandSource;
import org.bukkit.command.CommandSender;

/**
 * What a command caller looks like on a Bukkit server — the two things the core's command policy needs.
 *
 * <p>Bukkit is the one family here with real permission nodes, so the node the core asks about is looked up
 * as-is: {@code mcqq}, {@code mcqq.status}, and so on.
 */
final class BukkitCommandSource implements CommandSource {

    private final CommandSender sender;
    private final BukkitPlatformBase platform;

    BukkitCommandSource(CommandSender sender, BukkitPlatformBase platform) {
        this.sender = sender;
        this.platform = platform;
    }

    @Override
    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
    }

    @Override
    public void reply(String line) {
        platform.sendLine(sender, line);
    }
}
