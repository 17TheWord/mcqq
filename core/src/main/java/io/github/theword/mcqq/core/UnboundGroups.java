package io.github.theword.mcqq.core;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跟机器人说过话、但配置里还没有的会话。
 *
 * <p>存在的原因是顺序：配置要 {@code group-openid}，而知道它的唯一途径是群里发一条消息 ——
 * 那正是桥接要决定“拿这个不认识的群怎么办”的时刻。悄悄丢掉，运维就没什么可粘；
 * 记在日志里又未必是他会看的地方。所以记住它：日志每群一次、{@code /qq status} 列出、
 * {@code /qq bind} 直接认。
 *
 * <p>有上限，且比 {@code /qq reload} 活得长。
 */
public final class UnboundGroups {

    /** How many are kept; the oldest falls out. Sixteen is more than anyone binds in one sitting. */
    private static final int LIMIT = 16;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 一个在跟机器人说话、但配置里还没有的会话 —— 群或子频道，以及是哪个 bot 听到的。
     *
     * <p>子频道要多带一个 {@code guildId}：绑定的时候要把它一起写进配置（{@code guild-id} +
     * {@code channel-id} 是它寻址的两个字段）。
     */
    public record Seen(String botId, BridgeConfig.Kind kind, String conversationId, String guildId,
            LocalTime at) {

        /** As {@code /qq status} prints it. */
        public String label() {
            String what = kind == BridgeConfig.Kind.CHANNEL ? "子频道 " : "群 ";
            return what + conversationId + "（bot " + botId + "，" + at.format(TIME) + "）";
        }
    }

    /** Insertion order, oldest first; {@link #remember} re-inserts so the last entry is the newest. */
    private final Map<String, Seen> byOpenid = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Seen> eldest) {
            return size() > LIMIT;
        }
    };

    /**
     * Remembers a group, and answers whether this is the first time it has been seen.
     *
     * <p>The caller logs the first sighting only: a group that keeps talking would otherwise own the log, and
     * one line is enough to tell the operator the id they were missing.
     */
    public boolean remember(String botId, BridgeConfig.Kind kind, String conversationId, String guildId) {
        synchronized (byOpenid) {
            // Remove before put so one that talks again moves to the end — LinkedHashMap keeps the original
            // position on a plain put, and "newest" is what a bare /qq bind uses.
            boolean first = byOpenid.remove(conversationId) == null;
            byOpenid.put(conversationId,
                    new Seen(botId, kind, conversationId, guildId, LocalTime.now()));
            return first;
        }
    }

    /** The group heard from most recently, which is what a bare {@code /qq bind} binds. Null when there is none. */
    public Seen newest() {
        synchronized (byOpenid) {
            Seen last = null;
            for (Seen seen : byOpenid.values()) {
                last = seen;
            }
            return last;
        }
    }

    /** Oldest first, so a listing reads in the order the groups showed up. */
    public List<Seen> all() {
        synchronized (byOpenid) {
            return new ArrayList<>(byOpenid.values());
        }
    }

    /**
     * What the operator typed, resolved against what has been seen: the whole openid, or any prefix of it that
     * matches exactly one group. Null when nothing matched, or when a prefix matched several — an ambiguous
     * bind is worse than a refused one.
     */
    public Seen find(String typed) {
        if (typed == null || typed.isBlank()) {
            return null;
        }
        List<Seen> matches = new ArrayList<>();
        synchronized (byOpenid) {
            for (Seen seen : byOpenid.values()) {
                if (seen.conversationId().equalsIgnoreCase(typed)) {
                    return seen;
                }
                if (seen.conversationId().toLowerCase().startsWith(typed.toLowerCase())) {
                    matches.add(seen);
                }
            }
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    /** A group that is bound now has no business in here. */
    public void forget(String groupOpenid) {
        synchronized (byOpenid) {
            byOpenid.remove(groupOpenid);
        }
    }
}
