package com.example.mcqq.neoforge;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.BridgeConfig.McEvent;
import com.example.mcqq.core.Templates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Minecraft → QQ: chat, joins, quits and deaths go to every group whose config asked for them.
 *
 * <p>NeoForge is told about them by class rather than by annotation: {@code NeoForge.EVENT_BUS.addListener} takes
 * the event type and a consumer, which is one line per event and needs no scanning of annotated classes.
 *
 * <p>This class is deliberately the only place that knows which NeoForge event carries which value — and it
 * hands over <em>values</em>, not a finished sentence. The wording comes from the operator's template.
 */
final class McToQq {

    private final Bridge bridge;

    private McToQq(Bridge bridge) {
        this.bridge = bridge;
    }

    static void registerOnce(Bridge bridge) {
        McToQq listener = new McToQq(bridge);
        NeoForge.EVENT_BUS.addListener(ServerChatEvent.class, listener::onChat);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedInEvent.class, listener::onJoin);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, listener::onQuit);
        NeoForge.EVENT_BUS.addListener(LivingDeathEvent.class, listener::onDeath);
    }

    private void onChat(ServerChatEvent event) {
        bridge.forward(McEvent.CHAT, Templates.values(
                "player", event.getUsername(),
                "text", event.getRawText()));
    }

    private void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        bridge.forward(McEvent.JOIN, Templates.values("player", event.getEntity().getName().getString()));
    }

    private void onQuit(PlayerEvent.PlayerLoggedOutEvent event) {
        bridge.forward(McEvent.QUIT, Templates.values("player", event.getEntity().getName().getString()));
    }

    private void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity killer = event.getSource().getEntity();
        bridge.forward(McEvent.DEATH, Templates.values(
                "player", player.getName().getString(),
                "killer", killer == null ? "不明原因" : killer.getName().getString()));
    }
}
