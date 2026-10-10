package io.github.theword.mcqq.paper;

import io.github.theword.mcqq.bukkit.common.CommonListeners;
import io.github.theword.mcqq.core.Bridge;
import io.github.theword.mcqq.core.BridgeConfig.McEvent;
import io.github.theword.mcqq.core.Templates;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * The Paper variant of the listeners: joins, quits and deaths come from {@code bukkit-common}, and chat is the
 * one event that has to be spelled differently here — {@link AsyncChatEvent} instead of the deprecated
 * {@code AsyncPlayerChatEvent} that Spigot still uses.
 */
final class PaperListeners extends CommonListeners {

    private PaperListeners(Bridge bridge) {
        super(bridge);
    }

    static void registerOnce(JavaPlugin plugin, Bridge bridge) {
        CommonListeners.registerOnce(plugin, new PaperListeners(bridge));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        bridge.forward(McEvent.CHAT, Templates.values(
                "player", event.getPlayer().getName(),
                "text", PlainTextComponentSerializer.plainText().serialize(event.message())));
    }
}
