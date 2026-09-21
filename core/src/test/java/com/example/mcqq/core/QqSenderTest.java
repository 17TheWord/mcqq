package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.skiesworld.qqbot.BotConfig;
import io.github.skiesworld.qqbot.QQBotClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The one call the bridge makes out to QQ, proven against a doubled platform: what lands on the wire for a group
 * that asked for Minecraft events, and what a refusal does to the caller — which is nothing, because a QQ outage
 * must not take a server tick with it.
 */
class QqSenderTest {

    private final MockWebServer server = new MockWebServer();

    private QQBotClient bot;
    private BridgeConfig.Group group;

    @TempDir
    Path dir;

    @BeforeEach
    void startBotAgainstTheDouble() throws Exception {
        server.start();
        bot = QQBotClient.create(BotConfig.builder("APP").accessToken("TOKEN")
                .apiBase(server.url("/").toString())
                .build());
        Path file = dir.resolve("config.yml");
        Files.writeString(file, """
                bots:
                  - id: main
                    app-id: "APP"
                    groups:
                      - group-openid: "GROUP1"
                        label: 主群
                """, StandardCharsets.UTF_8);
        group = BridgeConfig.parse(file).bots().get(0).group("GROUP1").orElseThrow();
    }

    @AfterEach
    void stopBot() throws Exception {
        bot.close();
        server.close();
    }

    @Test
    void aForwardedLineIsOnePostToThatGroup() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ret\":0,\"msg_id\":\"M1\",\"seq\":7}"));

        QqSender.send(bot, group, "[MC] Alice 加入了世界");

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request, "the bridge posted to the group");
        assertEquals("POST", request.getMethod());
        assertEquals("/v2/groups/GROUP1/messages", request.getPath());
        assertEquals("QQBot TOKEN", request.getHeader("Authorization"));
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("[MC] Alice 加入了世界"), body);
        assertTrue(body.contains("\"msg_type\":0"), "an active text message, not a passive answer: " + body);
    }

    @Test
    void aPlatformRefusalEndsTheSendAndNothingElse() {
        server.enqueue(new MockResponse().setBody("{\"code\":40054,\"message\":\"invalid group openid\"}"));

        assertDoesNotThrow(() -> QqSender.send(bot, group, "[MC] 发不出去"));
        assertEquals(1, server.getRequestCount(), "a refusal is not retried behind the tick's back");
    }

    @Test
    void aMessageSentForReviewIsReportedAndNotResent() {
        server.enqueue(new MockResponse().setBody("{\"code\":304023,\"message\":\"api audit not pass\"}"));

        assertDoesNotThrow(() -> QqSender.send(bot, group, "[MC] 等审核"));
        assertEquals(1, server.getRequestCount());
    }
}
