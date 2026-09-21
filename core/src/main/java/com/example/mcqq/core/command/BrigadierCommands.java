package com.example.mcqq.core.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Turns the core's {@link CommandTree} into a Brigadier tree.
 *
 * <p>This lives in the core even though it is command plumbing, because Brigadier is Mojang's own library
 * rather than a Minecraft class — and Minecraft's {@code Commands.literal} is a two-instruction wrapper around
 * {@code LiteralArgumentBuilder.literal}, so building the nodes directly produces exactly the same tree. That
 * leaves the Minecraft-native adapters with nothing to write but their sender type:
 *
 * <pre>{@code
 * BrigadierCommands.register(dispatcher, tree,
 *         Commands.hasPermission(Commands.LEVEL_GAMEMASTERS),
 *         FabricCommandSource::new);
 * }</pre>
 *
 * <p>{@code S} is whatever the platform calls a command sender. The core never sees it — it only asks for a
 * {@link CommandSource} back, which is the same two-method view of a caller the rest of the command code uses.
 */
public final class BrigadierCommands {

    private BrigadierCommands() {
    }

    /** Builds the node for the whole tree, ready to be added to a dispatcher's root. */
    public static <S> LiteralCommandNode<S> build(CommandTree tree,
            Predicate<S> permitted,
            Function<S, CommandSource> sourceOf) {
        LiteralCommandNode<S> root = LiteralArgumentBuilder.<S>literal(tree.root().name())
                .requires(permitted)
                .executes(context -> {
                    tree.run(sourceOf.apply(context.getSource()), List.of());
                    return 1;
                })
                .build();

        for (SubCommand child : tree.root().children()) {
            root.addChild(LiteralArgumentBuilder.<S>literal(child.name())
                    .executes(context -> {
                        tree.run(sourceOf.apply(context.getSource()), List.of(child.name()));
                        return 1;
                    })
                    .then(RequiredArgumentBuilder.<S, String>argument("args", StringArgumentType.greedyString())
                            .executes(context -> {
                                tree.run(sourceOf.apply(context.getSource()),
                                        withArguments(child.name(), context.getArgument("args", String.class)));
                                return 1;
                            }))
                    .build());
        }
        return root;
    }

    /** Builds the tree and hangs it on the dispatcher in one go. */
    public static <S> void register(CommandDispatcher<S> dispatcher, CommandTree tree,
            Predicate<S> permitted,
            Function<S, CommandSource> sourceOf) {
        dispatcher.getRoot().addChild(build(tree, permitted, sourceOf));
    }

    private static List<String> withArguments(String name, String raw) {
        List<String> args = new ArrayList<>();
        args.add(name);
        for (String part : raw.split(" ")) {
            if (!part.isEmpty()) {
                args.add(part);
            }
        }
        return args;
    }
}
