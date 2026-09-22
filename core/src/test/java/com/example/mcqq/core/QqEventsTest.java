package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.skiesworld.qqbot.event.EventType;
import io.github.skiesworld.qqbot.event.QQMessageEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 读事件字段：谁在说话，以及这条消息属于哪个会话。三个面各自的字段不一样，
 * 而且**都在事件里** —— 所以这一段能离线钉住：拿一条真的形状的事件，看取出来的是什么。
 */
class QqEventsTest {

    /** 一条群消息：身份在 author.member_role 上。 */
    private static QQMessageEvent groupMessage(String openid, String memberRole) {
        return event(EventType.GROUP_MESSAGE_CREATE, """
                {"group_openid":"G1","content":"hi","author":
                  {"member_openid":"%s","member_role":"%s","username":"小明"}}"""
                .formatted(openid, memberRole));
    }

    /** 一条频道消息：身份在 member.roles 上（身份组 ID 列表）。 */
    private static QQMessageEvent channelMessage(String openid, String rolesJson) {
        return event(EventType.AT_MESSAGE_CREATE, """
                {"guild_id":"GUILD","channel_id":"CH1","content":"hi",
                 "author":{"id":"%s","username":"小红"},
                 "member":{"joined_at":"2021-04-12T16:34:42+08:00","roles":%s}}"""
                .formatted(openid, rolesJson));
    }

    private static QQMessageEvent event(EventType type, String json) {
        JsonObject data = JsonParser.parseString(json).getAsJsonObject();
        return new QQMessageEvent("m1", 0, null, type.name(), type, data, null);
    }

    @Test
    void aGroupMessageCarriesTheMemberRole() {
        assertEquals("owner", QqEvents.of(groupMessage("A", "owner")).memberRole());
        assertEquals("admin", QqEvents.of(groupMessage("A", "admin")).memberRole());
        assertEquals("member", QqEvents.of(groupMessage("A", "member")).memberRole());
        assertEquals("A", QqEvents.of(groupMessage("A", "owner")).openid());
    }

    @Test
    void aChannelMessageCarriesTheRoleIds() {
        CommandAccess.Sender sender = QqEvents.of(channelMessage("U1", "[\"1\",\"2\"]"));

        assertEquals(Set.of("1", "2"), sender.roleIds());
        assertEquals("U1", sender.openid());
        assertEquals("", sender.memberRole(), "频道没有 member_role，那个字段该是空的");
    }

    @Test
    void theGuildIdComesFromTheEventAndIsEmptyElsewhere() {
        assertEquals("GUILD", QqEvents.guildId(channelMessage("U1", "[\"1\"]")),
                "绑子频道时要把 guild-id 一起写进配置，所以它得读得出来");
        assertEquals("", QqEvents.guildId(groupMessage("A", "member")), "群没有 guild_id");
    }

    @Test
    void aChannelMessageWithNoRolesIsEmptyNotBroken() {
        assertEquals(Set.of(), QqEvents.of(channelMessage("U1", "[]")).roleIds());
        assertEquals(Set.of(), QqEvents.of(channelMessage("U1", "null")).roleIds());
    }

    @Test
    void theFieldsFeedThePermissionCheck() {
        // 群主 + allow=owner → 放行；同一条消息在 allow=none 下不放行。
        CommandAccess ownerOnly = new CommandAccess(CommandAccess.Allow.OWNER, Set.of(), Set.of());
        assertTrue(ownerOnly.permits(QqEvents.of(groupMessage("A", "owner"))));
        assertFalse(ownerOnly.permits(QqEvents.of(groupMessage("A", "admin"))));

        // 频道：roles 里有 4（群主）→ allow=owner 放行；只有 1（全体）→ 不放行。
        assertTrue(ownerOnly.permits(QqEvents.of(channelMessage("U1", "[\"4\"]"))));
        assertFalse(ownerOnly.permits(QqEvents.of(channelMessage("U1", "[\"1\"]"))));

        // 额外身份组（子频道管理员 5）独立于 allow：allow=none 也放行。
        CommandAccess extra = new CommandAccess(CommandAccess.Allow.NONE, Set.of(), Set.of("5"));
        assertTrue(extra.permits(QqEvents.of(channelMessage("U1", "[\"1\",\"5\"]"))));
    }
}
