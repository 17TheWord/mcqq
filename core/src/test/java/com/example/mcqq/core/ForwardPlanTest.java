package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.mcqq.core.command.CommandTree;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Minecraft → QQ routing: who gets a line, which wording, and what happens to the colour codes.
 *
 * <p>All of it offline. That is the point of splitting {@code plan} out of {@code forward}: the decisions are
 * a pure function of the config and the event's values, so they can be pinned down without a server, a bot or
 * an HTTP call — and the only thing left needing a real machine is whether Minecraft fires the event at all.
 */
class ForwardPlanTest {

    @TempDir
    Path dir;

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

    /**
     * A runtime with no bot registered: the secret environment variable is never set here, so nothing connects
     * and no thread is started. {@code plan} does not care either way.
     */
    private BridgeRuntime runtime(String yaml) throws IOException {
        Path file = dir.resolve("config.yml");
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        return BridgeRuntime.start(BridgeConfig.parse(file), headless(), new UnboundGroups());
    }

    @Test
    void onlyTheGroupsThatSubscribedGetALine() throws Exception {
        BridgeRuntime runtime = runtime("""
                bots:
                  - id: main
                    app-id: "1"
                    secret-env: NOT_SET_ANYWHERE
                    groups:
                      - group-openid: "G1"
                        label: 订阅了
                        send-to-qq: [chat]
                      - group-openid: "G2"
                        label: 没订阅
                        send-to-qq: [death]
                """);

        List<BridgeRuntime.Delivery> deliveries = runtime.plan(BridgeConfig.McEvent.CHAT,
                Templates.values("player", "Alice", "text", "hi"));

        assertEquals(1, deliveries.size(), deliveries.toString());
        assertEquals("订阅了", deliveries.get(0).target().label());
    }

    @Test
    void theLineIsRenderedFromTheValuesTheEventSupplied() throws Exception {
        BridgeRuntime runtime = runtime("""
                bots:
                  - app-id: "1"
                    secret-env: NOT_SET_ANYWHERE
                    groups:
                      - group-openid: "G1"
                        send-to-qq: [chat]
                """);

        List<BridgeRuntime.Delivery> deliveries = runtime.plan(BridgeConfig.McEvent.CHAT,
                Templates.values("player", "Alice", "text", "你好"));

        assertEquals("[MC] Alice: 你好", deliveries.get(0).line());
    }

    @Test
    void eachGroupUsesTheTemplateInForceForIt() throws Exception {
        BridgeRuntime runtime = runtime("""
                templates:
                  mc-join: "[全局] {player} 来了"
                bots:
                  - app-id: "1"
                    secret-env: NOT_SET_ANYWHERE
                    groups:
                      - group-openid: "G1"
                        label: 继承全局
                        send-to-qq: [join]
                      - group-openid: "G2"
                        label: 自己覆盖
                        send-to-qq: [join]
                        templates:
                          mc-join: "[G2] {player} 上线"
                """);

        List<BridgeRuntime.Delivery> deliveries = runtime.plan(BridgeConfig.McEvent.JOIN,
                Templates.values("player", "Alice"));

        assertEquals(2, deliveries.size(), deliveries.toString());
        assertEquals("[全局] Alice 来了", deliveries.get(0).line());
        assertEquals("[G2] Alice 上线", deliveries.get(1).line());
    }

    @Test
    void aGroupWithAnEmptyTemplateGetsNothing() throws Exception {
        BridgeRuntime runtime = runtime("""
                bots:
                  - app-id: "1"
                    secret-env: NOT_SET_ANYWHERE
                    groups:
                      - group-openid: "G1"
                        label: 静音
                        send-to-qq: [join]
                        templates:
                          mc-join: ""
                """);

        assertTrue(runtime.plan(BridgeConfig.McEvent.JOIN, Templates.values("player", "Alice")).isEmpty());
    }

    @Test
    void colourCodesAreDroppedBeforeQqSeesTheLine() throws Exception {
        BridgeRuntime runtime = runtime("""
                templates:
                  mc-chat: "§b[MC]§r {player}: {text}"
                bots:
                  - app-id: "1"
                    secret-env: NOT_SET_ANYWHERE
                    groups:
                      - group-openid: "G1"
                        send-to-qq: [chat]
                """);

        List<BridgeRuntime.Delivery> deliveries = runtime.plan(BridgeConfig.McEvent.CHAT,
                Templates.values("player", "Alice", "text", "hi"));

        assertEquals("[MC] Alice: hi", deliveries.get(0).line(), "QQ renders plain text, so § goes");
    }

    @Test
    void contextPlaceholdersAreFilledForEveryEvent() throws Exception {
        BridgeRuntime runtime = runtime("""
                templates:
                  mc-quit: "{player} 走了（{platform}）"
                bots:
                  - app-id: "1"
                    secret-env: NOT_SET_ANYWHERE
                    groups:
                      - group-openid: "G1"
                        send-to-qq: [quit]
                """);

        List<BridgeRuntime.Delivery> deliveries = runtime.plan(BridgeConfig.McEvent.QUIT,
                Templates.values("player", "Alice"));

        assertEquals("Alice 走了（test-26.1.2）", deliveries.get(0).line());
    }

    @Test
    void aBotWithSeveralGroupsGetsOneDeliveryEach() throws Exception {
        BridgeRuntime runtime = runtime("""
                bots:
                  - id: main
                    app-id: "1"
                    secret-env: NOT_SET_ANYWHERE
                    groups:
                      - group-openid: "G1"
                        send-to-qq: [chat]
                      - group-openid: "G2"
                        send-to-qq: [chat]
                  - id: second
                    app-id: "2"
                    secret-env: NOT_SET_ANYWHERE
                    groups:
                      - group-openid: "G3"
                        send-to-qq: [chat]
                """);

        List<BridgeRuntime.Delivery> deliveries = runtime.plan(BridgeConfig.McEvent.CHAT,
                Templates.values("player", "Alice", "text", "hi"));

        assertEquals(List.of("G1", "G2", "G3"),
                deliveries.stream().map(delivery -> delivery.target().conversationId()).toList());
        assertEquals(List.of("1", "1", "2"),
                deliveries.stream().map(delivery -> delivery.bot().appId()).toList(),
                "each delivery carries the bot that owns the group");
    }
}
