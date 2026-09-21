package com.example.mcqq.core.command.sub;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.command.SubCommand;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /qq reload} — re-reads the config and replaces the running bots. Prints the outcome and then the same
 * status {@code /qq status} would, because the reason to reload is almost always to see whether it worked.
 */
public final class ReloadCommand extends SubCommand {

    private final Bridge bridge;

    public ReloadCommand(Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "reload";
    }

    @Override
    public String description() {
        return "重读配置并换掉正在跑的 bot（不会重启服务器）";
    }

    @Override
    public List<String> execute(List<String> args) {
        List<String> lines = new ArrayList<>();
        lines.add(bridge.reload());
        lines.addAll(bridge.statusLines());
        return lines;
    }
}
