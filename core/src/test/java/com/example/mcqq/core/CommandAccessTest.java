package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 谁能执行命令。放行有三个**互相独立**的来源（白名单 / 额外身份组 / 内置层级），
 * 这里把每一种组合钉住 —— 尤其是"互不影响"那条：不在白名单、也不是群主，但配了身份组，照样放行。
 *
 * <p>纯逻辑，所以不用服务器、不用机器人、不用网络。
 */
class CommandAccessTest {

    private static CommandAccess access(CommandAccess.Allow allow, List<String> whitelist,
            List<String> roles) {
        return new CommandAccess(allow, new LinkedHashSet<>(whitelist), new LinkedHashSet<>(roles));
    }

    /** 群里的一条消息：只有 member_role。 */
    private static CommandAccess.Sender inGroup(String openid, String memberRole) {
        return new CommandAccess.Sender(openid, memberRole, Set.of());
    }

    /** 频道里的一条消息：只有身份组 ID。 */
    private static CommandAccess.Sender inChannel(String openid, String... roleIds) {
        return new CommandAccess.Sender(openid, "", Set.of(roleIds));
    }

    @Test
    void theWhitelistWorksOnItsOwn() {
        // 用户点名要的性质：既不是群主也不是管理员，但在白名单里 —— 照样能执行。
        CommandAccess only = access(CommandAccess.Allow.NONE, List.of("A"), List.of());

        assertTrue(only.permits(inGroup("A", "member")));
        assertTrue(only.permits(inChannel("A")));
        assertFalse(only.permits(inGroup("B", "member")));
    }

    @Test
    void noneMeansNothingButTheWhitelistAndExtraRoles() {
        CommandAccess none = access(CommandAccess.Allow.NONE, List.of(), List.of());

        assertFalse(none.permits(inGroup("X", "owner")), "allow=none 时群主也不行");
        assertFalse(none.permits(inGroup("X", "admin")), "管理员也不行");
        assertFalse(none.permits(inChannel("X", CommandAccess.ROLE_GUILD_OWNER)));
        assertFalse(none.permits(inChannel("X", CommandAccess.ROLE_GUILD_ADMIN)));
    }

    @Test
    void ownerAllowsOnlyTheOwner() {
        CommandAccess owner = access(CommandAccess.Allow.OWNER, List.of(), List.of());

        assertTrue(owner.permits(inGroup("X", "owner")));
        assertFalse(owner.permits(inGroup("X", "admin")), "owner 档不该放行管理员");
        assertTrue(owner.permits(inChannel("X", CommandAccess.ROLE_GUILD_OWNER)));
        assertFalse(owner.permits(inChannel("X", CommandAccess.ROLE_GUILD_ADMIN)));
    }

    @Test
    void adminIncludesTheOwner() {
        CommandAccess admin = access(CommandAccess.Allow.ADMIN, List.of(), List.of());

        assertTrue(admin.permits(inGroup("X", "owner")), "管理员档包含群主");
        assertTrue(admin.permits(inGroup("X", "admin")));
        assertFalse(admin.permits(inGroup("X", "member")));
        assertTrue(admin.permits(inChannel("X", CommandAccess.ROLE_GUILD_OWNER)));
        assertTrue(admin.permits(inChannel("X", CommandAccess.ROLE_GUILD_ADMIN)));
        assertFalse(admin.permits(inChannel("X", CommandAccess.ROLE_EVERYONE)), "全体成员不算");
    }

    @Test
    void extraRolesAreIndependentOfAllow() {
        // 用户点名要的性质：白名单和身份枚举互不影响 —— 身份组也不受 allow 的档位限制。
        CommandAccess none = access(CommandAccess.Allow.NONE, List.of(), List.of(CommandAccess.ROLE_CHANNEL_ADMIN));

        assertTrue(none.permits(inChannel("X", CommandAccess.ROLE_CHANNEL_ADMIN)),
                "allow=none 时，配了子频道管理员照样放行");
        assertFalse(none.permits(inChannel("X", CommandAccess.ROLE_GUILD_ADMIN)),
                "但没配的那个身份组还是不行");
    }

    @Test
    void customRoleIdsWork() {
        CommandAccess custom = access(CommandAccess.Allow.NONE, List.of(), List.of("1234567890"));

        assertTrue(custom.permits(inChannel("X", "1234567890")), "自定义身份组 ID 照常判断");
        assertFalse(custom.permits(inChannel("X", "999")));
    }

    @Test
    void theBuiltInOwnerAndAdminIdsAreAllowBusinessNotRoles() {
        // 写进 roles 的 2 / 4 会被剔掉（配置解析那边同时会记一条提示）——
        // 否则"写了不生效"那句话就是假的。
        CommandAccess confused = access(CommandAccess.Allow.NONE, List.of(),
                List.of(CommandAccess.ROLE_GUILD_ADMIN, CommandAccess.ROLE_GUILD_OWNER));

        assertFalse(confused.permits(inChannel("X", CommandAccess.ROLE_GUILD_OWNER)),
                "allow=none 时，roles 里写 4 不该放行");
        assertTrue(confused.extraRoleIds().isEmpty(), "内置的那两个 ID 不该留在额外身份组里");
    }

    @Test
    void directMessagesHaveNoIdentitySoOnlyTheWhitelistWorks() {
        CommandAccess admin = access(CommandAccess.Allow.ADMIN, List.of("owner-openid"), List.of());

        assertTrue(admin.permits(CommandAccess.Sender.of("owner-openid")));
        assertFalse(admin.permits(CommandAccess.Sender.of("stranger")),
                "私聊没有身份概念，allow 再宽也不该放行陌生人");
    }

    @Test
    void describeIsNullWhenNothingIsConfigured() {
        assertNull(access(CommandAccess.Allow.NONE, List.of(), List.of()).describe(),
                "什么都没配就别在状态里占一行");
    }

    @Test
    void describeSaysWhatIsConfigured() {
        assertEquals("群主与管理员", access(CommandAccess.Allow.ADMIN, List.of(), List.of()).describe());
        assertEquals("只认白名单 + 2 个白名单",
                access(CommandAccess.Allow.NONE, List.of("A", "B"), List.of()).describe());
        assertEquals("群主 + 1 个白名单 + 身份组 5",
                access(CommandAccess.Allow.OWNER, List.of("A"), List.of("5")).describe());
    }
}
