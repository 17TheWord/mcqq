package com.example.mcqq.bukkit.common;

import com.example.mcqq.core.command.CommandTree;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * {@code /qq} on a Bukkit server: one executor for the whole tree, driven by the core.
 *
 * <p>There is nothing to do here but hand the arguments over — dispatch, the permission check, the refusal
 * message, the prefix and tab completion all come from {@link CommandTree}. Adding a sub-command in the core
 * needs no change in this file.
 *
 * <p>Permissions are checked per node rather than once at the root, which is why {@code plugin.yml} declares the
 * root with its children instead of putting {@code permission:} on the command itself.
 */
final class QqCommand implements CommandExecutor, TabCompleter {

    private final CommandTree tree;
    private final BukkitPlatformBase platform;

    QqCommand(CommandTree tree, BukkitPlatformBase platform) {
        this.tree = tree;
        this.platform = platform;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        tree.run(new BukkitCommandSource(sender, platform), List.of(args));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return tree.completions(new BukkitCommandSource(sender, platform), List.of(args));
    }
}
