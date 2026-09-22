package com.example.mcqq.core;

import io.github.skiesworld.qqbot.event.EventType;
import io.github.skiesworld.qqbot.event.QQEvent;
import io.github.skiesworld.qqbot.event.QQMessageEvent;
import io.github.skiesworld.qqbot.event.QQNoticeEvent;
import io.github.skiesworld.qqbot.handler.On;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * QQ → Minecraft. Group chat, @-mentions and the join/leave notices are put in the players' chat and echoed to
 * the console; the bot's own private chats with users are deliberately not, since nobody on the server asked.
 *
 * <p>What each line looks like is the operator's business now: the values are collected here and the wording
 * comes from the template in force for that group (see {@link Templates}). An empty template means the group
 * wants this event to stay quiet.
 *
 * <p>Every way a message can be dropped says so through {@link Log#debug} — a group set to outgoing only, a
 * duplicate push from the platform. Silence is the hardest thing to debug.
 *
 * <p>The one exception is an unbound group, and it is the important one: that is the case where the operator is
 * still trying to find out what to put in the file. It goes into {@link UnboundGroups} and is logged once at
 * WARN with the id they need, instead of being dropped without a trace.
 *
 * <p>Everything lands on the receiving thread of the SDK's dispatcher, so the one thing done here is handing the
 * line to the platform, which owns the hop onto the game's thread. Nothing blocking belongs on that path.
 */
public final class QqToMc {

    /** The platform may push the same {@code msg_id} twice; repeated bridges into chat look like a spam bot. */
    private static final int SEEN_LIMIT = 512;

    private final MinecraftPlatform platform;
    private final BridgeConfig config;
    private final String botId;
    private final UnboundGroups unbound;
    private final Map<String, Boolean> seen = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > SEEN_LIMIT;
        }
    };

    public QqToMc(MinecraftPlatform platform, BridgeConfig config, String botId, UnboundGroups unbound) {
        this.platform = platform;
        this.config = config;
        this.botId = botId;
        this.unbound = unbound;
    }

    @On({EventType.GROUP_MESSAGE_CREATE, EventType.GROUP_AT_MESSAGE_CREATE,
            EventType.MESSAGE_CREATE, EventType.AT_MESSAGE_CREATE})
    public void onGroupMessage(QQMessageEvent message) {
        Optional<BridgeConfig.Target> bound = target(message);
        if (bound.isEmpty()) {
            noteUnbound(message.conversationId());
            return;
        }
        BridgeConfig.Target target = bound.get();
        if (!target.receivesFromQq()) {
            Log.debug("群 " + target.label() + " 配成了只出不进，忽略这条消息");
            return;
        }
        String content = message.content();
        if (content == null || content.isBlank()) {
            Log.debug("群 " + target.label() + " 的消息没有文字内容，忽略");
            return;
        }
        if (!firstSeen("m:" + message.id())) {
            Log.debug("群 " + target.label() + " 的消息 " + message.id() + " 是平台重推的，忽略");
            return;
        }
        String who = message.author() == null || message.author().username == null
                ? shortId(message.senderId()) : message.author().username;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("group", target.label());
        values.put("user", who);
        values.put("text", content.strip());
        broadcast(target, Templates.QQ_CHAT, values);

        int attachments = message.segments().media().size();
        if (attachments > 0) {
            values.put("count", String.valueOf(attachments));
            broadcast(target, Templates.QQ_ATTACHMENT, values);
        }
    }

    /** Who left and who came: the notice names them as its subject, and nobody else is asked about it. */
    @On({EventType.GROUP_MEMBER_ADD, EventType.GROUP_MEMBER_REMOVE})
    public void onGroupMemberChange(QQNoticeEvent notice) {
        Optional<BridgeConfig.Target> bound = target(notice);
        if (bound.isEmpty()) {
            noteUnbound(notice.conversationId());
            return;
        }
        BridgeConfig.Target target = bound.get();
        if (!target.receivesFromQq()) {
            Log.debug("群 " + target.label() + " 配成了只出不进，忽略成员变动");
            return;
        }
        if (!firstSeen("n:" + notice.name() + ':' + notice.id())) {
            Log.debug("群 " + target.label() + " 的成员变动 " + notice.id() + " 是平台重推的，忽略");
            return;
        }
        Optional<String> member = notice.subject();
        if (member.isEmpty()) {
            Log.debug("群 " + target.label() + " 的成员变动没有说是谁，忽略");
            return;
        }
        String key = notice.type() == EventType.GROUP_MEMBER_ADD
                ? Templates.QQ_MEMBER_ADD : Templates.QQ_MEMBER_REMOVE;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("group", target.label());
        values.put("member", shortId(member.get()));
        broadcast(target, key, values);
    }

    private Optional<BridgeConfig.Target> target(QQEvent event) {
        return config.target(conversationId(event));
    }

    /**
     * 事件的会话标识：群是 {@code group_openid}，子频道是 {@code channel_id}。
     *
     * <p>SDK 的 {@code scene()} 知道每个面该取哪个键（{@code ReplyTarget} 里就是那张表），
     * 所以先问它；它认不出来时才退回 {@code conversationId()}。
     */
    private static String conversationId(QQEvent event) {
        var scene = event.scene();
        String id = scene == null ? null : scene.targetId(event);
        return id == null || id.isBlank() ? event.conversationId() : id;
    }

    /**
     * Records a group that talked to us without being in the config, and says so once.
     *
     * <p>This is the operator's way in. The config wants an openid, the only source of one is a message from the
     * group, and a log is not always where the operator is looking — so the id is kept for {@code /qq status}
     * and {@code /qq bind}, and the first sighting is loud enough to be found in a log. Once per group and not
     * once per message, or a busy group would own the console.
     */
    private void noteUnbound(String groupOpenid) {
        if (unbound.remember(botId, groupOpenid)) {
            Log.warn("config: 收到群 " + groupOpenid + " 的消息，但它没绑定 —— 在控制台敲 /qq bind 就能绑上"
                    + "（或把 " + groupOpenid + " 填进 config.yml 的 group-openid）");
        } else {
            Log.debug("收到群 " + groupOpenid + " 的消息，这个群还是没绑定，忽略");
        }
    }

    private synchronized boolean firstSeen(String key) {
        return seen.putIfAbsent(key, Boolean.TRUE) == null;
    }

    private void broadcast(BridgeConfig.Target target, String templateKey, Map<String, String> values) {
        String line = Templates.render(config.template(target.conversationId(), templateKey),
                Templates.withContext(values, platform.label()));
        if (line.isEmpty()) {
            Log.debug("群 " + target.label() + " 的 " + templateKey + " 模板为空，不播报");
            return;
        }
        // The console copy carries the colour codes; the platform renders them for the players.
        Log.info(line);
        try {
            platform.broadcast(line);
        } catch (RuntimeException e) {
            // The platform side is adapter code on the game's thread; a bad line there must not take the
            // bridge's dispatcher down with it.
            Log.error("把 QQ 消息送进聊天栏失败", e);
        }
    }

    private static String shortId(String openid) {
        if (openid == null || openid.isBlank()) {
            return "未知用户";
        }
        return openid.length() <= 6 ? openid : openid.substring(openid.length() - 6);
    }
}
