package com.example.mcqq.neoforge;

import com.example.mcqq.core.MinecraftPlatform;
import com.example.mcqq.core.command.CommandTree;
import java.nio.file.Path;
import java.util.Locale;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The NeoForge side of the seam, and the only file in this project that knows what a {@code MinecraftServer} is
 * on the bridge's behalf.
 *
 * <p>The server object arrives late — a mod is constructed before any server exists — so it is attached when the
 * server starts and detached when it stops. Commands are registered on NeoForge's own event, which fires while
 * the server builds its command tree, so that one waits for the event instead of being called at construction.
 */
final class NeoForgePlatform implements MinecraftPlatform {

    private volatile MinecraftServer server;

    void attach(MinecraftServer started) {
        this.server = started;
    }

    void detach() {
        this.server = null;
    }

    @Override
    public String label() {
        // `name()`, not `getName()`: WorldVersion is a record-style interface in 26.x.
        return "neoforge-" + SharedConstants.getCurrentVersion().name();
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
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
        NeoForge.EVENT_BUS.addListener(RegisterCommandsEvent.class,
                event -> QqCommands.register(event.getDispatcher(), tree));
    }
}
