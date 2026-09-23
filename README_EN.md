<div align="right">
🌍English / <a href="README.md">中文</a>
</div>

<div align="center">

# MC ↔ QQ Bot

✨ Bridges a QQ group and a Minecraft server ✨

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

<p align="center">
  <a href="https://github.com/17TheWord/mcqq/releases">⬇️ Download</a>
  ·
  <a href="https://github.com/17TheWord/mcqq/issues">🐛 Report an issue</a>
</p>

## What it does

A server-side mod/plugin that talks to the official QQ Bot API v2 (the gateway of [qqbot-java-sdk](https://github.com/skiesworld/qqbot-java-sdk)).

QQ to Minecraft:

- Someone talks in the group, it shows up in chat: `§b[QQ group]§r name: text`
- Someone joins or leaves the group, one line is announced
- Images and other attachments are reported as a count (the official payload keeps text and attachments as sibling fields, with no position information)

Minecraft to QQ:

- Player chat, join, quit and death (with the killer's name) are posted to the configured groups
- Prefixed `[MC]`, with `§` colour codes stripped

Groups, text sub-channels of a QQ channel, and direct messages are all supported, each with its own direction switches and message templates.

You can also run server commands from QQ. It is off by default and needs an explicit permission setup. Commands go over RCON and the output is sent back to the group.

## Supported platforms

- Fabric 26.1 - 26.2: `mods/`, needs Fabric API
- NeoForge 26.1 - 26.2: `mods/`
- Forge 26.1 - 26.2: `mods/`
- Forge 1.20.1 - 1.20.2: `mods/`
- Paper / Folia / Purpur 1.20.1 - 26.2: `plugins/`
- Spigot / CraftBukkit 1.20.1 - 26.2: `plugins/`

One jar covers the whole Bukkit family from 1.20.1 up to 26.x, so you only pick Paper-family or Spigot-family. Install the wrong one and it prints the reason and disables itself instead of dropping or duplicating messages.

## Quick start

1. Download the jar for your platform from [Releases](https://github.com/17TheWord/mcqq/releases) and drop it in `mods/` or `plugins/`
2. Create a bot on the [QQ Open Platform](https://q.qq.com/) and copy its AppID and AppSecret
3. Start the server once, the config is written to `config/mcqq/config.yml` (Paper uses `plugins/mcqq/config.yml`)
4. Fill in three things:

```yaml
bots:
  - id: main
    app-id: "your AppID"
    secret: "your AppSecret"
    groups:
      - group-openid: "the group's openid"
        label: Main group
```

5. `/qq reload` to apply, then `/qq test` to post a test message to the group

You do not have to copy the group openid by hand. Leave `groups:` empty, start the server, @ the bot once in the group, then run `/qq bind` from the console. The plugin writes the group that spoke last into the config and reloads itself. Sub-channels work the same way and the `guild-id` is written along with it.

`/qq bind` only accepts a target the bot has actually received a message from, so a typo cannot wire your chat box to a stranger's group.

## Configuration

The live config is `config.yml`, and the annotated reference is `config.example.yml`.

Upgrades never make you edit the file by hand. If keys a new version added are missing, the plugin backs the file up to `.bak`, adds them with their defaults, and says so in the log.

The AppSecret lives in `config.yml`. Do not paste that file into an issue or a group chat, paste `config.example.yml` instead. You can also replace `secret:` with `secret-env: QQ_BOT_SECRET` to read it from an environment variable.

The switches you will actually touch:

```yaml
      - group-openid: "xxxxxxxx"
        label: Main group
        receive-from-qq: true
        send-to-qq: [chat, join, quit, death]
```

`send-to-qq: []` means incoming only. `debug: true` writes every "why was this not forwarded" decision to the log, turn it on first when a message does not reach QQ.

## Commands

Requires OP level 2 or the `mcqq` permission.

- `/qq status`: each bot's connection state, its own id, the bound groups and directions, plus any problems found in the config
- `/qq reload`: re-read the config and restart the running bots
- `/qq bind [first few chars of the id]`: bind a group or sub-channel the bot has heard from
- `/qq templates`: the message templates currently in effect and the available placeholders
- `/qq test`: post a test message to every configured group
- `/qq run <command>`: run a command on the server and print its output
- `/qq help`: list every sub-command

Bad credentials never hold up the server. A bot that fails to start is reported in the status and the log, fix the config and `/qq reload`.

## Message templates

Every message's shape lives in `templates:`, so changing wording does not need a new release.

```yaml
templates:
  qq-chat: "§b[QQ {group}]§r {user}§7:§r {text}"
  mc-chat: "[MC] {player}: {text}"
  mc-join: "[MC] {player} joined the game"
  mc-death: "[MC] {player} died (killed by {killer})"
```

Placeholders: `{group}` `{user}` `{text}` `{count}` `{member}` `{player}` `{killer}` `{platform}` `{time}`

A misspelled placeholder is left as-is so you can see the typo. An empty string means do not announce. Precedence is group over global over built-in default, and a group only writes the keys it wants to override.

## Notes

- Outgoing messages use the platform's proactive messages, which are throttled and may be sent for review. Failures are logged only, the plugin never blocks a server tick and never lets chat fail because of QQ.
- Command output prefers a passive reply, which does not spend the proactive quota. When that is not possible it falls back to a proactive message, so if you disable "allow proactive messages" in the QQ client a command still runs but you will not see its output.
- QQ events run on their own threads, so a slow QQ never stalls the server and a busy server never queues up QQ.

## For developers

`core/` does not know Minecraft exists, so it compiles and tests without the game. 103 test cases, all offline.

- [docs/VERSIONS.md](docs/VERSIONS.md): what to do when a new Minecraft version lands
- [docs/MULTIPLATFORM.md](docs/MULTIPLATFORM.md): per-platform build facts, the layout, and the traps
- [docs/COMMANDS.md](docs/COMMANDS.md): the three conversation kinds, their fields and limits, the permission model, why commands go over RCON
- [docs/TEMPLATES.md](docs/TEMPLATES.md): templates, placeholders and config precedence
- [docs/RELEASING.md](docs/RELEASING.md): the release flow and the shape of CI
- [THIRD-PARTY.md](THIRD-PARTY.md): bundled third-party components and licences

```bash
./gradlew build                    # core + fabric + neoforge + bukkit
./gradlew build -PwithForge=true   # when you need Forge
```

## License

This project is [MIT](LICENSE). The jars bundle other people's code (the official QQ SDK, OkHttp, Gson, SnakeYAML, Kotlin, all Apache-2.0). The list and the full licence text are in [THIRD-PARTY.md](THIRD-PARTY.md).
