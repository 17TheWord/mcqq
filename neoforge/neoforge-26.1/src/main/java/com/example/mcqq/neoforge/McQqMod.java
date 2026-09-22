package com.example.mcqq.neoforge;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.Constants;
import com.example.mcqq.core.Log;
import com.example.mcqq.core.Slf4jSink;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * Mod entrypoint: builds the platform adapter, hands it to the bridge, and wires the server lifecycle to the
 * bridge's start/stop. Nothing else — the config, the bots, the routing and the command tree all live in
 * {@code core}.
 *
 * <p>The bridge is created once here rather than per server start, so {@code /qq reload} can swap bots and
 * bindings without stacking a second copy of every listener.
 */
@Mod(Constants.MOD_ID)
public final class McQqMod {

    public McQqMod(IEventBus modBus) {
        Log.install(new Slf4jSink());

        NeoForgePlatform platform = new NeoForgePlatform();
        Bridge bridge = new Bridge(platform);

        McToQq.registerOnce(bridge);
        platform.registerCommands(bridge.commands());

        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, event -> {
            platform.attach(event.getServer());
            bridge.start();
        });
        NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> {
            bridge.stop();
            platform.detach();
        });

        Log.info(Constants.MOD_ID + " loaded; the QQ bridge comes up with the server");
    }
}
