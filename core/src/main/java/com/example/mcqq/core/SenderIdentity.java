package com.example.mcqq.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
 * <p>这是"事件里的原始字段"到"权限判断的输入"之间唯一的一层，所以它单独在这儿、单独测。
 */
final class SenderIdentity {

    private SenderIdentity() {
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
