<div align="right">
🌍English / <a href="README.md">中文</a>
</div>

<div align="center">

# MC ↔ QQ Bot

✨ A Minecraft server mod/plugin that bridges your world and a QQ bot — group chat in the chat box, player events out to the group ✨

</div>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="license"></a>
  <a href="https://github.com/17TheWord/mcqq/releases"><img src="https://img.shields.io/github/v/release/17TheWord/mcqq" alt="release"></a>
  <img src="https://img.shields.io/badge/Minecraft-1.20.1--26.2-blue" alt="minecraft">
</p>

<p align="center">
  <a href="https://fabricmc.net"><img src="https://img.shields.io/badge/Fabric-supported-blue" alt="fabric"></a>
  <a href="https://neoforged.net"><img src="https://img.shields.io/badge/NeoForge-supported-blue" alt="neoforge"></a>
  <a href="https://files.minecraftforge.net"><img src="https://img.shields.io/badge/Forge-supported-blue" alt="forge"></a>
  <a href="https://papermc.io"><img src="https://img.shields.io/badge/Paper-supported-blue" alt="paper"></a>
  <a href="https://www.spigotmc.org"><img src="https://img.shields.io/badge/Spigot-supported-blue" alt="spigot"></a>
</p>

## What it does

A server-side mod/plugin that connects QQ and Minecraft in both directions, over the
**official QQ Bot API v2** (the gateway of [qqbot-java-sdk](https://github.com/skiesworld/qqbot-java-sdk)).

**QQ → Minecraft**

* Someone talks in the group → it shows up in chat: `§b[QQ group]§r name: text`
* Someone joins / leaves the group → `[QQ group] name joined the group`
* Images and other attachments are reported as a count (the official payload keeps text and
  attachments as sibling fields, with no position information)

**Minecraft → QQ**

* Player chat, join, quit and death (with the killer's name) → posted to the configured groups
* Prefixed `[MC]`, with `§` colour codes stripped before sending

**Three kinds of conversation**: QQ groups, **text sub-channels** of a QQ channel, and **direct messages**.
Each one gets its own direction switches, its own message templates and its own command permissions.

**Remote command execution (optional, off by default)**: a message in QQ can run a command on the
server and send the output back. It is the only path that lets QQ affect the server, so it is off
unless you configure it — and it needs an explicit permission setup.

## Supported platforms

| Platform | Minecraft versions | Where it goes |
| --- | --- | --- |
| Fabric | 26.1 – 26.2 | `mods/` (needs Fabric API) |
| NeoForge | 26.1 – 26.2 | `mods/` |
| Forge | 26.1 – 26.2 | `mods/` |
| Forge | 1.20.1 – 1.20.2 | `mods/` |
| Paper / **Folia** / Purpur | **1.20.1 – 26.2** | `plugins/` |
| Spigot / CraftBukkit | **1.20.1 – 26.2** | `plugins/` |

**One jar covers the whole Bukkit family from 1.20.1 up to 26.x** (compiling against the oldest
target is what lets it run on everything above), so you only pick "Paper-family" or "Spigot-family".
**Install the wrong one and it tells you** — on the wrong server it prints the reason and disables
itself instead of silently dropping or duplicating messages.

## Quick start

1. Download the jar for your platform from [Releases](https://github.com/17TheWord/mcqq/releases)
   and drop it in `mods/` or `plugins/`
2. Create a bot on the [QQ Open Platform](https://q.qq.com/) and copy its **AppID** and **AppSecret**
3. Start the server once — the config is written to:
   * Fabric / NeoForge / Forge: `config/mcqq/config.yml`
   * Paper / Spigot: `plugins/mcqq/config.yml`
4. Fill in three things:

   ```yaml
   bots:
     - id: main
       app-id: "your AppID"
       secret: "your AppSecret"
       groups:
         - group-openid: "the group's openid"   # not the QQ group number
           label: Main group
   ```

5. `/qq reload` to apply, then `/qq test` to post a test message to every configured group —
   that is how you check the credentials and the group openid

**You do not have to copy the group openid by hand.** Leave `groups:` empty, start the server,
@ the bot once in the group, then run `/qq bind` from the console: the plugin writes the group
that spoke last into `config.yml` and reloads itself. Sub-channels work the same way (it writes
the `guild-id` along with the `channel-id`). You can also fill them in by hand — the ids show up
in the server log and in `/qq status`.

> `/qq bind` only accepts a target the bot has **actually received a message from**, so a typo
> cannot wire your chat box to a stranger's group.

## Configuration

The live config is `config.yml`; the annotated reference is **`config.example.yml`** (the template
shipped in the jar, refreshed on every start).

**Upgrades never make you edit the file by hand.** If your `config.yml` is missing keys a new
version added, the plugin backs the file up to `.bak`, adds the missing keys with their defaults,
and says so in the log.

⚠️ **The AppSecret lives in `config.yml`** — do not paste that file into an issue or a group chat.
Paste `config.example.yml` from the same folder instead (it is the template, without your secret).
You can also replace `secret:` with `secret-env: QQ_BOT_SECRET` to read it from an environment variable.

The switches you will actually touch:

```yaml
      - group-openid: "xxxxxxxx"
        label: Main group
        receive-from-qq: true                    # QQ → Minecraft
        send-to-qq: [chat, join, quit, death]    # Minecraft → QQ; use [] for "incoming only"
```

* `debug: true` writes every "why was this not forwarded" decision to the log — turn it on first
  when a message does not reach QQ
* One bot can have many groups; you can also run several bots (each gets its own connection)

## Commands

Requires OP level 2 or the `mcqq` permission (Brigadier with completion on Fabric, `plugin.yml`
on Paper).

| Command | What it does |
| --- | --- |
| `/qq status` | Each bot's connection state, its own id, the bound groups and directions, plus any problems found in the config |
| `/qq reload` | Re-read the config and restart the running bots |
| `/qq bind [first few chars of the id]` | Bind a group / sub-channel the bot has heard from (writes `config.yml` and reloads) |
| `/qq templates` | The message templates currently in effect, and the available placeholders |
| `/qq test` | Post a test message to every configured group, to check credentials and group openids |
| `/qq run <command>` | Run a command on the server and print its output (same path QQ uses — handy for diagnosing) |
| `/qq help` | List every sub-command |

**Bad credentials never hold up the server**: a bot that fails to start is reported in
`/qq status` and the log. Fix the config and `/qq reload` — no restart needed.

## Message templates: the wording is configuration, not code

Every message's shape lives in the config's `templates:`, so changing wording does not need a new release:

```yaml
templates:
  qq-chat: "§b[QQ {group}]§r {user}§7:§r {text}"     # QQ → Minecraft
  mc-chat: "[MC] {player}: {text}"                   # Minecraft → QQ
  mc-join: "[MC] {player} joined the game"
  mc-death: "[MC] {player} died (killed by {killer})"
```

* **Placeholders**: `{group}` `{user}` `{text}` `{count}` `{member}` `{player}` `{killer}` `{platform}` `{time}`
* A misspelled placeholder is left **as-is** (so you can see the typo), and is reported in `/qq status`
* **An empty string means "do not announce"** (`mc-join: ""` turns off join messages)
* **Precedence: group > global > built-in default**; a group only writes the keys it wants to override

## Good to know

* **Outgoing messages are rate-limited.** Minecraft → QQ uses the platform's *proactive* messages,
  which are throttled and may be sent for review. Failures and review are logged only — the plugin
  **never blocks a server tick waiting on QQ, and never lets chat fail because of QQ**.
* **Command output prefers a passive reply** (which does not spend the proactive quota). When a
  passive reply is not possible it falls back to a proactive message — so if you disable
  "allow proactive messages" in the QQ client, a command still runs but you will not see its output.
* **QQ events run on their own threads**: a slow QQ never stalls the server, and a busy server
  never queues up QQ.

## For developers

This repository is "**core + one adapter per platform**": `core/` does not know Minecraft exists,
so it compiles and tests without the game (103 test cases, all offline).

| Document | What it covers |
| --- | --- |
| [docs/VERSIONS.md](docs/VERSIONS.md) | What to do when a new Minecraft version lands |
| [docs/MULTIPLATFORM.md](docs/MULTIPLATFORM.md) | Per-platform build facts, the layout, and the traps |
| [docs/COMMANDS.md](docs/COMMANDS.md) | The three conversation kinds, their fields and limits, the permission model, why commands go over RCON |
| [docs/TEMPLATES.md](docs/TEMPLATES.md) | Templates, placeholders and config precedence |
| [docs/RELEASING.md](docs/RELEASING.md) | The release flow, the shape of CI, and what to touch when adding a platform |
| [THIRD-PARTY.md](THIRD-PARTY.md) | Bundled third-party components and licences |

```bash
./gradlew build                    # core + fabric + neoforge + bukkit
./gradlew build -PwithForge=true   # when you need Forge (this is what CI passes)
```

## License

The code is **MIT** (see [LICENSE](LICENSE)). The jars **bundle other people's code**
(the official QQ SDK, OkHttp, Gson, SnakeYAML, Kotlin — all Apache-2.0); the list is in
[THIRD-PARTY.md](THIRD-PARTY.md), and both files are packed into the jar as well.
