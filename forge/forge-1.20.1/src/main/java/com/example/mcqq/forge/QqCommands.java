package com.example.mcqq.forge;

import com.example.mcqq.core.command.BrigadierCommands;
import com.example.mcqq.core.command.CommandTree;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;

/**
 * Registers {@code /qq} on Forge 1.20.1.
 *
 * <p>Four lines, like every other adapter: the tree, the dispatch and the permission policy all live in the
 * core, and the Brigadier plumbing is shared. What is left is the two things that really are platform-specific —
 * the sender type, and what "an operator" means here (op level 2, spelled as an int on this generation).
 */
final class QqCommands {

    private QqCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandTree tree) {
        BrigadierCommands.register(dispatcher, tree,
                source -> source.hasPermission(ForgeCommandSource.OP_LEVEL),
                ForgeCommandSource::new);
    }
}
