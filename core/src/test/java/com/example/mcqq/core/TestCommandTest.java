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
 * {@code /qq test}: what it says before anything is sent.
 *
 * <p>The sends themselves go down the ordinary path and report through the log, so what is worth pinning down
 * offline is the part that does not need a network — that it names a bot which never started instead of
 * pretending, and that an empty config says so.
 */
class TestCommandTest {

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

    /** A started bridge whose config is whatever {@code yaml} says. No secret env var is set, so nothing connects. */
    private Bridge bridge(String yaml) throws IOException {
        Path file = dir.resolve("mcqq").resolve("config.yml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        Bridge bridge = new Bridge(headless());
        bridge.start();
        return bridge;
    }

    @Test
    void aBotThatNeverStartedIsNamedRatherThanSkippedSilently() throws Exception {
        List<String> lines = bridge("""
                bots:
                  - id: main
                    app-id: "123456789"
                    secret-env: NOT_SET_ANYWHERE
                    groups:
                      - group-openid: "G1"
                        label: 主群
                """).test();

        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("main"), lines.get(0));
        assertTrue(lines.get(0).contains("未注册"), lines.get(0));
    }

    @Test
    void anEmptyConfigSaysThereIsNothingToTest() throws Exception {
        List<String> lines = bridge("bots: []\n").test();

        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("没有"), lines.get(0));
    }

    @Test
    void itSaysSoWhenTheBridgeIsNotRunning() {
        assertEquals(List.of("桥接未运行（服务器还没起来，或已经关了）"), new Bridge(headless()).test());
    }
}
