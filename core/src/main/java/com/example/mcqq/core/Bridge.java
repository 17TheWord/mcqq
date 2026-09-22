package com.example.mcqq.core;

import com.example.mcqq.core.command.CommandTree;
import com.example.mcqq.core.command.RootCommand;
import com.example.mcqq.core.command.sub.BindCommand;
import com.example.mcqq.core.command.sub.HelpCommand;
import com.example.mcqq.core.command.sub.ReloadCommand;
import com.example.mcqq.core.command.sub.RunCommand;
import com.example.mcqq.core.command.sub.StatusCommand;
import com.example.mcqq.core.command.sub.TestCommand;
import com.example.mcqq.core.command.sub.TemplatesCommand;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The bridge's lifecycle: config in, bots up, and the one place {@code /qq reload} can swap them.
 *
 * <p>The platform's entry point creates one of these at startup and calls {@link #start()} when the server is
 * up, {@link #stop()} when it goes down. Everything else — reading the config, keeping the bots, routing both
 * directions, and the command tree — happens here, so an adapter never repeats that logic.
 *
 * <p>Listeners and commands are registered by the platform <em>once</em> and read the current runtime through
 * this object, which is what lets a reload replace the bots without stacking a second copy of every listener.
 */
public final class Bridge {

    private final MinecraftPlatform platform;
    private final CommandTree commands;
    /**
     * Groups that talked to a bot without being bound. It lives here rather than in the runtime because a
     * {@code /qq reload} replaces the runtime, and the moment an operator reloads is exactly the moment they are
     * about to run {@code /qq bind}.
     */
    private final UnboundGroups unboundGroups = new UnboundGroups();
    private volatile BridgeRuntime runtime;
    private volatile boolean started;

    public Bridge(MinecraftPlatform platform) {
        this.platform = platform;
        this.commands = buildCommands();
    }

    /** The {@code /qq} tree. The platform registers whatever is in here and never walks it itself. */
    public CommandTree commands() {
        return commands;
    }

    private CommandTree buildCommands() {
        RootCommand root = new RootCommand();
        // The tree first, so the one node that has to see all of it can be added to it.
        CommandTree tree = new CommandTree(root);
        root.addChild(new StatusCommand(this));
        root.addChild(new ReloadCommand(this));
        root.addChild(new BindCommand(this));
        root.addChild(new RunCommand(this));
        root.addChild(new TemplatesCommand(this));
        root.addChild(new TestCommand(this));
        root.addChild(new HelpCommand(tree));
        return tree;
    }

    /** Reads the config and brings the bots up. Never throws into server startup. */
    public synchronized void start() {
        started = true;
        swap(replacement());
    }

    /** Takes the bots down; the server is going away. */
    public synchronized void stop() {
        started = false;
        swap(null);
    }

    /** Re-reads the config and replaces the running bots. Returns a one-line summary for the caller. */
    public synchronized String reload() {
        if (!started) {
            return "桥接未运行（服务器还没起来，或已经关了）";
        }
        BridgeRuntime replacement = replacement();
        swap(replacement);
        return replacement == null ? "配置读取失败，详见服务端日志" : "已重载配置";
    }

    /** What {@code /qq status} prints: one line per bot, then anything that went wrong. */
    public List<String> statusLines() {
        BridgeRuntime active = runtime;
        List<String> lines = new ArrayList<>();
        if (active == null) {
            lines.add("桥接未运行（服务器还没起来，或上一次 reload 失败）");
        } else {
            lines.addAll(active.statusLines());
        }
        // The groups that talked to a bot without being bound. This is the answer to "where does the
        // group-openid come from", and it is here because /qq status is the first thing an operator runs.
        List<UnboundGroups.Seen> unbound = unboundGroups.all();
        if (!unbound.isEmpty()) {
            lines.add("收到过消息但没绑定的群（敲 /qq bind 绑最近那个，或 /qq bind <openid 前几位>）：");
            for (UnboundGroups.Seen seen : unbound) {
                lines.add("  " + seen.label());
            }
        }
        return lines;
    }

    /**
     * Binds a group that has already talked to a bot: writes it into the config and reloads, so the operator
     * never has to open the file. Returns what to print.
     *
     * <p>Only groups in {@link UnboundGroups} can be bound this way, which is a safety property and not just a
     * convenience: it means {@code /qq bind} can only ever attach a group that has actually sent this bot
     * something, so a typo cannot wire the server's chat to a stranger's group.
     */
    public synchronized List<String> bindGroup(UnboundGroups.Seen target) {
        Path path = BridgeConfig.configPath(platform.configDir());
        String label;
        try {
            label = BridgeConfig.bindGroup(path, target.botId(), target.groupOpenid());
        } catch (IOException e) {
            return List.of("绑定失败：" + e.getMessage());
        }
        unboundGroups.forget(target.groupOpenid());
        Log.info("把群 " + target.groupOpenid() + " 绑到 bot " + target.botId() + "，已写进 config.yml");
        List<String> lines = new ArrayList<>();
        lines.add("已绑定 " + label + "（写进 config.yml，旧文件备份在 config.yml.bak；"
                + "要改显示名就编辑那个 label）");
        lines.add(reload());
        lines.addAll(statusLines());
        return lines;
    }

    /**
     * 用服务端自己的 RCON 跑一条命令，把回显拿回来 —— 与 QQ 侧那条路走的是同一个方法，
     * 所以运维可以用它确认"回显到底抓不抓得到"。
     *
     * <p>走 RCON 而不是在进程里派发：原版命令只认真正的 Craft 发送者，而它的输出直接进日志，
     * 外面套一层收不到（见 {@link Rcon}）。RCON 是协议，所以这里一处实现，各平台通用。
     */
    public List<String> runCommand(String command) {
        return ServerConsole.run(platform, command);
    }

    /** What {@code /qq bind} resolves its argument against. */
    public UnboundGroups unboundGroups() {
        return unboundGroups;
    }

    /** Queues one Minecraft event for every group that asked for it; does nothing before the first start. */
    public void forward(BridgeConfig.McEvent event, Map<String, String> values) {
        BridgeRuntime active = runtime;
        if (active == null) {
            return;
        }
        try {
            active.forward(event, values);
        } catch (RuntimeException e) {
            // This runs on the server's own event bus, where an exception is not a private matter: it reaches
            // whatever else is listening, and can land in the middle of a tick. Losing one forwarded line is
            // the cheaper outcome by a wide margin.
            Log.error("转发 " + event + " 到 QQ 失败", e);
        }
    }

    /** Queues a test message to every configured group; see {@link BridgeRuntime#test()}. */
    public List<String> test() {
        BridgeRuntime active = runtime;
        if (active == null) {
            return List.of("桥接未运行（服务器还没起来，或已经关了）");
        }
        return active.test();
    }

    /** The config in force, for the commands that report on it. Empty before the first start. */
    public java.util.Optional<BridgeConfig> config() {
        BridgeRuntime active = runtime;
        return active == null ? java.util.Optional.empty() : java.util.Optional.of(active.config());
    }

    /** The config the platform says to read, or null when it could not be read at all. */
    private BridgeRuntime replacement() {
        try {
            return BridgeRuntime.start(BridgeConfig.load(
                    BridgeConfig.configPath(platform.configDir())), platform, unboundGroups);
        } catch (Exception e) {
            Log.error("QQ 桥接读取配置失败；服务器照常运行", e);
            return null;
        }
    }

    private void swap(BridgeRuntime replacement) {
        BridgeRuntime old = runtime;
        runtime = replacement;
        if (old != null) {
            old.close();
        }
    }
}
