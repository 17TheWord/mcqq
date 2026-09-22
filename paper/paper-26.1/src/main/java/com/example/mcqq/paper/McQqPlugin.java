package com.example.mcqq.paper;

import com.example.mcqq.bukkit.common.JulSink;
import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.Log;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entrypoint: builds the platform adapter, hands it to the bridge, and wires the plugin lifecycle to the
 * bridge's start/stop. Nothing else — the config, the bots and the routing all live in {@code core}.
 *
 * <p>Unlike a mod, a plugin is enabled once the server is already running, so the bridge starts here rather than
 * on a "server started" event.
 */
public final class McQqPlugin extends JavaPlugin {

    private Bridge bridge;

    /**
     * Paper's chat event — the one thing a Spigot server does not have. It is checked before anything touches
     * the listener class, so the wrong jar gets a sentence instead of a NoClassDefFoundError somewhere inside
     * the event registration.
     */
    private static final boolean PAPER_CHAT = present("io.papermc.paper.event.player.AsyncChatEvent");

    @Override
    public void onEnable() {
        if (!PAPER_CHAT) {
            getLogger().severe("这个 jar 是给 Paper 系服务端（Paper / Folia / Purpur 等）的：聊天转发用的是 Paper 的 "
                    + "AsyncChatEvent。Spigot / CraftBukkit 请改用 mc-qq-spigot-…jar。插件已停用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Log.install(new JulSink(getLogger()));

        PaperPlatform platform = new PaperPlatform(this);
        bridge = new Bridge(platform);

        PaperListeners.registerOnce(this, bridge);
        platform.registerCommands(bridge.commands());

        Log.info("mc-qq loaded; the QQ bridge comes up with the server");
        bridge.start();
    }

    private static boolean present(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (bridge != null) {
            bridge.stop();
            bridge = null;
        }
    }
}
