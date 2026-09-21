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
 * The message templates: what the bridge substitutes, what it refuses to, and which of the three levels wins.
 *
 * <p>All of it is testable without a server, which is the reason the wording moved into the core instead of
 * staying a string literal in each adapter.
 */
class TemplatesTest {

    @TempDir
    Path dir;

    private BridgeConfig read(String yaml) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return BridgeConfig.parse(file);
    }

    private static final String ONE_GROUP = """
            bots:
              - app-id: "1"
                groups:
                  - group-openid: "G1"
                    label: 主群
            """;

    @Test
    void substitutesTheValuesItHasAndLeavesTheRestAlone() {
        String rendered = Templates.render("[MC] {player}: {text} ({platform})",
                Templates.values("player", "Alice", "text", "hi"));

        assertEquals("[MC] Alice: hi ({platform})", rendered,
                "a placeholder with no value stays visible instead of turning into an empty line");
    }

    @Test
    void anEmptyTemplateMeansStayQuiet() {
        assertEquals("", Templates.render("", Map.of("player", "Alice")));
        assertEquals("", Templates.render(null, Map.of()));
    }

    @Test
    void contextAddsPlatformAndTimeToWhateverTheEventSupplied() {
        Map<String, String> values = Templates.withContext(Templates.values("player", "Alice"), "paper-26.2");

        assertEquals("Alice", values.get("player"));
        assertEquals("paper-26.2", values.get("platform"));
        assertTrue(values.get("time").matches("\\d\\d:\\d\\d:\\d\\d"), values.get("time"));
    }

    @Test
    void aMisspelledPlaceholderIsReportedRatherThanIgnored() {
        assertEquals(List.of("plyer"), List.copyOf(Templates.unknown("[MC] {plyer}: {text}")));
        assertTrue(Templates.unknown("[MC] {player}: {text}").isEmpty());
    }

    @Test
    void everyTemplateKeyHasABuiltInDefaultSoAnOlderConfigKeepsWorking() {
        for (String key : Templates.keys()) {
            assertFalse(Templates.defaults().get(key).isEmpty(), key);
        }
        assertEquals("[MC] {player} 加入了世界", Templates.defaults().get(Templates.MC_JOIN));
    }

    @Test
    void aConfigWithoutTemplatesUsesTheDefaultsAndSaysSoNowhereLoud() throws Exception {
        BridgeConfig config = read(ONE_GROUP);

        assertTrue(config.templates().isEmpty());
        assertTrue(config.problems().isEmpty(), config.problems().toString());
        assertEquals(Templates.defaults().get(Templates.MC_CHAT), config.template("G1", Templates.MC_CHAT));
    }

    @Test
    void theGlobalTemplateBeatsTheBuiltInDefault() throws Exception {
        BridgeConfig config = read("""
                templates:
                  mc-chat: "{player} 说：{text}"
                """ + ONE_GROUP);

        assertTrue(config.problems().isEmpty(), config.problems().toString());
        assertEquals("{player} 说：{text}", config.template("G1", Templates.MC_CHAT));
        assertEquals(Templates.defaults().get(Templates.MC_JOIN), config.template("G1", Templates.MC_JOIN),
                "a key the file did not mention still falls back");
    }

    @Test
    void aGroupOverridesOnlyTheKeysItNames() throws Exception {
        BridgeConfig config = read("""
                templates:
                  mc-chat: "{player}: {text}"
                  mc-join: "[MC] {player} 加入了世界"
                bots:
                  - app-id: "1"
                    groups:
                      - group-openid: "G1"
                        label: 主群
                        templates:
                          mc-chat: "[主群] {player}: {text}"
                """);

        assertTrue(config.problems().isEmpty(), config.problems().toString());
        assertEquals("[主群] {player}: {text}", config.template("G1", Templates.MC_CHAT), "the group's override wins");
        assertEquals("[MC] {player} 加入了世界", config.template("G1", Templates.MC_JOIN),
                "and only for the key it named");
    }

    @Test
    void aGroupCanSilenceOneEventWithAnEmptyTemplate() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - app-id: "1"
                    groups:
                      - group-openid: "G1"
                        templates:
                          mc-join: ""
                """);

        assertEquals("", config.template("G1", Templates.MC_JOIN));
        assertEquals(Templates.defaults().get(Templates.MC_CHAT), config.template("G1", Templates.MC_CHAT));
    }

    @Test
    void anUnknownPlaceholderInTheFileIsAProblem() throws Exception {
        BridgeConfig config = read("""
                templates:
                  mc-chat: "{player} 对 {nonsense} 说：{text}"
                """ + ONE_GROUP);

        assertTrue(config.problems().stream().anyMatch(p -> p.contains("nonsense")), config.problems().toString());
        assertEquals("{player} 对 {nonsense} 说：{text}", config.template("G1", Templates.MC_CHAT),
                "the template still loads — the operator gets told, not blocked");
    }

    @Test
    void anUnknownTemplateKeyIsReportedAndDropped() throws Exception {
        BridgeConfig config = read("""
                templates:
                  mc-chat: "{player}: {text}"
                  mc-chatt: "{player}: {text}"
                """ + ONE_GROUP);

        assertTrue(config.problems().stream().anyMatch(p -> p.contains("mc-chatt")), config.problems().toString());
        assertFalse(config.templates().containsKey("mc-chatt"));
        assertEquals("{player}: {text}", config.template("G1", Templates.MC_CHAT));
    }

    @Test
    void aGroupOverrideIsCheckedToo() throws Exception {
        BridgeConfig config = read("""
                bots:
                  - app-id: "1"
                    groups:
                      - group-openid: "G1"
                        templates:
                          qq-chat: "{who} 说：{text}"
                """);

        assertTrue(config.problems().stream().anyMatch(p -> p.contains("who")), config.problems().toString());
    }
}
