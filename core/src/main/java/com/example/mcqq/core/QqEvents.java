package com.example.mcqq.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.skiesworld.qqbot.event.QQEvent;
import io.github.skiesworld.qqbot.event.QQMessageEvent;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 从一条 QQ 消息里取出"谁在说话"，收成权限判断要的那个形状。
 *
 * <p>三个面的字段不一样，而且**都在事件里**（不用额外调接口）：
 *
 * <ul>
 *   <li>群：{@code author.member_role}，取值 {@code owner} / {@code admin} / {@code member}；
 *   <li>频道：{@code member.roles}，身份组 ID 列表（{@code 4} 群主、{@code 2} 管理员、
 *       {@code 5} 子频道管理员，其余是自定义）；
 *   <li>私聊：两个都没有 —— 它只认白名单。
 * </ul>
 *
 * <p>会话标识也在这里取：**群是 {@code group_openid}、子频道是 {@code channel_id}、
 * 私聊是 {@code user_openid}**。SDK 的 {@code scene()} 里就有"哪个面取哪个键"那张表，所以先问它，
 * 它认不出来时才退回 {@code conversationId()}。
 *
 * <p>读事件字段这件事只有这一处，所以它单独在这儿、单独测。
 */
final class QqEvents {

    private QqEvents() {
    }

    /** 子频道属于哪个频道。群与私聊没有这个字段，返回空串。 */
    static String guildId(QQEvent event) {
        com.google.gson.JsonObject raw = event.rawObject();
        if (raw == null || !raw.has("guild_id") || !raw.get("guild_id").isJsonPrimitive()) {
            return "";
        }
        return raw.get("guild_id").getAsString().trim();
    }

    /** 这条消息属于哪个会话：群 openid / 子频道 id / 用户 openid。 */
    static String conversationId(QQEvent event) {
        var scene = event.scene();
        String id = scene == null ? null : scene.targetId(event);
        return id == null || id.isBlank() ? event.conversationId() : id;
    }

    /**
     * 去掉正文开头的 @ 标记。
     *
     * <p>QQ 把"@ 机器人"写成**正文的一部分**（{@code <@openid>}），所以 {@code @机器人 /mcc list}
     * 收到的正文其实是 {@code <@...> /mcc list} —— 不剥掉的话命令前缀永远匹配不上。
     * 这是真机验证发现的：消息进了聊天栏，命令却完全没执行。
     */
    static String stripLeadingMentions(String text) {
        String trimmed = text.stripLeading();
        while (trimmed.startsWith("<@")) {
            int end = trimmed.indexOf('>');
            if (end < 0) {
                break;
            }
            trimmed = trimmed.substring(end + 1).stripLeading();
        }
        return trimmed;
    }

    static CommandAccess.Sender of(QQMessageEvent message) {
        String memberRole = message.author() == null || message.author().memberRole == null
                ? "" : message.author().memberRole;
        return new CommandAccess.Sender(message.senderId(), memberRole, roleIds(message));
    }

    /** 频道事件里的 {@code member.roles}；群事件里没有这一段，返回空集。 */
    private static Set<String> roleIds(QQMessageEvent message) {
        JsonObject raw = message.rawObject();
        if (raw == null || !raw.has("member") || !raw.get("member").isJsonObject()) {
            return Set.of();
        }
        JsonElement roles = raw.getAsJsonObject("member").get("roles");
        if (roles == null || !roles.isJsonArray()) {
            return Set.of();
        }
        JsonArray array = roles.getAsJsonArray();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonElement role : array) {
            if (role.isJsonPrimitive()) {
                String id = role.getAsString().trim();
                if (!id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }
}
