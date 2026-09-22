package com.example.mcqq.forge;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.BridgeConfig.McEvent;
import com.example.mcqq.core.Templates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Minecraft → QQ: chat, joins, quits and deaths go to every group whose config asked for them.
 *
 * <p>Forge 26.x moved to <em>per-event</em> buses: each event class carries its own static {@code BUS}, so a
 * listener is one line and there is no central bus to register into. That is why this file looks different from
 * the NeoForge one even though both platforms hand out the same vanilla event payloads.
 *
 * <p>This class is deliberately the only place that knows which Forge event carries which value — and it hands
 * over <em>values</em>, not a finished sentence. The wording comes from the operator's template.
 */
final class McToQq {

    private final Bridge bridge;

    private McToQq(Bridge bridge) {
        this.bridge = bridge;
    }

    static void registerOnce(Bridge bridge) {
        McToQq listener = new McToQq(bridge);
        ServerChatEvent.BUS.addListener(listener::onChat);
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(listener::onJoin);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(listener::onQuit);
        LivingDeathEvent.BUS.addListener(listener::onDeath);
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
