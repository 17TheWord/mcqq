package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.mcqq.core.command.CommandTree;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.skiesworld.qqbot.api.Api;
import io.github.skiesworld.qqbot.event.EventType;
import io.github.skiesworld.qqbot.event.Outbound;
import io.github.skiesworld.qqbot.event.QQEvent;
import io.github.skiesworld.qqbot.event.QQMessageEvent;
import io.github.skiesworld.qqbot.message.MessageBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * QQ → Minecraft 的入口：去重、会话归属（未绑定的群不碰命令、私聊不进待绑列表）、文本净化。
 *
 * <p>钉的都是真出过界的事：平台重推一条消息，命令会被跑两遍；"配置里查不到"被当成私聊时，
 * 私聊白名单在机器人加入的任何群里都能执行命令；陌生人的 § 换行能伪造系统发言、往日志里塞假行。
 */
class QqToMcTest {

    private static final String OUTPUT = "There are 0 of a max of 20 players online";

    @TempDir
    Path serverDir;

    private final List<String> broadcasts = new ArrayList<>();
    private final List<String> replies = new ArrayList<>();
    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void captureLog() {
        broadcasts.clear();
        replies.clear();
        log.clear();
        Log.debugEnabled(false);
        Log.install(new Log.Sink() {
            @Override
            public void info(String message) {
                log.add("INFO " + message);
            }

            @Override
            public void warn(String message, Throwable cause) {
                log.add("WARN " + message);
            }

            @Override
            public void error(String message, Throwable cause) {
                log.add("ERROR " + message);
            }

            @Override
            public void debug(String message) {
                log.add("DEBUG " + message);
            }
        });
    }

    @AfterEach
    void releaseLog() {
        Log.install(new Log.Sink() {
            @Override
            public void info(String message) {
            }

            @Override
            public void warn(String message, Throwable cause) {
            }

            @Override
            public void error(String message, Throwable cause) {
            }

            @Override
            public void debug(String message) {
            }
        });
    }

    private Outbound recorder() {
        return new Outbound() {
            @Override
            public void reply(QQEvent event, MessageBuilder builder) {
                replies.add(builder.toGroup().content);
            }

            @Override
            public Api api() {
                return null;
            }
        };
    }

    /** 聊天栏是个 List：进过什么，断言什么。 */
    private MinecraftPlatform capturing() {
        return new MinecraftPlatform() {
            @Override
            public String label() {
                return "test-26.1.2";
            }

            @Override
            public Path configDir() {
                return serverDir.resolve("plugins");
            }

            @Override
            public void broadcast(String line) {
                broadcasts.add(line);
            }

            @Override
            public void onMainThread(Runnable task) {
                task.run();
            }

            @Override
            public void registerCommands(CommandTree tree) {
            }
        };
    }

    /** G1 是绑定群（管理员可执行命令）；OWNER 只在私聊白名单里。 */
    private BridgeConfig config(FakeRcon rcon) throws IOException {
        Files.createDirectories(serverDir.resolve("plugins"));
        Files.writeString(serverDir.resolve("server.properties"), """
                enable-rcon=true
                rcon.port=%d
                rcon.password=hunter2
                """.formatted(rcon.port()), StandardCharsets.UTF_8);
        Path file = serverDir.resolve("plugins").resolve("mcqq").resolve("config.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                command:
                  enabled: true
                  prefix: "/mcc"

                bots:
                  - id: main
                    app-id: "1"
                    secret-env: MCQQ_TEST_NEVER_SET
                    direct:
                      whitelist: ["OWNER"]
                    groups:
                      - group-openid: "G1"
                        label: 主群
                        command:
                          allow: admin
                """, StandardCharsets.UTF_8);
        return BridgeConfig.parse(file);
    }

    private QqToMc listener(BridgeConfig config, UnboundGroups unbound) {
        return new QqToMc(capturing(), config, config.bots().get(0), unbound,
                new ConsoleRunner(Runnable::run));
    }

    private QQMessageEvent group(String id, String groupOpenid, String content,
            String openid, String role) {
        JsonObject data = JsonParser.parseString("{\"group_openid\":\"" + groupOpenid
                + "\",\"content\":\"" + content + "\",\"author\":{\""
                + "member_openid\":\"" + openid + "\",\"member_role\":\"" + role
                + "\",\"username\":\"某人\"}}").getAsJsonObject();
        return new QQMessageEvent(id, 0, null, EventType.GROUP_MESSAGE_CREATE.name(),
                EventType.GROUP_MESSAGE_CREATE, data, recorder());
    }

    private QQMessageEvent direct(String id, String openid, String content) {
        JsonObject data = JsonParser.parseString("{\"user_openid\":\"" + openid
                + "\",\"content\":\"" + content + "\",\"author\":{\"user_openid\":\""
                + openid + "\",\"username\":\"某人\"}}").getAsJsonObject();
        return new QQMessageEvent(id, 0, null, EventType.C2C_MESSAGE_CREATE.name(),
                EventType.C2C_MESSAGE_CREATE, data, recorder());
    }

    @Test
    void strangersCannotWearServerColoursOrFakeLogLines() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            // "§c[系统]" 想冒充红色系统发言；换行想在聊天栏和日志里凭空多出一行。
            listener(config(rcon), new UnboundGroups()).onGroupMessage(
                    group("e1", "G1", "§c[系统]§r 今天天气不错\\n我就是服务器", "BOSS", "admin"));

            assertEquals(1, broadcasts.size());
            String line = broadcasts.get(0);
            assertTrue(line.contains("[系统] 今天天气不错 我就是服务器"), line);
            assertFalse(line.contains("§c"), "格式码该被剥掉，模板自己的 §b/§r 不受影响：" + line);
            assertFalse(line.contains("\n"), "一条消息不该变成两行：" + line);
        }
    }

    @Test
    void aRepushedChatMessageIsBridgedOnce() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            QqToMc listener = listener(config(rcon), new UnboundGroups());
            listener.onGroupMessage(group("e1", "G1", "hi", "X", "member"));
            listener.onGroupMessage(group("e1", "G1", "hi", "X", "member"));
            assertEquals(1, broadcasts.size(), "同一个事件 id 推两次，聊天栏只该进一行");
        }
    }

    @Test
    void aRepushedCommandIsRunOnce() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            QqToMc listener = listener(config(rcon), new UnboundGroups());
            listener.onGroupMessage(group("e9", "G1", "/mcc list", "BOSS", "admin"));
            listener.onGroupMessage(group("e9", "G1", "/mcc list", "BOSS", "admin"));
            assertEquals(List.of(OUTPUT), replies, "重推不该让命令跑第二遍");
        }
    }

    @Test
    void privateChatWithoutACommandIsNeitherBridgedNorRecordedAsUnbound() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            UnboundGroups unbound = new UnboundGroups();
            listener(config(rcon), unbound).onGroupMessage(direct("e1", "U1", "在吗"));
            assertTrue(unbound.all().isEmpty(), "私聊不是「没绑定的群」，别把它记进待绑列表");
            assertEquals(List.of(), broadcasts);
        }
    }

    @Test
    void anUnboundGroupThatTypesACommandStillOnlyGetsTheBindHint() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            UnboundGroups unbound = new UnboundGroups();
            // OWNER 在私聊白名单里 —— 但这里是未绑定的群，命令轮不到执行，走的是"没绑定"的指路。
            listener(config(rcon), unbound).onGroupMessage(
                    group("e1", "G9", "/mcc list", "OWNER", "member"));

            assertEquals(1, unbound.all().size());
            assertEquals("G9", unbound.newest().conversationId());
            assertTrue(log.stream().anyMatch(line -> line.startsWith("WARN") && line.contains("/qq bind")),
                    "运维该被告知怎么把它绑上：" + log);
            assertEquals(List.of(), replies, "既不执行，也就不回话");
            assertEquals("", rcon.lastCommand(), "更不该把命令发进服务器");
        }
    }
}
