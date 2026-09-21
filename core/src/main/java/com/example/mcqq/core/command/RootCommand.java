package com.example.mcqq.core.command;

import com.example.mcqq.core.Constants;

/**
 * The {@code /qq} node itself. Sub-commands are added to it by whoever builds the tree
 * (see {@code Bridge}), which is what keeps this class free of the bridge's internals.
 */
public final class RootCommand extends SubCommand {

    @Override
    public String name() {
        return Constants.COMMAND;
    }

    @Override
    public String description() {
        return "QQ 桥接的主命令";
    }

    @Override
    public java.util.List<String> execute(java.util.List<String> args) {
        return java.util.List.of("用法：" + Constants.COMMAND + " <" + subNames() + ">，详见 "
                + usage() + " help");
    }

    private String subNames() {
        return String.join("|", children().stream().map(SubCommand::name).toList());
    }
}
