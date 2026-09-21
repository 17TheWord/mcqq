package com.example.mcqq.fabric;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.BridgeConfig.McEvent;
import com.example.mcqq.core.Templates;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Minecraft → QQ: chat, joins, quits and deaths go to every group whose config asked for them.
 *
 * <p>The four listeners are registered once at mod init and hold the one {@link Bridge} for the life of the
 * process; a reload swaps the bots inside that bridge rather than the bridge itself, which is what keeps a
 * second copy of every listener from stacking up. The sends happen on the bridge's own threads: a QQ round trip
 * is never waited on inside a server tick.
 *
 * <p>This class is deliberately the only place that knows which Minecraft type carries which value — but it
 * hands over <em>values</em>, not a finished sentence. The wording comes from the operator's template, so
 * changing "加入了世界" does not need a mod update.
 */
final class McToQq {

    private McToQq() {
    }

    static void registerOnce(Bridge bridge) {
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, boundChatType) ->
                bridge.forward(McEvent.CHAT, Templates.values(
                        "player", sender.getName().getString(),
                        "text", message.signedContent())));
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) ->
                bridge.forward(McEvent.JOIN, Templates.values("player", name(listener))));
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) ->
                bridge.forward(McEvent.QUIT, Templates.values("player", name(listener))));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> onDeath(bridge, entity, damageSource));
    }

    private static void onDeath(Bridge bridge, LivingEntity entity, DamageSource damageSource) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        Entity killer = damageSource.getEntity();
        bridge.forward(McEvent.DEATH, Templates.values(
                "player", player.getName().getString(),
                "killer", killer == null ? "不明原因" : killer.getName().getString()));
    }

    private static String name(ServerGamePacketListenerImpl listener) {
        ServerPlayer player = listener.getPlayer();
        return player == null ? "某玩家" : player.getName().getString();
    }
}
