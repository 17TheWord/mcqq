package com.example.mcqq.fabric;

import com.example.mcqq.core.command.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * What a command caller looks like on Fabric — the two things the core's command policy needs from a platform.
 *
 * <p>Minecraft 26.1 dropped the old int levels for {@code PermissionCheck}/{@code PermissionSet}, and a mod
 * platform has no permission nodes anyway, so the node name is ignored here and op level 2 answers for it.
 */
final class FabricCommandSource implements CommandSource {

    private final CommandSourceStack source;

    FabricCommandSource(CommandSourceStack source) {
        this.source = source;
    }

    @Override
    public boolean hasPermission(String node) {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source);
    }

    @Override
    public void reply(String line) {
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
