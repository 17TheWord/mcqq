package com.example.mcqq.fabric;

import com.example.mcqq.core.MinecraftPlatform;
import com.example.mcqq.core.command.CommandTree;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Fabric side of the seam, and the only file in this project that knows what a {@code MinecraftServer} is
 * on the bridge's behalf.
 *
 * <p>The server object arrives late — a mod is initialised before any server exists — so it is attached when
 * {@code SERVER_STARTED} fires and detached when the server stops. Everything else is available from the loader
 * at init time, which is why the command can be registered before there is a server to run it on.
 */
final class FabricPlatform implements MinecraftPlatform {

    private volatile MinecraftServer server;

    void attach(MinecraftServer started) {
        this.server = started;
    }

    void detach() {
        this.server = null;
    }

    @Override
    public String label() {
        return "fabric-" + FabricLoader.getInstance().getModContainer("minecraft")
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public void broadcast(String line) {
        MinecraftServer current = server;
        if (current == null) {
            return;
        }
        Component message = Component.literal(line);
        // The server thread owns a player's packet queue, hence the hop before any per-player send.
        current.execute(() -> {
            for (ServerPlayer player : current.getPlayerList().getPlayers()) {
                player.sendSystemMessage(message);
            }
        });
    }

    @Override
    public void onMainThread(Runnable task) {
        MinecraftServer current = server;
        if (current == null) {
            task.run();
        } else {
            current.execute(task);
        }
    }

    @Override
    public void registerCommands(CommandTree tree) {
        QQCommands.registerOnce(tree);
    }
}
