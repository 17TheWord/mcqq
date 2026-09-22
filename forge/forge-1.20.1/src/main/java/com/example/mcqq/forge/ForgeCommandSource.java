package com.example.mcqq.forge;

import com.example.mcqq.core.command.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * What a command caller looks like on Forge — the two things the core's command policy needs from a platform.
 *
 * <p>This generation still speaks op levels as plain ints: {@code Commands.LEVEL_GAMEMASTERS} only arrives at
 * 1.20.5. A mod platform has no permission nodes anyway, so the node name is ignored here and op level 2
 * answers for it — the same thing {@code LEVEL_GAMEMASTERS} means.
 */
final class ForgeCommandSource implements CommandSource {

    /** Op level 2: what a vanilla operator gets, and what the newer {@code LEVEL_GAMEMASTERS} resolves to. */
    static final int OP_LEVEL = 2;

    private final CommandSourceStack source;

    ForgeCommandSource(CommandSourceStack source) {
        this.source = source;
    }

    @Override
    public boolean hasPermission(String node) {
        return source.hasPermission(OP_LEVEL);
    }

    @Override
    public void reply(String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
