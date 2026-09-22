package com.example.mcqq.core;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The groups that have talked to a bot without being bound in the config.
 *
 * <p>It exists because of the order things happen in: the config asks for a {@code group-openid}, and the only
 * way to learn one is for the group to send a message — which is also the exact moment the bridge has to decide
 * what to do with a group it does not recognise. Dropping it quietly leaves the operator with nothing to paste
 * into the file, and on a panel host there is no shell to read a log from either. So it is remembered instead:
 * reported in the log once per group, listed by {@code /qq status}, and accepted by {@code /qq bind} without
 * anyone having to copy an id.
 *
 * <p>Bounded on purpose — a bot sitting in a busy group nobody ever bound should not grow this forever — and it
 * outlives a {@code /qq reload}, because that is when the operator is most likely to be looking at it.
 */
public final class UnboundGroups {

    /** How many are kept; the oldest falls out. Sixteen is more than anyone binds in one sitting. */
    private static final int LIMIT = 16;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** One group, and the bot that heard from it. */
    public record Seen(String botId, String groupOpenid, LocalTime at) {

        /** As {@code /qq status} prints it. */
        public String label() {
            return "群 " + groupOpenid + "（bot " + botId + "，" + at.format(TIME) + "）";
        }
    }

    /** Insertion order, oldest first; {@link #remember} re-inserts so the last entry is the newest. */
    private final Map<String, Seen> byOpenid = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Seen> eldest) {
            // A bot sitting in a busy group nobody ever bound would otherwise grow this for as long as the
            // server runs. Nothing here is worth keeping forever — the operator binds one group at a time.
            return size() > LIMIT;
        }
    };

    /**
     * Remembers a group, and answers whether this is the first time it has been seen.
     *
     * <p>The caller logs the first sighting only: a group that keeps talking would otherwise own the log, and
     * one line is enough to tell the operator the id they were missing.
     */
    public boolean remember(String botId, String groupOpenid) {
        synchronized (byOpenid) {
            // Remove before put so a group that talks again moves to the end — LinkedHashMap keeps the original
            // position on a plain put, and "newest" is what a bare /qq bind uses.
            boolean first = byOpenid.remove(groupOpenid) == null;
            byOpenid.put(groupOpenid, new Seen(botId, groupOpenid, LocalTime.now()));
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
                if (seen.groupOpenid().equalsIgnoreCase(typed)) {
                    return seen;
                }
                if (seen.groupOpenid().toLowerCase().startsWith(typed.toLowerCase())) {
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
