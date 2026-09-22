package com.example.mcqq.forge;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.Constants;
import com.example.mcqq.core.Log;
import com.example.mcqq.core.Slf4jSink;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Mod entrypoint: builds the platform adapter, hands it to the bridge, and wires the server lifecycle to the
 * bridge's start/stop. Nothing else — the config, the bots, the routing and the command tree all live in
 * {@code core}.
 *
 * <p>Two things differ from the 26.x module, and both come from this generation's event model: the constructor
 * takes no arguments (Forge only started injecting {@code FMLJavaModLoadingContext} later), and every listener
 * is registered on one central bus, {@link MinecraftForge#EVENT_BUS}, rather than on a per-event static
 * {@code BUS} field.
 *
 * <p>The bridge is created once here rather than per server start, so {@code /qq reload} can swap bots and
 * bindings without stacking a second copy of every listener.
 */
@Mod(Constants.MOD_ID)
public final class McQqMod {

    public McQqMod() {
        Log.install(new Slf4jSink());

        ForgePlatform platform = new ForgePlatform();
        Bridge bridge = new Bridge(platform);

        McToQq.registerOnce(bridge);
        platform.registerCommands(bridge.commands());

        // The parameter types are spelled out because the bus infers the event type from the lambda.
        MinecraftForge.EVENT_BUS.addListener((ServerStartedEvent event) -> {
            platform.attach(event.getServer());
            bridge.start();
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> {
            bridge.stop();
            platform.detach();
        });

        Log.info(Constants.MOD_ID + " loaded; the QQ bridge comes up with the server");
    }
}
