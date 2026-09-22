package com.example.mcqq.core.command.sub;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.BridgeConfig;
import com.example.mcqq.core.Templates;
import com.example.mcqq.core.command.SubCommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code /qq templates} — what every message currently says, what can be put in one, and what to paste into the
 * config to change it.
 *
 * <p>It exists because the templates are the one part of the config an operator is most likely to want to change
 * and least likely to find: they are optional, so an older config has no trace of them, and "why does this line
 * look like that" has no other answer. Printing the effective values is cheaper than a `.bak` file and it cannot
 * go stale.
 */
public final class TemplatesCommand extends SubCommand {

    private final Bridge bridge;

    public TemplatesCommand(Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public String name() {
        return "templates";
    }

    @Override
    public String description() {
        return "显示当前生效的消息模板与可用占位符（空模板 = 不播报）";
    }

    @Override
    public List<String> execute(List<String> args) {
        Optional<BridgeConfig> current = bridge.config();
        if (current.isEmpty()) {
            return List.of("桥接未运行，看不到生效的模板");
        }
        BridgeConfig config = current.get();
        List<String> lines = new ArrayList<>();

        lines.add("占位符：" + placeholders());
        for (String key : Templates.keys()) {
            lines.add(key + " = " + config.globalTemplate(key));
        }
        for (BridgeConfig.Bot bot : config.bots()) {
            for (BridgeConfig.Target target : bot.targets()) {
                if (!target.templates().isEmpty()) {
                    lines.add(target.label() + " 覆盖了：" + String.join(", ", target.templates().keySet()));
                }
            }
        }
        if (config.templates().isEmpty()) {
            lines.add("配置里没有 templates 段（用的是内置默认）。要改就把下面这段粘进 config.yml，"
                    + "只写想改的键也行：");
            lines.add("templates:");
            for (String key : Templates.keys()) {
                lines.add("  " + key + ": \"" + Templates.defaults().get(key).replace("\"", "\\\"") + "\"");
            }
        }
        return lines;
    }

    private static String placeholders() {
        List<String> names = new ArrayList<>(Templates.KNOWN);
        names.sort(String::compareTo);
        return "{" + String.join("} {", names) + "}";
    }
}
