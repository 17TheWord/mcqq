package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.mcqq.core.command.CommandTree;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.skiesworld.qqbot.event.EventType;
import io.github.skiesworld.qqbot.event.QQMessageEvent;
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
 * The way in for somebody who cannot read a log and does not want to open a file: an unbound group is
 * remembered, the operator is told once, and {@code /qq bind} turns that into a config entry.
 *
 * <p>All of it offline, on purpose. The config here names a secret environment variable that is never set, so
 * {@code register} skips the bot and no HTTP call is ever made — which is what makes the whole chain, from a
 * group message to a rewritten file, testable without a server or a QQ account.
 */
class BindFlowTest {

    @TempDir
    Path dir;

    private final List<String> log = new ArrayList<>();

    @BeforeEach
    void captureLog() {
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

    @Test
    void anUnboundGroupIsRememberedAndReportedOnceNotPerMessage() throws Exception {
        BridgeConfig config = config("""
                bots:
                  - id: main
                    app-id: "1"
                    secret-env: MCQQ_TEST_NEVER_SET
                    groups:
                      - group-openid: "BOUND"
                        label: 主群
                """);
        UnboundGroups unbound = new UnboundGroups();
        QqToMc listener = new QqToMc(headless(), config, "main", unbound);

        listener.onGroupMessage(message("m1", "NEWGROUP"));
        listener.onGroupMessage(message("m2", "NEWGROUP"));
        listener.onGroupMessage(message("m3", "BOUND"));

        assertEquals("NEWGROUP", unbound.newest().groupOpenid(), "没绑定的群要被记下来");
        assertEquals(1, unbound.all().size(), "已经绑过的那个不该进这个列表");
        assertEquals(1, log.stream().filter(line -> line.startsWith("WARN") && line.contains("NEWGROUP")).count(),
                "同一个群只该刷一行日志，不是每条消息一行：" + log);
        assertTrue(log.stream().anyMatch(line -> line.contains("/qq bind")),
                "那行日志要直接告诉运维敲什么：" + log);
    }

    @Test
    void bindingWritesTheFileReloadsAndDropsItFromThePendingList() throws Exception {
        config("""
                bots:
                  - id: main
                    app-id: "1"
                    secret-env: MCQQ_TEST_NEVER_SET
                    groups: []
                """);
        Bridge bridge = new Bridge(headless());
        bridge.start();
        try {
            bridge.unboundGroups().remember("main", "NEWGROUP");

            List<String> lines = bridge.bindGroup(bridge.unboundGroups().newest());

            assertTrue(lines.get(0).startsWith("已绑定"), lines.toString());
            BridgeConfig written = BridgeConfig.parse(BridgeConfig.configPath(dir));
            assertEquals(List.of("NEWGROUP"), written.bots().get(0).groups().stream()
                    .map(BridgeConfig.Group::groupOpenid).toList(), "配置里要真的多出这个群");
            // 状态里显示的是 label（openid 的后六位），不是完整 openid —— 拿写进去的那个 label 对。
            String label = written.bots().get(0).groups().get(0).label();
            assertTrue(lines.stream().anyMatch(line -> line.contains(label)),
                    "重载后的状态里要能看到它：" + lines);
            assertNull(bridge.unboundGroups().newest(), "绑过的群不该还留在待绑列表里");
        } finally {
            bridge.stop();
        }
    }

    private BridgeConfig config(String yaml) throws Exception {
        Path file = BridgeConfig.configPath(dir);
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return BridgeConfig.parse(file);
    }

    /** One group message, as the SDK would hand it over: the openid lives in the event's JSON payload. */
    private static QQMessageEvent message(String id, String groupOpenid) {
        JsonObject data = JsonParser.parseString("{\"group_openid\":\"" + groupOpenid
                + "\",\"content\":\"hi\"}").getAsJsonObject();
        return new QQMessageEvent(id, 0, null, "GROUP_MESSAGE_CREATE",
                EventType.GROUP_MESSAGE_CREATE, data, null);
    }

    private MinecraftPlatform headless() {
        return new MinecraftPlatform() {
            @Override
            public String label() {
                return "test-26.1.2";
            }

            @Override
            public Path configDir() {
                return dir;
            }

            @Override
            public void broadcast(String line) {
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
}
