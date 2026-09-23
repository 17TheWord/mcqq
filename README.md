<div align="right">

🌍 <a href="README_EN.md">English</a> / 中文

</div>

<div align="center">

<h1>MC ↔ QQ Bot</h1>

<p>✨ 将 QQ 与 Minecraft 服务端连接起来 ✨</p>

</div>

<p align="center">

<a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-green" alt="license"></a> <a href="https://github.com/17TheWord/mcqq/releases"><img src="https://img.shields.io/github/v/release/17TheWord/mcqq" alt="release"></a> <img src="https://img.shields.io/badge/Minecraft-1.20.1--26.2-blue" alt="minecraft">

</p>

<p align="center">

<a href="https://fabricmc.net"><img src="https://img.shields.io/badge/Fabric-supported-blue" alt="fabric"></a> <a href="https://neoforged.net"><img src="https://img.shields.io/badge/NeoForge-supported-blue" alt="neoforge"></a> <a href="https://files.minecraftforge.net"><img src="https://img.shields.io/badge/Forge-supported-blue" alt="forge"></a> <a href="https://papermc.io"><img src="https://img.shields.io/badge/Paper-supported-blue" alt="paper"></a> <a href="https://www.spigotmc.org"><img src="https://img.shields.io/badge/Spigot-supported-blue" alt="spigot"></a>

</p>

<p align="center">

<a href="https://github.com/17TheWord/mcqq/releases">⬇️ 下载</a>
· <a href="https://github.com/17TheWord/mcqq/issues">🐛 反馈问题</a>

</p>

## 介绍

`MC ↔ QQ Bot` 是一个 Minecraft 服务端 Mod / Plugin，通过 QQ 官方机器人 API v2 将 QQ 与 Minecraft 服务端连接起来。

项目基于 [`qqbot-java-sdk`](https://github.com/skiesworld/qqbot-java-sdk) 接入 QQ 开放平台，支持 QQ 群、频道文字子频道和私聊，并可以分别配置不同目标的消息收发方向。

**QQ → Minecraft**

* QQ 聊天事件
* 群成员加入 / 退出事件
* 消息附件数量提示
* 群、频道文字子频道、私聊分别配置

消息可以通过模板自定义，例如：

```text
§b[QQ 主群]§r 用户名§7:§r Hello
```

**Minecraft → QQ**

支持以下 Minecraft 服务端事件：

* 玩家聊天事件
* 玩家加入事件
* 玩家退出事件
* 玩家死亡事件

不同群或频道可以分别配置需要接收的事件类型。

**QQ → Minecraft 命令**

可以在 QQ 中执行 Minecraft 服务端命令。

命令执行默认关闭，需要显式配置权限。命令通过 RCON 发送到服务器，执行结果返回 QQ。

## 支持的平台

| 平台                     | Minecraft 版本    | 安装方式                  |
| ---------------------- | --------------- | --------------------- |
| Fabric                 | 26.1 - 26.2     | `mods/`，需要 Fabric API |
| NeoForge               | 26.1 - 26.2     | `mods/`               |
| Forge                  | 26.1 - 26.2     | `mods/`               |
| Forge                  | 1.20.1 - 1.20.2 | `mods/`               |
| Paper / Folia / Purpur | 1.20.1 - 26.2   | `plugins/`            |
| Spigot / CraftBukkit   | 1.20.1 - 26.2   | `plugins/`            |

Bukkit 系列使用同一份 JAR，覆盖 Minecraft 1.20.1 至 26.x。

Paper 系和 Spigot 系请选择对应的平台安装。检测到平台不匹配时，插件会在日志中说明原因并停用自身。

## 快速开始

从 [Releases](https://github.com/17TheWord/mcqq/releases) 下载对应平台的版本：

* Mod 放入 `mods/`
* Plugin 放入 `plugins/`

前往 [QQ 开放平台](https://q.qq.com/) 创建机器人，获取 `AppID` 和 `AppSecret`。

首次启动服务器后，配置文件会自动生成：

```text
config/mcqq/config.yml
```

Paper 系平台使用：

```text
plugins/mcqq/config.yml
```

填入机器人信息和需要绑定的目标：

```yaml
bots:
  - id: main
    app-id: "你的 AppID"
    secret: "你的 AppSecret"
    groups:
      - group-openid: "群的 openid"
        label: MC 主群
```

修改配置后执行：

```text
/qq reload
```

然后使用：

```text
/qq test
```

向配置中的目标发送测试消息。

### 自动绑定

不需要手动填写 `group-openid`。

首次使用时，可以先将 `groups:` 留空。机器人启动后，在目标群中 @ 一次机器人，然后执行：

```text
/qq bind
```

插件会从最近收到的消息中找到对应目标，将群信息写入配置并自动重载。

子频道使用相同方式绑定，同时会保存对应的 `guild-id`。

`/qq bind` 只接受机器人实际收到过消息的目标，因此不会因为输入错误而绑定到陌生群组或频道。

## 配置

主要配置文件为 `config.yml`，完整的带注释配置示例见 `config.example.yml`。

升级时，如果发现配置文件缺少新增项，插件会先创建 `.bak` 备份，再自动补齐缺失配置，并在日志中记录新增的配置项。

AppSecret 可以直接写入配置：

```yaml
secret: "你的 AppSecret"
```

也可以使用环境变量：

```yaml
secret-env: QQ_BOT_SECRET
```

不要将包含真实 `AppSecret` 的 `config.yml` 发布到 Issue、群聊或公开仓库。如需提交配置，请使用 `config.example.yml`。

每个目标都可以独立配置消息方向：

```yaml
- group-openid: "xxxxxxxx"
  label: MC 主群
  receive-from-qq: true
  send-to-qq: [chat, join, quit, death]
```

`send-to-qq: []` 表示只接收 QQ 消息，不向该目标发送 Minecraft 事件。

`debug: true` 会记录消息处理和转发过程中的详细信息。遇到消息没有成功转发时，可以开启此选项查看原因。

## 命令

> 需要 OP 2 级或 `mcqq` 权限。

* 查看 Bot 在线状态、自身 ID、已绑定目标、消息方向以及当前配置问题。

  ```text
  /qq status
  ```

* 重新加载配置，并重新初始化正在运行的 Bot。

  ```text
  /qq reload
  ```

* 绑定机器人最近收到消息的群或子频道，可使用目标 ID 的前几位进行匹配。

  ```text
  /qq bind [id 前几位]
  ```

* 查看当前生效的消息模板和可用占位符。

  ```text
  /qq templates
  ```

* 向所有已配置目标发送一条测试消息。

  ```text
  /qq test
  ```

* 通过 RCON 执行 Minecraft 服务端命令，并返回执行结果。

  ```text
  /qq run <命令>
  ```

* 查看所有可用的子命令。

  ```text
  /qq help
  ```

## 消息模板

消息格式由 `templates:` 配置：

```yaml
templates:
  qq-chat: "§b[QQ {group}]§r {user}§7:§r {text}"
  mc-chat: "[MC] {player}: {text}"
  mc-join: "[MC] {player} 加入了世界"
  mc-death: "[MC] {player} 死亡（{killer}）"
```

可用占位符：

| 占位符          | 含义            |
| ------------ | ------------- |
| `{group}`    | QQ 群或频道名称     |
| `{user}`     | QQ 用户名        |
| `{text}`     | 消息内容          |
| `{count}`    | 附件或成员数量       |
| `{member}`   | 成员名称          |
| `{player}`   | Minecraft 玩家名 |
| `{killer}`   | 凶手名称          |
| `{platform}` | 来源平台          |
| `{time}`     | 时间            |

未识别的占位符会原样保留，便于发现配置错误。

模板配置优先级：

```text
群级配置 > 全局配置 > 内置默认值
```

群级配置只需要填写需要覆盖的项目。

将模板设置为空字符串，可以关闭对应事件的消息。

## 运行说明

向 QQ 发送消息使用平台主动消息能力，因此可能受到平台频率限制或审核机制影响。发送失败只记录日志，不会影响 Minecraft 侧事件处理。

命令回显优先使用被动回复，以减少主动消息额度消耗。无法使用被动回复时，插件会尝试发送主动消息。

## 开发

项目将与 Minecraft 无关的核心逻辑放在 `core/` 中，可以独立编译和测试。

当前核心测试共 103 个用例，均可在离线环境下运行。

相关文档：

* [版本支持](docs/VERSIONS.md)：新增 Minecraft 版本时需要处理的内容
* [多平台实现](docs/MULTIPLATFORM.md)：平台结构、构建方式以及多版本支持说明
* [命令与权限](docs/COMMANDS.md)：QQ / Minecraft 两侧的字段、限制和命令执行机制
* [消息模板](docs/TEMPLATES.md)：模板、占位符和配置层级
* [发布流程](docs/RELEASING.md)：项目发布与 CI 流程
* [第三方组件](THIRD-PARTY.md)：打包组件及其许可证

构建核心以及常用平台：

```bash
./gradlew build
```

需要同时构建 Forge：

```bash
./gradlew build -PwithForge=true
```

## 开源许可

本项目使用 [MIT](LICENSE) 许可证。

项目产物中包含第三方组件，包括 QQ 官方 SDK、OkHttp、Gson、SnakeYAML 和 Kotlin 等，相关组件均遵循各自的开源许可证。

完整清单和许可证文本见 [THIRD-PARTY.md](THIRD-PARTY.md)。
