package com.example.mcqq.core.command.sub;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.command.SubCommand;
import java.util.List;

/** {@code /qq status} — what the bridge is doing right now. */
public final class StatusCommand extends SubCommand {

    private final Bridge bridge;

    public StatusCommand(Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "status";
    }

    @Override
    public String description() {
        return "显示每个 bot 的在线状态、绑定的群与配置里读出来的问题";
    }

    @Override
    public List<String> execute(List<String> args) {
        return bridge.statusLines();
    }
}
