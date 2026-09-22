package com.example.mcqq.forge;

import com.example.mcqq.core.command.BrigadierCommands;
import com.example.mcqq.core.command.CommandTree;
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
