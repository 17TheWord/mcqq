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
 * <p>Every way a message can be dropped says so through {@link Log#debug} — an unbound group, a group set to
 * outgoing only, a duplicate push from the platform. Silence is the hardest thing to debug.
 *
 * <p>Everything lands on the receiving thread of the SDK's dispatcher, so the one thing done here is handing the
 * line to the platform, which owns the hop onto the game's thread. Nothing blocking belongs on that path.
 */
public final class QqToMc {

    /** The platform may push the same {@code msg_id} twice; repeated bridges into chat look like a spam bot. */
    private static final int SEEN_LIMIT = 512;

    private final MinecraftPlatform platform;
    private final BridgeConfig config;
    private final Map<String, Boolean> seen = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > SEEN_LIMIT;
        }
    };

    public QqToMc(MinecraftPlatform platform, BridgeConfig config) {
        this.platform = platform;
        this.config = config;
    }

    @On({EventType.GROUP_MESSAGE_CREATE, EventType.GROUP_AT_MESSAGE_CREATE})
    public void onGroupMessage(QQMessageEvent message) {
        Optional<BridgeConfig.Group> bound = group(message);
        if (bound.isEmpty()) {
            Log.debug("收到群 " + message.conversationId() + " 的消息，但这个群没绑定，忽略");
            return;
        }
        BridgeConfig.Group group = bound.get();
        if (!group.receivesFromQq()) {
            Log.debug("群 " + group.label() + " 配成了只出不进，忽略这条消息");
            return;
        }
        String content = message.content();
        if (content == null || content.isBlank()) {
            Log.debug("群 " + group.label() + " 的消息没有文字内容，忽略");
            return;
        }
        if (!firstSeen("m:" + message.id())) {
            Log.debug("群 " + group.label() + " 的消息 " + message.id() + " 是平台重推的，忽略");
            return;
        }
        String who = message.author() == null || message.author().username == null
                ? shortId(message.senderId()) : message.author().username;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("group", group.label());
        values.put("user", who);
        values.put("text", content.strip());
        broadcast(group, Templates.QQ_CHAT, values);

        int attachments = message.segments().media().size();
        if (attachments > 0) {
            values.put("count", String.valueOf(attachments));
            broadcast(group, Templates.QQ_ATTACHMENT, values);
        }
    }

    /** Who left and who came: the notice names them as its subject, and nobody else is asked about it. */
    @On({EventType.GROUP_MEMBER_ADD, EventType.GROUP_MEMBER_REMOVE})
    public void onGroupMemberChange(QQNoticeEvent notice) {
        Optional<BridgeConfig.Group> bound = group(notice);
        if (bound.isEmpty()) {
            Log.debug("收到群 " + notice.conversationId() + " 的成员变动，但这个群没绑定，忽略");
            return;
        }
        BridgeConfig.Group group = bound.get();
        if (!group.receivesFromQq()) {
            Log.debug("群 " + group.label() + " 配成了只出不进，忽略成员变动");
            return;
        }
        if (!firstSeen("n:" + notice.name() + ':' + notice.id())) {
            Log.debug("群 " + group.label() + " 的成员变动 " + notice.id() + " 是平台重推的，忽略");
            return;
        }
        Optional<String> member = notice.subject();
        if (member.isEmpty()) {
            Log.debug("群 " + group.label() + " 的成员变动没有说是谁，忽略");
            return;
        }
        String key = notice.type() == EventType.GROUP_MEMBER_ADD
                ? Templates.QQ_MEMBER_ADD : Templates.QQ_MEMBER_REMOVE;
        Map<String, String> values = new LinkedHashMap<>();
        values.put("group", group.label());
        values.put("member", shortId(member.get()));
        broadcast(group, key, values);
    }

    private Optional<BridgeConfig.Group> group(QQEvent event) {
        return config.group(event.conversationId());
    }

    private synchronized boolean firstSeen(String key) {
        return seen.putIfAbsent(key, Boolean.TRUE) == null;
    }

    private void broadcast(BridgeConfig.Group group, String templateKey, Map<String, String> values) {
        String line = Templates.render(config.template(group.groupOpenid(), templateKey),
                Templates.withContext(values, platform.label()));
        if (line.isEmpty()) {
            Log.debug("群 " + group.label() + " 的 " + templateKey + " 模板为空，不播报");
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
