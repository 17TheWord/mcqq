package com.example.mcqq.core;

import io.github.skiesworld.qqbot.event.EventType;
import io.github.skiesworld.qqbot.event.QQEvent;
import io.github.skiesworld.qqbot.event.QQMessageEvent;
import io.github.skiesworld.qqbot.event.QQNoticeEvent;
import io.github.skiesworld.qqbot.handler.On;
import io.github.skiesworld.qqbot.message.ReplyTarget;
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
    private final BridgeConfig.Bot bot;
    private final String botId;
    private final UnboundGroups unbound;
    private final McCommands commands;
    private final Map<String, Boolean> seen = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return size() > SEEN_LIMIT;
        }
    };

    public QqToMc(MinecraftPlatform platform, BridgeConfig config, BridgeConfig.Bot bot,
            UnboundGroups unbound, ConsoleRunner console) {
        this.platform = platform;
        this.config = config;
        this.bot = bot;
        this.botId = bot.id();
        this.unbound = unbound;
        this.commands = new McCommands(platform, config, bot, console);
    }

    @On({EventType.GROUP_MESSAGE_CREATE, EventType.GROUP_AT_MESSAGE_CREATE,
            EventType.MESSAGE_CREATE, EventType.AT_MESSAGE_CREATE, EventType.C2C_MESSAGE_CREATE})
    public void onGroupMessage(QQMessageEvent message) {
        // 去重排在最前：平台重推一次，聊天栏就多一行是小事，命令**就多跑一遍**是大事。
        if (!firstSeen("m:" + message.eventId())) {
            Log.debug("消息 " + message.eventId() + " 是平台重推的，忽略");
            return;
        }
        // 私聊先定身份：它没有"目标"可查，但**未绑定的群也没有** —— 两者只能靠 scene 区分。
        // 未绑定会话（包括敲了命令前缀的）一律走 noteUnbound：记档一次 + 给运维指路。
        boolean privateChat = QqEvents.isPrivateChat(message);
        Optional<BridgeConfig.Target> bound = target(message);
        if (!privateChat && bound.isEmpty()) {
            noteUnbound(message);
            return;
        }
        if (commands.handle(message)) {
            return;
        }
        if (privateChat) {
            // 私聊只为命令服务：没命中前缀的消息没人问过服务器，故意不桥接。
            Log.debug("私聊里的普通消息，不桥接");
            return;
        }
        BridgeConfig.Target target = bound.get();
        // 身份字段是权限判断的输入，而"事件里到底带了什么"只有真机能告诉我们 —— 所以打全。
        CommandAccess.Sender sender = QqEvents.of(message);
        Log.debug("收到 " + target.label() + " 的消息：openid=" + sender.openid()
                + " member_role='" + sender.memberRole() + "' roles=" + sender.roleIds()
                + " 内容：" + sanitize(message.content()));
        // mentions 里每个 User 的字段全打出来 —— "at 的是不是当前 bot"要靠它跟 <@...> 里的 id 对上，
        // 而那个 id 跟 selfId() 不是一个体系（真机实测），所以得先看清它的形状。
        for (io.github.skiesworld.qqbot.model.User mentioned : message.mentions()) {
            Log.debug("  mentions 一项：id=" + mentioned.id + " user_openid=" + mentioned.userOpenid
                    + " member_openid=" + mentioned.memberOpenid + " union_openid=" + mentioned.unionOpenid
                    + " bot=" + mentioned.bot + " username=" + sanitize(mentioned.username));
        }
        Log.debug("  mentionedBot()=" + message.mentionedBot());
        // 被动回复要的是消息自己的 id（messageId()），信封的 id（eventId()）是另一回事 —— 真机上验过两者不同。
        com.google.gson.JsonElement inner = message.rawObject().get("id");
        Log.debug("  事件 id（eventId()）=" + message.eventId()
                + "  d.id=" + (inner == null ? "(没有)" : inner.getAsString()));
        if (!target.receivesFromQq()) {
            Log.debug("群 " + target.label() + " 配成了只出不进，忽略这条消息");
            return;
        }
        // content() 里 @ 标记已经由 SDK 剥掉了（<@openid> 在 MC 里没有意义）；剩下的部分是陌生人文本，净化再用。
        String content = sanitize(message.content());
        if (content.isEmpty()) {
            Log.debug("群 " + target.label() + " 的消息没有文字内容，忽略");
            return;
        }
        String who = message.author() == null || message.author().username == null
                ? shortId(message.senderId()) : sanitize(message.author().username);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("group", target.label());
        values.put("user", who);
        values.put("text", content);
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
            noteUnbound(notice);
            return;
        }
        BridgeConfig.Target target = bound.get();
        if (!target.receivesFromQq()) {
            Log.debug("群 " + target.label() + " 配成了只出不进，忽略成员变动");
            return;
        }
        if (!firstSeen("n:" + notice.name() + ':' + notice.eventId())) {
            Log.debug("群 " + target.label() + " 的成员变动 " + notice.eventId() + " 是平台重推的，忽略");
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
        // 只看**这个 bot** 的目标：同一个群被两个 bot 都加了时，不该串台。
        return bot.target(QqEvents.conversationId(event));
    }

    /**
     * Records a group that talked to us without being in the config, and says so once.
     *
     * <p>This is the operator's way in. The config wants an openid, the only source of one is a message from the
     * group, and a log is not always where the operator is looking — so the id is kept for {@code /qq status}
     * and {@code /qq bind}, and the first sighting is loud enough to be found in a log. Once per group and not
     * once per message, or a busy group would own the console.
     */
    private void noteUnbound(QQEvent event) {
        String conversationId = QqEvents.conversationId(event);
        boolean channel = event.scene() == ReplyTarget.CHANNEL;
        BridgeConfig.Kind kind = channel ? BridgeConfig.Kind.CHANNEL : BridgeConfig.Kind.GROUP;
        String what = channel ? "子频道 " : "群 ";
        if (unbound.remember(botId, kind, conversationId, QqEvents.guildId(event))) {
            Log.warn("config: 收到" + what + conversationId + " 的消息，但它没绑定 —— 在控制台敲 /qq bind 就能绑上"
                    + "（或把 " + conversationId + " 填进 config.yml 的 "
                    + (channel ? "channel-id" : "group-openid") + "）");
        } else {
            Log.debug("收到" + what + conversationId + " 的消息，它还是没绑定，忽略");
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

    /**
     * 陌生人文本进服务器前的净化：剥掉 § 格式码 —— 否则借颜色码就能伪造别的玩家的发言、给聊天栏调色；
     * 控制字符（含换行）折成空格 —— 一条 QQ 消息不该能在日志或聊天栏里凭空多出几行。
     *
     * <p>只动"从 QQ 来"的文本；模板作者自己写的 § 是功能，不经过这里。{@code §.} 的剥法与
     * MC→QQ 出方向一致（见 {@code BridgeRuntime.plain}），少一处行为差异。
     */
    static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("§.", "").replace("§", "")
                .replaceAll("[\\p{Cntrl}]+", " ").trim();
    }

    private static String shortId(String openid) {
        if (openid == null || openid.isBlank()) {
            return "未知用户";
        }
        return openid.length() <= 6 ? openid : openid.substring(openid.length() - 6);
    }
}
