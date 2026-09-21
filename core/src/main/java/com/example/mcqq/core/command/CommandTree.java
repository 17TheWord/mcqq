package com.example.mcqq.core.command;

import com.example.mcqq.core.Constants;
import com.example.mcqq.core.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The command tree, plus everything an adapter needs from it: run something, complete something, and show what
 * there is.
 *
 * <p>An adapter never walks the tree. It builds a {@link CommandSource} for whoever is asking and calls
 * {@link #run} or {@link #completions(CommandSource, List)}; the permission check, the refusal message, the
 * output prefix and the guard against a command that throws all happen here, once, for every platform. That is
 * the whole reason the tree is in the core — a new sub-command is one class here and nothing anywhere else.
 *
 * <p>The {@code lines} / {@code completions} / {@code helpLines} methods are pure: they answer "what would be
 * printed" without printing, which is what makes all of this testable without a game.
 */
public final class CommandTree {

    /** A node, and how many leading arguments its path consumed. */
    private record Match(SubCommand node, int consumed) {
    }

    private final RootCommand root;

    public CommandTree(RootCommand root) {
        this.root = root;
    }

    public RootCommand root() {
        return root;
    }

    /** Which node would answer this argument list; the root itself when nothing matches. */
    public SubCommand resolve(List<String> args) {
        return match(args).node();
    }

    /**
     * Runs whatever matches on behalf of {@code source}, checking permission first and printing the outcome.
     *
     * <p>A command that throws is reported rather than allowed to escape: this runs inside a server's command
     * handling, and an exception there is a bad way to find out that a config file is malformed.
     */
    public void run(CommandSource source, List<String> args) {
        SubCommand target = resolve(args);
        if (!source.hasPermission(target.permissionNode())) {
            source.reply(Constants.PREFIX + " 你没有 " + target.permissionNode() + " 权限");
            return;
        }
        List<String> lines;
        try {
            lines = lines(args);
        } catch (RuntimeException e) {
            Log.error("命令 " + target.usage() + " 执行失败", e);
            source.reply(Constants.PREFIX + " 命令执行失败：" + e);
            return;
        }
        for (String line : lines) {
            source.reply(Constants.PREFIX + " " + line);
        }
    }

    /** What {@link #run} would print, without printing it. */
    public List<String> lines(List<String> args) {
        Match match = match(args);
        if (match.node() == root && !args.isEmpty()) {
            return List.of("没有子命令 " + args.get(0) + "；可用：" + String.join("|", names(root)),
                    "用法见 " + root.usage() + " help");
        }
        return match.node().execute(args.subList(match.consumed(), args.size()));
    }

    /** What {@code /qq help} prints: the tree, one line per node. */
    public List<String> helpLines() {
        List<String> lines = new ArrayList<>();
        collect(root, lines);
        return lines;
    }

    /** Every name that could follow this argument list, whether or not the caller may use it. */
    public List<String> completions(List<String> args) {
        if (args.isEmpty()) {
            return names(root);
        }
        SubCommand node = root;
        for (int i = 0; i < args.size() - 1; i++) {
            SubCommand next = child(node, args.get(i));
            if (next == null) {
                return List.of();
            }
            node = next;
        }
        String typed = args.get(args.size() - 1).toLowerCase(Locale.ROOT);
        return names(node).stream().filter(name -> name.startsWith(typed)).toList();
    }

    /** The same, minus what the caller is not allowed to run — a suggestion should not be a dead end. */
    public List<String> completions(CommandSource source, List<String> args) {
        List<String> allowed = new ArrayList<>();
        for (String name : completions(args)) {
            List<String> candidate = new ArrayList<>(args.subList(0, Math.max(0, args.size() - 1)));
            candidate.add(name);
            if (source.hasPermission(resolve(candidate).permissionNode())) {
                allowed.add(name);
            }
        }
        return allowed;
    }

    private static void collect(SubCommand node, List<String> lines) {
        lines.add(node.usage() + " — " + node.description());
        for (SubCommand child : node.children()) {
            collect(child, lines);
        }
    }

    private Match match(List<String> args) {
        SubCommand node = root;
        int consumed = 0;
        for (String arg : args) {
            SubCommand next = child(node, arg);
            if (next == null) {
                break;
            }
            node = next;
            consumed++;
        }
        return new Match(node, consumed);
    }

    private static SubCommand child(SubCommand node, String name) {
        for (SubCommand candidate : node.children()) {
            if (candidate.name().equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<String> names(SubCommand node) {
        return node.children().stream().map(SubCommand::name).toList();
    }
}
