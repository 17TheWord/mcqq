package com.example.mcqq.forge;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.Constants;
import com.example.mcqq.core.Log;
import com.example.mcqq.core.Slf4jSink;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

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

    public McQqMod(FMLJavaModLoadingContext context) {
        Log.install(new Slf4jSink());

        ForgePlatform platform = new ForgePlatform();
        Bridge bridge = new Bridge(platform);

        McToQq.registerOnce(bridge);
        platform.registerCommands(bridge.commands());

        ServerStartedEvent.BUS.addListener(event -> {
            platform.attach(event.getServer());
            bridge.start();
        });
        ServerStoppingEvent.BUS.addListener(event -> {
            bridge.stop();
            platform.detach();
        });

        Log.info(Constants.MOD_ID + " loaded; the QQ bridge comes up with the server");
    }
}
