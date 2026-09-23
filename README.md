<div align="right">
🌍<a href="README_EN.md">English</a> / 中文
</div>

<div align="center">

# MC ↔ QQ Bot

✨ 把 QQ 官方机器人接进 Minecraft —— 群消息播进聊天栏，玩家聊天 / 进服 / 退服 / 死亡发到群里 ✨

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

## 介绍

一个服务端 mod / 插件，把 QQ 和 Minecraft 双向接起来，走 **QQ 官方机器人 API v2**（[qqbot-java-sdk](https://github.com/skiesworld/qqbot-java-sdk) 的网关）。

**QQ → Minecraft**

* 群里有人说话 → 播进聊天栏：`§b[QQ 群名]§r 昵称: 文本`
* 群里有人进群 / 退群 → `[QQ 群名] xxx 进了群`
* 图片等附件只报数量（官方 payload 里文本与附件是平级字段，没有位置信息）

**Minecraft → QQ**

* 玩家聊天、进服、退服、死亡（带凶手名）→ 发到配置的群
* 前缀 `[MC]`，发送前去掉 `§` 颜色码

**三个面都支持**：群、**频道的文字子频道**、**私聊**。各自可以单独配收发方向、单独配文案、单独配谁能用命令。

**命令执行（可选，默认关）**：在 QQ 里发一条消息就能让服务器跑命令，回显发回群里。
这是唯一一条"从 QQ 能影响服务器"的路，所以默认关着，且要显式配权限。

## 支持的平台与版本

| 平台 | 覆盖的 MC 版本 | 装到哪 |
| --- | --- | --- |
| Fabric | 26.1 – 26.2 | `mods/`（需要 Fabric API） |
| NeoForge | 26.1 – 26.2 | `mods/` |
| Forge | 26.1 – 26.2 | `mods/` |
| Forge | 1.20.1 – 1.20.2 | `mods/` |
| Paper / **Folia** / Purpur | **1.20.1 – 26.2** | `plugins/` |
| Spigot / CraftBukkit | **1.20.1 – 26.2** | `plugins/` |

**Bukkit 那一族一份 jar 覆盖 1.20.1 一直到 26.x**（编译对最老的目标就能一路往上跑），
所以只需要按"Paper 系 / Spigot 系"二选一。**装错了它会明确告诉你** —— 在不对的服务端上会打印原因并停用自己，不会静默地少转发或重复转发。

## 快速开始

1. 从 [Releases](https://github.com/17TheWord/mcqq/releases) 下载对应平台的那份 jar，放进 `mods/` 或 `plugins/`
2. 在 [QQ 开放平台](https://q.qq.com/) 建一个机器人，拿到 **AppID** 与 **AppSecret**
3. 启动一次服务器 —— 会写出配置：
   * Fabric / NeoForge / Forge：`config/mcqq/config.yml`
   * Paper / Spigot：`plugins/mcqq/config.yml`
4. 填三个地方：

   ```yaml
   bots:
     - id: main
       app-id: "你的 AppID"
       secret: "你的 AppSecret"
       groups:
         - group-openid: "群的 openid"     # 不是 QQ 群号
           label: MC 主群
   ```

5. `/qq reload` 让配置生效，然后 `/qq test` 往群里发一条测试消息，验证凭证与群 openid

**群的 openid 不用手抄**：先把 `groups:` 留空，开服后在群里 @ 一次机器人，然后在控制台敲 `/qq bind` ——
插件会把最近说话的那个群写进 `config.yml` 并自动重载。子频道同理（它会连 `guild-id` 一起写上）。
也可以手填：那些 id 会出现在服务端日志和 `/qq status` 里。

> `/qq bind` 只接受**机器人真的收到过消息**的目标 —— 所以打错字也不会把聊天栏接到陌生的地方去。

## 配置

真正的配置是 `config.yml`，带全部注释的说明在 **`config.example.yml`**（打包的模板副本，每次启动刷新）。

**升级不会让你手改文件**：插件发现你的 `config.yml` 少了新增的键，会先备份成 `.bak`，
再把缺的键（带默认值）补进去，并在日志里说清补了什么。

⚠️ **AppSecret 就写在 `config.yml` 里** —— 别把这个文件贴到 issue 或群里。要贴就贴同目录的 `config.example.yml`（模板，没有你的密钥）。
也可以把 `secret:` 换成 `secret-env: QQ_BOT_SECRET`，从环境变量读。

几个常用开关：

```yaml
      - group-openid: "xxxxxxxx"
        label: MC 主群
        receive-from-qq: true              # QQ → MC
        send-to-qq: [chat, join, quit, death]   # MC → QQ；写 [] 就是只进不出
```

* `debug: true` 会把"为什么不转发"的每一步写进日志 —— 消息没到 QQ 时先开它
* 一个 bot 可以配多个群；也可以配多个 bot（QQ 侧每个 bot 一条独立连接）

## 命令

需要 OP 2 级或 `mcqq` 权限（Fabric 走 Brigadier 带补全，Paper 走 `plugin.yml`）。

| 命令 | 作用 |
| --- | --- |
| `/qq status` | 每个 bot 的在线状态、自身 id、绑定的群与方向，以及配置里读出来的问题 |
| `/qq reload` | 重读配置，换掉正在跑的 bot |
| `/qq bind [id 前几位]` | 把机器人收到过消息的群 / 子频道绑上（写进 `config.yml` 并重载） |
| `/qq templates` | 当前生效的消息模板、可用占位符 |
| `/qq test` | 往每个配置的群各发一条测试消息，验证凭证与群 openid |
| `/qq run <命令>` | 在服务端跑一条命令并打印回显（和 QQ 侧走同一条路，用来排查） |
| `/qq help` | 列出所有子命令 |

**凭证不对不会拖住服务器**：bot 起不来只记在 `/qq status` 与日志里，修好配置后 `/qq reload` 即可。

## 消息模板：文案是配置，不是代码

每条消息长什么样都写在配置的 `templates:` 里，改文案不用等新版本：

```yaml
templates:
  qq-chat: "§b[QQ {group}]§r {user}§7:§r {text}"     # QQ → MC
  mc-chat: "[MC] {player}: {text}"                   # MC → QQ
  mc-join: "[MC] {player} 加入了世界"
  mc-death: "[MC] {player} 死亡（{killer}）"
```

* **可用占位符**：`{group}` `{user}` `{text}` `{count}` `{member}` `{player}` `{killer}` `{platform}` `{time}`
* 写错的占位符**原样显示**（一眼看出拼错），加载时也会报进 `/qq status`
* **空字符串 = 不播报**（`mc-join: ""` 就是不报进服）
* **优先级：群级 > 全局 > 内置默认**，群级只写要覆盖的键

## 几点说明

* **主动消息有限额**：MC → QQ 走的是平台的主动消息，会被频控或送去审核。
  失败或进审核只记日志，**绝不在 tick 里等待，也绝不让聊天因为 QQ 而失败**。
* **命令回显优先走被动回复**（不占额度）。被动回复走不通时自动改用主动消息 ——
  所以如果你在 QQ 客户端里关掉了「允许主动发送」，命令会执行但收不到回显。
* **QQ 的事件跑在自己的线程上**：QQ 慢不卡服务器，服务器繁忙也不排队卡 QQ。

## 开发者

这个仓库是"**core + 每个平台一个 adapter**"的结构：`core/` 不认识 Minecraft，可以脱离游戏编译与测试（103 个用例，全离线）。

| 文档 | 是什么 |
| --- | --- |
| [docs/VERSIONS.md](docs/VERSIONS.md) | 加一个新 MC 版本要做什么 |
| [docs/MULTIPLATFORM.md](docs/MULTIPLATFORM.md) | 每个平台的构建事实、结构，以及加平台/加版本时的坑 |
| [docs/COMMANDS.md](docs/COMMANDS.md) | 三个面的字段与限额、权限模型、命令执行为什么走 RCON |
| [docs/TEMPLATES.md](docs/TEMPLATES.md) | 消息模板、占位符与配置层级 |
| [docs/RELEASING.md](docs/RELEASING.md) | 发布流程、CI 的形状，以及加平台/窗口要动哪几处 |
| [THIRD-PARTY.md](THIRD-PARTY.md) | 打包进去的第三方组件与许可证 |

```bash
./gradlew build                    # core + fabric + neoforge + bukkit
./gradlew build -PwithForge=true   # 需要 Forge 时（CI 就是这么调的）
```

## 许可证

代码是 **MIT**（见 [LICENSE](LICENSE)）。产物里**打包了别人的代码**（QQ 官方 SDK、OkHttp、Gson、SnakeYAML、Kotlin 等，都是 Apache-2.0），
清单在 [THIRD-PARTY.md](THIRD-PARTY.md)，两份文件也都打进了 jar。
