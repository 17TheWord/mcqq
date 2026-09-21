package com.example.mcqq.core.command.sub;

import com.example.mcqq.core.command.CommandTree;
import com.example.mcqq.core.command.SubCommand;
import java.util.List;

/**
 * {@code /qq help} — the tree, generated from the tree itself.
 *
 * <p>It is a node like any other (so every platform's auto-registration picks it up), but it is the one node
 * that has to know the whole tree, which is why it is added once the tree exists.
 */
public final class HelpCommand extends SubCommand {

    private final CommandTree tree;

    public HelpCommand(CommandTree tree) {
        this.tree = tree;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String description() {
        return "列出所有子命令";
    }

    @Override
    public List<String> execute(List<String> args) {
        return tree.helpLines();
    }
}
