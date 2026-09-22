package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The config file is the whole operator interface, so its parsing is tested without a server, a bot or a
 * Minecraft jar: what loaded, what was skipped, and what the operator was told about it.
 *
 * <p>That this test compiles at all is the point of the split — {@code core} has no Minecraft on its classpath.
 */
class BridgeConfigTest {

    @TempDir
    Path dir;

    private BridgeConfig read(String yaml) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return BridgeConfig.parse(file);
    }

    @Test
    void readsBotsGroupsAndBothDirections() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "123456789"
                    secret-env: QQ_BOT_SECRET
                    groups:
                      - group-openid: "G1"
                        label: 主群
                      - group-openid: "G2"
                        receive-from-qq: false
                        send-to-qq: [death]
                """);

        assertTrue(config.problems().isEmpty(), "a filled-in file has nothing to complain about: "
                + config.problems());

        BridgeConfig.Bot bot = config.bots().get(0);
        assertEquals("123456789", bot.appId());
        assertEquals("QQ_BOT_SECRET", bot.secretEnvironmentVariable());
        assertEquals(List.of("G1", "G2"), bot.groups().stream().map(BridgeConfig.Group::groupOpenid).toList());

        BridgeConfig.Group main = bot.group("G1").orElseThrow();
        assertEquals("主群", main.label());
        assertTrue(main.receivesFromQq());
        assertEquals(List.of(BridgeConfig.McEvent.CHAT, BridgeConfig.McEvent.JOIN,
                BridgeConfig.McEvent.QUIT, BridgeConfig.McEvent.DEATH),
                main.sendEvents().stream().sorted().toList());

        BridgeConfig.Group out = bot.group("G2").orElseThrow();
        assertFalse(out.receivesFromQq());
        assertEquals("G2", out.label(), "no label falls back to the openid");
        assertEquals(List.of(BridgeConfig.McEvent.DEATH), List.copyOf(out.sendEvents()));
    }

    @Test
    void aPlaceholderOrAMissingGroupIsReportedInsteadOfSilentlyIgnored() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - app-id: "REPLACE_ME"
                    groups:
                      - label: 没有 openid
                """);

        assertTrue(config.bots().isEmpty(), "the bot has no usable app-id");
        assertEquals(1, config.problems().size(), config.problems().toString());
        assertTrue(config.problems().get(0).contains("REPLACE_ME"), config.problems().toString());
    }

    @Test
    void thePackagedTemplateIsWrittenOnFirstStartAndSaysWhatToFillIn() throws Exception {
        Path file = dir.resolve("mcqq").resolve("config.yml");

        BridgeConfig config = BridgeConfig.load(file);

        assertTrue(Files.exists(file), "a first start leaves the template behind for the admin to edit");
        assertTrue(config.bots().isEmpty(), "nothing in the template is a usable id yet");
        assertTrue(config.problems().stream().allMatch(p -> p.contains("REPLACE_ME")),
                config.problems().toString());
    }

    @Test
    void readsTheSecretStraightFromTheFile() throws Exception {
        // 密钥写在文件里是主路径 —— 这条别悄悄回归。
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "123456789"
                    secret: "abc123"
                    groups:
                      - group-openid: "G1"
                """);

        assertTrue(config.problems().isEmpty(), config.problems().toString());
        assertEquals("abc123", config.bots().get(0).secret());
    }

    @Test
    void aPlaceholderSecretCountsAsAbsent() throws Exception {
        // 模板里的 REPLACE_ME 不该被当成真密钥发给平台 —— 那只会换来一次登录失败，而不是一句"去填它"。
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "123456789"
                    secret: "REPLACE_ME"
                    groups:
                      - group-openid: "G1"
                """);

        assertTrue(config.bots().get(0).secret().isEmpty(), "空串表示没有 —— 与 text() 的约定一致");
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("REPLACE_ME") && p.contains("secret")),
                config.problems().toString());
    }

    @Test
    void writingBothSecretAndSecretEnvIsReported() throws Exception {
        // 两个都写是没人会故意写出来的配置；静默挑一个就是"为什么还在用旧密钥"的开端。
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "123456789"
                    secret: "abc123"
                    secret-env: QQ_BOT_SECRET
                    groups:
                      - group-openid: "G1"
                """);

        assertEquals("abc123", config.bots().get(0).secret());
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("secret-env")),
                config.problems().toString());
    }

    @Test
    void omittingSecretEnvKeepsTheOldDefault() throws Exception {
        // 老配置里省略 secret-env 时用的就是这个名字。留着，别让既有配置静默失效。
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "123456789"
                    groups:
                      - group-openid: "G1"
                """);

        assertEquals("QQ_BOT_SECRET", config.bots().get(0).secretEnvironmentVariable());
    }

    @Test
    void bindingAGroupWritesItIntoTheRightBotAndKeepsABackup() throws Exception {
        Path file = dir.resolve("mcqq").resolve("config.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                bots:
                  - id: main
                    app-id: "123456789"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                        label: 主群
                  - id: second
                    app-id: "987654321"
                    secret: "def"
                    groups: []
                """, StandardCharsets.UTF_8);

        String label = BridgeConfig.bindGroup(file, "second", "NEWOPENID");

        assertTrue(Files.exists(file.resolveSibling("config.yml.bak")), "改文件前必须留备份");
        BridgeConfig config = BridgeConfig.parse(file);
        BridgeConfig.Bot second = bot(config, "second");
        assertEquals(List.of("NEWOPENID"), second.groups().stream()
                .map(BridgeConfig.Group::groupOpenid).toList());
        assertEquals(label, second.group("NEWOPENID").orElseThrow().label());
        // 另一个 bot 一个字都不该动
        assertEquals(List.of("G1"), bot(config, "main").groups().stream()
                .map(BridgeConfig.Group::groupOpenid).toList());
    }

    @Test
    void bindingTheSameGroupTwiceIsRefused() throws Exception {
        Path file = dir.resolve("mcqq").resolve("config.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                bots:
                  - id: main
                    app-id: "123456789"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                """, StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> BridgeConfig.bindGroup(file, "main", "G1"),
                "已经绑过的群不该被绑第二次");
    }

    @Test
    void readsTheCommandPolicy() throws Exception {
        BridgeConfig config = read("""
                command:
                  enabled: true
                  prefix: "/mcc"

                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                        label: 主群
                        command:
                          allow: admin
                          whitelist: ["A"]
                """);

        assertTrue(config.problems().isEmpty(), config.problems().toString());
        assertTrue(config.commandsEnabled());
        assertEquals("/mcc", config.commandPrefix());

        CommandAccess access = config.bots().get(0).groups().get(0).commandAccess();
        assertEquals(CommandAccess.Allow.ADMIN, access.allow());
        assertTrue(access.permits(new CommandAccess.Sender("A", "member", Set.of())), "白名单");
        assertTrue(access.permits(new CommandAccess.Sender("B", "admin", Set.of())), "管理员");
        assertFalse(access.permits(new CommandAccess.Sender("C", "member", Set.of())));
    }

    @Test
    void commandsAreOffByDefaultAndThePrefixHasADefault() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                """);

        assertFalse(config.commandsEnabled(), "命令执行默认必须关着 —— 它是唯一一条能影响服务器的路径");
        assertEquals("/mcc", config.commandPrefix());
        assertNull(config.bots().get(0).groups().get(0).commandAccess().describe(),
                "没配 command 的目标不该在状态里占一行");
    }

    @Test
    void theBuiltInOwnerAndAdminRoleIdsInRolesAreReported() throws Exception {
        // 那两个 ID 归 allow 管；写进 roles 不生效，所以要说一声，免得有人以为"没写 2 所以管理员用不了"。
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                        command:
                          roles: ["2", "5", "999"]
                """);

        assertTrue(config.problems().stream().anyMatch(p -> p.contains("command.roles")),
                config.problems().toString());
        assertEquals(Set.of("5", "999"),
                config.bots().get(0).groups().get(0).commandAccess().extraRoleIds(),
                "内置的 2 要被剔掉，5 和自定义的留下");
    }

    @Test
    void anUnknownAllowIsReportedAndTreatedAsNone() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                        command:
                          allow: 管理员
                """);

        assertEquals(CommandAccess.Allow.NONE, config.bots().get(0).groups().get(0).commandAccess().allow());
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("command.allow")),
                config.problems().toString());
    }

    @Test
    void enabledWithNothingConfiguredIsReported() throws Exception {
        // "开着但谁都执行不了"是最容易发生的误会 —— 状态和日志里都要说。
        BridgeConfig config = read("""
                command:
                  enabled: true

                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                """);

        assertTrue(config.problems().stream().anyMatch(p -> p.contains("谁都执行不了")),
                config.problems().toString());
    }

    @Test
    void aPrefixWithSpacesFallsBackToTheDefault() throws Exception {
        BridgeConfig config = read("""
                command:
                  prefix: "/mcc now"

                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                """);

        assertEquals("/mcc", config.commandPrefix());
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("command.prefix")),
                config.problems().toString());
    }

    @Test
    void readsChannelsAlongsideGroups() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                        label: 主群
                    channels:
                      - guild-id: "GUILD"
                        channel-id: "CH1"
                        label: 主频道
                        send-to-qq: [chat, death]
                        command:
                          allow: owner
                """);

        assertTrue(config.problems().isEmpty(), config.problems().toString());
        BridgeConfig.Bot bot = config.bots().get(0);
        assertEquals(1, bot.groups().size());
        assertEquals(1, bot.channels().size());

        BridgeConfig.Channel channel = bot.channels().get(0);
        assertEquals("GUILD", channel.guildId());
        assertEquals("CH1", channel.channelId());
        assertEquals("主频道", channel.label());
        assertTrue(channel.receivesFromQq());
        assertTrue(channel.sendsToQq(BridgeConfig.McEvent.CHAT));
        assertFalse(channel.sendsToQq(BridgeConfig.McEvent.JOIN), "只订阅了 chat 与 death");
        assertEquals(BridgeConfig.Kind.CHANNEL, channel.kind());
        assertEquals(CommandAccess.Allow.OWNER, channel.commandAccess().allow());

        // 群和子频道放在一起时，顺序是先群后频道。
        assertEquals(List.of("G1", "CH1"), bot.targets().stream()
                .map(BridgeConfig.Target::conversationId).toList());
    }

    @Test
    void aTargetIsFoundByItsOwnKeyWhicheverKindItIs() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    groups:
                      - group-openid: "G1"
                        label: 主群
                    channels:
                      - guild-id: "GUILD"
                        channel-id: "CH1"
                        label: 主频道
                """);

        assertEquals("主群", config.target("G1").orElseThrow().label());
        assertEquals("主频道", config.target("CH1").orElseThrow().label());
        assertTrue(config.target("不存在").isEmpty());
        // 子频道也能按自己的 id 查到模板（群级覆盖那条路对两种目标都成立）。
        assertEquals("[MC] {player}: {text}", config.template("CH1", "mc-chat"));
    }

    @Test
    void aChannelWithoutChannelIdIsSkipped() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    channels:
                      - guild-id: "GUILD"
                        label: 没有 channel-id
                """);

        assertTrue(config.bots().get(0).channels().isEmpty());
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("channel-id")),
                config.problems().toString());
    }

    @Test
    void aChannelWithoutGuildIdIsKeptButMentioned() throws Exception {
        // guild-id 不参与匹配（匹配只用 channel-id），但少了它在日志和状态里认不出是哪儿 ——
        // 所以只说一声，不跳过。
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    channels:
                      - channel-id: "CH1"
                        label: 主频道
                """);

        assertEquals(1, config.bots().get(0).channels().size(), "少了 guild-id 也要留着");
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("guild-id")),
                config.problems().toString());
    }

    @Test
    void readsTheDirectWhitelist() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    direct:
                      whitelist: ["OWNER"]
                    groups:
                      - group-openid: "G1"
                """);

        assertTrue(config.problems().isEmpty(), config.problems().toString());
        CommandAccess access = config.bots().get(0).directAccess();
        assertEquals(CommandAccess.Allow.NONE, access.allow(), "私聊没有身份概念，allow 固定是 none");
        assertTrue(access.permits(CommandAccess.Sender.of("OWNER")));
        assertFalse(access.permits(CommandAccess.Sender.of("陌生人")));
    }

    @Test
    void aDirectAllowKeyIsReportedAsMeaningless() throws Exception {
        // 私聊事件里没有角色字段，所以 allow 在这儿没有对象可判 —— 写了要说一声。
        BridgeConfig config = read("""
                bots:
                  - id: main
                    app-id: "1"
                    secret: "abc"
                    direct:
                      allow: admin
                      whitelist: ["OWNER"]
                """);

        assertTrue(config.problems().stream().anyMatch(p -> p.contains("direct.allow")),
                config.problems().toString());
        assertTrue(config.bots().get(0).directAccess().permits(CommandAccess.Sender.of("OWNER")));
    }

    private static BridgeConfig.Bot bot(BridgeConfig config, String id) {
        return config.bots().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void theAnnotatedTemplateIsAlsoKeptNextToTheConfig() throws Exception {
        Path file = dir.resolve("mcqq").resolve("config.yml");

        BridgeConfig.load(file);

        Path example = file.resolveSibling("config.example.yml");
        assertTrue(Files.exists(example), "the comments live in the example file, so it has to be there");
        assertTrue(Files.readString(example).contains("AppSecret"),
                "and it is the packaged template, comments and all");
    }

    @Test
    void anOlderConfigGetsTheNewKeysAndKeepsItsOldBytesInABackup() throws Exception {
        Path file = dir.resolve("mcqq").resolve("config.yml");
        Files.createDirectories(file.getParent());
        String older = """
                bots:
                  - app-id: "123456789"
                    groups:
                      - group-openid: "G1"
                """;
        Files.writeString(file, older, StandardCharsets.UTF_8);

        BridgeConfig config = BridgeConfig.load(file);

        String updated = Files.readString(file);
        assertTrue(updated.contains("templates:"), updated);
        for (String key : Templates.keys()) {
            assertTrue(updated.contains(key + ":"), "missing " + key + " in\n" + updated);
        }
        assertTrue(updated.contains("123456789"), "the operator's own values are kept: " + updated);
        assertEquals(older, Files.readString(file.resolveSibling("config.yml.bak")),
                "the previous bytes are kept verbatim");
        assertTrue(config.problems().isEmpty(), config.problems().toString());
        assertEquals(Templates.defaults().get(Templates.MC_CHAT), config.template("G1", Templates.MC_CHAT));
    }

    @Test
    void aConfigThatIsAlreadyCompleteIsNotRewritten() throws Exception {
        Path file = dir.resolve("mcqq").resolve("config.yml");
        Files.createDirectories(file.getParent());
        StringBuilder yaml = new StringBuilder("bots:\n  - app-id: \"1\"\n    groups:\n      - group-openid: \"G1\"\n");
        yaml.append("templates:\n");
        for (Map.Entry<String, String> entry : Templates.defaults().entrySet()) {
            yaml.append("  ").append(entry.getKey()).append(": \"").append(entry.getValue()).append("\"\n");
        }
        Files.writeString(file, yaml.toString(), StandardCharsets.UTF_8);

        BridgeConfig.load(file);

        assertEquals(yaml.toString(), Files.readString(file), "nothing to add means nothing is touched");
        assertFalse(Files.exists(file.resolveSibling("config.yml.bak")), "and no backup is made for a no-op");
    }

    @Test
    void anUnknownEventNameIsAProblemNotASilentLoss() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - app-id: "1"
                    groups:
                      - group-openid: "G1"
                        send-to-qq: [chat, resurrection]
                """);

        BridgeConfig.Group group = config.bots().get(0).group("G1").orElseThrow();
        assertTrue(group.sendsToQq(BridgeConfig.McEvent.CHAT));
        assertFalse(group.sendsToQq(BridgeConfig.McEvent.DEATH), "an explicit list replaces the default four");
        assertTrue(config.problems().stream().anyMatch(p -> p.contains("resurrection")),
                config.problems().toString());
    }

    @Test
    void aFileThatIsNotAMappingAtAllSaysSo() throws Exception {
        BridgeConfig config = read("just a string\n");

        assertTrue(config.bots().isEmpty());
        assertEquals(1, config.problems().size(), config.problems().toString());
        // 断言它说了是哪个文件，而不是断言具体措辞 —— 措辞会变，文件名不会。
        assertTrue(config.problems().get(0).contains("config.yml"), config.problems().toString());
    }

    @Test
    void debugIsOffUnlessTheFileAsksForIt() throws Exception {
        BridgeConfig quiet = read("""
                bots:
                  - app-id: "1"
                """);
        assertFalse(quiet.debug(), "a config that says nothing about it stays quiet");

        BridgeConfig loud = read("""
                debug: true
                bots:
                  - app-id: "1"
                """);
        assertTrue(loud.debug());
    }

    @Test
    void theEventNamesTheFileMayUseAreTheOnesInTheEnum() {
        assertEquals(BridgeConfig.McEvent.DEATH, BridgeConfig.McEvent.parse("  death ").orElseThrow());
        assertTrue(BridgeConfig.McEvent.parse("nonsense").isEmpty());
        assertTrue(BridgeConfig.McEvent.parse(null).isEmpty());
    }
}
