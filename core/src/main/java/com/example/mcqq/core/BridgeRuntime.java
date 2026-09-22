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
    private final ExecutorService dispatcher;
    private final Bots bots = new Bots();
    /** Written from the startup thread and the bot connector, read by {@code /qq status} on a player's thread. */
    private final List<String> problems = new CopyOnWriteArrayList<>();
    private final List<QQBotClient> registered = new ArrayList<>();

    private volatile boolean connecting = true;

    private BridgeRuntime(MinecraftPlatform platform, BridgeConfig config) {
        this.platform = platform;
        this.config = config;
        // One task per dispatch, off the game's thread; the bus keeps each conversation's order itself.
        this.dispatcher = Executors.newVirtualThreadPerTaskExecutor();
        this.problems.addAll(config.problems());
    }

    /** Starts the bots for an already-parsed config. Nothing here throws into server startup. */
    public static BridgeRuntime start(BridgeConfig config, MinecraftPlatform platform) {
        Log.debugEnabled(config.debug());
        BridgeRuntime runtime = new BridgeRuntime(platform, config);
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
        // /qq status is the only other way these surface, and an admin who never runs it still needs to know
        // why nothing arrived.
        for (String problem : config.problems()) {
            Log.warn("config: " + problem);
        }
        for (BridgeConfig.Bot bot : config.bots()) {
            register(bot);
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
        String secret = System.getenv(botConfig.secretEnvironmentVariable());
        if (secret == null || secret.isBlank()) {
            problems.add("bot " + botConfig.id() + ": 环境变量 " + botConfig.secretEnvironmentVariable()
                    + " 未设置，已跳过");
            return;
        }
        BotConfig qq = BotConfig.builder(botConfig.appId())
                .clientSecret(secret)
                // GROUP_MEMBER_* is what reports somebody joining or leaving the group.
                .intents(Intent.GROUP_AND_C2C_EVENT, Intent.GROUP_MEMBER_EVENT)
                .build();
        QQBotClient bot = new QQBotClient(qq, new HttpTransport(qq), new EventBus(dispatcher::execute));
        bot.handlers().register(new QqToMc(platform, config));
        bots.register(bot);
        registered.add(bot);
        Log.info("bridging bot " + botConfig.id() + " with " + botConfig.groups().size() + " group(s)");
    }

    private void connectAll() {
        for (QQBotClient bot : registered) {
            String appId = bot.config().appId();
            try {
                bot.start();
            } catch (Exception e) {
                problems.add("bot " + appId + " 启动失败：" + e.getMessage());
                Log.error("bot " + appId + " could not start; /qq status shows why", e);
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
