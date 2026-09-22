package com.example.mcqq.bukkit.common;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.BridgeConfig.McEvent;
import com.example.mcqq.core.Templates;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minecraft → QQ for the three events every server in the family fires the same way: joins, quits and deaths.
 *
 * <p>Chat is deliberately <em>not</em> here: Paper has {@code AsyncChatEvent} and Spigot only has the
 * deprecated {@code AsyncPlayerChatEvent}, so each variant brings its own. A variant extends this class and
 * registers one listener — Bukkit finds the inherited handlers too.
 *
 * <p>Everything is observed at {@code MONITOR}: the point is to report what happened, not to change it. Only
 * the values are read here — the wording comes from the operator's template in the core.
 */
public class CommonListeners implements Listener {

    protected final Bridge bridge;

    protected CommonListeners(Bridge bridge) {
        this.bridge = bridge;
    }

    /** Registers one listener for the whole family — the variant passes its own subclass. */
    public static void registerOnce(JavaPlugin plugin, Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        bridge.forward(McEvent.JOIN, Templates.values("player", event.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        bridge.forward(McEvent.QUIT, Templates.values("player", event.getPlayer().getName()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        bridge.forward(McEvent.DEATH, Templates.values(
                "player", event.getEntity().getName(),
                "killer", killer == null ? "不明原因" : killer.getName()));
    }
}
