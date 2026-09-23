<div align="right">
🌍<a href="README_EN.md">English</a> / 中文
</div>

<div align="center">

# MC ↔ QQ Bot

✨ 把 QQ 群和 Minecraft 服务端接起来 ✨

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
  <a href="https://github.com/17TheWord/mcqq/releases">⬇️ 下载</a>
  ·
  <a href="https://github.com/17TheWord/mcqq/issues">🐛 反馈问题</a>
</p>

## 介绍

服务端 mod / 插件，走 QQ 官方机器人 API v2（[qqbot-java-sdk](https://github.com/skiesworld/qqbot-java-sdk) 的网关）。

QQ 到 Minecraft：

- 群里有人说话，播进聊天栏：`§b[QQ 群名]§r 昵称: 文本`
- 有人进群退群，播一行提示
- 图片等附件只报数量（官方 payload 里文本和附件是平级字段，没有位置信息）

Minecraft 到 QQ：

- 玩家聊天、进服、退服、死亡（带凶手名）发到配置的群
- 前缀 `[MC]`，发送前去掉 `§` 颜色码

群、频道的文字子频道、私聊都支持，各自可以单独配收发方向和文案。

还可以在 QQ 里执行服务器命令，默认关着，要显式配权限。命令走 RCON，回显发回群里。

## 支持的平台

- Fabric 26.1 - 26.2：放 `mods/`，需要 Fabric API
- NeoForge 26.1 - 26.2：放 `mods/`
- Forge 26.1 - 26.2：放 `mods/`
- Forge 1.20.1 - 1.20.2：放 `mods/`
- Paper / Folia / Purpur 1.20.1 - 26.2：放 `plugins/`
- Spigot / CraftBukkit 1.20.1 - 26.2：放 `plugins/`

Bukkit 那一族一份 jar 覆盖 1.20.1 到 26.x，按 Paper 系或 Spigot 系二选一就行。装错了它会打印原因并停用自己，不会少转发或者重复转发。

## 快速开始

1. 从 [Releases](https://github.com/17TheWord/mcqq/releases) 下载对应平台那份，放进 `mods/` 或 `plugins/`
2. 在 [QQ 开放平台](https://q.qq.com/) 建一个机器人，拿到 AppID 和 AppSecret
3. 启动一次服务器，配置会写在 `config/mcqq/config.yml`（Paper 侧是 `plugins/mcqq/config.yml`）
4. 填三个地方：

```yaml
bots:
  - id: main
    app-id: "你的 AppID"
    secret: "你的 AppSecret"
    groups:
      - group-openid: "群的 openid"
        label: MC 主群
```

5. `/qq reload` 生效，然后 `/qq test` 往群里发一条测试消息

群的 openid 不用手抄。先把 `groups:` 留空，开服后在群里 @ 一次机器人，然后在控制台敲 `/qq bind`，插件会把最近说话的那个群写进配置并自动重载。子频道同理，它会连 `guild-id` 一起写上。

`/qq bind` 只接受机器人真的收到过消息的目标，所以打错字也不会把聊天栏接到陌生的地方去。

## 配置

真正生效的是 `config.yml`，带注释的说明在 `config.example.yml`。

升级不用你手改文件。发现少了新增的键，插件会先备份成 `.bak`，再把缺的键带默认值补进去，并在日志里说清补了什么。

AppSecret 就写在 `config.yml` 里，别把这个文件贴到 issue 或群里，要贴就贴 `config.example.yml`。也可以把 `secret:` 换成 `secret-env: QQ_BOT_SECRET` 从环境变量读。

常用的几个开关：

```yaml
      - group-openid: "xxxxxxxx"
        label: MC 主群
        receive-from-qq: true
        send-to-qq: [chat, join, quit, death]
```

`send-to-qq` 写 `[]` 就是只进不出。`debug: true` 会把每一步为什么没转发写进日志，消息没到 QQ 时先开它。

## 命令

需要 OP 2 级或者 `mcqq` 权限。

- `/qq status`：每个 bot 的在线状态、自身 id、绑定的群与方向，以及配置里读出来的问题
- `/qq reload`：重读配置，换掉正在跑的 bot
- `/qq bind [id 前几位]`：把机器人收到过消息的群或子频道绑上
- `/qq templates`：当前生效的消息模板和可用占位符
- `/qq test`：往每个配置的群各发一条测试消息
- `/qq run <命令>`：在服务端跑一条命令并打印回显
- `/qq help`：列出所有子命令

凭证不对不会拖住服务器，bot 起不来只记在状态和日志里，修好配置 `/qq reload` 即可。

## 消息模板

每条消息长什么样都写在 `templates:` 里，改文案不用等新版本。

```yaml
templates:
  qq-chat: "§b[QQ {group}]§r {user}§7:§r {text}"
  mc-chat: "[MC] {player}: {text}"
  mc-join: "[MC] {player} 加入了世界"
  mc-death: "[MC] {player} 死亡（{killer}）"
```

可用的占位符：`{group}` `{user}` `{text}` `{count}` `{member}` `{player}` `{killer}` `{platform}` `{time}`

写错的占位符原样显示，一眼能看出拼错了。空字符串就是不播报。优先级是群级大于全局大于内置默认，群级只写要覆盖的键。

## 说明

- 发到 QQ 走的是平台的主动消息，会被频控或送去审核。失败只记日志，不会卡服务器，也不会让聊天因为 QQ 而失败。
- 命令回显优先走被动回复，不占额度。走不通时改用主动消息，所以如果你在 QQ 客户端里关掉了允许主动发送，命令会执行但收不到回显。
- QQ 的事件跑在自己的线程上，QQ 慢不卡服务器，服务器忙也不排队卡 QQ。

## 开发者

`core/` 不认识 Minecraft，可以脱离游戏编译和测试，103 个用例全离线。

- [docs/VERSIONS.md](docs/VERSIONS.md)：加一个新 MC 版本要做什么
- [docs/MULTIPLATFORM.md](docs/MULTIPLATFORM.md)：每个平台的构建事实、结构，以及加平台加版本时的坑
- [docs/COMMANDS.md](docs/COMMANDS.md)：三个面的字段与限额、权限模型、命令执行为什么走 RCON
- [docs/TEMPLATES.md](docs/TEMPLATES.md)：消息模板、占位符与配置层级
- [docs/RELEASING.md](docs/RELEASING.md)：发布流程和 CI 的形状
- [THIRD-PARTY.md](THIRD-PARTY.md)：打包进去的第三方组件与许可证

```bash
./gradlew build                    # core + fabric + neoforge + bukkit
./gradlew build -PwithForge=true   # 需要 Forge 时
```

## 开源许可

本项目使用 [MIT](LICENSE) 作为开源许可证。产物里打包了别人的代码（QQ 官方 SDK、OkHttp、Gson、SnakeYAML、Kotlin 等，都是 Apache-2.0），清单和许可证全文在 [THIRD-PARTY.md](THIRD-PARTY.md)。
