package com.example.mcqq.core;

import io.github.skiesworld.qqbot.QQBotClient;
import io.github.skiesworld.qqbot.error.ApiException;
import io.github.skiesworld.qqbot.error.AuditPendingException;
import io.github.skiesworld.qqbot.message.MessageBuilder;
import java.time.Duration;

/**
 * The one call the bridge makes out to QQ, with the platform's refusals turned into log lines.
 *
 * <p>A send happens on the bridge's own threads: a QQ round trip is never waited on inside a server tick, and a
 * refusal (rate limit, review) is logged rather than thrown back at whoever just talked. That is the whole
 * contract — a QQ outage must not take a Minecraft server down with it.
 */
final class QqSender {

    /**
     * What the platform did with one send. {@code accepted} counts the review queue as success: from the
     * bridge's side the message was taken. The note exists for the caller that has someone to tell, which is
     * only {@code /qq test}.
     */
    record Outcome(boolean accepted, String note) {
    }

    private QqSender() {
    }

    /** Never throws. */
    static Outcome send(QQBotClient bot, BridgeConfig.Group group, String text) {
        try {
            bot.api().group().sendGroupMessage(group.groupOpenid(), MessageBuilder.of(text).toGroup());
            Log.debug("已发往 QQ 群 " + group.label() + "：" + text);
            return new Outcome(true, "已发出");
        } catch (AuditPendingException e) {
            Log.info("发往 QQ 群 " + group.label() + " 的消息进入人工审核：" + text);
            if (e.auditId() != null) {
                bot.audits().resultOf(e.auditId(), Duration.ofMinutes(5)).thenAccept(outcome ->
                        Log.info("QQ 审核结论 " + outcome.auditId() + " -> " + outcome.status()));
            }
            return new Outcome(true, "平台收下了，但进了人工审核");
        } catch (ApiException e) {
            Log.warn("发往 QQ 群 " + group.label() + " 失败 err_code=" + e.errCode() + "：" + e.getMessage()
                    + "（内容：" + text + "）");
            return new Outcome(false, "平台拒绝 err_code=" + e.errCode() + "：" + e.getMessage());
        } catch (RuntimeException e) {
            Log.warn("发往 QQ 群 " + group.label() + " 失败（内容：" + text + "）", e);
            return new Outcome(false, e.getClass().getSimpleName() + "：" + e.getMessage());
        }
    }
}
