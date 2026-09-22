package com.example.mcqq.bukkit;

import com.example.mcqq.core.command.CommandTree;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jspecify.annotations.NonNull;

/**
 * {@code /qq} on Bukkit: one executor for the whole tree, driven by the core.
 *
 * <p>There is nothing to do here but hand the arguments over — dispatch, the permission check, the refusal
 * message, the prefix and tab completion all come from {@link CommandTree}. Adding a sub-command in the core
 * needs no change in this file, and {@link BukkitCommandSource} is the only Bukkit-specific part left.
 *
 * <p>Permissions are checked per node rather than once at the root, which is why {@code plugin.yml} declares the
 * root with its children instead of putting {@code permission:} on the command itself.
 */
final class QqCommand implements CommandExecutor, TabCompleter {

    private final CommandTree tree;

    QqCommand(CommandTree tree) {
        this.tree = tree;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        tree.run(new BukkitCommandSource(sender), List.of(args));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label, String @NonNull [] args) {
        return tree.completions(new BukkitCommandSource(sender), List.of(args));
    }
}
