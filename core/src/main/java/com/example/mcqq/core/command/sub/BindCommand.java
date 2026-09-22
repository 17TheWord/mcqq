package com.example.mcqq.core.command.sub;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.UnboundGroups;
import com.example.mcqq.core.command.SubCommand;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code /qq bind} — binds a group that has already talked to a bot, without opening the config.
 *
 * <p>It exists because of the order things happen in. The config wants a {@code group-openid}; the only place
 * that id appears is a message from the group; and the people this is for run on panel hosts, where reading a
 * log and editing a file <em>is</em> the problem. So a bare {@code /qq bind} takes the group heard from most
 * recently — the operator types six characters right after asking the group to say something — and an argument
 * (the openid, or a long enough prefix of it) picks a different one.
 *
 * <p>Only groups the bot has actually heard from can be bound: see {@link Bridge#bindGroup}.
 */
public final class BindCommand extends SubCommand {

    private final Bridge bridge;

    public BindCommand(Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "bind";
    }

    @Override
    public String description() {
        return "把机器人收到过消息的群绑上（写进 config.yml 并重载；不带参数 = 绑最近说话的那个）";
    }

    @Override
    public List<String> execute(List<String> args) {
        UnboundGroups groups = bridge.unboundGroups();
        UnboundGroups.Seen target = args.isEmpty() ? groups.newest() : groups.find(args.get(0));
        if (target == null) {
            return nothingToBind(args, groups);
        }
        return bridge.bindGroup(target);
    }

    /** Why nothing was bound, and what could be — the two questions this command is asked in practice. */
    private static List<String> nothingToBind(List<String> args, UnboundGroups groups) {
        List<UnboundGroups.Seen> known = groups.all();
        List<String> lines = new ArrayList<>();
        if (known.isEmpty()) {
            lines.add("还没有哪个群给机器人发过消息。在群里 @ 一下机器人（或让它收到一条群消息），"
                    + "再敲一次 /qq bind。");
            return lines;
        }
        if (args.isEmpty()) {
            lines.add("没有可绑的群（机器人还没收到过任何群消息）。");
            return lines;
        }
        // A prefix that matched several lands here too, and listing them is the only way to pick one.
        lines.add("没找到「" + args.get(0) + "」。现在能绑的是（可以只写 openid 的前几位）：");
        for (UnboundGroups.Seen seen : known) {
            lines.add("  " + seen.label());
        }
        return lines;
    }
}
