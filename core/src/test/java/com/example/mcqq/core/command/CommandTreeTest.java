package com.example.mcqq.core.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.mcqq.core.Bridge;
import com.example.mcqq.core.Constants;
import com.example.mcqq.core.MinecraftPlatform;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The command tree, tested without a game, a server or a command sender — which is the whole reason it lives in
 * the core instead of being written once per platform.
 *
 * <p>What is worth pinning down is everything the adapters rely on: names, usage lines and permission nodes are
 * <em>derived</em> from the tree, and the policy — who may run what, what a refusal says, what happens when a
 * command throws — is applied here rather than repeated in each platform.
 */
class CommandTreeTest {

    @TempDir
    Path dir;

    /** A platform that does nothing, so the bridge can be built and its tree inspected. */
    private MinecraftPlatform headless() {
        return new MinecraftPlatform() {
            @Override
            public String label() {
                return "test";
            }

            @Override
            public Path configDir() {
                return dir;
            }

            @Override
            public void broadcast(String line) {
            }

            @Override
            public void onMainThread(Runnable task) {
                task.run();
            }

            @Override
            public void registerCommands(CommandTree tree) {
            }
        };
    }

    private CommandTree tree() {
        return new Bridge(headless()).commands();
    }

    /** Collects what a command printed, and answers permission however the test wants. */
    private static final class Recorder implements CommandSource {

        private final List<String> printed = new ArrayList<>();
        private final boolean permitted;

        private Recorder(boolean permitted) {
            this.permitted = permitted;
        }

        @Override
        public boolean hasPermission(String node) {
            return permitted;
        }

        @Override
        public void reply(String line) {
            printed.add(line);
        }
    }

    @Test
    void theRootIsTheCommandAndItsSubCommandsAreTheOnesTheCoreAdded() {
        CommandTree tree = tree();

        assertEquals("qq", tree.root().name());
        assertEquals(List.of("status", "reload", "bind", "run", "templates", "test", "help"),
                tree.root().children().stream().map(SubCommand::name).toList());
    }

    @Test
    void permissionNodesAreDerivedFromThePath() {
        CommandTree tree = tree();

        assertEquals("mcqq", tree.root().permissionNode());
        assertEquals("mcqq.status", tree.resolve(List.of("status")).permissionNode());
        assertEquals("mcqq.reload", tree.resolve(List.of("reload")).permissionNode());
        assertEquals("mcqq.help", tree.resolve(List.of("help")).permissionNode());
        assertEquals("mcqq.templates", tree.resolve(List.of("templates")).permissionNode());
        assertEquals("mcqq.test", tree.resolve(List.of("test")).permissionNode());
    }

    @Test
    void usageLinesAreDerivedFromThePath() {
        CommandTree tree = tree();

        assertEquals("/qq", tree.root().usage());
        assertEquals("/qq status", tree.resolve(List.of("status")).usage());
    }

    @Test
    void anUnknownSubCommandSaysSoInsteadOfDoingNothing() {
        CommandTree tree = tree();

        List<String> lines = tree.lines(List.of("nonsense"));

        assertEquals(2, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("没有子命令 nonsense"), lines.get(0));
        assertTrue(lines.get(0).contains("status|reload|bind|run|templates|test|help"), lines.get(0));
        assertEquals("mcqq", tree.resolve(List.of("nonsense")).permissionNode(),
                "an unmatched argument resolves to the root, so a platform checks the root's permission");
    }

    @Test
    void theBareCommandExplainsItself() {
        List<String> lines = tree().lines(List.of());

        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("status|reload|bind|run|templates|test|help"), lines.get(0));
    }

    @Test
    void helpIsGeneratedFromTheTree() {
        List<String> lines = tree().lines(List.of("help"));

        assertEquals(8, lines.size(), lines.toString());
        assertTrue(lines.get(0).startsWith("/qq — "), lines.get(0));
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("/qq status — ")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("/qq reload — ")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("/qq bind — ")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("/qq run — ")), lines.toString());
        assertTrue(lines.stream().anyMatch(line -> line.startsWith("/qq help — ")), lines.toString());
    }

    @Test
    void completionOffersTheChildrenThatMatchWhatWasTyped() {
        CommandTree tree = tree();

        assertEquals(List.of("status", "reload", "bind", "run", "templates", "test", "help"), tree.completions(List.of()));
        assertEquals(List.of("reload"), tree.completions(List.of("re")));
        assertEquals(List.of(), tree.completions(List.of("zzz")));
    }

    @Test
    void statusReportsThatTheBridgeIsNotRunningYet() {
        List<String> lines = tree().lines(List.of("status"));

        assertEquals(1, lines.size(), lines.toString());
        assertTrue(lines.get(0).contains("桥接未运行"), lines.get(0));
    }

    @Test
    void runPrintsEveryLineWithThePrefix() {
        Recorder recorder = new Recorder(true);

        tree().run(recorder, List.of("status"));

        assertEquals(1, recorder.printed.size(), recorder.printed.toString());
        assertTrue(recorder.printed.get(0).startsWith(Constants.PREFIX + " "), recorder.printed.get(0));
    }

    @Test
    void runRefusesWithoutTheNodesPermissionAndNamesIt() {
        Recorder recorder = new Recorder(false);

        tree().run(recorder, List.of("reload"));

        assertEquals(1, recorder.printed.size(), recorder.printed.toString());
        assertTrue(recorder.printed.get(0).contains("mcqq.reload"), recorder.printed.get(0));
        assertTrue(recorder.printed.get(0).contains("权限"), recorder.printed.get(0));
    }

    @Test
    void completionLeavesOutWhatTheCallerMayNotRun() {
        CommandTree tree = tree();

        assertEquals(List.of("status", "reload", "bind", "run", "templates", "test", "help"), tree.completions(new Recorder(true), List.of()));
        assertEquals(List.of(), tree.completions(new Recorder(false), List.of()));
    }

    @Test
    void aCommandThatThrowsIsReportedInsteadOfEscaping() {
        RootCommand root = new RootCommand();
        root.addChild(new SubCommand() {
            @Override
            public String name() {
                return "boom";
            }

            @Override
            public String description() {
                return "总是抛异常";
            }

            @Override
            public List<String> execute(List<String> args) {
                throw new IllegalStateException("boom");
            }
        });
        Recorder recorder = new Recorder(true);

        new CommandTree(root).run(recorder, List.of("boom"));

        assertEquals(1, recorder.printed.size(), recorder.printed.toString());
        assertTrue(recorder.printed.get(0).contains("命令执行失败"), recorder.printed.get(0));
        assertTrue(recorder.printed.get(0).contains("boom"), recorder.printed.get(0));
    }
}
