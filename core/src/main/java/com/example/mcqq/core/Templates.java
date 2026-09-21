package com.example.mcqq.core;

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
 * The message templates an operator can rewrite, and the placeholders they may use.
 *
 * <p>Every line the bridge prints or forwards used to be composed in Java, which meant a server owner could not
 * change a word of it. Now the wording lives in the config as a template with <em>named</em> placeholders
 * ({@code {player}}, {@code {text}}, …), and this class knows the two things that need to agree about them:
 * the set of names the bridge fills, and how to substitute them.
 *
 * <p>Two deliberate choices:
 *
 * <ul>
 *   <li>Our placeholders are {@code {braces}}. The third-party placeholder libraries (Patbox's on the mod side,
 *       HelpChat's on Bukkit) both use {@code %percent%}, so a template can carry both without ambiguity —
 *       and ours is the one that always resolves.
 *   <li>A placeholder with no value is left <em>as written</em> rather than replaced with nothing. A typo then
 *       shows up in game instead of turning into a silently empty line.
 * </ul>
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

    /** Every name the bridge can fill. A template using anything else has a typo in it. */
    public static final Set<String> KNOWN = Set.of(
            "group", "user", "text", "count", "member", "player", "killer", "platform", "time");

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
