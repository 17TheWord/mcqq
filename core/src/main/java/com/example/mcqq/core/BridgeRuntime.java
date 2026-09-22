package com.example.mcqq.core;

import io.github.skiesworld.qqbot.BotConfig;
import io.github.skiesworld.qqbot.Bots;
import io.github.skiesworld.qqbot.QQBotClient;
import io.github.skiesworld.qqbot.event.EventBus;
import io.github.skiesworld.qqbot.http.HttpTransport;
import io.github.skiesworld.qqbot.websocket.Intent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The running bridge: config in, bots and listeners out.
 *
 * <p>Bots are started on a background thread rather than the server's startup thread, because
 * {@link QQBotClient#start()} asks the platform who the bot is before it goes online — a wrong secret or an
 * unreachable network must delay the bridge, never the server. What it reported stays readable through
 * {@link #statusLines()} and {@code /qq status}.
 *
 * <p>The only thing it needs from the game is a way to put a line in chat, and that arrives as
 * {@link MinecraftPlatform} rather than as a server object.
 */
public final class BridgeRuntime implements AutoCloseable {

    private final MinecraftPlatform platform;
    private final BridgeConfig config;
    private final UnboundGroups unbound;
    private final ExecutorService dispatcher;
    private final Bots bots = new Bots();
    /** Written from the startup thread and the bot connector, read by {@code /qq status} on a player's thread. */
    private final List<String> problems = new CopyOnWriteArrayList<>();
    private final List<QQBotClient> registered = new ArrayList<>();

    private volatile boolean connecting = true;

    private BridgeRuntime(MinecraftPlatform platform, BridgeConfig config, UnboundGroups unbound) {
        this.platform = platform;
        this.config = config;
        this.unbound = unbound;
        // One task per dispatch, off the game's thread; the bus keeps each conversation's order itself.
        //
        // A cached pool rather than virtual threads, even though virtual threads fit this shape better:
        // `core` is compiled to Java 17 so the same jar also runs on 1.20.1, and
        // `newVirtualThreadPerTaskExecutor` is a Java 21 API. The pool keeps the two properties this code
        // depends on — a task never waits for a free thread, and a task is never rejected — and the load is one
        // short task per forwarded event, so the thread count stays small in practice.
        this.dispatcher = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, Constants.MOD_ID + "-dispatch");
            thread.setDaemon(true);
            return thread;
        });
        this.problems.addAll(config.problems());
    }

    /** Starts the bots for an already-parsed config. Nothing here throws into server startup. */
    public static BridgeRuntime start(BridgeConfig config, MinecraftPlatform platform, UnboundGroups unbound) {
        Log.debugEnabled(config.debug());
        BridgeRuntime runtime = new BridgeRuntime(platform, config, unbound);
        runtime.start();
        return runtime;
    }

    /**
     * Queues one Minecraft event for every group that asked for it, on the bridge's threads: a server tick must
     * not wait on a QQ round trip.
     *
     * <p>The line is rendered per group, because a group may override the template; an empty result means the
     * group asked for this event and then asked for it to be silent. Colour codes are dropped on the way out,
     * since QQ renders plain text.
     */
    public void forward(BridgeConfig.McEvent event, Map<String, String> values) {
        for (Delivery delivery : plan(event, values)) {
            QQBotClient bot = bots.get(delivery.bot().appId()).orElse(null);
            if (bot == null) {
                // Every branch that drops an event says so: "why did my message not arrive" has no other
                // answer, and it is the question an operator actually asks.
                Log.debug("不转发 " + event + " → 群 " + delivery.group().label()
                        + "：bot " + delivery.bot().id() + " 未注册（凭证缺失或启动失败）");
                continue;
            }
            Log.debug("转发 " + event + " → 群 " + delivery.group().label() + "：" + delivery.line());
            dispatcher.execute(() -> QqSender.send(bot, delivery.group(), delivery.line()));
        }
    }

    /** One group and the exact line it would get. */
    record Delivery(BridgeConfig.Bot bot, BridgeConfig.Group group, String line) {
    }

    /**
     * What this event would send, and to whom — a pure function of the config and the event's values.
     *
     * <p>It is separated from {@link #forward} so the routing can be tested without a server, a bot or an HTTP
     * call: which groups subscribed, which template each one ends up with, and that colour codes are dropped
     * before QQ sees them are all decisions this returns.
     */
    List<Delivery> plan(BridgeConfig.McEvent event, Map<String, String> values) {
        String key = Templates.mcKey(event);
        String platformLabel = platform.label();
        List<Delivery> deliveries = new ArrayList<>();
        for (BridgeConfig.Bot botConfig : config.bots()) {
            for (BridgeConfig.Group group : botConfig.groups()) {
                if (!group.sendsToQq(event)) {
                    Log.debug("不转发 " + event + " → 群 " + group.label() + "：该群没订阅这个事件");
                    continue;
                }
                String line = plain(config.template(group.groupOpenid(), key), values, platformLabel);
                if (line.isEmpty()) {
                    Log.debug("不转发 " + event + " → 群 " + group.label() + "：模板为空（= 静音）");
                    continue;
                }
                deliveries.add(new Delivery(botConfig, group, line));
            }
        }
        return deliveries;
    }

    private static String plain(String template, Map<String, String> values, String platformLabel) {
        return Templates.render(template, Templates.withContext(values, platformLabel)).replaceAll("§.", "").trim();
    }

    private void start() {
        for (String problem : config.problems()) {
            Log.warn("config: " + problem);
        }
        int alreadyLogged = problems.size();
        for (BridgeConfig.Bot bot : config.bots()) {
            register(bot);
        }
        // register() adds to the same list, so what it found — "this bot has no credentials, skipped" — has to
        // be logged here as well. Leaving it to /qq status alone is the worst failure mode there is: the admin
        // who just pasted a config, sees nothing happen, and only has the log to go on gets no answer at all.
        for (String problem : problems.subList(alreadyLogged, problems.size())) {
            Log.warn("config: " + problem);
        }
        if (registered.isEmpty()) {
            connecting = false;
            return;
        }
        Thread starter = new Thread(this::connectAll, Constants.MOD_ID + "-start");
        starter.setDaemon(true);
        starter.start();
    }

    private void register(BridgeConfig.Bot botConfig) {
        // The file first, matching what the template shows. The environment variable is the other supported way,
        // for an operator who would rather not keep the secret in a file. (The config already reports it when
        // both are written.)
        String secret = botConfig.secret();
        if (secret.isBlank()) {
            secret = System.getenv(botConfig.secretEnvironmentVariable());
        }
        if (secret == null || secret.isBlank()) {
            // Say both ways: whoever reads this is here because nothing connected, and the two lines that
            // would fix it are next to each other in the file.
            problems.add("bot " + botConfig.id() + ": 没有可用的 AppSecret —— 在 config.yml 里写"
                    + " secret: <AppSecret>，或设环境变量 "
                    + botConfig.secretEnvironmentVariable() + "；已跳过");
            return;
        }
        BotConfig qq = BotConfig.builder(botConfig.appId())
                .clientSecret(secret)
                // GROUP_MEMBER_* is what reports somebody joining or leaving the group.
                .intents(Intent.GROUP_AND_C2C_EVENT, Intent.GROUP_MEMBER_EVENT)
                .build();
        QQBotClient bot = new QQBotClient(qq, new HttpTransport(qq), new EventBus(dispatcher::execute));
        bot.handlers().register(new QqToMc(platform, config, botConfig.id(), unbound));
        bots.register(bot);
        registered.add(bot);
        Log.info("已接入 bot " + botConfig.id() + "，绑了 " + botConfig.groups().size() + " 个群");
    }

    private void connectAll() {
        for (QQBotClient bot : registered) {
            String appId = bot.config().appId();
            try {
                bot.start();
            } catch (Exception e) {
                problems.add("bot " + appId + " 启动失败：" + e.getMessage());
                Log.error("bot " + appId + " 启动失败；原因见 /qq status", e);
            }
        }
        connecting = false;
    }

    /**
     * Queues one message per configured group, down the same path a real event takes, and says what was queued
     * and what could not be. The results arrive on the log: this runs on the server's command thread, and a QQ
     * round trip has no business blocking it.
     */
    public List<String> test() {
        List<String> lines = new ArrayList<>();
        String text = Constants.PREFIX + " 测试消息（来自 " + platform.label() + "）";
        if (config.bots().isEmpty()) {
            lines.add("配置里没有 bot，没有可测的群");
            return lines;
        }
        for (BridgeConfig.Bot botConfig : config.bots()) {
            QQBotClient bot = bots.get(botConfig.appId()).orElse(null);
            if (bot == null) {
                lines.add("bot " + botConfig.id() + "：未注册（凭证缺失或启动失败），跳过");
                continue;
            }
            for (BridgeConfig.Group group : botConfig.groups()) {
                lines.add("bot " + botConfig.id() + " → 群 " + group.label() + "：已排队，结果见服务端日志");
                dispatcher.execute(() -> {
                    QqSender.Outcome outcome = QqSender.send(bot, group, text);
                    Log.info("测试消息 → 群 " + group.label() + "：" + outcome.note());
                });
            }
        }
        return lines;
    }

    /** The config this runtime was started from, so the commands can report on the templates in force. */
    public BridgeConfig config() {
        return config;
    }

    /** One line per bot, plus anything that went wrong reading the config or starting it. */
    public List<String> statusLines() {        List<String> lines = new ArrayList<>();
        lines.add("平台 " + platform.label());
        if (config.bots().isEmpty()) {
            lines.add("配置里没有 bot：编辑 " + platform.configDir().resolve(Constants.MOD_ID).resolve("config.yml")
                    + " 后 /qq reload");
        }
        for (BridgeConfig.Bot botConfig : config.bots()) {
            QQBotClient bot = bots.get(botConfig.appId()).orElse(null);
            String state = bot == null ? "未注册" : bot.isOnline() ? "在线" : connecting ? "连接中" : "未连接";
            String self = bot == null || bot.selfId() == null ? "" : " id=" + bot.selfId();
            lines.add("bot " + botConfig.id() + " [" + state + self + "] 群：" + groupSummary(botConfig));
        }
        // 命令执行是"从 QQ 能影响服务器"的那条线，所以状态里必须能一眼看到它开着没有、谁能用。
        if (config.commandsEnabled()) {
            lines.add("命令执行：开着，命令头 " + config.commandPrefix());
            for (BridgeConfig.Bot botConfig : config.bots()) {
                for (BridgeConfig.Group group : botConfig.groups()) {
                    String access = group.commandAccess().describe();
                    if (access != null) {
                        lines.add("  " + group.label() + "：" + access);
                    }
                }
            }
        } else {
            lines.add("命令执行：关着（config.yml 的 command.enabled）");
        }
        if (config.templates().isEmpty()) {
            // Not a problem, but it is the one thing an upgraded config cannot tell you about itself.
            lines.add("文案用的是内置默认（配置里没有 templates 段）；/qq templates 看当前生效的文案");
        }
        lines.addAll(problems);
        return lines;
    }

    private static String groupSummary(BridgeConfig.Bot botConfig) {
        List<String> groups = new ArrayList<>();
        for (BridgeConfig.Group group : botConfig.groups()) {
            groups.add(group.label() + "(收=" + group.receivesFromQq() + ", 发=" + group.sendEvents() + ")");
        }
        return groups.toString();
    }

    @Override
    public void close() {
        bots.close();
        dispatcher.shutdownNow();
    }
}
