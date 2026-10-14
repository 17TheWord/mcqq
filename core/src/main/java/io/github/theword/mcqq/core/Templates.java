package io.github.theword.mcqq.core;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 运维可改的消息文案，以及它们能用的占位符。
 *
 * <p>我们的占位符是 {@code {braces}}（第三方库用 {@code %percent%}，一份模板能同时带两种）。
 * 没有值的占位符<b>原样留下</b>，不替换成空 —— 拼写错误会显示在游戏里，而不是变成一行空白。
 *
 * <p>{@code mention-*} 那四个不是整行，是<em>片段</em>：替换 {@code {text}} 里的一段，
 * 所以值里带 {@code §} 颜色码是正常用法（见 {@link #MENTION_USER}）。
 */
public final class Templates {

    /** QQ → Minecraft. */
    public static final String QQ_CHAT = "qq-chat";
    public static final String QQ_ATTACHMENT = "qq-attachment";
    public static final String QQ_MEMBER_ADD = "qq-member-add";
    public static final String QQ_MEMBER_REMOVE = "qq-member-remove";

    /** Minecraft → QQ. One per {@link BridgeConfig.McEvent}, named to match the config's event names. */
    public static final String MC_CHAT = "mc-chat";
    public static final String MC_JOIN = "mc-join";
    public static final String MC_QUIT = "mc-quit";
    public static final String MC_DEATH = "mc-death";

    /**
     * QQ → Minecraft. {@code @} 标记的渲染方式。
     *
     * <p>和上面那些**不是一类**：上面每个键是"某个事件整行怎么说"，这四个是**片段** ——
     * 它们替换掉 {@code {text}} 里的一段，所以值里带 {@code §} 颜色码是正常用法。
     * 放在同一个 {@code templates:} 段里，是为了白拿三样现成的东西：加载时自动补键、
     * {@code /qq templates} 能列出来、以及按群 / 子频道覆盖。
     *
     * <p>空值 = 把这个标记**删掉**（想退回"@ 标记不出现在聊天里"就填空）。
     */
    public static final String MENTION_USER = "mention-user";
    public static final String MENTION_CHANNEL = "mention-channel";
    public static final String MENTION_EMOJI = "mention-emoji";
    public static final String MENTION_EVERYONE = "mention-everyone";

    /**
     * Every name the bridge can fill. A template using anything else has a typo in it.
     *
     * <p>{@code name} / {@code id} 只有 {@code mention-*} 那四个键会填：{@code name} 是 QQ 昵称
     * （能查到才有），{@code id} 是原始 id（总在）。别的事件模板里写它们只会原样显示出来。
     */
    public static final Set<String> KNOWN = Set.of(
            "group", "user", "text", "count", "member", "player", "killer", "platform", "time",
            "name", "id");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Map<String, String> DEFAULTS = defaults();

    private Templates() {
    }

    /** The built-in wording: what a config that says nothing about templates gets, and what used to be hardcoded. */
    public static Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(QQ_CHAT, "§b[QQ {group}]§r {user}§7:§r {text}");
        map.put(QQ_ATTACHMENT, "§b[QQ {group}]§r {user} 发了 {count} 个附件（文字里不含它们）");
        map.put(QQ_MEMBER_ADD, "§8[QQ {group}] {member} 进了群§r");
        map.put(QQ_MEMBER_REMOVE, "§8[QQ {group}] {member} 退了群§r");
        map.put(MC_CHAT, "[MC] {player}: {text}");
        map.put(MC_JOIN, "[MC] {player} 加入了世界");
        map.put(MC_QUIT, "[MC] {player} 离开了世界");
        map.put(MC_DEATH, "[MC] {player} 死亡（{killer}）");
        // @ 标记的默认渲染。名字来自事件里的 mentions 数组（真机实测：群和频道的 @用户 都在里面，
        // 带 username），所以**不用调任何接口**。
        //
        // · user   —— 群和频道共用：<@openid> / <@!数字id> 都走它。§a 绿字，§r 复位，后面的正文仍是默认色。
        // · channel—— <#子频道id> 的兜底。实测平台**已经把子频道渲染成 #名字**推过来（不是 <#id>），
        //             所以这个键基本不会触发；真触发时手里只有 id，没有名字。
        // · emoji  —— <emoji:277> 这类系统表情，SDK 不认、平台不转，默认给个可读的占位。
        // · everyone—— @everyone 是纯文本，直接换掉。
        map.put(MENTION_USER, "§a@{name}§r");
        map.put(MENTION_CHANNEL, "§b#{id}§r");
        map.put(MENTION_EMOJI, "[表情]");
        map.put(MENTION_EVERYONE, "§e@所有人§r");
        return java.util.Collections.unmodifiableMap(map);
    }

    /** Every template key, in the order a config file should list them. */
    public static List<String> keys() {
        return List.copyOf(DEFAULTS.keySet());
    }

    /** The template key a Minecraft event uses, derived so the two can never drift apart. */
    public static String mcKey(BridgeConfig.McEvent event) {
        return "mc-" + event.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The values every template gets for free, plus whatever the event supplied. {@code platform} and
     * {@code time} are the same in both directions, so both call sites share this instead of each adding them.
     */
    public static Map<String, String> withContext(Map<String, String> values, String platformLabel) {
        Map<String, String> all = new LinkedHashMap<>();
        all.put("platform", platformLabel);
        all.put("time", LocalTime.now().format(TIME_FORMAT));
        all.putAll(values);
        return all;
    }

    /** Substitutes the values we have; leaves anything else untouched. An empty template means "stay quiet". */
    public static String render(String template, Map<String, String> values) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group(0) : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** The names a template uses, whether or not they are known ones. */
    public static Set<String> placeholders(String template) {
        Set<String> names = new LinkedHashSet<>();
        if (template == null) {
            return names;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }

    /** Names in this template that the bridge will never fill — i.e. typos worth reporting at load time. */
    public static Set<String> unknown(String template) {
        Set<String> unknown = placeholders(template);
        unknown.removeAll(KNOWN);
        return unknown;
    }

    /** Convenience for the adapters: {@code values("player", name, "text", text)}, in pairs. */
    public static Map<String, String> values(String... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("values() takes key/value pairs");
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            values.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return values;
    }
}
