package com.example.mcqq.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * {@code config/mcqq/config.yml}: which bots run, which groups they bridge, and which Minecraft events go out.
 *
 * <p>The secret is read from the environment by name rather than stored in the file, because this file sits in a
 * server directory that gets copied, backed up and screenshotted. A missing file is created from the packaged
 * template, so a first start shows an admin what the knobs are instead of failing.
 *
 * <p>Nothing here mentions Minecraft: this class is the reason the core can be tested without a game.
 */
public final class BridgeConfig {

    /** Outbound Minecraft events; the config file names them in lower case, the way the QQ events are named. */
    public enum McEvent {
        CHAT,
        JOIN,
        QUIT,
        DEATH;

        public static Optional<McEvent> parse(String raw) {
            if (raw == null) {
                return Optional.empty();
            }
            String key = raw.trim().toUpperCase(Locale.ROOT);
            for (McEvent event : values()) {
                if (event.name().equals(key)) {
                    return Optional.of(event);
                }
            }
            return Optional.empty();
        }
    }

    /** One QQ group: where it is bridged and, per direction, what is allowed in it. */
    public static final class Group {

        private final String groupOpenid;
        private final String label;
        private final boolean receiveFromQq;
        private final Set<McEvent> sendToQq;
        private final Map<String, String> templates;

        Group(String groupOpenid, String label, boolean receiveFromQq, Set<McEvent> sendToQq,
                Map<String, String> templates) {
            this.groupOpenid = groupOpenid;
            this.label = label;
            this.receiveFromQq = receiveFromQq;
            // Linked, so /qq status shows the events in the order the file wrote them.
            this.sendToQq = Collections.unmodifiableSet(new LinkedHashSet<>(sendToQq));
            // Only what this group overrides; anything else falls through to the global template.
            this.templates = Collections.unmodifiableMap(new LinkedHashMap<>(templates));
        }

        public String groupOpenid() {
            return groupOpenid;
        }

        /** Name used in chat lines and {@code /qq status}; falls back to the openid. */
        public String label() {
            return label.isBlank() ? groupOpenid : label;
        }

        public boolean receivesFromQq() {
            return receiveFromQq;
        }

        public boolean sendsToQq(McEvent event) {
            return sendToQq.contains(event);
        }

        /** What this group receives from Minecraft, for {@code /qq status}. */
        public Set<McEvent> sendEvents() {
            return sendToQq;
        }

        /** The templates this group overrides; empty means it uses the global ones. */
        public Map<String, String> templates() {
            return templates;
        }

        @Override
        public String toString() {
            return "Group{" + groupOpenid + " '" + label + "' in=" + receiveFromQq + " out=" + sendToQq
                    + " templates=" + templates.keySet() + '}';
        }
    }

    /** One QQ bot account and the groups it bridges. */
    public static final class Bot {

        private final String id;
        private final String appId;
        private final String secretEnvironmentVariable;
        private final Map<String, Group> groups;

        Bot(String id, String appId, String secretEnvironmentVariable, Map<String, Group> groups) {
            this.id = id;
            this.appId = appId;
            this.secretEnvironmentVariable = secretEnvironmentVariable;
            // Copy, but keep the file's order: Map.copyOf would shuffle the groups in /qq status and in the
            // forward loop.
            this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
        }

        public String id() {
            return id;
        }

        public String appId() {
            return appId;
        }

        public String secretEnvironmentVariable() {
            return secretEnvironmentVariable;
        }

        public List<Group> groups() {
            return List.copyOf(groups.values());
        }

        public Optional<Group> group(String groupOpenid) {
            return Optional.ofNullable(groups.get(groupOpenid));
        }

        @Override
        public String toString() {
            return "Bot{" + id + " app=" + appId + " groups=" + groups.keySet() + '}';
        }
    }

    /** What the packaged template leaves in the file; a bot or group still carrying it was never filled in. */
    private static final String PLACEHOLDER = "REPLACE_ME";

    private final Map<String, Bot> bots;
    private final Map<String, String> templates;
    private final boolean debug;
    private final List<String> problems;

    private BridgeConfig(Map<String, Bot> bots, Map<String, String> templates, boolean debug,
            List<String> problems) {
        this.bots = bots;
        this.templates = Collections.unmodifiableMap(new LinkedHashMap<>(templates));
        this.debug = debug;
        this.problems = problems;
    }

    /**
     * Whether the bridge should say why it did or did not forward something. Off by default: the lines are
     * useful exactly when an operator is asking that question, and noisy otherwise.
     */
    public boolean debug() {
        return debug;
    }

    public List<Bot> bots() {
        return List.copyOf(bots.values());
    }

    /** The global templates the file set, without the built-in fallbacks. */
    public Map<String, String> templates() {
        return templates;
    }

    /**
     * The template in force for one key and one group: the group's override if it wrote one, else the global
     * one from the file, else the built-in default. That is the whole precedence rule, in one place.
     */
    public String template(String groupOpenid, String key) {
        Optional<Group> group = group(groupOpenid);
        if (group.isPresent()) {
            String override = group.get().templates().get(key);
            if (override != null) {
                return override;
            }
        }
        return globalTemplate(key);
    }

    /** The template for a key with no group in the picture — i.e. what a group inherits. */
    public String globalTemplate(String key) {
        String configured = templates.get(key);
        return configured != null ? configured : Templates.defaults().getOrDefault(key, "");
    }

    /** The group with this openid, whichever bot owns it. */
    public Optional<Group> group(String groupOpenid) {
        for (Bot bot : bots.values()) {
            Optional<Group> found = bot.group(groupOpenid);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    /** What was wrong with the file; the bridge still runs with whatever did parse. */
    public List<String> problems() {
        return List.copyOf(problems);
    }

    /** The single bot most servers have, or empty when none is configured. */
    public Optional<Bot> onlyBot() {
        return bots.size() == 1 ? Optional.of(bots.values().iterator().next()) : Optional.empty();
    }

    /** What a first start writes out, and what is kept next to the live config as the annotated reference. */
    private static final String EXAMPLE_FILE = "config.example.yml";
    private static final String BACKUP_SUFFIX = ".bak";

    public static Path configPath(Path configDir) {
        return configDir.resolve(Constants.MOD_ID).resolve("config.yml");
    }

    /**
     * Reads {@code path}, and keeps it complete:
     *
     * <ol>
     *   <li>a copy of the packaged template is kept next to it as {@code config.example.yml} — that file is
     *       where the comments live, and it is refreshed on every start so it always describes the running
     *       version;
     *   <li>a missing config is created from that template;
     *   <li>an existing one gets the template keys it does not have yet, with the old file kept as
     *       {@code config.yml.bak}.
     * </ol>
     *
     * <p>The third step only ever adds keys whose values were <em>already in effect</em> through the built-in
     * fallback, so it changes what the file says without changing what the bridge does. That is what makes
     * rewriting the file safe to do automatically — the comments it loses are in the example file, and the
     * previous bytes are in the backup.
     */
    public static BridgeConfig load(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        writeExample(path);
        if (Files.exists(path)) {
            syncTemplates(path);
        } else {
            Files.write(path, templateBytes());
            Log.warn("wrote the default config to " + path + "; fill in app-id and the group openids");
        }
        return parse(path);
    }

    /** The packaged template, as bytes — used for the first-run file and for the annotated example alike. */
    private static byte[] templateBytes() throws IOException {
        try (InputStream template = BridgeConfig.class.getResourceAsStream(Constants.CONFIG_TEMPLATE)) {
            if (template == null) {
                throw new IOException("the packaged config template is missing from the jar");
            }
            return template.readAllBytes();
        }
    }

    /** Keeps {@code config.example.yml} in step with the jar, without touching it when it already matches. */
    private static void writeExample(Path configPath) throws IOException {
        Path example = configPath.resolveSibling(EXAMPLE_FILE);
        byte[] template = templateBytes();
        if (Files.exists(example) && java.util.Arrays.equals(Files.readAllBytes(example), template)) {
            return;
        }
        Files.write(example, template);
    }

    /**
     * Adds the built-in template keys an older config does not have yet.
     *
     * <p>Only {@code templates} is touched: those are defaults, and filling them in cannot change behaviour.
     * {@code bots} is an <em>example</em>, so merging it would be actively wrong — it would add a REPLACE_ME bot
     * to somebody's working config.
     */
    @SuppressWarnings("unchecked")
    private static void syncTemplates(Path path) throws IOException {
        Map<String, Object> root = readYaml(path);
        if (root == null) {
            return;
        }
        Object configured = root.get("templates");
        if (configured != null && !(configured instanceof Map)) {
            return;
        }
        Map<String, Object> templates = configured == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>((Map<String, Object>) configured);
        List<String> added = new ArrayList<>();
        for (Map.Entry<String, String> entry : Templates.defaults().entrySet()) {
            if (!templates.containsKey(entry.getKey())) {
                templates.put(entry.getKey(), entry.getValue());
                added.add(entry.getKey());
            }
        }
        if (added.isEmpty()) {
            return;
        }
        root.put("templates", templates);
        Path backup = path.resolveSibling(path.getFileName() + BACKUP_SUFFIX);
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
        writeYaml(path, root);
        Log.warn("配置里缺了 " + added.size() + " 个模板键，已补进 " + path.getFileName() + "（原文件备份在 "
                + backup.getFileName() + "，带注释的说明见 " + EXAMPLE_FILE + "）：" + String.join(", ", added));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readYaml(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            return loaded instanceof Map ? (Map<String, Object>) loaded : null;
        } catch (IOException | RuntimeException e) {
            // A file that cannot be read is parse()'s problem to report, not this one's to fail on.
            return null;
        }
    }

    private static void writeYaml(Path path, Map<String, Object> root) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write("# 这个文件被 " + Constants.MOD_ID + " 补全过：升级时把新增的键加了进来，值就是内置默认（行为与之前一致）。\n"
                    + "# 带注释的完整说明在同目录的 " + EXAMPLE_FILE + "，改动前的版本备份在 "
                    + path.getFileName() + BACKUP_SUFFIX + "。\n");
            new Yaml(options).dump(root, writer);
        }
    }

    /** Reads and validates; a broken entry is reported in {@link #problems()} and the rest still loads. */
    public static BridgeConfig parse(Path path) throws IOException {
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (!(loaded instanceof Map)) {
                return new BridgeConfig(Map.of(), Map.of(), false,
                        List.of(path + " is empty or is not a mapping"));
            }
            root = (Map<String, Object>) loaded;
        }

        List<String> problems = new ArrayList<>();
        Map<String, Bot> bots = new LinkedHashMap<>();
        // Missing templates are not a problem: the built-in wording is what every older config already had.
        // `/qq status` mentions it and `/qq templates` prints what to paste, so nobody gets a startup warning
        // for a file that is not wrong.
        Map<String, String> templates = templates(root.get("templates"), "全局", problems);
        Object configuredBots = root.get("bots");
        if (!(configuredBots instanceof List)) {
            return new BridgeConfig(Map.of(), templates, truthy(root.get("debug")),
                    List.of(path + " needs a top-level 'bots:' list"));
        }
        for (Object entry : (List<Object>) configuredBots) {
            if (!(entry instanceof Map)) {
                problems.add("a 'bots:' entry is not a mapping");
                continue;
            }
            Map<String, Object> botMap = (Map<String, Object>) entry;
            String appId = text(botMap.get("app-id"));
            if (appId.isEmpty()) {
                problems.add("a bot has no app-id, skipped");
                continue;
            }
            String id = text(botMap.get("id"));
            if (id.isEmpty()) {
                id = appId;
            }
            if (appId.contains(PLACEHOLDER)) {
                // Starting it would only get a refusal from the platform, so say what to edit instead.
                problems.add("bot " + id + " still has the " + PLACEHOLDER + " app-id from the template, skipped");
                continue;
            }
            Map<String, Group> groups = groups(botMap.get("groups"), id, problems);
            if (groups.isEmpty()) {
                problems.add("bot " + id + " bridges no group; add one under 'groups:'");
            }
            String key = id;
            if (bots.containsKey(key)) {
                problems.add("duplicate bot id " + key + "; the later one wins");
            }
            bots.put(key, new Bot(id, appId, orDefault(text(botMap.get("secret-env")), "QQ_BOT_SECRET"), groups));
        }
        return new BridgeConfig(bots, templates, truthy(root.get("debug")), problems);
    }

    /**
     * Reads a {@code templates:} block and reports what is wrong with it: an unknown key would silently do
     * nothing, and a misspelled placeholder would silently render as itself.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, String> templates(Object configured, String where, List<String> problems) {
        Map<String, String> templates = new LinkedHashMap<>();
        if (configured == null) {
            return templates;
        }
        if (!(configured instanceof Map)) {
            problems.add(where + " 的 templates 不是键值对，已忽略");
            return templates;
        }
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) configured).entrySet()) {
            String key = entry.getKey();
            if (!Templates.keys().contains(key)) {
                problems.add(where + " 的 templates 里有未知的键 '" + key + "'，已忽略（可用："
                        + String.join(", ", Templates.keys()) + "）");
                continue;
            }
            String value = text(entry.getValue());
            Set<String> unknown = Templates.unknown(value);
            if (!unknown.isEmpty()) {
                problems.add(where + " 的模板 " + key + " 用了不存在的占位符 " + unknown
                        + "（可用：" + String.join(", ", Templates.KNOWN) + "）");
            }
            templates.put(key, value);
        }
        return templates;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Group> groups(Object configured, String botId, List<String> problems) {
        Map<String, Group> groups = new LinkedHashMap<>();
        if (!(configured instanceof List)) {
            return groups;
        }
        for (Object entry : (List<Object>) configured) {
            if (!(entry instanceof Map)) {
                problems.add("bot " + botId + " has a 'groups:' entry that is not a mapping");
                continue;
            }
            Map<String, Object> groupMap = (Map<String, Object>) entry;
            String openid = text(groupMap.get("group-openid"));
            if (openid.isEmpty()) {
                problems.add("bot " + botId + " has a group with no group-openid, skipped");
                continue;
            }
            if (openid.contains(PLACEHOLDER)) {
                problems.add("bot " + botId + " has the " + PLACEHOLDER + " group-openid from the template, skipped");
                continue;
            }
            Set<McEvent> send = new LinkedHashSet<>();
            Object sendTo = groupMap.get("send-to-qq");
            if (sendTo instanceof List) {
                for (Object name : (List<Object>) sendTo) {
                    Optional<McEvent> event = McEvent.parse(text(name));
                    if (event.isPresent()) {
                        send.add(event.get());
                    } else {
                        problems.add("group " + openid + " listens for unknown event '" + text(name) + "'");
                    }
                }
            } else if (sendTo == null) {
                send.addAll(List.of(McEvent.CHAT, McEvent.JOIN, McEvent.QUIT, McEvent.DEATH));
            }
            groups.put(openid, new Group(openid, text(groupMap.get("label")),
                    groupMap.get("receive-from-qq") == null || truthy(groupMap.get("receive-from-qq")), send,
                    templates(groupMap.get("templates"), "群 " + openid, problems)));
        }
        return groups;
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = text(value);
        return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes") || text.equals("1");
    }

    private static String orDefault(String value, String fallback) {
        return value.isEmpty() ? fallback : value;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
