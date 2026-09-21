package com.example.mcqq.core;

/**
 * Names that more than one place has to agree on.
 *
 * <p>They live in the core because the adapters are the ones that disagree: the command has to be called the
 * same thing in a Brigadier literal, in {@code plugin.yml} and in the mod descriptor, and the permission node
 * has to match between the code that checks it and the file that declares it.
 */
public final class Constants {

    /**
     * The mod id, and the same string on every platform.
     *
     * <p>It is not the project's name ({@code mc-qq}) because NeoForge mod ids may only match
     * {@code ^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$}: a hyphen is rejected outright and FML refuses to start.
     * {@code mcqq} is the spelling Fabric, NeoForge and Bukkit all accept — so the id is one string everywhere,
     * and it is also the name of the folder the config lives in.
     */
    public static final String MOD_ID = "mcqq";

    /** What every line the bridge prints to an operator starts with. */
    public static final String PREFIX = "[mc-qq]";

    /** The root command: {@code /qq}. */
    public static final String COMMAND = "qq";

    /**
     * Permission root. Sub-commands derive from their path, so {@code /qq status} is {@code mcqq.status} and
     * the root itself is {@code mcqq}. Declared (with its children) in the platform's descriptor.
     */
    public static final String PERMISSION_ROOT = "mcqq";

    /**
     * The level a mod platform falls back to when it has no permission nodes: vanilla op level 2. It lives here
     * as the single statement of intent; each platform spells it its own way, because they disagree on the type
     * — Bukkit has real permission nodes, Minecraft 26.1 names it {@code Commands.LEVEL_GAMEMASTERS} (a
     * {@code PermissionCheck}, not an int), and Forge-style APIs still take the number.
     */
    public static final int OP_LEVEL = 2;

    /** The config template that a first start writes out; packaged in this jar. */
    public static final String CONFIG_TEMPLATE = "/assets/mcqq/config.example.yml";

    private Constants() {
    }
}
