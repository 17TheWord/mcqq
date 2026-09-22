package com.example.mcqq.forge;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.BridgeConfig.McEvent;
import com.example.mcqq.core.Templates;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/**
 * Minecraft → QQ: chat, joins, quits and deaths go to every group whose config asked for them.
 *
 * <p>This generation registers on one central bus, {@link MinecraftForge#EVENT_BUS}, instead of the per-event
 * static {@code BUS} fields the 26.x module uses — that is the only structural difference between the two
 * files. The payloads are the same: 1.20.1's {@code ServerChatEvent} also hands out {@code getUsername()} and
 * {@code getRawText()} (checked against {@code forge-1.20.1-47.4.23-sources.jar}, not assumed), so the chat
 * line below is character-for-character what the 26.x module sends.
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
        MinecraftForge.EVENT_BUS.addListener(listener::onChat);
        MinecraftForge.EVENT_BUS.addListener(listener::onJoin);
        MinecraftForge.EVENT_BUS.addListener(listener::onQuit);
        MinecraftForge.EVENT_BUS.addListener(listener::onDeath);
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
