package com.example.mcqq.fabric;

import com.example.mcqq.core.command.BrigadierCommands;
import com.example.mcqq.core.command.CommandTree;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.Commands;

/**
 * Registers {@code /qq} on Fabric.
 *
 * <p>All that is left here is the platform's own spelling of two things: which sender type it has, and what
 * "an operator" means on a mod platform (op level 2, since there are no permission nodes). The tree itself —
 * names, descriptions, permission nodes, dispatch, output prefix — comes from the core, and the Brigadier
 * plumbing is shared with every other Minecraft-native platform.
 */
final class QQCommands {

    private QQCommands() {
    }

    static void registerOnce(CommandTree tree) {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                BrigadierCommands.register(dispatcher, tree,
                        Commands.hasPermission(Commands.LEVEL_GAMEMASTERS),
                        FabricCommandSource::new));
    }
}
