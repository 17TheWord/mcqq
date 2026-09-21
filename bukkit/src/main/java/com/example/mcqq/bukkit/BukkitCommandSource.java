package com.example.mcqq.bukkit;

import com.example.mcqq.core.command.CommandSource;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * What a command caller looks like on Bukkit — the two things the core's command policy needs from a platform.
 *
 * <p>Bukkit is the one platform here with real permission nodes, so the node the core asks about is looked up
 * as-is: {@code mcqq}, {@code mcqq.status}, and so on.
 */
final class BukkitCommandSource implements CommandSource {

    private final CommandSender sender;

    BukkitCommandSource(CommandSender sender) {
        this.sender = sender;
    }

    @Override
    public boolean hasPermission(String node) {
        return sender.hasPermission(node);
    }

    @Override
    public void reply(String line) {
        sender.sendMessage(Component.text(line));
    }
}
