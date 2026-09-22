# mc-qq — Minecraft ↔ QQ 官方机器人桥（Fabric，Minecraft 26.1.2）

> 纯 `Vibe` 项目，少量 `Review` 凑合用。

服务端 Fabric mod：把群聊天、进群退群从 QQ 搬进 Minecraft 聊天栏，把玩家聊天、进服退服、死亡按配置广播到 QQ 群。
QQ 侧走 [qqbot-java-sdk](https://github.com/skiesworld/qqbot-java-sdk) 0.0.4 的网关连接，配置在 `config/mcqq/config.yml`，
改完 `/qq reload` 生效。

## 文档在哪

| 文件 | 是什么 |
| --- | --- |
| [docs/VERSIONS.md](docs/VERSIONS.md) | **加一个新 MC 版本要做什么**：三种情况、具体步骤、什么时候才需要加"版本窗口"子项目。 |
| [docs/MULTIPLATFORM.md](docs/MULTIPLATFORM.md) | 每个平台的构建事实、结构，以及加平台/加版本时要知道的坑。 |
| [docs/TEMPLATES.md](docs/TEMPLATES.md) | 消息模板、占位符与配置层级；含第三方占位符库怎么接。 |
| [THIRD-PARTY.md](THIRD-PARTY.md) | 打包进去的第三方组件与许可证。 |
| [LICENSE](LICENSE) | MIT。 |

## 工程结构：core + 每个平台一个 adapter

**一个平台一个目录，平台下面按"版本窗口"再分一层** —— 同一个平台会有多个窗口（26.1 / 26.3 …），
所以项目路径是 `平台:窗口`，产物名也带窗口（否则两个窗口会撞名）：

```
core/                          不认识 Minecraft 的那一半：常量、配置、模板、QQ 机器人、双向路由、命令树、接缝。**Java 17 字节码**
bukkit-common/                 Bukkit 一族的共享部分（**编译对 spigot-api**）：平台基类、命令、JUL 日志、进服/退服/死亡监听
fabric/fabric-26.1/            Fabric 适配（loom）：入口点、4 个事件监听、把命令树注册进 Brigadier。Java 25
neoforge/neoforge-26.1/        NeoForge（moddev）：事件用 NeoForge 的事件总线、命令挂 RegisterCommandsEvent
forge/forge-26.1/              Forge（ForgeGradle）：26.x 的事件 API 与 NeoForge 完全不同（每个事件自带静态 BUS）
forge/forge-1.20.1/            Forge 1.20.1（ModDevGradle 的 legacyforge）：**同一个构建里的另一个窗口**，Java 17，产物要 reobf 到 SRG
spigot/spigot-26.1/            Spigot 变体：入口、老聊天事件（AsyncPlayerChatEvent）、`§` 字符串渲染
paper/paper-26.1/              Paper 变体：入口、新聊天事件（AsyncChatEvent）、Adventure 渲染 + Folia 调度
```

窗口名用**窗口起点**（`fabric-26.1` 覆盖 `[26.1, 26.2]`，范围写在描述符里）。
产物：`mc-qq-fabric-26.1-<版本>.jar` / `-neoforge-26.1-` / `-forge-26.1-` / `-forge-1.20.1-` / `-spigot-26.1-` / `-paper-26.1-`。

**`forge/forge-1.20.1` 为什么不用独立构建**：1.20.1 的 ForgeGradle 是第 6 代（要 Gradle 8），26.1 的是第 7 代
（要 Gradle 9.3+），而同一个 plugin id 在一个构建里只能有一个版本。但 ModDevGradle 的
`net.neoforged.moddev.legacyforge` 是**同一个 artifact** 的 addon、与 `net.neoforged.moddev` 同版本，
所以这一代就待在这个构建里。依据见 [docs/MULTIPLATFORM.md](docs/MULTIPLATFORM.md) 第六节。
⚠️ 它的验证方式与别的平台不同（dev run 看不见兄弟项目的 classes 目录，见那份文档"六"）。

**Bukkit 一族装哪个**：Paper 系（含 Purpur，以及 **Folia**）装 `paper` 那份；Spigot / CraftBukkit 装 `spigot` 那份。
**装错了会明确告诉你**：spigot 那份在 Paper 上会打印一句原因并停用自己（反过来也一样），
不会静默地少转发或者重复转发。

**Bukkit 那两个 jar 覆盖的是 1.20.1 → 26.x**（比别的平台宽得多）：插件端编译对**最老**的目标就能一路往上跑，
所以这两份编译对 paper-api / spigot-api **1.20.1**、`options.release = 17`、`api-version: '1.20'`。
实测同一个 jar（md5 一致）在 Paper 1.20.1 与 Paper 26.2 上都加载成功、命令都能跑。
⚠️ `api-version` 的语法是 **major.minor**（`1.20`），不是完整的 MC 版本 —— 写 `1.20.1` 会被直接拒绝加载。
（反过来说：编译对最新的 26.1.2 只会**缩小**覆盖范围，没有任何好处。）

加一个新窗口 = 复制一个窗口目录 + 在 `platforms.yml` 的矩阵里加一行。
**`bukkit-common/` 是唯一一个"跨平台"的模块**：它编译对 spigot-api（Bukkit 一族的最低公分母），
Paper 变体与 Spigot 变体都依赖它，各自只补自己那点差异（Paper 用 Adventure 渲染 + Folia 调度，
Spigot 直接发 `§` 字符串）。

**公共的东西各只有一处**：版本事实在 `gradle.properties`；
描述符的显示名/作者/许可证/链接/描述在 **`descriptors.properties`**（**UTF-8，别并回 gradle.properties** ——
原因写在那个文件顶部：Java Properties 是 ISO-8859-1，非 ASCII 会被双重编码，而 YAML 1.1 会因此拒绝
`plugin.yml`，Bukkit 插件完全不加载）；
平台清单在 `platforms.yml`；打包与 relocate（打进去哪些库、改哪些名、塞哪两个许可文件）在根 `build.gradle.kts`。
所以每个平台模块只剩"自己的名字 + 自己的 API 依赖 + 自己的描述符"。

⚠️ **Forge 默认不进本地构建**：它的工具链（ForgeGradle 的 mavenizer）在**配置阶段**就要下载，而 Gradle 会配置
所有 include 的项目 —— 只要它在列表里，网速差的机器上**任何** `./gradlew` 调用都会失败（连另外三个平台也编不了）。
所以 `settings.gradle.kts` 里它是按需 include 的：

```bash
./gradlew build                      # 本地默认：core + fabric + neoforge + bukkit
./gradlew build -PwithForge=true     # 需要 Forge 时（CI 就是这么调的）
```

CI runner 的网络没问题，所以两个工作流都带了这个参数。

⚠️ **mod 的 id 是 `mcqq`，和项目名 `mc-qq` 不是一回事。** 原因是 NeoForge 的 modId 只允许
`^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$` —— **连字符直接被拒**，FML 起不来。`mcqq` 是 Fabric、NeoForge、Bukkit
三家都接受的拼法，所以：

* **id = `mcqq`**：三个描述符、配置目录（`config/mcqq/`、Paper 侧 `plugins/mcqq/`）、插件名、资源路径、日志器名。
* **项目名 = `mc-qq`**：仓库名、jar 文件名（`mc-qq-<版本>.jar`）、聊天栏前缀 `[mc-qq]`。

两个拼法各有出处，改的时候别只改一处 —— `descriptors.properties` 的 `mod_id` 与 `Constants.MOD_ID` 是同一件事的两侧。

* **接缝只有一个接口**：`MinecraftPlatform { label, configDir, broadcast, onMainThread, registerCommands }`。
  `MinecraftServer` / `ServerPlayer` / `Component` 一律不进 core —— adapter 把事件拼成句子后交过去，
  带 `§` 的文本由 adapter 自己渲染（Fabric 用 `Component.literal`，Paper 用 Adventure 的 legacy 反序列化）。
  core 因此可以脱离游戏编译和测试（21 个测试，全离线）。
* **命令注册也在 core**：Brigadier 是 Mojang 的独立库（不是 Minecraft 类），所以"遍历命令树建 Brigadier
  节点"这段泛型化后放在 core，两个 mod 平台各只剩一行（自己的 sender 类型 + OP 等级）。
* **命令树也在 core，连"分发与权限策略"一起**：`/qq` 的子命令是 core 里的一个节点类，名字、用法行、权限节点
  全部**由它在树里的位置推导**（`/qq status` → `mcqq.status`），`/qq help` 由树生成；判权限、拒绝话术、
  输出前缀、以及"命令抛异常不许带崩服务端"都在 `CommandTree.run(...)` 里做一次。
  平台只需要包一个两方法的 `CommandSource`（`hasPermission` + `reply`）并把参数交出去 ——
  所以**加一个子命令不用动任何 adapter**。这套骨架参考了鹊桥的 `SubCommand`/`CommandExecutorHelper`，
  但用接口代替了它的 `Object sender`（少一次 cast，多一层编译期保护）。
* **常量在 `core/Constants`**：mod id、输出前缀、命令名、权限根 —— 这些是 adapter 之间最容易对不上的东西。
* **日志也是接缝**：MC 给 slf4j、Bukkit 给 `java.util.logging`，core 只认自己的 `Log.Sink`。
* **core 编到 release 21**：既拿到虚拟线程，又同时覆盖两代游戏（1.20.5 起是 Java 21，26.1 起是 Java 25）。
  降到 17 会换来 1.18–1.20.4 那一线，代价是失去虚拟线程。
* **打包**：每个平台把 `core` 与 SDK/OkHttp/Gson/kotlin/SnakeYAML 一起 shadow 进自己的 jar 并 relocate ——
  jar 本身是服务端唯一保证存在的 classpath。`core` 自己不带这些依赖（compileOnly）。
* 加一个平台 = 加一个目录 + 在 `settings.gradle.kts` 里 `include`，不动 core。

### bukkit 这一版的边界

* **只支持 Paper 系**（Paper / Folia / Purpur 等）。聊天走 Paper 的 `AsyncChatEvent`。
  普通 Bukkit 的 `AsyncPlayerChatEvent` 虽然已废弃，但**在 26.1.2 的聊天链路里还在**
  （Paper 的 `ChatProcessor` 同时保留了现代与旧两条路径），所以以后要支持 Spigot 是可行的 ——
  但需要真人打一句话实测过才能声称。
* **已经在真实 Paper 26.1.2 上加载并跑过**：插件被接受、`onEnable` 跑通、`plugins/mcqq/config.yml` 被写出、
  `/qq status` 与 `/qq reload` 通过 RCON 实际执行过。
* **Folia 已经真机加载过**（Folia 26.1.2-8）：插件被接受、命令树可用、平台标识 `folia-26.1.2`，
  `plugin.yml` 里声明了 `folia-supported: true`，广播在 Folia 上按玩家排到各自的区域线程。
  **但广播本身没验过**（要一个真人玩家 + 一条入站 QQ 消息）。
* **Spigot / CraftBukkit 不支持**，而且会**明确告诉你**：启动时检查不到 Paper 的聊天事件类就打印原因并停用自己，
  而不是抛一句 `NoClassDefFoundError`。
* **还没证的**：聊天转发（要玩家真的打一句话）。

## 你需要知道的版本事实

* **Minecraft 26.1 起官方不再混淆**，所以没有 Yarn：源码直接用 Mojang 名（`MinecraftServer`、`ServerPlayer`、`Component`）。
  Yarn 停在 1.21.11，别照旧教程写 `yarnMappings`。
* **Java 25**。`gradle/gradle-daemon-jvm.properties` 要求 Gradle 自己跑在 25 上（不是只编译到 25）。本机只有 21 时
  要么让 Gradle 自动装 toolchain，要么手动放一个 JDK 并在**机器级** `gradle.properties`（即
  `%GRADLE_USER_HOME%/gradle.properties`，默认 `C:\Users\<你>\.gradle\`）里登记：
  `org.gradle.java.installations.paths=D:/SDK/OpenJDK-21,D:/SDK/jdk-25.0.4.1+1`。这台机器上 foojay 报
  “No defined toolchain download url for WINDOWS on x86_64”，所以走的是手动那条路（Adoptium 的 zip 要用
  `Expand-Archive` 解，Git Bash 的 `tar` 不认 zip）。
* 非混淆用的是 loom 的 **no-remap** 插件 id `net.fabricmc.fabric-loom`：它**没有** `modImplementation` 这些
  `mod*` 配置，也**没有** `remapJar` 任务，依赖直接写 `implementation`，产物就是 `shadowJar`。旧教程里那两样都来自
  混淆时代的 `fabric-loom`。
* 现成版本：fabric-loom **1.18.2**、Loader **0.19.5**、Fabric API **0.155.3+26.1.2**、Gradle wrapper 9.7.1。
* **26.1 → 26.2 是一个窗口**：描述符写的是范围（Fabric `minecraft: ">=26.1"`、Paper `api-version: '26.1'`），
  编译对 26.1.2 的产物在 Paper 26.2 上跑通；Fabric 侧换成 26.2 + Fabric API `0.161.0+26.2`
  **源码一行没改**就编译通过并加载。所以平时不必一版本一 jar，只有 API 真断了才分裂。

## 构建与安装

```bash
./gradlew build
# 产物（名字里带平台与窗口）：
#   fabric/fabric-26.1/build/libs/mc-qq-fabric-26.1-<版本>.jar      → Fabric 服务端 mods/
#   neoforge/neoforge-26.1/build/libs/mc-qq-neoforge-26.1-<版本>.jar → NeoForge 服务端 mods/
#   forge/forge-26.1/build/libs/mc-qq-forge-26.1-<版本>.jar          → Forge 服务端 mods/（见下：默认不在构建里）
#   paper/paper-26.1/build/libs/mc-qq-paper-26.1-<版本>.jar          → Paper 系服务端 plugins/
# 同目录的 -dev.jar 是不带任何依赖的瘦 jar，不要用它。
# 另有 core/build/libs/mc-qq-core-<版本>.jar，那是内部产物，不需要单独安装。
```

Fabric 侧需要 Fabric API 一起在 `mods/` 里。三个平台的**打包产物都实机加载过**（不是只跑开发服）：
Paper 用真服务端 + `plugins/`，Fabric 用真服务端 + `mods/`，NeoForge 用 dev 启动器 + `run/mods/`。SDK 与它的 OkHttp/Gson、kotlin-stdlib、以及 SnakeYAML 都被 relocate 进
每个平台的 jar，所以不会和 MC 自带的 Gson 抢类，也不和其他 mod 各自带的 kotlin 打架；`slf4j-api` 由服务端提供，
既不打进 jar 也不 relocate，桥接日志直接进 `logs/latest.log`（Paper 侧走 `java.util.logging`，进同一个文件）。

`./gradlew :fabric:runServer` / `:neoforge:runServer` 跑开发服，工作目录分别是 `fabric/run/`、`neoforge/run/`。

## 配置

首次启动会写出两个文件，都在 `config/mcqq/`（Paper 侧是 `plugins/mcqq/`）：

| 文件 | 是什么 |
| --- | --- |
| `config.yml` | 真正生效的配置。你编辑这个。 |
| `config.example.yml` | 打包的模板副本，**带全部注释**，每次启动刷新，永远描述当前版本。 |
| `config.yml.bak` | 只在"插件往你的文件里补过键"时出现，是补之前那一版。 |

**升级不会让你手改文件**：插件启动时如果发现你的 `config.yml` 少了新增的键，会先把原文件备份成 `.bak`，
再把缺的键（带内置默认值）补进去，并在日志里说清补了什么。补进去的值**本来就已经在生效**
（缺键走内置默认），所以这一步不改变任何行为，只是让你能看见并改它。
带注释的说明始终在 `config.example.yml` 里 —— 补过的 `config.yml` 顶部会指向它。

**AppSecret 直接写在 `config.yml` 里**（模板给的也是这一行）：

```yaml
bots:
  - id: main
    app-id: "123456789"
    secret: "你的 AppSecret"
    groups:
      - group-openid: "xxxxxxxx"
        label: MC 主群
        receive-from-qq: true
        send-to-qq: [chat, join, quit, death]
```

想把密钥放在环境变量里（不进文件）时，把 `secret:` 那行换成 `secret-env: QQ_BOT_SECRET` 即可；
两个都填以文件里的为准，日志里会说一声。

⚠️ 因为密钥现在就在这个文件里，**别把 `config.yml` 贴到 issue / 群里** —— 要贴就贴同目录那份
`config.example.yml`（它是模板，没有你的密钥）。

* `group-openid` 是群的 openid，不是 QQ 群号；子频道则是 `guild-id` + `channel-id` 两个。
  **拿它们不用手抄**：先把 `groups:` / `channels:` 留空（或整段删掉），开服后在群里或子频道里
  @ 一次机器人，然后在控制台敲 `/qq bind` —— 插件会把最近说话的那个写进 `config.yml` 并自动重载，
  这条不用碰文件。也可以手填：那些 id 会出现在服务端日志和 `/qq status` 里。
* `/qq bind <id 前几位>` 指定绑哪个；`/qq status` 会列出「收到过消息但还没绑定」的群与子频道。
  `/qq bind` 只接受**机器人真的收到过消息**的目标，所以打错字也不会把聊天栏接到陌生的地方去。
* `receive-from-qq: false` 就是「只出不进」；`send-to-qq` 缺省取那四项，写 `[]` 即「只进不出」。
* 一个 bot 多个群、多个 bot 都支持（QQ 侧每个 bot 一条独立连接、独立总线）。

## 消息模板：文案是配置，不是代码

每条消息长什么样都写在配置的 `templates:` 里，改文案不用等新版本。**这一整段可以删掉**，删了就回到内置默认
（行为与老版本完全一致），`/qq templates` 会打印当前生效的文案和一段可以直接粘贴的写法。

```yaml
templates:
  qq-chat: "§b[QQ {group}]§r {user}§7:§r {text}"     # QQ -> MC
  mc-chat: "[MC] {player}: {text}"                   # MC -> QQ
  mc-join: "[MC] {player} 加入了世界"
  mc-death: "[MC] {player} 死亡（{killer}）"
```

* **可用占位符**：`{group}` `{user}` `{text}` `{count}` `{member}` `{player}` `{killer}` `{platform}` `{time}`。
  写错的占位符**不会被替换掉**，会原样显示 —— 一眼能看出拼错了；加载时也会报进 `/qq status`。
* **空字符串 = 不播报**（例如某群 `mc-join: ""` 就是不报进服）。
* **优先级：群级 > 全局 > 内置默认**。群级只写要覆盖的键，其余继承全局：

```yaml
    groups:
      - group-openid: "xxxxxxxx"
        label: 公告群
        templates:
          mc-chat: "[公告] {player}: {text}"   # 只覆盖这一个
          mc-join: ""                          # 这个群不播报进服
```

* **`debug: true`** 会把"为什么不转发"的每个判断写进日志（也会打印实际转发的内容）。
  消息没到 QQ 时先开它，`/qq reload` 就能看到是哪一步挡住的。

* 我们自己的占位符用 `{花括号}`。第三方占位符库（mod 侧 Patbox 的 Text Placeholder API、Bukkit 侧
  HelpChat 的 PlaceholderAPI）都用 `%百分号%`，所以两者不会打架 —— **但目前 `%...%` 原样显示**，
  要接它们需要平台侧实现一个钩子，见 [TEMPLATES.md](docs/TEMPLATES.md) 第三节。

## 发布（维护者）

发布链在 `.github/workflows/` 里，仿照 [17TheWord/QueQiao](https://github.com/17TheWord/QueQiao) 的
`build.yml`（它每个"平台×版本"格子独立构建，再用 `Kir-Antipov/mc-publish` 按各自的 loaders 与
game-versions 发到 Modrinth / CurseForge）：

CI 跑在 **`ubuntu-26.04`**（钉死，不用 `ubuntu-latest`）：`-latest` 会在 GitHub 迁移时悄悄换掉底层系统
（2026-10-19 起指向 Ubuntu 26），而构建工具链对系统版本敏感。GitHub 弃用某个镜像时要手动把这
三处一起改。

**"有哪些平台"只写在一个地方**：`workflows/platforms.yml`（可复用工作流）。test 与 release 都 `uses:` 它，
所以加平台、改 jar 名、改 loaders 只动那一个文件；而**版本事实**（`mod_version`、`publish_game_versions_*`、
`minecraft_version_range`）的唯一出处是 `gradle.properties`，platforms.yml 只负责读出来。
⚠️ `game-versions` 是**每个窗口一份**（`publish_game_versions_26_1` / `_1_20_1`）—— 26.x 的 jar 装不到
1.20.1 上，共用一份列表会让发布页误导人。

| 文件 | 什么时候跑 | 干什么 |
| --- | --- | --- |
| `workflows/platforms.yml` | 被下面两个 `uses:` | 从 `gradle.properties` 读出版本事实，并生成**平台矩阵**（name / 子项目 / jar / loaders / 额外参数） |
| `actions/set-java/action.yml` | 被各 job 调用 | 装 JDK 25、配 Gradle 缓存 |
| `workflows/test.yml` | 推 main、每个 PR | **每个平台一格、并行**（`fail-fast: false`）：`./gradlew :core:test :<平台>:build`，哪格挂了就是哪个平台坏了；每格存自己的 jar |
| `workflows/release.yml` | 打 `v*` 标签 | 校验标签与 `mod_version` 一致 → 一次构建出全部产物 → 存 artifact + 建 GitHub Release → **发布矩阵**：四个产物各一格，并行各发一次 mc-publish |

矩阵和 QueQiao 是同一个形状（每格一次 mc-publish，并行，各自声明 `loaders` 与 `game-versions`）。
两处用法不同，是有意的：

* **test** 用矩阵做**并行 + 失败隔离**：四格各自 `:core:test :<平台>:build`，一眼看出是哪个平台坏了。
  代价是 MC 工具链各下一遍（runner 网络快，换来的是定位速度）。
* **release** 只 build **一次**（出全四个产物），矩阵只用在发布：拆成四格只会把工具链下载四遍，
  而发布本来就是每个产物一次。

**没配项目 id 时 `publish` 整个 job 跳过**：构建、Actions artifacts、GitHub Release 照常，只是不上架。
想先手动传 Modrinth/CF 的话，从那次 Actions 运行的 artifacts 里下载三个 jar 即可。

**发一次要做的**：

1. 改 `gradle.properties` 的 `mod_version`，提交
2. `git tag v0.1.0 && git push origin v0.1.0`
3. 想发到 Modrinth / CurseForge 的话，先在平台上把项目建好，然后在仓库
   Settings → Secrets and variables → Actions 里配：Variables `MODRINTH_ID` / `CURSEFORGE_ID`，
   Secrets `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN`。**没配也不会失败** —— 那几步会被跳过，GitHub Release 照常创建。

标签里带连字符（`v0.2.0-beta.1`）会被当成预发布，Modrinth 上的 version-type 也会是 beta。

## 许可证

代码是 **MIT**（见 [LICENSE](LICENSE)）—— 与同作者的 QueQiaoTool 一致。产物里**打包了别人的代码**
（QQ 官方 SDK、OkHttp、Gson、SnakeYAML、Kotlin 等，都是 Apache-2.0），清单在
[THIRD-PARTY.md](THIRD-PARTY.md)，两份文件也都打进了 jar。

## 命令（需要 OP 2 / `mcqq` 权限）

| 命令 | 作用 |
| --- | --- |
| `/qq status` | 每个 bot 的在线状态、自身 id、绑定的群与方向，外加配置里读出来的问题 |
| `/qq reload` | 重读配置，换掉正在跑的 bot；MC 侧监听器只注册一次，所以不会重复转发 |
| `/qq templates` | 当前生效的消息模板、可用占位符，缺 `templates:` 段时还会打印可粘贴的写法 |
| `/qq test` | 往每个配置的群各发一条测试消息，验证凭证与群 openid（结果见日志；不阻塞服务器） |
| `/qq help` | 列出所有子命令（由命令树生成，不手写） |

这几个是 core 里的几个节点；平台侧只是把整棵树注册进去，所以**加子命令不用改 adapter**。
Fabric 侧走 Brigadier（带补全），Paper 侧走 `plugin.yml` 的命令 + 自己的补全，权限节点在两边都由路径推导：
`mcqq` 是根，`mcqq.status` / `mcqq.reload` / `mcqq.templates` / `mcqq.test` / `mcqq.help` 是子节点
（`plugin.yml` 里用 `children` 挂上，默认给 OP）。

启动时凭证不对（appid/secret 错、后台没开通）不会拖住服务器：bot 起不来就记在 `/qq status` 与日志里，
修好配置后 `/qq reload` 即可，不必重启。

## 两个方向具体搬什么

**QQ → MC**：`GROUP_MESSAGE_CREATE`（全量群消息）与 `GROUP_AT_MESSAGE_CREATE`（@ 机器人）→
`§b[QQ 群名]§r 昵称: 文本`；附件只报数量与「文字里不含它们」（官方 payload 里文本与附件是平级字段，
没有位置信息）。`GROUP_MEMBER_ADD/REMOVE` → `[QQ 群名] xxx 进了群/退了群`。单聊（别人私聊机器人）不转发。
同一条 `msg_id` 被平台重推时只播一次（SDK 还没做入站去重，这里先挡着）。

**MC → QQ**：玩家聊天、进服、退服、死亡（带凶手名），前缀 `[MC]`，发送时去掉 `§`颜色码。
这属于平台的主动消息，会被频控或送去审核：失败/进审核只记日志，绝不在 tick 里等待，也绝不让聊天因为 QQ 而失败。

## 开发用的测试服（可以删）

`paper/paper-26.1/run`、`fabric/fabric-26.1/run`、`neoforge/neoforge-26.1/run` 是验证用的服务端目录
（合计约 800M），都在 `.gitignore` 里、不进仓库。它们是**验证环境**：留着，下次改完直接起服复验，不用重新下载
（Mojang 那边现在限速）。要腾空间可以直接删整个目录 —— 代价是下次要重新拉服务端与依赖。
最可以删的是 `bukkit/run-folia`（Folia 已经验过，它的缓存还是从 `bukkit/run` 复制过去的）。

## 线程

QQ 的事件跑在 mod 自己的虚拟线程上（`EventBus(Executor)` 注进去），进 MC 聊天栏时 `server.execute(...)` hop 回主线程；
MC 的事件在 tick 里只做一次投递。所以：QQ 慢不卡服务器，服务器繁忙也不排队卡 QQ。

## 离线证到了什么，什么还得靠真机

`./gradlew build` 跑 9 个测试，全离线：配置解析（含首次写出模板、占位符被跳过而不是硬连）、出站请求的线上形状
（MockWebServer 假平台：`POST /v2/groups/{openid}/messages`、`Authorization: QQBot <token>`、`msg_type:0`），
以及平台拒绝/进审核时 `send` 只记日志、不重试、不抛给 tick。relocate 后的 jar 也单独点过一次：从 jar 里造 client、
解析一条群消息 payload，字段照旧。

真机跑过 `./gradlew runServer`（26.1.2 + Fabric API 0.155.3）：mod 加载、监听器注册、`SERVER_STARTED` 写出模板并
把「还是 REPLACE_ME」报进日志。**没证到的**：`/qq status` 与 `/qq reload` 的实际执行——loom 的 runServer 从管道喂
控制台命令不工作（原版 `stop` 同样报 "An unexpected error occurred trying to execute that command"），要进游戏用 OP
玩家按 `/qq` 试；以及真实群消息的往返、`author`/`mentions` 在真 payload 里到底长什么样（见 SDK README 的已知边界）。
