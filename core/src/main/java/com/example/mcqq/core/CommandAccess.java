package com.example.mcqq.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 谁能在哪个会话里执行命令。
 *
 * <p>放行有三个**互相独立**的来源，任一命中即可 —— 一个既不是群主、也不是管理员、但在白名单里的人
 * 照样能执行：
 *
 * <ol>
 *   <li>白名单（QQ 侧的 openid）；
 *   <li>这个目标额外允许的身份组（子频道管理员 + 自定义身份组）；
 *   <li>内置层级（群主 / 管理员），由 {@code allow} 决定放宽到哪一档。
 * </ol>
 *
 * <p>身份数据**随事件来**（群看 {@code author.member_role}，频道看 {@code member.roles}），
 * 所以这里只做判断，不查任何接口、也没有缓存要维护。
 *
 * <p>和 Minecraft 无关，所以整个判断可以在没有服务器的情况下测。
 */
public final class CommandAccess {

    /** 身份枚举。{@code ADMIN} 包含 {@code OWNER} —— 管理员能做的事群主也能做。 */
    public enum Allow {
        /** 都不行，只认白名单与额外身份组。 */
        NONE("none"),
        /** 群主；频道里是身份组 {@code 4}。 */
        OWNER("owner"),
        /** 群主或管理员；频道里是身份组 {@code 4} 或 {@code 2}。 */
        ADMIN("admin");

        private final String text;

        Allow(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }

        /** 配置文件里写的那个词，写错了只记问题、不抛异常。 */
        static Allow parse(Object value, String where, List<String> problems) {
            String text = value == null ? "" : String.valueOf(value).trim();
            for (Allow allow : values()) {
                if (allow.text.equals(text)) {
                    return allow;
                }
            }
            if (!text.isEmpty()) {
                problems.add(where + " 的 command.allow 是 '" + text + "'，不认识（可用：none / owner / admin），"
                        + "按 none 处理");
            }
            return NONE;
        }
    }

    /**
     * 频道身份组的内置 ID，来自官方文档「频道身份组对象(Role)」：
     * {@code 1} 全体成员、{@code 2} 管理员、{@code 4} 群主/创建者、{@code 5} 子频道管理员。
     */
    static final String ROLE_EVERYONE = "1";

    static final String ROLE_GUILD_ADMIN = "2";
    static final String ROLE_GUILD_OWNER = "4";

    /** 子频道管理员：它是"额外的"，归 {@code roles} 管，{@code allow} 不管。 */
    static final String ROLE_CHANNEL_ADMIN = "5";

    /**
     * 谁想执行命令 —— 三个面的公共形状。
     *
     * <p>群只填 {@code memberRole}，频道只填 {@code roleIds}，私聊两个都空（它没有身份概念）。
     */
    public record Sender(String openid, String memberRole, Set<String> roleIds) {

        public Sender {
            openid = openid == null ? "" : openid;
            memberRole = memberRole == null ? "" : memberRole;
            roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        }

        /** 没有身份的发送者：私聊，或者事件里没带身份字段时的兜底。 */
        public static Sender of(String openid) {
            return new Sender(openid, "", Set.of());
        }
    }

    /** 什么都没配：只有白名单（空）和额外身份组（空），{@code allow} 是 none。 */
    public static final CommandAccess NONE = new CommandAccess(Allow.NONE, Set.of(), Set.of());

    private final Allow allow;
    private final Set<String> whitelist;
    private final Set<String> extraRoleIds;

    CommandAccess(Allow allow, Set<String> whitelist, Set<String> extraRoleIds) {
        this.allow = allow;
        this.whitelist = Collections.unmodifiableSet(new LinkedHashSet<>(whitelist));
        // 内置的群主（4）与管理员（2）归 allow 管，所以**从额外身份组里剔掉** ——
        // 配置解析会为此记一条提示（"写在这里不生效"），这里保证那句话是真的。
        Set<String> extra = new LinkedHashSet<>(extraRoleIds);
        extra.remove(ROLE_GUILD_ADMIN);
        extra.remove(ROLE_GUILD_OWNER);
        this.extraRoleIds = Collections.unmodifiableSet(extra);
    }

    public Allow allow() {
        return allow;
    }

    public Set<String> whitelist() {
        return whitelist;
    }

    /** 额外允许的身份组（子频道管理员 + 自定义）。{@code allow} 管的那两个内置 ID 不在这里。 */
    public Set<String> extraRoleIds() {
        return extraRoleIds;
    }

    /** 这个发送者能不能执行命令。 */
    public boolean permits(Sender sender) {
        // 三个来源互相独立，所以顺序无所谓 —— 写成一个"任一命中"的列表更容易看出这一点。
        if (whitelist.contains(sender.openid())) {
            return true;
        }
        if (!Collections.disjoint(sender.roleIds(), extraRoleIds)) {
            return true;
        }
        if (allow == Allow.NONE) {
            return false;
        }
        if ("owner".equals(sender.memberRole())) {
            return true;
        }
        if (allow == Allow.ADMIN && "admin".equals(sender.memberRole())) {
            return true;
        }
        if (sender.roleIds().contains(ROLE_GUILD_OWNER)) {
            return true;
        }
        return allow == Allow.ADMIN && sender.roleIds().contains(ROLE_GUILD_ADMIN);
    }

    /** 给 {@code /qq status} 用的一句话；没配任何东西时返回 null，让调用方干脆不打印。 */
    public String describe() {
        if (allow == Allow.NONE && whitelist.isEmpty() && extraRoleIds.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder(switch (allow) {
            case NONE -> "只认白名单";
            case OWNER -> "群主";
            case ADMIN -> "群主与管理员";
        });
        if (!whitelist.isEmpty()) {
            text.append(" + ").append(whitelist.size()).append(" 个白名单");
        }
        if (!extraRoleIds.isEmpty()) {
            text.append(" + 身份组 ").append(String.join("/", extraRoleIds));
        }
        return text.toString();
    }
}
