package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
        assertTrue(config.problems().get(0).contains("not a mapping"), config.problems().toString());
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
