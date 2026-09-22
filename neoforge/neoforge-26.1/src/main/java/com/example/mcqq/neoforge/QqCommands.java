package com.example.mcqq.neoforge;

import com.example.mcqq.core.command.BrigadierCommands;
import com.example.mcqq.core.command.CommandTree;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Registers {@code /qq} on NeoForge.
 *
 * <p>This used to be a line-for-line copy of the Fabric adapter's: both platforms hand out the same Brigadier
 * types and the same {@code CommandSourceStack}. The shared half now lives in the core's
 * {@link BrigadierCommands}, and what remains is the two things that really are platform-specific — the sender
 * type and what "an operator" means here (op level 2, since mod platforms have no permission nodes).
 */
final class QqCommands {

    private QqCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandTree tree) {
        BrigadierCommands.register(dispatcher, tree,
                Commands.hasPermission(Commands.LEVEL_GAMEMASTERS),
                NeoForgeCommandSource::new);
    }
}
