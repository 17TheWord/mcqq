package com.example.mcqq.spigot;

import com.example.mcqq.bukkit.common.JulSink;
import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.Log;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin entrypoint for Spigot: builds the platform adapter, hands it to the bridge, and wires the plugin
 * lifecycle to the bridge's start/stop.
 *
 * <p>Unlike a mod, a plugin is enabled once the server is already running, so the bridge starts here.
 *
 * <p>It refuses to run on a Paper-family server on purpose: this variant listens to the legacy
 * {@code AsyncPlayerChatEvent}, and whether Paper still fires that one alongside its own {@code AsyncChatEvent}
 * has not been measured. Refusing is the honest choice — a wrong jar gets one clear sentence instead of
 * possibly forwarding every chat message twice.
 */
public final class SpigotPlugin extends JavaPlugin {

    private Bridge bridge;

    private static final boolean ON_PAPER = present("io.papermc.paper.event.player.AsyncChatEvent");

    @Override
    public void onEnable() {
        if (ON_PAPER) {
            getLogger().severe("这个 jar 是给 Spigot / CraftBukkit 的。当前服务端是 Paper 系，请改用 "
                    + "mc-qq-paper-…jar（那份用 AsyncChatEvent，也不会重复转发）。插件已停用。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        Log.install(new JulSink(getLogger()));

        SpigotPlatform platform = new SpigotPlatform(this);
        bridge = new Bridge(platform);

        SpigotListeners.registerOnce(this, bridge);
        platform.registerCommands(bridge.commands());

        Log.info("mc-qq 已加载；QQ 桥接会随服务器启动");
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
