package io.github.theword.mcqq.forge;

import io.github.theword.mcqq.core.command.BrigadierCommands;
import io.github.theword.mcqq.core.command.CommandTree;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

/**
 * Registers {@code /qq} on Forge.
 *
 * <p>Four lines, like the NeoForge and Fabric adapters: the tree, the dispatch and the permission policy all
 * live in the core, and the Brigadier plumbing is shared. What is left is the two things that really are
 * platform-specific — the sender type, and what "an operator" means here (op level 2).
 */
final class QqCommands {

    private QqCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandTree tree) {
        BrigadierCommands.register(dispatcher, tree,
                Commands.hasPermission(Commands.LEVEL_GAMEMASTERS),
                ForgeCommandSource::new);
    }
}
