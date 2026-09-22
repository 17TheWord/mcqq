package com.example.mcqq.fabric;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.Constants;
import com.example.mcqq.core.Log;
import com.example.mcqq.core.Slf4jSink;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * Mod entrypoint: builds the platform adapter, hands it to the bridge, and wires the server lifecycle to the
 * bridge's start/stop. Nothing else — the config, the bots, the routing and the command tree all live in
 * {@code core}.
 *
 * <p>The bridge and the listeners are created once here rather than per server start, so {@code /qq reload} can
 * swap bots and bindings without stacking a second copy of every listener.
 */
public final class McQqMod implements ModInitializer {

    public static final String MOD_ID = Constants.MOD_ID;

    @Override
    public void onInitialize() {
        Log.install(new Slf4jSink());

        FabricPlatform platform = new FabricPlatform();
        Bridge bridge = new Bridge(platform);

        McToQq.registerOnce(bridge);
        platform.registerCommands(bridge.commands());

        ServerLifecycleEvents.SERVER_STARTED.register(started -> {
            platform.attach(started);
            bridge.start();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> {
            bridge.stop();
            platform.detach();
        });
        Log.info(MOD_ID + " 已加载；QQ 桥接会随服务器启动");
    }
}
