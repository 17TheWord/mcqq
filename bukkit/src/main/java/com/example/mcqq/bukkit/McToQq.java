package com.example.mcqq.bukkit;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.BridgeConfig.McEvent;
import com.example.mcqq.core.Templates;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Minecraft → QQ: chat, joins, quits and deaths go to every group whose config asked for them.
 *
 * <p>Everything is observed at {@code MONITOR}: the point is to report what happened, not to change it, and the
 * chat event is skipped when something else has already cancelled it. The sends themselves happen on the
 * bridge's own threads — a QQ round trip is never waited on inside a tick.
 *
 * <p>The chat event is Paper's {@code AsyncChatEvent}. That is a deliberate narrowing for the first release:
 * plain Bukkit's {@code AsyncPlayerChatEvent} is deprecated and its continued delivery on Paper is exactly the
 * thing that would have to be measured before the plugin could claim Spigot support.
 *
 * <p>Only the values are read here — the wording comes from the operator's template in the core.
 */
final class McToQq implements Listener {

    private final Bridge bridge;

    private McToQq(Bridge bridge) {
        this.bridge = bridge;
    }

    static void registerOnce(JavaPlugin plugin, Bridge bridge) {
        plugin.getServer().getPluginManager().registerEvents(new McToQq(bridge), plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        bridge.forward(McEvent.CHAT, Templates.values(
                "player", event.getPlayer().getName(),
                "text", PlainTextComponentSerializer.plainText().serialize(event.message())));
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
