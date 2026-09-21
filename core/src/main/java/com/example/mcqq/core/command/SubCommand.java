package com.example.mcqq.core.command;

import com.example.mcqq.core.Constants;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One node of the command tree.
 *
 * <p>The tree lives in the core so that adding a sub-command is one class here and <em>no</em> change in any
 * adapter: they register whatever {@link CommandTree#root()} has, generically. What a platform still owns is
 * the two things only it can know — how to print a line and who is allowed to run it (see
 * {@link #permissionNode()} and {@link #execute(List)}).
 *
 * <p>Derived rather than declared: a node's path, its usage line and its permission node all come from its
 * position in the tree, so the three can never drift apart. {@code /qq status} is
 * {@code mcqq.status} by construction.
 */
public abstract class SubCommand {

    private SubCommand parent;
    private final List<SubCommand> children = new ArrayList<>();

    public final void addChild(SubCommand child) {
        child.parent = this;
        children.add(child);
    }

    public final List<SubCommand> children() {
        return Collections.unmodifiableList(children);
    }

    public final SubCommand parent() {
        return parent;
    }

    /** The literal this node answers to, e.g. {@code status}. */
    public abstract String name();

    /** One line for {@code /qq help}. */
    public abstract String description();

    /** Root to this node, space separated, e.g. {@code qq status}. */
    public final String path() {
        List<String> names = new ArrayList<>();
        for (SubCommand node = this; node != null; node = node.parent) {
            names.add(0, node.name());
        }
        return String.join(" ", names);
    }

    /** What {@code /qq help} shows and what an operator is told to type, e.g. {@code /qq status}. */
    public String usage() {
        return "/" + path();
    }

    /**
     * The permission a caller must hold, derived from the path: the root is {@code mcqq}, so {@code /qq status}
     * is {@code mcqq.status}. Checking it is the adapter's job — it is the only side that has a sender.
     */
    public final String permissionNode() {
        SubCommand root = this;
        while (root.parent != null) {
            root = root.parent;
        }
        String belowRoot = path().substring(root.name().length()).trim();
        return belowRoot.isEmpty() ? Constants.PERMISSION_ROOT
                : Constants.PERMISSION_ROOT + "." + belowRoot.replace(' ', '.');
    }

    /** The lines this command wants printed. Arguments are whatever followed the node's path. */
    public abstract List<String> execute(List<String> args);
}
