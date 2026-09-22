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
 * <p>The AppSecret can be written straight into this file ({@code secret:}) or named as an environment variable
 * ({@code secret-env:}). Both are supported. The file is what the template shows, because it is the one that
 * needs nothing beyond editing the file; the variable is there for an operator who would rather keep the secret
 * out of something that gets backed up and pasted into bug reports.
 *
 * <p>A missing file is created from the packaged template, so a first start shows an admin what the knobs are
 * instead of failing.
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
    public static final class Group implements Target {

        private final String groupOpenid;
        private final String label;
        private final boolean receiveFromQq;
        private final Set<McEvent> sendToQq;
        private final Map<String, String> templates;
        private final CommandAccess commandAccess;

        Group(String groupOpenid, String label, boolean receiveFromQq, Set<McEvent> sendToQq,
                Map<String, String> templates, CommandAccess commandAccess) {
            this.groupOpenid = groupOpenid;
            this.label = label;
            this.receiveFromQq = receiveFromQq;
            this.commandAccess = commandAccess;
            // Linked, so /qq status shows the events in the order the file wrote them.
            this.sendToQq = Collections.unmodifiableSet(new LinkedHashSet<>(sendToQq));
            // Only what this group overrides; anything else falls through to the global template.
            this.templates = Collections.unmodifiableMap(new LinkedHashMap<>(templates));
        }

        public String groupOpenid() {
            return groupOpenid;
        }

        @Override
        public String conversationId() {
            return groupOpenid;
        }

        @Override
        public Kind kind() {
            return Kind.GROUP;
        }

        /** Name used in chat lines and {@code /qq status}; falls back to the openid. */
        @Override
        public String label() {
            return label.isBlank() ? groupOpenid : label;
        }

        @Override
        public boolean receivesFromQq() {
            return receiveFromQq;
        }

        @Override
        public boolean sendsToQq(McEvent event) {
            return sendToQq.contains(event);
        }

        /** What this group receives from Minecraft, for {@code /qq status}. */
        public Set<McEvent> sendEvents() {
            return sendToQq;
        }

        /** The templates this group overrides; empty means it uses the global ones. */
        @Override
        public Map<String, String> templates() {
            return templates;
        }

        /** 这个群里谁能执行命令。 */
        @Override
        public CommandAccess commandAccess() {
            return commandAccess;
        }

        @Override
        public String toString() {
            return "Group{" + groupOpenid + " '" + label + "' in=" + receiveFromQq + " out=" + sendToQq
                    + " templates=" + templates.keySet() + '}';
        }
    }

    /** 出站时走哪个接口。群和子频道的发送方式不同，桥接逻辑一样。 */
    public enum Kind {
        GROUP,
        CHANNEL
    }

    /**
     * 一个可以双向桥接的目标：群，或者频道的文字子频道。
     *
     * <p>两者的**键**和**发送方式**都不同（群是 {@code group-openid}，子频道是 {@code guild-id} +
     * {@code channel-id}），但桥接逻辑一模一样 —— 匹配入站消息、套模板、按事件过滤、判权限。
     * 所以那些逻辑写在这个接口上，只有"怎么发"和"键从哪来"分叉。
     */
    public interface Target {

        /** 会话标识，入站事件按它匹配：群是 group-openid，子频道是 channel-id。 */
        String conversationId();

        /** 出站走哪个接口。 */
        Kind kind();

        /** 出现在聊天行与 {@code /qq status} 里的名字；没填就退回 id。 */
        String label();

        boolean receivesFromQq();

        boolean sendsToQq(McEvent event);

        /** 这个目标订阅了哪些 MC 事件，给 {@code /qq status} 用。 */
        Set<McEvent> sendEvents();

        /** 这个目标覆盖的模板；空表示用全局的。 */
        Map<String, String> templates();

        /** 谁能在这里执行命令。 */
        CommandAccess commandAccess();
    }

    /**
     * 频道的一个文字子频道。
     *
     * <p>寻址要**两个**字段（{@code guild-id} + {@code channel-id}），所以它没法塞进 {@code groups:}
     * 那个列表 —— 那样一半字段会空着，而 {@code groups:} 这个名字也就变成假的了。
     */
    public static final class Channel implements Target {

        private final String guildId;
        private final String channelId;
        private final String label;
        private final boolean receiveFromQq;
        private final Set<McEvent> sendToQq;
        private final Map<String, String> templates;
        private final CommandAccess commandAccess;

        Channel(String guildId, String channelId, String label, boolean receiveFromQq,
                Set<McEvent> sendToQq, Map<String, String> templates, CommandAccess commandAccess) {
            this.guildId = guildId;
            this.channelId = channelId;
            this.label = label;
            this.receiveFromQq = receiveFromQq;
            this.sendToQq = Collections.unmodifiableSet(new LinkedHashSet<>(sendToQq));
            this.templates = Collections.unmodifiableMap(new LinkedHashMap<>(templates));
            this.commandAccess = commandAccess;
        }

        public String guildId() {
            return guildId;
        }

        public String channelId() {
            return channelId;
        }

        @Override
        public String conversationId() {
            return channelId;
        }

        @Override
        public Kind kind() {
            return Kind.CHANNEL;
        }

        @Override
        public String label() {
            return label.isBlank() ? channelId : label;
        }

        @Override
        public boolean receivesFromQq() {
            return receiveFromQq;
        }

        @Override
        public boolean sendsToQq(McEvent event) {
            return sendToQq.contains(event);
        }

        /** What this channel receives from Minecraft, for {@code /qq status}. */
        public Set<McEvent> sendEvents() {
            return sendToQq;
        }

        @Override
        public Map<String, String> templates() {
            return templates;
        }

        @Override
        public CommandAccess commandAccess() {
            return commandAccess;
        }

        @Override
        public String toString() {
            return "Channel{" + guildId + '/' + channelId + " label=" + label + '}';
        }
    }

    /** One QQ bot account and the groups it bridges. */
    public static final class Bot {

        private final String id;
        private final String appId;
        private final String secret;
        private final String secretEnvironmentVariable;
        private final Map<String, Group> groups;
        private final Map<String, Channel> channels;

        Bot(String id, String appId, String secret, String secretEnvironmentVariable, Map<String, Group> groups,
                Map<String, Channel> channels) {
            this.id = id;
            this.appId = appId;
            this.secret = secret;
            this.secretEnvironmentVariable = secretEnvironmentVariable;
            // Copy, but keep the file's order: Map.copyOf would shuffle the groups in /qq status and in the
            // forward loop.
            this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
            this.channels = Collections.unmodifiableMap(new LinkedHashMap<>(channels));
        }

        public String id() {
            return id;
        }

        public String appId() {
            return appId;
        }

        /** The AppSecret as written in the file; empty when this bot names an environment variable instead. */
        public String secret() {
            return secret;
        }

        /** The environment variable holding the AppSecret; never blank, because it has a default. */
        public String secretEnvironmentVariable() {
            return secretEnvironmentVariable;
        }

        public List<Group> groups() {
            return List.copyOf(groups.values());
        }

        public Optional<Group> group(String groupOpenid) {
            return Optional.ofNullable(groups.get(groupOpenid));
        }

        public List<Channel> channels() {
            return List.copyOf(channels.values());
        }

        public Optional<Channel> channel(String channelId) {
            return Optional.ofNullable(channels.get(channelId));
        }

        /** 群和子频道放在一起，顺序是"先群后频道" —— 出站与状态都按这个顺序走。 */
        public List<Target> targets() {
            List<Target> all = new ArrayList<>(groups.values());
            all.addAll(channels.values());
            return List.copyOf(all);
        }

        /** 按会话标识找一个目标：群看 group-openid，子频道看 channel-id。 */
        public Optional<Target> target(String conversationId) {
            Optional<Group> group = group(conversationId);
            return group.isPresent() ? Optional.of(group.get()) : channel(conversationId).map(c -> c);
        }

        @Override
        public String toString() {
            return "Bot{" + id + " app=" + appId + " groups=" + groups.keySet() + '}';
        }
    }

    /** What the packaged template leaves in the file; a bot or group still carrying it was never filled in. */
    private static final String PLACEHOLDER = "REPLACE_ME";

    /** 命令执行默认**关着**：它是唯一一条"从 QQ 能影响服务器"的路径，必须显式打开。 */
    private static final String COMMAND_PREFIX_DEFAULT = "/mcc";

    private final Map<String, Bot> bots;
    private final Map<String, String> templates;
    private final boolean debug;
    private final boolean commandsEnabled;
    private final String commandPrefix;
    private final List<String> problems;

    private BridgeConfig(Map<String, Bot> bots, Map<String, String> templates, boolean debug,
            boolean commandsEnabled, String commandPrefix, List<String> problems) {
        this.bots = bots;
        this.templates = Collections.unmodifiableMap(new LinkedHashMap<>(templates));
        this.debug = debug;
        this.commandsEnabled = commandsEnabled;
        this.commandPrefix = commandPrefix;
        this.problems = problems;
    }

    /** 命令执行开着没有。默认关。 */
    public boolean commandsEnabled() {
        return commandsEnabled;
    }

    /** 命令头，三个面（群 / 子频道 / 私聊）都用它。 */
    public String commandPrefix() {
        return commandPrefix;
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
    public String template(String conversationId, String key) {
        Optional<Target> target = target(conversationId);
        if (target.isPresent()) {
            String override = target.get().templates().get(key);
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

    /** 按会话标识找一个目标，群和子频道都认。入站消息用这个。 */
    public Optional<Target> target(String conversationId) {
        for (Bot bot : bots.values()) {
            Optional<Target> found = bot.target(conversationId);
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
            Log.warn("已写出默认配置到 " + path + "；把 app-id、secret 和群的 openid 填进去");
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
        writeYaml(path, root,
                "# 这个文件被 " + Constants.MOD_ID + " 补全过：升级时把新增的键加了进来，值就是内置默认（行为与之前一致）。\n");
    }

    private static void writeYaml(Path path, Map<String, Object> root, String why) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write(why
                    + "# 带注释的完整说明在同目录的 " + EXAMPLE_FILE + "，改动前的版本备份在 "
                    + path.getFileName() + BACKUP_SUFFIX + "。\n");
            new Yaml(options).dump(root, writer);
        }
    }

    /**
     * Adds one group to one bot and rewrites the file — what {@code /qq bind} is built on.
     *
     * <p>This is the one place the file changes for a reason other than "it was missing a default", and it is
     * the same bargain as the rest of this class: the comments live in {@code config.example.yml} and the
     * previous bytes go to {@code config.yml.bak}, so a dump that loses the operator's blank lines costs
     * nothing. It is what lets an operator bind a group from the console, without pasting a
     * thirty-character id into a file editor.
     *
     * @return the label the group got, so the caller can say what it did
     * @throws IOException when the file cannot be read or written, or the bot or group is not there to change;
     *     the message is written for the operator, because that is where it ends up
     */
    @SuppressWarnings("unchecked")
    public static String bindGroup(Path path, String botId, String groupOpenid) throws IOException {
        Map<String, Object> root = readYaml(path);
        if (root == null) {
            throw new IOException("读不出 " + path.getFileName() + "（它不是一个 YAML 对象？）");
        }
        if (!(root.get("bots") instanceof List)) {
            throw new IOException(path.getFileName() + " 里没有 bots 段");
        }
        for (Object entry : (List<Object>) root.get("bots")) {
            if (!(entry instanceof Map)) {
                continue;
            }
            Map<String, Object> bot = (Map<String, Object>) entry;
            if (!botId.equals(text(bot.get("id")))) {
                continue;
            }
            Object configured = bot.get("groups");
            List<Object> groups = configured instanceof List ? (List<Object>) configured : new ArrayList<>();
            for (Object group : groups) {
                if (group instanceof Map
                        && groupOpenid.equals(text(((Map<String, Object>) group).get("group-openid")))) {
                    throw new IOException("这个群已经绑在 bot " + botId + " 上了");
                }
            }
            String label = "群 " + lastSix(groupOpenid);
            Map<String, Object> added = new LinkedHashMap<>();
            added.put("group-openid", groupOpenid);
            // Deliberately no send-to-qq: absent means the default set, which is what a new binding wants.
            added.put("label", label);
            groups.add(added);
            bot.put("groups", groups);

            Path backup = path.resolveSibling(path.getFileName() + BACKUP_SUFFIX);
            Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);
            writeYaml(path, root,
                    "# 这个文件被 " + Constants.MOD_ID + " 改过：/qq bind 把一个群加了进来。\n");
            return label;
        }
        throw new IOException("配置里没有 id 是 " + botId + " 的 bot");
    }

    /** The tail of an id, which is all that is worth showing of an openid nobody can read anyway. */
    private static String lastSix(String id) {
        return id.length() <= 6 ? id : id.substring(id.length() - 6);
    }

    /** Reads and validates; a broken entry is reported in {@link #problems()} and the rest still loads. */
    public static BridgeConfig parse(Path path) throws IOException {
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Object loaded = new Yaml().load(reader);
            if (!(loaded instanceof Map)) {
                return new BridgeConfig(Map.of(), Map.of(), false, false, COMMAND_PREFIX_DEFAULT,
                        List.of(path + " 是空的，或者不是一个键值对（YAML 对象）"));
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
                    commandsEnabled(root), commandPrefix(root, problems),
                    List.of(path + " needs a top-level 'bots:' list"));
        }
        for (Object entry : (List<Object>) configuredBots) {
            if (!(entry instanceof Map)) {
                problems.add("'bots:' 里有一项不是键值对，已忽略");
                continue;
            }
            Map<String, Object> botMap = (Map<String, Object>) entry;
            String appId = text(botMap.get("app-id"));
            if (appId.isEmpty()) {
                problems.add("有个 bot 没写 app-id，已跳过");
                continue;
            }
            String id = text(botMap.get("id"));
            if (id.isEmpty()) {
                id = appId;
            }
            if (appId.contains(PLACEHOLDER)) {
                // Starting it would only get a refusal from the platform, so say what to edit instead.
                problems.add("bot " + id + " 的 app-id 还是模板里的 " + PLACEHOLDER + "，已跳过");
                continue;
            }
            Map<String, Group> groups = groups(botMap.get("groups"), id, problems);
            if (groups.isEmpty()) {
                // 两条路都要说：填文件，或者让群先说一句话再敲 /qq bind。
                problems.add("bot " + id + " 还没绑任何群 —— 在群里 @ 一下机器人然后敲 /qq bind，"
                        + "或把群的 openid 填进 'groups:'");
            }
            String key = id;
            if (bots.containsKey(key)) {
                problems.add("bot id 重复：" + key + " —— 后一个生效");
            }
            // A secret still carrying the template's placeholder counts as absent: the runtime then says what to
            // write, instead of handing "REPLACE_ME" to the platform and reporting a login failure.
            String secret = text(botMap.get("secret"));
            if (secret.contains(PLACEHOLDER)) {
                problems.add("bot " + id + " 的 secret 还是模板里的 " + PLACEHOLDER);
                secret = "";
            }
            // Both written is a config nobody means to write, and silently picking one is how "why is it still
            // using the old secret" starts. The file wins, and this says so.
            String secretEnv = text(botMap.get("secret-env"));
            if (!secret.isEmpty() && !secretEnv.isEmpty()) {
                problems.add("bot " + id + ": secret 与 secret-env 都填了，用 secret"
                        + "（要改用环境变量就把 secret 删掉）");
            }
            bots.put(key, new Bot(id, appId, secret, orDefault(secretEnv, "QQ_BOT_SECRET"), groups,
                    channels(botMap.get("channels"), id, problems)));
        }
        // "开着但谁都执行不了"是最容易发生的误会，所以在这里就说出来。
        if (commandsEnabled(root) && bots.values().stream()
                .flatMap(bot -> bot.groups().stream())
                .allMatch(group -> group.commandAccess().describe() == null)) {
            problems.add("command.enabled 开着，但没有任何群配了 command（allow / whitelist / roles），"
                    + "所以谁都执行不了命令");
        }
        return new BridgeConfig(bots, templates, truthy(root.get("debug")),
                commandsEnabled(root), commandPrefix(root, problems), problems);
    }

    /**
     * 读一个 bot 的 {@code channels:} 列表。
     *
     * <p>和 {@code groups:} 是两套键：子频道的寻址是 {@code guild-id} + {@code channel-id} 两个字段。
     * 其余（{@code label} / {@code receive-from-qq} / {@code send-to-qq} / {@code templates} /
     * {@code command}）语义完全一样。
     */
    private static Map<String, Channel> channels(Object configured, String botId, List<String> problems) {
        Map<String, Channel> channels = new LinkedHashMap<>();
        if (configured == null) {
            return channels;
        }
        if (!(configured instanceof List)) {
            problems.add("bot " + botId + " 的 'channels:' 不是列表，已忽略");
            return channels;
        }
        for (Object entry : (List<Object>) configured) {
            if (!(entry instanceof Map)) {
                problems.add("bot " + botId + " 的 'channels:' 里有一项不是键值对，已忽略");
                continue;
            }
            Map<String, Object> channelMap = (Map<String, Object>) entry;
            String channelId = text(channelMap.get("channel-id"));
            if (channelId.isEmpty()) {
                problems.add("bot " + botId + " 有个子频道没写 channel-id，已跳过");
                continue;
            }
            if (channelId.contains(PLACEHOLDER)) {
                problems.add("bot " + botId + " 的 channel-id 还是模板里的 " + PLACEHOLDER + "，已跳过");
                continue;
            }
            String guildId = text(channelMap.get("guild-id"));
            if (guildId.isEmpty()) {
                // 匹配只用 channel-id，但 guild-id 是"这个子频道属于哪个频道"的唯一凭据 ——
                // 少了它在日志和状态里都认不出是哪儿，所以只说一声，不跳过。
                problems.add("bot " + botId + " 的子频道 " + channelId + " 没写 guild-id"
                        + "（只影响日志与状态里怎么称呼它，不影响桥接）");
            }
            Set<McEvent> send = new LinkedHashSet<>();
            Object sendTo = channelMap.get("send-to-qq");
            if (sendTo instanceof List) {
                for (Object name : (List<Object>) sendTo) {
                    Optional<McEvent> event = McEvent.parse(text(name));
                    if (event.isPresent()) {
                        send.add(event.get());
                    } else {
                        problems.add("子频道 " + channelId + " 订阅了未知的事件 '" + text(name) + "'，已忽略");
                    }
                }
            } else if (sendTo == null) {
                send.addAll(List.of(McEvent.CHAT, McEvent.JOIN, McEvent.QUIT, McEvent.DEATH));
            }
            channels.put(channelId, new Channel(guildId, channelId, text(channelMap.get("label")),
                    channelMap.get("receive-from-qq") == null || truthy(channelMap.get("receive-from-qq")),
                    send, templates(channelMap.get("templates"), "子频道 " + channelId, problems),
                    commandAccess(channelMap.get("command"), "子频道 " + channelId, problems)));
        }
        return channels;
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
                problems.add("bot " + botId + " 的 'groups:' 里有一项不是键值对，已忽略");
                continue;
            }
            Map<String, Object> groupMap = (Map<String, Object>) entry;
            String openid = text(groupMap.get("group-openid"));
            if (openid.isEmpty()) {
                problems.add("bot " + botId + " 有个群没写 group-openid，已跳过");
                continue;
            }
            if (openid.contains(PLACEHOLDER)) {
                problems.add("bot " + botId + " 的 group-openid 还是模板里的 " + PLACEHOLDER + "，已跳过");
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
                        problems.add("群 " + openid + " 订阅了未知的事件 '" + text(name) + "'，已忽略");
                    }
                }
            } else if (sendTo == null) {
                send.addAll(List.of(McEvent.CHAT, McEvent.JOIN, McEvent.QUIT, McEvent.DEATH));
            }
            groups.put(openid, new Group(openid, text(groupMap.get("label")),
                    groupMap.get("receive-from-qq") == null || truthy(groupMap.get("receive-from-qq")), send,
                    templates(groupMap.get("templates"), "群 " + openid, problems),
                    commandAccess(groupMap.get("command"), "群 " + openid, problems)));
        }
        return groups;
    }

    /** 命令执行开着没有。整段没写就是关 —— 这是唯一一条"从 QQ 能影响服务器"的路径。 */
    @SuppressWarnings("unchecked")
    private static boolean commandsEnabled(Map<String, Object> root) {
        Object configured = root.get("command");
        return configured instanceof Map && truthy(((Map<String, Object>) configured).get("enabled"));
    }

    /** 命令头。空白等于没写；带空格的一律退回默认值（命令头带空格没法用）。 */
    @SuppressWarnings("unchecked")
    private static String commandPrefix(Map<String, Object> root, List<String> problems) {
        Object configured = root.get("command");
        if (!(configured instanceof Map)) {
            return COMMAND_PREFIX_DEFAULT;
        }
        String prefix = text(((Map<String, Object>) configured).get("prefix"));
        if (prefix.isEmpty()) {
            return COMMAND_PREFIX_DEFAULT;
        }
        if (prefix.contains(" ")) {
            problems.add("command.prefix 里有空格（'" + prefix + "'），命令头不能带空格，已按 "
                    + COMMAND_PREFIX_DEFAULT + " 处理");
            return COMMAND_PREFIX_DEFAULT;
        }
        return prefix;
    }

    /**
     * 读一个目标的 {@code command:} 块。
     *
     * <p>{@code roles} 只放"额外的"身份组 —— 子频道管理员（5）和自定义身份组。内置的群主（4）与
     * 管理员（2）归 {@code allow} 管；写进 roles 不生效，所以要说一声，免得有人以为
     * "roles 里没写 2，所以管理员用不了"。
     */
    @SuppressWarnings("unchecked")
    private static CommandAccess commandAccess(Object configured, String where, List<String> problems) {
        if (!(configured instanceof Map)) {
            return CommandAccess.NONE;
        }
        Map<String, Object> map = (Map<String, Object>) configured;
        Set<String> roles = strings(map.get("roles"));
        for (String builtin : List.of(CommandAccess.ROLE_GUILD_ADMIN, CommandAccess.ROLE_GUILD_OWNER)) {
            if (roles.contains(builtin)) {
                problems.add(where + " 的 command.roles 里有 '" + builtin + "' —— 群主/管理员由 allow 管，"
                        + "写在这里不生效，可以删掉");
            }
        }
        return new CommandAccess(CommandAccess.Allow.parse(map.get("allow"), where, problems),
                strings(map.get("whitelist")), roles);
    }

    /** 一串字符串，去掉空白项。 */
    @SuppressWarnings("unchecked")
    private static Set<String> strings(Object configured) {
        Set<String> values = new LinkedHashSet<>();
        if (configured instanceof List) {
            for (Object value : (List<Object>) configured) {
                String one = text(value);
                if (!one.isEmpty()) {
                    values.add(one);
                }
            }
        }
        return values;
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
