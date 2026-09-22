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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * QQ 侧那条流程：判前缀 → 判权限 → 走 RCON 跑 → 把回显聚合成**一条**回过去。
 *
 * <p>整条路离线可测：RCON 那半头是测试里自己的小服务端（{@link FakeRcon}），平台那半头是假的
 * （只要 {@code configDir()} 指到临时目录，就能找到 {@code server.properties}）。
 *
 * <p>重点钉住的是**三个面共用一套规则、只有"拒绝时说不说话"不同**：群回一句"你没有权限"，
 * 私聊静默。
 */
class McCommandsTest {

    private static final String OUTPUT = "There are 0 of a max of 20 players online";

    @TempDir
    Path serverDir;

    private final List<String> replies = new ArrayList<>();

    /** 收回复用：SDK 把回复绑在事件上，所以给它一个记事的 Outbound 就够了。 */
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

    /** 平台只提供一件事：configDir()。它的父目录就是"服务端目录"，server.properties 在那儿。 */
    private MinecraftPlatform headless() {
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

    /** 把 server.properties 指向一个刚起的假 RCON，并给出一份打开命令执行的配置。 */
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
                          whitelist: ["HELPER"]
                """, StandardCharsets.UTF_8);
        return BridgeConfig.parse(file);
    }

    private McCommands commands(BridgeConfig config) {
        return new McCommands(headless(), config, config.bots().get(0));
    }

    private QQMessageEvent groupMessage(String openid, String memberRole, String content) {
        return event(EventType.GROUP_MESSAGE_CREATE, """
                {"group_openid":"G1","content":"%s","author":
                  {"member_openid":"%s","member_role":"%s","username":"某人"}}"""
                .formatted(content, openid, memberRole));
    }

    private QQMessageEvent directMessage(String openid, String content) {
        return event(EventType.C2C_MESSAGE_CREATE, """
                {"user_openid":"%s","content":"%s","author":
                  {"user_openid":"%s","username":"某人"}}""".formatted(openid, content, openid));
    }

    private QQMessageEvent event(EventType type, String json) {
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        return new QQMessageEvent("m1", 0, null, type.name(), type, data, recorder());
    }

    @Test
    void aGroupAdminRunsACommandAndGetsTheOutputBack() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            McCommands commands = commands(config(rcon));

            assertTrue(commands.handle(groupMessage("BOSS", "admin", "/mcc list")));

            assertEquals(List.of(OUTPUT), replies, "回显要聚合成一条回过去");
            assertEquals("list", rcon.lastCommand(), "前缀要去掉，只把命令本身发过去");
        }
    }

    @Test
    void theWhitelistWorksOnItsOwn() throws Exception {
        // HELPER 只是普通成员，但在白名单里 —— 用户点名要的性质。
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            McCommands commands = commands(config(rcon));

            assertTrue(commands.handle(groupMessage("HELPER", "member", "/mcc list")));
            assertEquals(List.of(OUTPUT), replies);
        }
    }

    @Test
    void aGroupWithoutPermissionIsToldSo() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            McCommands commands = commands(config(rcon));

            assertTrue(commands.handle(groupMessage("NOBODY", "member", "/mcc list")));

            assertEquals(List.of("你没有权限执行命令"), replies);
            assertEquals("", rcon.lastCommand(), "没权限就不该把命令发出去");
        }
    }

    @Test
    void aWhitelistedDirectMessageRunsAndAStrangerIsIgnoredSilently() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            McCommands commands = commands(config(rcon));

            assertTrue(commands.handle(directMessage("OWNER", "/mcc list")));
            assertEquals(List.of(OUTPUT), replies, "私聊里的白名单要能执行");

            replies.clear();
            assertTrue(commands.handle(directMessage("STRANGER", "/mcc list")));
            assertEquals(List.of(), replies, "陌生人试命令时不该从这里确认机器人在");
        }
    }

    @Test
    void aMessageThatIsNotACommandIsLeftAlone() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            McCommands commands = commands(config(rcon));

            assertFalse(commands.handle(groupMessage("BOSS", "admin", "大家好")), "不是命令就别管它");
            assertFalse(commands.handle(directMessage("OWNER", "在吗")), "私聊里的普通消息也一样");
            assertEquals(List.of(), replies);
        }
    }

    @Test
    void onlyTheErrorsThatMeanTheReplyPathIsClosedSwitchToProactive() {
        // 真机上抓到的那个：群里没 @ 机器人的消息，msg_id 用不了。
        assertTrue(McCommands.replyPathIsClosed(40034024), "msg_id 无效或越权");
        assertTrue(McCommands.replyPathIsClosed(40034005), "msg_id 已过期");
        assertTrue(McCommands.replyPathIsClosed(304103), "消息 ID 已过期");
        assertTrue(McCommands.replyPathIsClosed(40034128), "被动回复时间或次数超限");

        // 别的错误（内容违规、被禁言）不该改走主动消息 —— 换个通道发同样的内容只会再失败一次。
        assertFalse(McCommands.replyPathIsClosed(40034006), "消息内容违规");
        assertFalse(McCommands.replyPathIsClosed(40054002), "机器人被禁言");
        assertFalse(McCommands.replyPathIsClosed(0));
    }

    @Test
    void thePrefixAloneExplainsItself() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            McCommands commands = commands(config(rcon));

            assertTrue(commands.handle(groupMessage("BOSS", "admin", "/mcc")));

            assertEquals(1, replies.size());
            assertTrue(replies.get(0).contains("用法"), replies.toString());
        }
    }

    @Test
    void commandExecutionOffMeansTheMessageIsJustAMessage() throws Exception {
        try (FakeRcon rcon = new FakeRcon("hunter2", OUTPUT)) {
            BridgeConfig on = config(rcon);
            BridgeConfig off = BridgeConfig.parse(offSwitch(on));

            assertFalse(commands(off).handle(groupMessage("BOSS", "admin", "/mcc list")),
                    "总开关关着时，/mcc 只是一句普通消息");
            assertEquals(List.of(), replies);
        }
    }

    /** 把 enabled 改成 false 再读一遍，省得写第二份配置。 */
    private Path offSwitch(BridgeConfig on) throws IOException {
        Path file = serverDir.resolve("plugins").resolve("mcqq").resolve("config.yml");
        Files.writeString(file, Files.readString(file, StandardCharsets.UTF_8)
                .replace("enabled: true", "enabled: false"), StandardCharsets.UTF_8);
        return file;
    }
}
