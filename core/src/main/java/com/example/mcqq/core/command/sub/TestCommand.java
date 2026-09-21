package com.example.mcqq.core.command.sub;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.command.SubCommand;
import java.util.List;

/**
 * {@code /qq test} — queues one message to every configured group, so an operator can find out whether the
 * credentials and the group openids are right without waiting for somebody to chat.
 *
 * <p>It goes down the same path a real event takes, which is the point: a test that used a different route
 * would prove nothing about the real one. What it cannot do is wait for the answers — this runs on the server's
 * command thread and a QQ round trip has no business blocking it — so the per-group results land in the log.
 */
public final class TestCommand extends SubCommand {

    private final Bridge bridge;

    public TestCommand(Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "test";
    }

    @Override
    public String description() {
        return "往每个配置的群各发一条测试消息（结果见服务端日志）";
    }

    @Override
    public List<String> execute(List<String> args) {
        return bridge.test();
    }
}
