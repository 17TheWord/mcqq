package com.example.mcqq.spigot;

import com.example.mcqq.bukkit.common.CommonListeners;
import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.BridgeConfig.McEvent;
import com.example.mcqq.core.Templates;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Spigot variant of the listeners: joins, quits and deaths come from {@code bukkit-common}, and chat is
 * {@link AsyncPlayerChatEvent} — the event every Bukkit-family server has had for years, and the reason this
 * variant can run on Spigot at all.
 */
final class SpigotListeners extends CommonListeners {

    private SpigotListeners(Bridge bridge) {
        super(bridge);
    }

    static void registerOnce(JavaPlugin plugin, Bridge bridge) {
        CommonListeners.registerOnce(plugin, new SpigotListeners(bridge));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        bridge.forward(McEvent.CHAT, Templates.values(
                "player", event.getPlayer().getName(),
                "text", event.getMessage()));
    }
}
