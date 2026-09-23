package com.example.mcqq.core.command.sub;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.Log;
import com.example.mcqq.core.command.SubCommand;
import java.util.List;

/**
 * {@code /qq run <命令>} —— 以控制台身份跑一条命令，回显稍后进服务端日志。
 *
 * <p>它和 QQ 侧那条路走的是**同一份实现**（{@code ServerConsole.run} → 同一个 RCON 线程），
 * 所以它的用处是：运维不用真去 QQ 里发一条，就能确认"回显到底抓不抓得到"。
 *
 * <p>回显为什么进日志而不是当场打回聊天栏：这条命令跑在服务端主线程上，而 RCON 收下的命令恰恰
 * 要等主线程去执行 —— 当场同步等就是自己等自己，必挂满超时。交给后台线程后，结果回来时已经没有
 * "这次调用"可以填了，日志是哪条命令的谁都能对得上。
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
        return "以控制台身份跑一条命令（RCON 线程执行，回显稍后进服务端日志）";
    }

    @Override
    public List<String> execute(List<String> args) {
        if (args.isEmpty()) {
            return List.of("用法：/qq run <命令>（不带斜杠）");
        }
        String command = String.join(" ", args);
        bridge.submitCommand(command, lines -> {
            Log.info("「/qq run " + command + "」的回显：");
            for (String line : lines) {
                Log.info("  " + line);
            }
        });
        return List.of("命令已交给 RCON 线程，回显稍后进服务端日志。");
    }
}
