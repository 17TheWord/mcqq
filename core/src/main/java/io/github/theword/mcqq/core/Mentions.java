package io.github.theword.mcqq.core;

import io.github.skiesworld.qqbot.model.User;
import java.util.List;
import java.util.Map;

/**
 * QQ 消息正文里的 {@code @} 标记 → 聊天栏里读得懂的东西。
 *
 * <p>SDK 的 {@code content()} 会把 {@code mentions} 背书过的 {@code <@...>} 删掉（判命令前缀要的
 * 就是那个结果），删了位置就没了、聊天栏看不出“他 @ 了谁”。所以这里拿<em>原始</em>正文自己替换。
 *
 * <p>四种标记里只有 {@code <@id>} 的名字是现成的（事件的 {@code mentions} 数组里就有，真机实测
 * 群和频道都带 {@code username}），<b>不用调接口</b>；{@code <#id>} 平台通常已渲染成 {@code #名字}；
 * {@code <emoji:id>} 和 {@code @everyone} 没人转，会原样漏出来。
 *
 * <p>替换串来自 {@link Templates} 的 {@code mention-*} 键（空值 = 删掉这个标记）。
 * 解析边界对齐 SDK 的 {@code withoutMentions}：配不上 {@code >} 就把剩下的原样输出。
 */
final class Mentions {

    private Mentions() {
    }

    /**
     * 把正文里的 @ 标记换成可读的东西。
     *
     * <p>调用方要<strong>先净化再调这里</strong>：{@link QqToMc#sanitize} 会剥掉 {@code §}，而替换串里的
     * {@code §a} / {@code §r} 是功能、不能被剥。反过来，昵称是陌生人文本，所以这里对<em>名字</em>
     * 单独净化一次。
     */
    static String render(String text, List<User> mentions, BridgeConfig config, String conversationId) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String userFormat = config.template(conversationId, Templates.MENTION_USER);
        String channelFormat = config.template(conversationId, Templates.MENTION_CHANNEL);
        String emojiFormat = config.template(conversationId, Templates.MENTION_EMOJI);

        StringBuilder out = new StringBuilder(text.length());
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf('<', index);
            int end = start < 0 ? -1 : text.indexOf('>', start + 1);
            if (start < 0 || end < 0) {
                out.append(text, index, text.length());
                break;
            }
            out.append(text, index, start);
            String replaced = replace(text.substring(start + 1, end), mentions,
                    userFormat, channelFormat, emojiFormat);
            if (replaced == null) {
                // 不认得的尖括号内容原样留着：它可能是玩家真写的一个 <b>，不该被我们吃掉。
                out.append(text, start, end + 1);
            } else {
                out.append(replaced);
            }
            index = end + 1;
        }
        return everyone(out.toString(), config.template(conversationId, Templates.MENTION_EVERYONE));
    }

    /** 认得的尖括号内容 → 替换串；不认得 → {@code null}，调用方原样保留。 */
    private static String replace(String body, List<User> mentions,
            String userFormat, String channelFormat, String emojiFormat) {
        if (body.startsWith("@")) {
            String id = body.substring(1);
            return user(id.startsWith("!") ? id.substring(1) : id, mentions, userFormat);
        }
        if (body.startsWith("#")) {
            return fill(channelFormat, "", body.substring(1));
        }
        if (body.startsWith("emoji:")) {
            return fill(emojiFormat, "", body.substring("emoji:".length()));
        }
        return null;
    }

    /** @某人：名字从 mentions 里取；取不到就退回短 id —— 至少别让一长串原始 id 漏进聊天栏。 */
    private static String user(String id, List<User> mentions, String format) {
        User mentioned = find(mentions, id);
        String name = mentioned == null ? "" : QqToMc.sanitize(mentioned.username);
        return fill(format, name.isEmpty() ? QqToMc.shortId(id) : name, id);
    }

    /** 按 id 在 mentions 里找人 —— 四个 id 字段都认，和 SDK 判「这条 @ 是不是给我的」用同一套。 */
    private static User find(List<User> mentions, String id) {
        for (User user : mentions) {
            if (id.equals(user.id) || id.equals(user.userOpenid) || id.equals(user.memberOpenid)
                    || id.equals(user.unionOpenid)) {
                return user;
            }
        }
        return null;
    }

    /** {@code @everyone} 是纯文本，直接换掉；正文里没有就一个字节都不动。 */
    private static String everyone(String text, String format) {
        if (text.indexOf("@everyone") < 0) {
            return text;
        }
        return text.replace("@everyone", Templates.render(format, Map.of()));
    }

    /**
     * 只认 {@code {name}} / {@code {id}}；空格式 = 删掉。走 {@link Templates#render} 是为了和别处一个脾气：
     * 认不出的占位符原样留着，运维能看见自己的笔误。
     */
    private static String fill(String format, String name, String id) {
        return Templates.render(format, Templates.values("name", name, "id", id));
    }
}
