package com.example.mcqq.core.command.sub;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.command.SubCommand;
import java.util.List;

/**
 * {@code /qq run <命令>} —— 以控制台身份跑一条命令，把回显打出来。
 *
 * <p>它和 QQ 侧那条路走的是**同一个平台方法**（{@code MinecraftPlatform.runCommand}），
 * 所以它的用处是：运维不用真去 QQ 里发一条，就能确认"回显到底抓不抓得到"。
 * 各平台的派发 API 不一样，这是唯一一处能当场看出某个平台有没有接好的地方。
 */
public final class RunCommand extends SubCommand {

    private final Bridge bridge;

    public RunCommand(Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "run";
    }

    @Override
    public String description() {
        return "以控制台身份跑一条命令并打印回显（验证命令回显那条路）";
    }

    @Override
    public List<String> execute(List<String> args) {
        if (args.isEmpty()) {
            return List.of("用法：/qq run <命令>（不带斜杠）");
        }
        List<String> lines = bridge.runCommand(String.join(" ", args));
        if (lines.isEmpty()) {
            return List.of("（这条命令没有回显）");
        }
        return lines;
    }
}
