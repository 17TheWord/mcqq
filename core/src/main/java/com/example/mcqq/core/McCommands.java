package com.example.mcqq.core;

import io.github.skiesworld.qqbot.event.QQMessageEvent;
import java.util.List;
import java.util.Optional;

/**
 * QQ 侧发来的命令：判前缀、判权限、跑、把回显聚合成**一条**回给别人。
 *
 * <p>三个面（群 / 子频道 / 私聊）共用同一套规则 —— 前缀、权限、聚合。只有一处不同：
 * **拒绝时说不说话**。群与子频道回一句"你没有权限"（那里的人知道机器人在，反馈有用）；
 * 私聊**静默**，不向陌生人确认这个机器人在这儿。
 *
 * <p>回显必须聚合成一条：被动回复次数有限（单聊 4 次、群聊 5 次，每条入站消息），
 * 逐行发会在第 5 行开始失败。
 */
final class McCommands {

    /**
     * 一条回复的长度上限。QQ 没有明说单条上限，但超长会被平台拒掉，而且回复次数有限、
     * 不适合分页 —— 所以宁可截断。
     */
    private static final int MAX_LENGTH = 1500;

    private final MinecraftPlatform platform;
    private final BridgeConfig config;
    private final BridgeConfig.Bot bot;

    McCommands(MinecraftPlatform platform, BridgeConfig config, BridgeConfig.Bot bot) {
        this.platform = platform;
        this.config = config;
        this.bot = bot;
    }

    /** 这条消息是命令吗？是就处理掉并返回 true（执行成功、被拒、被静默都算处理了）。 */
    boolean handle(QQMessageEvent message) {
        if (!config.commandsEnabled()) {
            return false;
        }
        String prefix = config.commandPrefix();
        String text = message.content() == null ? "" : message.content().strip();
        if (prefix.isEmpty() || !text.startsWith(prefix)) {
            return false;
        }
        String command = text.substring(prefix.length()).strip();
        if (command.isEmpty()) {
            reply(message, "用法：" + prefix + " <命令>");
            return true;
        }

        // 会话属于谁：群 / 子频道看配置里的目标，私聊没有目标。
        Optional<BridgeConfig.Target> target = bot.target(QqEvents.conversationId(message));
        CommandAccess access = target.map(BridgeConfig.Target::commandAccess).orElseGet(bot::directAccess);
        boolean privateChat = target.isEmpty();

        if (!access.permits(QqEvents.of(message))) {
            if (privateChat) {
                // 不出声：陌生人试命令时，不该从这里确认这个机器人在这儿。
                Log.debug("私聊里有人想执行命令但不在白名单，已静默");
            } else {
                reply(message, "你没有权限执行命令");
            }
            return true;
        }

        Log.info("执行命令（" + (target.map(BridgeConfig.Target::label).orElse("私聊")) + "）：" + command);
        reply(message, render(ServerConsole.run(platform, command)));
        return true;
    }

    /** 回显聚合成一条；超长截断。 */
    private static String render(List<String> lines) {
        String text = String.join("\n", lines);
        return text.length() <= MAX_LENGTH ? text : text.substring(0, MAX_LENGTH) + "\n…（已截断）";
    }

    /** 回不出去（网络、审核、限流）不能让桥接的消息循环跟着倒。 */
    private static void reply(QQMessageEvent message, String text) {
        try {
            message.reply(text);
        } catch (RuntimeException e) {
            Log.warn("把命令的回显发回 QQ 失败", e);
        }
    }
}
