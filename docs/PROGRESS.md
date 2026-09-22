# 进度快照（2026-09-21）

> 2026-09-21 之后的补充都写在这里，最新的在最前面。历史轮次保留原文（它们记录的是当时的判断，
> 里面的 `mc_qq`、`config/mc-qq/` 等字样是**当时**的事实，不是现在的）。

## forge-1.20.1 可行性复核（2026-09-22，最新）

用户问"给 forge 支持 1.20.1 要怎么做、好不好做"。**只调研，没动代码**；产出写进
`docs/MULTIPLATFORM.md` **§8.6**。要点：

* **代际差已实测**：1.20.1 官方 MDK = Gradle **8.8** + FG `[6.0,6.2)` + Java **17** + forge 47.4.23；
  本项目 = Gradle 9.7.1 + FG `[7.0.17,8)` + Java 25。→ 必须独立构建（`legacy/`），
  三条硬约束里任一条都够：同一 plugin id 不能有两个版本；FG 7 强制 Gradle ≥ 9.3.0
  （依据 ForgeGradle `FG_7.0` 分支 commit "Bump minimum Gradle to 9.3.0"）。
* ⚠️ **发现文档里一个系统性错误**：8.5 / 9.3 / 9.4 都写"core release 17"，
  实际 `gradle.properties:70` 是 **`core_java_release=21`**，而且降到 17 **编不过**：
  `./gradlew :core:build -Pcore_java_release=17` →
  `BridgeRuntime.java:43 错误: 找不到符号 Executors.newVirtualThreadPerTaskExecutor()`。
  虚拟线程是 Java 21 API，core 里只此一处（全仓 grep 确认）。→ 已修正文档，并加了 §8.6。
* ⚠️ **第二处低估**：8.5 说"1.20.1 的 adapter 差异只有两处改名"，实测**不止**。
  对照 `refs/QueQiao/forge/origin`（真跨 1.16.5→1.21 的源码）：26.x 用
  `ServerChatEvent.BUS.addListener(...)` + `getUsername()/getRawText()`，
  1.20.1 用 `MinecraftForge.EVENT_BUS.register(this)` + `@SubscribeEvent` +
  `getPlayer()/getMessage().getString()`；`@Mod` 构造器也从"注入 FMLJavaModLoadingContext"回到无参。
  影响 `McQqMod` / `McToQq` / `ForgePlatform`（177 行里的多数）。
  好消息：`sendSystemMessage(Component)` 1.19+ 就有（鹊桥 `// IF >= forge-1.19`），那两行不用改。
* **结论**：结构上一步（照抄 settings 的按需 include 门控 + 自己的 wrapper），代码上两步
  （core 去虚拟线程 → release 17；forge 适配器换事件模型）。
* 顺手补了 legacy 共享 core 的**第三个选择**：legacy 直接把 `core/src/main/java` 加进自己的 sourceSet
  （core 的依赖**全是 `compileOnly`**，已核实）—— 最省，且 release 级别由 legacy 自己定。

**没有落地任何代码**。`refs/` 里的 1.20.1 MDK 与鹊桥源码是本次的唯一证据来源。

## Bukkit 一族改成"编译对最老目标"：一个 jar 覆盖 1.20.1 → 26.x（2026-09-22，最新）

用户指出插件端的标准做法是**编译对最老的目标、一个 jar 往上覆盖**。核实下来成立，而且原来的做法是**反的** ——
`bukkit-common` / paper / spigot 都编译对 26.1.2，所以往低版本装不了（编译时用的是 26.x 才有的符号）。

### 第一步（最便宜）：编译

三个模块分别编译对 1.20.1 的 API，**全过、零错误**：

| 模块 | 换成 | 结果 |
| --- | --- | --- |
| `bukkit-common` | `spigot-api:1.20.1-R0.1-SNAPSHOT` | ✓ |
| `paper/paper-26.1` | `paper-api:1.20.1-R0.1-SNAPSHOT` | ✓ |
| `spigot/spigot-26.1` | `spigot-api:1.20.1-R0.1-SNAPSHOT` | ✓ |

连 Folia 的 `server.getGlobalRegionScheduler()` / `player.getScheduler()` 和 Adventure 的 `AsyncChatEvent`
都在 paper-api 1.20.1 里 —— 用到的全是十来年没动过的东西。**代价只有"不能用 1.20.1 之后新增的 API"**，
对本项目是零成本。

### 改了什么

* `gradle.properties`：两个 API 坐标换成 1.20.1。
* 两个 bukkit 模块的 `options.release` 25 → **17**（1.20.1 的服务端跑 Java 17）。
* 两个 `plugin.yml` 的 `api-version`（见下）。

### ⚠️ `api-version` 是 **major.minor**，不是完整的 MC 版本

第一次写 `api-version: '1.20.1'`，Paper 1.20.1 直接拒绝加载：

```
org.bukkit.plugin.InvalidPluginException: Unsupported API version 1.20.1
```

改成 **`'1.20'`** 就好了。（26.x 那份写 `26.1` 本来就对，因为它正好是 major.minor —— 之前的正确是巧合。）

### 真机验证：**同一个 jar**，两端都跑

同一个文件（md5 `5282481127eb14870a3f025e5a61cc6b` 一致）分别丢进两个测试服：

| 服务端 | 结果 |
| --- | --- |
| **Paper 1.20.1**（build 196，Java 17） | `平台 paper-1.20.1，主线程调度走 经典调度器`；RCON 敲 `/qq status` / `help` 全通 |
| **Paper 26.2**（build 126，Java 25） | `平台 paper-26.2，主线程调度走 经典调度器`（加载成功） |
| **spigot 那份** 在 Paper 1.20.1 上 | 走到它该走的拒绝路径：`这个 jar 是给 Spigot / CraftBukkit 的…插件已停用` |

26.2 那边**没**重验命令路径：那条路径与 `api-version` 无关，且早先会话已验过；而"`api-version: 1.20` 会不会被
26.2 拒绝"这个**真正的风险**，已经被"它加载成功了"排除掉（否则会报同样的 `InvalidPluginException`）。

### 一个还悬着的决定：窗口名

`paper/paper-26.1` 与 `spigot/spigot-26.1` 现在覆盖的是 **1.20.1 → 26.x**，但目录名与产物名还写着 `26.1`
（按项目"窗口名 = 窗口起点"的约定应该叫 `-1.20.1`）。改名会动到**产物名**与 CI 矩阵，而且 Modrinth 的
`game-versions` 要**逐个列出** —— 一个覆盖三十来个版本的 jar 就得列三十来个，而文档的规矩是"只写实测过的"。
这两条都是对外的，留给用户定。**代码这边已经一致了，只剩名字。**

## forge-1.20.1 窗口：已落地并在真机跑通（2026-09-22）

从"复核可行性"变成了"做完了"。`forge/forge-1.20.1/` 是**主构建**里的一个子项目（用 ModDevGradle 的
`net.neoforged.moddev.legacyforge`，与 neoforge 用的 `net.neoforged.moddev` 同 artifact 同版本）——
**没有建 `legacy/` 独立构建**，§8.6 的第一版结论被彻底推翻。

**产物**：`mc-qq-forge-1.20.1-0.1.0.jar`，4.18 MB，含 core + relocate 后的 SDK，字节码 **61（Java 17）**；
`reobfShadowJar` 把 Minecraft 引用改成了 SRG 名（`getName()` → `m_7755_`、`getEntity()` → `m_7639_`，
`javap` 实测，不是"任务跑了"就算）。

**真机验证**（分两步，因为这一步有个坑）：

① **跑起来**：`./gradlew :forge:forge-1.20.1:installDevJar` 把 jar 放进 `run/mods/`，
再 `runServer -PwithForge=true -PjarOnly`（`-PjarOnly` 不把 source set 当 mod，否则同一 modId 被发现两次）。
在真 Forge 1.20.1（47.4.23）上：

```
[mcqq/]: mcqq loaded; the QQ bridge comes up with the server
Done (7.665s)! For help, type "help"
[mcqq/]: wrote the default config to ...\run\config\mcqq\config.yml; fill in app-id and the group openids
```

② **命令真的能跑**：起服时开 RCON，用 `refs/rcon.py` 敲命令（技能里写着"驱动控制台要用 RCON，
别靠 stdin 管道"—— 这次照做了，也正因此才发现下面那个 bug）：

```
$ qq status
[mc-qq] 平台 forge-1.20.1
[mc-qq] 配置里没有 bot：编辑 ...\run\config\mcqq\config.yml 后 /qq reload
[mc-qq] bot main still has the REPLACE_ME app-id from the template, skipped

$ qq help      → 6 个子命令全列出来
$ qq templates → 9 个模板与 9 个占位符全列出来
$ qq reload    → 已重载配置（+ 重读了状态）
$ qq test      → 配置里没有 bot，没有可测的群
```

`平台 forge-1.20.1` 这行同时证明了移植的 `getCurrentVersion().getName()` 是对的。
命令路径（`RegisterCommandsEvent` → `BrigadierCommands` → `hasPermission(2)` → `sendSuccess`）全通 ✓。

### ⚠️ 一个我自己的验证方法错误：dev 服该放**未 reobf** 的 jar

第一次我把 `build/libs/` 里那份（reobf 过的）塞进 `run/mods/`，命令一敲就炸：

```
java.lang.NoSuchMethodError: 'boolean net.minecraft.commands.CommandSourceStack.m_6761_(int)'
```

查映射表（`build/moddev/artifacts/namedToIntermediate.tsrg:18968` → `hasPermission (I)Z m_6761_`）
**映射是对的**。真正的原因是 dev run 的 launchTarget 是 `forgeserveruserdev`，
**那个环境里游戏类是 named 映射**（`hasPermission`），而 `build/libs/` 那份已经 reobf 成 SRG 了。
**reobf 的那份是给正式服用的**（正式服游戏类是 SRG）。

→ 所以这个窗口的验证要分两条路，**别混成一个**：
* **代码对不对** → `build/devlibs/` 的 jar（未 reobf）放进 dev 服，RCON 敲命令。
* **reobf 对不对** → 看字节码（`getName()` → `m_7755_`）并与映射表对照，或丢进一个真正式服。
`installDevJar` 任务就是为第一条路准备的（它只拷 devlibs 里那份厚的，避开同名的薄 jar）。

### 适配器移植：比预估的小得多

`ServerChatEvent` 在 1.20.1 上**也有** `getUsername()` / `getRawText()`
（从 `forge-1.20.1-47.4.23-sources.jar` 里读的，不是猜的）→ **聊天那一行一个字都没改**。
真正要改的只有四处：

| | 26.x | 1.20.1 |
| --- | --- | --- |
| 事件注册 | `ServerChatEvent.BUS.addListener(...)`（每事件一个静态 BUS） | `MinecraftForge.EVENT_BUS.addListener(...)`（一个中心总线） |
| 入口构造器 | `McQqMod(FMLJavaModLoadingContext context)` | 无参构造 |
| 权限 | `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` | `source.hasPermission(2)` |
| 版本号 | `SharedConstants.getCurrentVersion().name()` | `.getName()` |

（`IEventBus.addListener(Consumer<T>)` 在 eventbus 6.2.33 里 `javap` 确认存在。）
**一轮编译就过，零错误零警告。**

### ⚠️ 两个只有这一代才会暴露的坑

**1. dev run 看不见兄弟项目的 classes 目录。**
Forge 1.20.1 的模块系统把类路径建成一串 **jar**（`build/moddev/serverLegacyClasspath.txt`，85 条全是 jar），
而 `project(':core')` 在同一构建内解析成的是 classes 目录。症状极迷惑：**mod 加载成功、模组列表里也有，
构造器一碰 core 就 `NoClassDefFoundError: com/example/mcqq/core/Log$Sink`**。NeoForge 26.x 没这毛病。
→ 所以这个窗口的验证方式定为**验打包产物**：`-PjarOnly` 不把 source set 当 mod（否则同一 modId 被发现两次），
改把 `build/libs/` 里那个 jar 放进 `run/mods/` —— 那也正是服主拿到的东西（含 shadow 与 reobf）。

**2. jar 里带了没 relocate 的注解库 → Forge 1.20.1 直接拒绝启动。**

```
java.lang.module.ResolutionException: Modules com.google.errorprone.annotations and mcqq
export package com.google.errorprone.annotations.concurrent to module minecraft
```

`com.google.errorprone:error_prone_annotations`（gson 传递）与 `org.jetbrains:annotations`
（kotlin-stdlib 传递）**只含注解、运行时无用**，但 relocate 规则按**包名**匹配（`com.google.gson`、`kotlin`），
匹配不到它们的包（`com.google.errorprone.annotations`、`org.jetbrains.annotations`）→ 原样进了 jar。
它的模块系统不允许同一个包出现在两个模块里，26.x 的加载器不检查这个，所以只有这一代炸。
→ 修法：在根构建的 `bundled` 里 `exclude` 掉这两个 artifact（**所有平台一起受益**，jar 还小了 0.2MB）。
→ **教训**：relocate 规则要按"包里**实际**有什么"验，不能按"我写了哪几个库名"信。
验法是列 jar 里除 `shaded/` 与自己之外的包 —— 应该为空。

## forge-1.20.1：`legacy/` 大概率不用建（2026-09-22 复核，推翻当天早先的结论）

用户问"给 forge 支持 1.20.1 要怎么做、好不好做"。我第一版答"代际差太大 → 必须建 `legacy/` 独立构建"，
**当天就被自己推翻了**。完整记录在 `docs/MULTIPLATFORM.md` **§8.6**。

* **代际差是真的**（1.20.1 官方 ForgeGradle MDK = Gradle 8.8 + FG `[6.0,6.2)` + Java 17；
  本项目 = Gradle 9.7.1 + FG `[7.0.17,8)` + Java 25），**但它不决定"必须独立构建"**。
* **推翻它的证据**：官方 MDK 仓库 `NeoForgeMDKs/MDK-Forge-1.20.1-ModDevGradle` 用的是
  `net.neoforged.moddev.legacyforge`；ModDevGradle 的 `LEGACY.md` 原文说它是
  *"released alongside the normal plugin with **the same version**"*、支持 *"1.17 up to 1.20.1"*、
  且是 *"an 'addon' plugin ... on top of the normal plugin"*。
  本地实证：缓存里的 `moddev-gradle-2.0.147.jar` **同时**提供 `net.neoforged.moddev` 和
  `net.neoforged.moddev.legacyforge` —— 同 artifact 同版本，**没有"同一 plugin id 两个版本"的冲突**。
* → `forge/forge-1.20.1` 可以是**主构建**里的一个子项目，与 neoforge-26.1 共用 moddev-gradle 2.0.147。
* **还没验的三条**（跑一次 build 就知道）：legacyforge 在 Gradle 9.7.1 上能否跑（MDK 给的是 8.14.5）；
  它与 FG7 同构建共存有无摩擦；1.20.1 的产物要 reobf 到 SRG，得把
  `obfuscation { reobfuscate(tasks.named('shadowJar'), sourceSets.main) }` 接上（我们发的正是 shadowJar）。
* 适配器差异**不止**"两处改名"：26.x 用 `ServerChatEvent.BUS.addListener(...)` + `getUsername()/getRawText()`，
  1.20.1 用 `MinecraftForge.EVENT_BUS.register(this)` + `@SubscribeEvent` + `getPlayer()/getMessage().getString()`；
  `@Mod` 构造器也从"注入 FMLJavaModLoadingContext"回到无参（对照 `refs/QueQiao/forge/origin`）。
  好消息：`sendSystemMessage(Component)` 1.19+ 就有，`ForgePlatform` 那两行不用改。

## core → release 17（2026-09-22，用户拍板）

`BridgeRuntime` 的 dispatcher 从 `Executors.newVirtualThreadPerTaskExecutor()` 换成
`Executors.newCachedThreadPool(...)`（守护线程，名字 `mcqq-dispatch`）；`core_java_release=17`。

选缓存线程池是因为它保住了这段代码依赖的两个性质：**任务不等空闲线程**、**任务不被拒绝**
（`newFixedThreadPool` 会排队；有界池 + AbortPolicy 会把异常抛进游戏的聊天事件里）。
虚拟线程在本项目的收益只是"任务很多时不炸"，而负载是"每个转发事件一个短任务"，平台线程池够用。

**验证**：`clean build` 绿（31 任务）；core 测试 **47 用例 0 失败**；字节码分层（`javap -verbose` 实测）——
`com/example/mcqq/core/*` 与 `com/example/mcqq/bukkit/common/*` = **61（Java 17）**，
`com/example/mcqq/paper/*` = **69（Java 25）**。

## 修掉一个"Bukkit 插件完全不加载"的编码 bug（2026-09-22，第 4 步的回归）

**现象**：冒烟测试里 Paper 报 `Invalid plugin.yml`，插件一条日志都没有（`Initialized 0 plugins`）。

**根因链**（Python 复现出的字节与 jar 里**逐字节一致**）：

```
gradle.properties 磁盘上是正确 UTF-8: E6 8A 8A E8 81 8A …        ("把聊天")
  ↓ Java Properties 按 ISO-8859-1 读（Gradle 对 gradle.properties 就是这个规范行为）
字符: æ U+008A U+008A è U+0081 U+008A …（含 C1 控制字符 U+008A）
  ↓ 写成 UTF-8
jar 里: C3 A6 C2 8A C2 8A C3 A8 C2 81 C2 8A …
  ↓ SnakeYAML 按 UTF-8 读，YAML 1.1 拒绝 C1 控制字符
Invalid plugin.yml → 插件完全不加载
```

**这是第 4 步的回归**：中文原先写在描述符里，Gradle 按 UTF-8 读写都没事；搬进 `gradle.properties`
才中招。**为什么只有 Bukkit 炸得响**：JSON / TOML 容忍那些字节，fabric / neoforge / forge 只是描述
显示成乱码（也不对，但不致命）；YAML 1.1 是严格拒绝的。

**修法**：描述符那 6 个公共字段挪到 **`descriptors.properties`（UTF-8）**；根 `build.gradle.kts` 顶部用
`InputStreamReader(UTF_8)` 读进来、挂成根项目的 extra property —— `Project.property` 会沿父项目向上找，
所以 5 个平台模块的脚本**一行都没改**。另在 `subprojects` 里给 `ProcessResources` 显式设
`filteringCharset = "UTF-8"`（本机 `file.encoding=UTF-8` 但 `native.encoding=GBK`，不想依赖这个巧合）。

**验证**（这次连中文一起查 —— 上次就是漏了这一步才让 bug 溜过去）：4 个 jar 的描述符
**0 个未展开 `${...}`、0 个 C1 控制字符、中文逐字正确**；真机跑 Paper 26.2 两次 ——
paper 那份 `Enabling mcqq` / `平台 paper-26.2，主线程调度走 经典调度器` / `Disabling` 齐全，
spigot 那份走到它该走的拒绝路径，**两份都 0 个 `Invalid plugin.yml`**。

**教训**：第 4 步我只验了"占位符展开了、ASCII 字段对"就宣布通过 —— **没验编码**。
以后凡"把非 ASCII 数据搬过一层构建机制"，验一次**真机加载**比 grep 字段名可靠。

## 平台重构第 4 步：抽公共配置（2026-09-22）

两块：

* **描述符的公共字段**（`mod_name` / `mod_authors` / `mod_license` / `mod_url` / `mod_description`，加上已有的
  `mod_id`）进 `gradle.properties`；五个描述符改用 `${...}`，各自 `processResources` 一次
  `expand(resourceFacts)` 填进去。平台的差异（`plugin.yml` 那句"这份是 Paper 系"）仍留在各自描述符里。
* **打包与 relocate 收到根构建**：`bundled` 配置、SDK/SnakeYAML 依赖、shadowJar 的改名列表与排除项、
  LICENSE/THIRD-PARTY 注入 —— 原来在五个平台模块里各写一遍，现在在根构建的
  `plugins.withId("com.gradleup.shadow")` 块里一次。各模块只剩 `add("bundled", project(":core"))`。
  **没有新模块、没用 `buildSrc`**：根构建的 `subprojects` 块本来就在子项目脚本之前跑。
  ⚠️ 副作用：Kotlin 模块里的 `bundled(...)` 类型安全访问器失效（配置不再由本模块创建）→ 改用 `add(...)`。

**验证**：`clean build` 全绿（31 个任务）；四个能构建的 jar 里描述符**全部展开**（`grep -c '\${'` = 0），
字段值正确（`id: mcqq` / `name: MC ↔ QQ Bot` / `authors: [SkiesWorld]` / `license: MIT` / 中文描述），
`LICENSE` + `THIRD-PARTY.md` 仍在每个 jar 里 ✓。
⚠️ forge 的描述符展开没单独验（它不在本机构建里）；机制与 neoforge 完全相同。

**踩到的坑**：Kotlin DSL 里 `tasks.processResources { property("mod_id") }` 会去 **task** 上找属性 ✗
（Groovy 会回落到 project，所以那两份没事）→ 把 `resourceFacts` 提到顶层再 `expand`。
**这与"`javaRelease` 要提到顶层"是同一个坑，第二次了。**

## 平台重构第 2、3 步：bukkit-common + spigot 变体（2026-09-22）

* **第 2 步 `bukkit-common/`**：编译对 **spigot-api**（Bukkit 一族最低公分母），5 个共享类；
  paper 变体从 6 个文件削到 3 个。发送消息做成钩子 `sendLine(CommandSender, String)`。
  **抽共享模块直接暴露两个真实差异**：`Server.getMinecraftVersion()` 是 Paper 独有的（编译器抓的）；
  spigot-api 不带 Adventure（只有老的 `bungeecord-chat`）。
  **验证**：`clean build` 全绿 + Paper 26.2 真机（命令全通）+ jar 里 5 个 common 类 + 3 个 paper 类。
* **第 3 步 `spigot/spigot-26.1/`**：3 个文件（入口 + `AsyncPlayerChatEvent` 监听 + `§` 字符串渲染）。
  **编译通过**（证明老聊天事件在 spigot-api 里确实存在）。
  **拒绝路径实测**：装到 Paper 上 → `这个 jar 是给 Spigot / CraftBukkit 的……插件已停用` + 停用自己 ✓
  —— 这条同时把"Paper 是否仍触发老事件"的未知绕过去了（设计上不让混用发生）。
  ⚠️ **未验**：从没在真 Spigot 服务端上跑过（要 BuildTools 编译，本机网速不允许）。
* **矩阵现在是 5 格**（fabric / neoforge / forge / spigot / paper），实测 JSON 合法。
* **又一次同类教训**：往矩阵里插一行时又漏了行尾的 `'`（这已经是第三次了）—— 仍然是"切出来跑一遍"抓到的。
  **以后改这种多行 printf 串，别手拼，直接整块重写 + 立刻跑。**

## 平台重构第 1 步：目录改成「平台/窗口」（2026-09-22）

用户拍板了 `docs/PLATFORM-PLAN.md` 里的四个问题：**拆 2 个（spigot + paper）**、**做 `bukkit-common`**、
**命名用完全体 `fabric/fabric-26.1`**、**不上 version catalog**。第 1 步（纯搬家）已完成并验证。

```
core/                        → 不变
fabric/fabric-26.1/          ← 原 fabric/
neoforge/neoforge-26.1/      ← 原 neoforge/
forge/forge-26.1/            ← 原 forge/
paper/paper-26.1/            ← 原 bukkit/（Paper 系；Spigot 是另一个平台目录）
```

* `settings.gradle.kts` 用 `include("平台:窗口")`（项目路径 `:fabric:fabric-26.1`）；
* **产物名带上平台与窗口**：`mc-qq-fabric-26.1-0.1.0.jar` / `-neoforge-26.1-` / `-forge-26.1-` / `-paper-26.1-`
  —— 不加窗口的话，同一个平台的两个窗口会撞名（用户自己点出来的）；
* `platforms.yml` 的矩阵与 `release.yml` 的附件路径同步更新，矩阵**每个窗口一格 job**；
* 顺带修掉一句错消息：`/qq status` 里显示的配置路径少了 `config/` 前缀（早先我改 `Constants.MOD_ID` 时引入的），
  现在直接用平台给的真实路径（Paper 上显示 `plugins\mcqq\config.yml`）。

**验证**：`clean build` 全绿（47 测试）+ Paper 26.2 真机（`平台 paper-26.2`、`/qq status` 与 `/qq help` 正常）。

**搬家时踩的**：改 `platforms.yml` 矩阵那几行时，我用 python 拼字符串把行尾的 `'` 拼到了下一行行首、
又漏了项之间的逗号 —— 两次都是"切出来跑一遍"抓到的（JSON 解析直接报错）。**这类多行 shell 字符串，
手写一次就要跑一次。**

## runner 钉到 ubuntu-26.04（2026-09-22）

GitHub 提示 "`ubuntu-latest` 将在 2026-10-19 起迁移到 Ubuntu 26"。用户要求改用 26。

**先核实再改**（这条不能猜）：查了 GitHub 官方文档的 runner 表，**`ubuntu-26.04` 现在就已经是可用 label**
（和 `ubuntu-latest` / `ubuntu-24.04` / `ubuntu-22.04` 并列，公共库与私有库都列了，还有 `ubuntu-26.04-arm`）。
顺带纠正一处措辞：`ubuntu-latest` **不是被弃用**，只是届时指向 26。

**改动**：三个工作流的 `runs-on` 全部从 `ubuntu-latest` 改成 **`ubuntu-26.04`**（platforms 1 处、
release 2 处、test 1 处）。理由写进了 platforms.yml 顶部：`-latest` 会在迁移时悄悄换底层系统，
而 CI 的工具链对系统版本敏感（glibc / Java / 构建工具都跟着走），钉死换来可复现；代价是
GitHub 弃用某个镜像时要手动跟进。

**为什么这个 label 不能像平台清单那样共用**：`runs-on` 必须在 job 启动前定下来，而"读一个共享值"
本身得先跑一个 job —— 所以它只能在三个文件里各写一次。

## 修：CI 上矩阵里的版本号是空的（2026-09-22，最新）

用户在 GitHub 上跑 test 时报：

```
Error: No files were found with the provided path: fabric/build/libs/mc-qq-.jar.
```

**`mc-qq-.jar` —— 版本号是空的。** 根因：矩阵那一步用 shell 变量 `$VERSION`，而它是**上一步**（另一个 shell 进程）
设的 —— CI 里**跨步骤的 shell 变量不存在**，只有 `$GITHUB_OUTPUT` / `env:` 能传值。
（讽刺的是我给这段代码写的注释里就说了要小心 heredoc 和 jq，却没注意变量作用域。）

修法：

```yaml
      - name: Generate the platform matrix
        id: matrix
        env:
          VERSION: ${{ steps.version.outputs.version }}   # ← 从上游步骤的 output 注入
        run: |
          matrix=$(printf '...' "$VERSION" ...)
```

**我的验证方式也有问题**：上次我是显式 `VERSION=0.1.0 bash step.sh` 跑的，正好把这个接线错误掩盖了。
现在的做法是**按 CI 的分步方式模拟** —— 两步分别在独立 shell 里跑，中间只通过一个真的 `$GITHUB_OUTPUT`
文件传递，再把它当作 env 注入第二步：

```bash
GITHUB_OUTPUT=/tmp/out1 bash ci-step1.sh        # 第一步
V=$(grep '^version=' /tmp/out1 | cut -d= -f2)   # CI 会做这件事
VERSION="$V" GITHUB_OUTPUT=/tmp/out2 bash ci-step2.sh   # 第二步
python -c "json.loads(...)"                     # 校验 jar 路径里真的有版本号
```

改完实测：矩阵四格的 jar 路径都带 `0.1.0` ✓。

**顺手扫了一遍同类风险**：其余 `run:` 块里的 shell 变量（`$JARS`、`$GITHUB_REF_NAME`）都在同一步内定义，
或者来自 GitHub 自带的环境变量 ✓。

### 顺带：action 版本落后 1–3 个大版本

用户日志里的 `Node 20 is being deprecated` 是**动作自己的运行时**在报（`upload-artifact@v4` 声明 node20，
GitHub 用 Node 24 跑并给出提示），不是我们的问题 —— 但说明版本该升了。查了各仓库最新版后统一升级：

| 动作 | 原 | 现 |
| --- | --- | --- |
| actions/checkout | v6 | **v7** |
| actions/setup-java | v5 | **v6** |
| actions/cache | v5 | **v6** |
| actions/upload-artifact | v4 | **v7** |
| actions/download-artifact | v4 | **v8** |
| gradle/actions/setup-gradle | v6 | v6（已是最新） |

（这些大版本主要是换 Node 运行时；我们用的输入名 `path` / `name` / `if-no-files-found` / `java-version` /
`distribution` 都是长期稳定的那几个。）

## 矩阵抽成单一出处（2026-09-22，最新）

用户指出：平台清单散在三个地方（test 的 artifact 路径、release 的矩阵 JSON、release 的附件），
"后续更新版本要在一个 yml 改，test 和 release 都能用"。

**改法**：新增可复用工作流 `.github/workflows/platforms.yml`（`workflow_call`），输出
`version` / `game_versions` / `matrix`；test 与 release 都 `uses:` 它。于是：

* **平台清单只有一个地方**：加平台 = 往矩阵里加一行
  （`name` / `project` / `jar` / `loaders` / `game-versions` / `properties`）。
* **版本事实只有一个地方**：`gradle.properties`（`mod_version`、`publish_game_versions_*`、
  `minecraft_version_range`）—— platforms.yml 只读不写，四个描述符里的范围本来也是 Gradle 从同一份文件
  expand 出来的。所以"改版本号/改 MC 窗口"根本不碰 workflow。
* 矩阵里多一个 `properties` 字段：**只有 forge 那格**带 `-PwithForge=true`。因为 Gradle 会配置所有 include
  的项目，若每格都带上，其他三格也要白下一遍 Forge 工具链。

**test 与 release 的矩阵用法不同（刻意的）**：

* test：**每平台一格并行**（`fail-fast: false`），每格 `:core:test :<平台>:build` —— 换来失败隔离
  （哪格挂了就是哪个平台坏了），代价是工具链各下一遍。
* release：build **一次**出全四个产物，矩阵只用在发布（拆成四格只会把工具链下载四遍，而发布本来就是每产物一次）。

**验证**：四个 YAML 都过解析；把 platforms.yml 的矩阵那段与"读版本事实"那段**切出来原样执行**，
输出 4 格合法 JSON（`json.loads` 验过）、`version=0.1.0`、`game_versions=[26.1, 26.2]`；
release 里拼 jar 列表那段也跑过，四个路径都对。

## Forge 进 CI + 本地按需开关（2026-09-22）

接着上一条：本机下不动 Forge 工具链，但 **CI runner 的网络没问题**，所以让 CI 来跑它，本地保持可用。

* `settings.gradle.kts` 改成**按需 include**：
  ```kotlin
  val withForge = startParameter.projectProperties["withForge"].toBoolean()
  if (withForge) { include("forge") }
  ```
  默认（本地）→ 4 个项目（core/fabric/neoforge/bukkit）；`-PwithForge=true` → 多一个 `:forge`。
  实测：不带参数时 `./gradlew projects` 只列 4 个 ✓；带参数时会去配置 `:forge`（正好卡在那个下载上，说明开关有效）。
* `test.yml` 与 `release.yml` 的构建步骤都加上 `-PwithForge=true`；artifact、Release 附件、发布矩阵都多一格 forge。

**⚠️ 加这一格时又抓到一个真 bug**：矩阵那段 `printf` 里，`paper` 原来是**最后一项、没有逗号**，
我在它后面插 `forge` 就少了一个逗号 → 产出**非法 JSON**（`fromJson` 会直接炸）。
是"把 `run:` 那几行切出来原样执行"验出来的（`...paper folia"}{"name":"forge"...`），光看代码看不出来。
修好后实测输出 4 格、JSON 合法。

## 加 Forge 模块（2026-09-21）

用户问"你竟然没做 forge"。**确实没做**，补上了 —— 但**构建卡在这台机器的网络上**，所以状态是
"代码写好并编译验证过、还没进构建"。

### 先纠正一个可能的误解

**Forge 没有因为 NeoForge 分家而死**：`maven.minecraftforge.net` 上现有
`26.1.2-64.1.3`、`26.2-65.1.3`、`26.3-66.0.2` —— 26.x 一直在跟。

### Forge 26.x 的 API 与 NeoForge 差得不小（都实测过）

| | NeoForge | Forge 26.x |
| --- | --- | --- |
| 描述符 | `META-INF/neoforge.mods.toml`，`type="required"` | `META-INF/mods.toml`，`mandatory=true`，另有 `pack.mcmeta` |
| 依赖声明 | `[[dependencies.<id>]]` | 同，但字段是 `mandatory` |
| 事件注册 | `NeoForge.EVENT_BUS.addListener(类, 消费者)`（一条总线） | **每个事件自带静态 `BUS`**：`ServerChatEvent.BUS.addListener(消费者)` |
| `MinecraftForge.EVENT_BUS` | — | 只剩迁移用的壳（`EventBusMigrationHelper`） |
| 入口 | `@Mod` + `(IEventBus)` | `@Mod` + `(FMLJavaModLoadingContext)` |
| loaderVersion | `[3,)` | `[64,)`（FML 自己的 build 号） |

**入口类分散在几个 artifact 里**（找它们花了几轮）：`net.minecraftforge.fml.common.Mod` 在
`javafmllanguage`、`fml.loading.FMLPaths` 在 `fmlloader`；而且**版本号是 `26.1.2-64.1.3` 这种
（MC 版本 + FML build），不是 `64.1.3`** —— 直接按 `64.1.3` 取是 404。

**命令那部分完全复用 core**：`QqCommands` 只有 4 行，与 NeoForge 那份逐字相同（同为 vanilla 的
Brigadier + `CommandSourceStack`）—— `BrigadierCommands` 放在 core 的收益第三次兑现。

### 验证到哪一步

* ✅ **代码编译通过**：用 `javac` 直接对着真实 jar 编（`forge-universal` + `fmlloader` + `javafmllanguage`
  + `fmlcore` + `eventbus` + **vanilla 的 MC client jar** + core + brigadier + slf4j + jspecify + fastutil），
  5 个文件全部通过 —— 上面那些事件 API 的用法一个错都没有。
  ⚠️ 注意**不能用 NeoForge 打过补丁的 MC jar** 去编 Forge 代码：里面混着 `net.neoforged.neoforge.*` 接口，
  会报"找不到 ICommandSourceStackExtension"这类假错误。用 `neoformruntime/artifacts/minecraft_26.1.2_client.jar`。
* ❌ **Gradle 构建没跑通**：ForgeGradle 的 mavenizer 在**配置阶段**要下载整套 Forge 工具链，而这台机器到
  Mojang 只有 **~5KB/s**（实测：一个 30MB 的客户端 jar 要近两小时），跑了 22 分钟没有任何文件增长，只能停掉。
* ❌ 真机加载自然也没跑。

### 踩到的坑：截断的 manifest 会被缓存，之后每次重试都秒失败

ForgeGradle 报 `JsonSyntaxException: Unterminated string at column 73729` —— 它下载的
`launcher_manifest.json` **被截断了**（缓存里那份正好 73728 字节 = 72KB，缓冲区边界）。
这台机器到 Mojang 的连接**约 1/10 概率截断**（我连测 10 次：9 次拿到完整的 276542 字节 / 915 个版本，
1 次只有 196020 字节）。要命的是**坏的那份被缓存下来**，之后每次重试都读缓存、秒失败。

**解法**：删掉
`%GRADLE_USER_HOME%/caches/minecraftforge/forgegradle/mavenizer/caches/launcher_manifest.json`，再重试。

### 因此：`include("forge")` 暂时注释掉了

Gradle 配置阶段会配置所有 include 的项目 —— 只要 `:forge` 在里面，**本机任何 `./gradlew` 调用都会失败**
（其他三个平台也编不了）。所以 `settings.gradle.kts` 里那行是注释状态，旁边写清了怎么打开：

1. 网络好的时候（或直接在 CI 上）跑一次 `./gradlew :forge:build`；
2. 成功后去掉 `settings.gradle.kts` 里 `include("forge")` 的注释。

`forge/` 目录本身是完整的：`build.gradle`（照 MDK，ForgeGradle 7 + shadow）、`META-INF/mods.toml`、
`pack.mcmeta`、以及 5 个 Java 文件。

## 发布工作流改成矩阵（2026-09-21，最新）

用户指出的：我第一版把三个产物的 mc-publish 写成了**三步串行**，而 QueQiao 是**矩阵**（每格一次上传，并行）。
改对了 —— 现在 `release.yml` 是：

* `build` job：校验标签/版本号一致 → 算出一个矩阵（三个产物的文件名 + loaders）→ `./gradlew clean build`
  → 存 Actions artifacts → 建 GitHub Release 并附三个 jar。
* `publish` job：`strategy.matrix: ${{ fromJson(needs.build.outputs.matrix) }}`，**每个产物一格、并行**，
  各自 `download-artifact` 后跑一次 mc-publish，各自声明 `loaders` 与 `game-versions`。
  **没有项目 id 时整段 `if:` 跳过** —— 现阶段就是这样：构建、artifacts、Release 照常，只是不上架。

**构建没拆进矩阵**（和 QueQiao 的差别）：那边每格是独立工程所以每格自己 build；我们一次 `./gradlew build`
出全三个产物，拆成三格只会把 MC 工具链下载三遍。矩阵只用在"发布"这一步，那本来就是每个产物一次。

### ⚠️ 一个值得记的教训：YAML 块标量会把"顶格的续行"截断

第一版的矩阵生成我用了 `printf` + 反斜杠续行，写进 YAML 时**那几行续行漏了缩进（0 空格）**。
YAML 的块标量遇到缩进不足的行就**结束**，于是 `run:` 被截成半句、shell 报
`unexpected EOF while looking for matching ')'`。而**我之前的"校验"没抓到** —— 它只检查"顶层键是不是映射"，
截断后 YAML 仍然能解析。

**所以：光解析 YAML 不够，得把 `run:` 那几行切出来原样执行一遍。** 这次的验证方式是：

```bash
START=$(grep -n "matrix=\$(printf" .github/workflows/release.yml | cut -d: -f1)
sed -n "${START},$((START+7))p" .github/workflows/release.yml | sed 's/^          //' > step.sh
VERSION=0.1.0 GITHUB_OUTPUT=/tmp/out bash step.sh   # 跑出来就是 fromJson 要的 payload
```

顺带把 `jq` 也去掉了：它是"又一个要赌 runner 上有没有"的东西，`printf` 到处都有、本地也能照样跑。

## modid 统一成 `mcqq`（2026-09-21，最新）

用户注意到 NeoForge 那边的 id 是 `mc_qq`，问"能不能直接定义为 `mcqq`"。**可以，而且更好** —— `mcqq`
是三家都接受的拼法，于是三平台一个 id，`MOD_ID_NEOFORGE` 这个特例直接删掉。

* **id = `mcqq`**：`fabric.mod.json` 的 `id`、`neoforge.mods.toml` 的 `modId`（含 `[[dependencies.mcqq]]`）、
  `plugin.yml` 的 `name`、`Constants.MOD_ID`、`gradle.properties` 的 `mod_id`、
  配置目录（`config/mcqq/`、Paper 侧 `plugins/mcqq/`，由 `BridgeConfig` 从 `Constants.MOD_ID` 推导，不再写死）、
  资源路径（`assets/mcqq/`）、日志器名。
* **项目名仍是 `mc-qq`**：仓库名、jar 文件名（`archives_base_name`）、聊天栏前缀 `[mc-qq]`。
  这个分工写进 README 了 —— 一句话：**id 是给加载器和日志看的，项目名是给人看的**。
* 顺手修掉两处会漂移的字符串：状态里的 `config/mc-qq/config.yml` 改成由 `Constants.MOD_ID` 拼；线程名同理。

**真机验证**：Paper 26.2（`[mcqq] Enabling mcqq v0.1.0`、配置落在 `plugins/mcqq/`）、
NeoForge 26.1.2（`- mcqq (...)`、`[mcqq/] mcqq loaded`、配置落在 `config/mcqq/`）。

⚠️ **一个开发环境的坑**：改完 id 后 NeoForge 日志里出现
`mc_qq (version 0.1.0 -> MISSING)` —— 那是 `neoforge/run/.cache/jij/` 里**上一次运行留下的 JarJar 缓存**，
不是代码问题（全新安装不会有这个目录）。把它改名/删掉就干净了。**注意它被正在运行的服务端占着**，
先停服再动，否则会看到 `Permission denied`。

## 文档整理（同一天）

* 四份说明移进 `docs/`（PROGRESS / MULTIPLATFORM / QUEQIAO-NOTES / TEMPLATES），
  根目录只留 `README.md` + `LICENSE` + `THIRD-PARTY.md`（后两份是许可文件，惯例放根）。
* README 顶部加了**文档索引**，所有交叉链接都改过了（`docs/` 里的指回根目录用 `../`）。
* `THIRD-PARTY.md` 是**打进 jar** 的，所以它里面的相对链接去掉了（在 jar 里必然断），改成纯文字引用。
* 删掉了 16 个我起测试服留下的 `console*.log`，以及模块化之前遗留在根目录的 `run/`（2.6M，里面的配置目录还叫 `mc-qq`）。
* **测试服目录没有动**（合计约 813M）：`bukkit/run` 396M、`bukkit/run-folia` 274M、`fabric/run-prod` 138M、
  `fabric/run` 2.6M、`neoforge/run` 3M。它们都在 `.gitignore` 里，不进仓库；留着是为了下次验证不用重新下载
  （Mojang 那边现在限速）。要删的话见 README 的说明 —— `bukkit/run-folia` 最可以删（Folia 已验证过，
  它的缓存还是从 `bukkit/run` 复制来的）。


> 这是一份**带日期的状态快照**，不是文档：过时就删掉或重写，别让它长成第二份 README。
> 长期事实（怎么构建、配置项、版本坑）都在 [README.md](../README.md) 里。

## 一句话

**core + fabric + bukkit** 三个模块，`clean build` 全绿，**21 个测试全过**（全部离线，不需要游戏）。
两个平台都在真实服务端上加载并跑过命令：Paper 26.1.2 / 26.2 上插件被接受、配置落盘、`/qq` 整棵树可用；
Fabric 26.1.2 / 26.2 上模组被加载、配置落盘。

**还没证到的是聊天转发**（需要一个真人在 Paper 上打一句话），以及 Folia、Spigot、26.3 那一跳。
平台矩阵的调研与选型在 [MULTIPLATFORM.md](MULTIPLATFORM.md)。

## core/adapter 抽离：已完成并验证（2026-09-21）

```
core/      Constants · BridgeConfig · BridgeRuntime · Bridge · QqToMc · QqSender · Log
           + 接缝 MinecraftPlatform
           + command/：SubCommand · RootCommand · CommandTree · sub/{Status,Reload,Help}Command
           全部 release 21、零 MC 依赖，21 个测试在这
fabric/    McQqMod（入口）· McToQq（4 个监听）· QQCommands（Brigadier）· FabricPlatform · Slf4jSink
bukkit/    McQqPlugin（入口）· McToQq（4 个监听）· QqCommand（plugin.yml）· BukkitPlatform · JulSink
```

### 命令树搬进 core（2026-09-21，本轮新增）

参考 QueQiaoTool 的 `SubCommand` / `CommandExecutorHelper`（`/root/.../tool/command/`）做了这件事：
**命令树、命令介绍、权限节点、补全全部在 core，adapter 只注册整棵树**。

* `SubCommand`：树的一个节点。`path()` / `usage()` / `permissionNode()` 全部**从位置推导** ——
  `/qq status` → `mcqq.status`，所以三者不可能对不上。
* `CommandTree`：`execute(args)` / `complete(args)` / `resolve(args)` / `helpLines()`。adapter 只调这几个。
* `Constants`：mod id、输出前缀 `[mc-qq]`、命令名、权限根 `mcqq`、OP 等级。这些是"adapter 之间最容易对不上"的东西。
* 接缝随之从 `registerQqCommand(QqCommandHandler)` 变成 `registerCommands(CommandTree)`，`QqCommandHandler` 删掉了。

**收益是量出来的**：fabric 的 `QQCommands` 现在不认识任何具体子命令，只遍历 `tree.root().children()`
建 Brigadier 字面量 + 一个 greedy 参数；bukkit 的 `QqCommand` 只剩"解析 → 判权限 → 打印"三行逻辑，
补全也交给 `tree.complete(...)`。**加一个子命令 = core 里加一个类 + 在 `Bridge.buildCommands()` 里挂一行，
两个 adapter 一行都不用改。**

新增测试 `CommandTreeTest`（8 个）：树的结构、路径推导出的权限节点与用法、`help` 的生成、
未知子命令的提示、补全前缀过滤、未启动时的 status。**这些全部离线跑** —— 这正是把命令放 core 的收益。

### 再进一步：把"分发与权限策略"也搬进 core（同日，用户提议）

第一版只搬了**树**，adapter 里还留着策略的碎片：判权限放哪、拒绝时说什么、前缀谁加。
按用户的意思（鹊桥是这么做的）把这一层也收进 core，用一个两方法的接口代替鹊桥的 `Object sender`：

```java
// core
public interface CommandSource {              // 一次调用的上下文，adapter 现场包一个
    boolean hasPermission(String node);       // 语义由平台定：Bukkit 查节点，mod 平台答 OP 等级
    void reply(String line);                  // 收到的是最终一行（core 已经加好前缀）
}
CommandTree.run(CommandSource, args)          // 判权限 → 执行 → 逐行 reply；命令抛异常也在这里兜住
CommandTree.completions(CommandSource, args)  // 补全，且过滤掉调用者没权限的
```

于是 adapter 侧只剩"把参数交出去"：

```java
// bukkit
public boolean onCommand(sender, command, label, args) { tree.run(new BukkitCommandSource(sender), List.of(args)); return true; }
public List<String> onTabComplete(...) { return tree.completions(new BukkitCommandSource(sender), List.of(args)); }
// fabric 仍是遍历 children 建 Brigadier 字面量（这样 /qq status 是真正的子命令、补全由 Brigadier 给），
// 但每个 executes 只是一句 tree.run(new FabricCommandSource(source), args)
```

**比鹊桥好在哪**：`Object sender` 要 cast、编译期没有保护；这里是个两方法的接口，平台实现它，
core 不需要知道 sender 是什么类型。**照抄鹊桥的一处**：`SubCommand.execute` 里的异常兜底 —— 命令抛异常
不该把服务端的命令处理带崩，现在 `run` 会把它变成一行"命令执行失败"。

新增测试 4 个（带前缀输出、无权限时的拒绝与节点名、补全过滤、抛异常被兜住），**测试总数 9 → 21**。

复验：Paper 26.2 上 `qq status` / `qq help` / `qq nonsense` / `qq reload` 输出与重构前完全一致
（前缀现在由 core 加，所以更容易保证各平台一致）。

### 真机复验（Paper 26.2 build 126，同一台测试服）

换了命令分发与 `plugin.yml` 权限声明之后重跑了一遍：

```
$ qq
[mc-qq] 用法：qq <status|reload|help>，详见 /qq help
$ qq status
[mc-qq] 平台 paper-26.2
$ qq help
[mc-qq] /qq — QQ 桥接的主命令
[mc-qq] /qq status — 显示每个 bot 的在线状态、绑定的群与配置里读出来的问题
[mc-qq] /qq reload — 重读配置并换掉正在跑的 bot（不会重启服务器）
[mc-qq] /qq help — 列出所有子命令
$ qq nonsense
[mc-qq] 没有子命令 nonsense；可用：status|reload|help
$ help qq
§6Description: §fQQ 桥接的状态与重载
§f§6Usage: §f/qq <status|reload|help>
```

Paper 没有对 `plugin.yml` 的 `children` 权限写法报错，服务端也认得这个命令（`help qq` 有输出）。

⚠️ 一条环境教训：清理测试目录时别用 `rm -rf` 扫一堆文件 —— 本机沙箱会拦
（`SAFE_DELETE_BULK_CONFIRM_REQUIRED`，>50 项即触发）。测试服目录直接复用即可，不必清。

### 26.1 的权限 API 换了（顺手记下）

`Commands.hasPermission(int)` 在 26.1 已经不存在了：现在是
`net.minecraft.server.permissions.PermissionCheck` / `PermissionSet` / `PermissionLevel`，
`Commands.LEVEL_GAMEMASTERS` 是 `PermissionCheck` 常量，`hasPermission(PermissionCheck)` 返回
`PermissionProviderCheck`。所以 fabric 侧只能用它自己的名字表达"OP 2"，core 的 `Constants.OP_LEVEL`
只是这个意图的单一出处（Forge 系 API 仍然收 int）。

验证过的（`GRADLE_USER_HOME=E:/Build/Gradle ./gradlew clean build`，15s 全绿，17 个任务）：

* `core/build/libs/mc-qq-core-0.1.0.jar` 字节码 **major 65 = Java 21**；`net/minecraft|net/fabricmc` 类 **0 个**。
* `fabric/build/libs/mc-qq-0.1.0.jar`（4.38MB）与 `bukkit/build/libs/mc-qq-bukkit-0.1.0.jar`（4.38MB）：
  未 relocate 的第三方 **0**；core 的 15 个类都已并入；bukkit 里 `org/bukkit|io/papermc|net/kyori` **0 个**
  （paper-api 是 compileOnly，没被打进去）；`plugin.yml` 的 `${version}` 已展开成 `0.1.0`。
* 测试 **9 个**（BridgeConfigTest 6 + QqSenderTest 3），仍然全离线 —— 命令树搬进 core 之后变成 **21 个**
  （多出 `CommandTreeTest` 8 个），见下一节。
* **接缝在第二个平台上成立**：同一份 core jar 没改一行，同时被一个 mod 和一个插件各自打包。

抽离时踩到并记下来的：

1. **`core` 用 `--release 21` 而不是 17**：21 是虚拟线程的最低 API 等级，而 MC 从 1.20.5 起就是 Java 21，
   所以 21 同时覆盖 1.21.x 与 26.x 两代。写 17 就得用反射兜虚拟线程（已试过，能用，但没必要）。
2. **多模块后 `repositories` 不再继承**：必须在根 `build.gradle.kts` 的 `subprojects {}` 里声明，
   否则 `:core:compileJava` 报 "no repositories are defined"。
3. **`property()` 在 `tasks.withType<JavaCompile>().configureEach {}` 里解析到的是 task 不是 project**，
   要先在块外取出来（`val coreRelease = property(...)`）。
4. **`version` 也要在根上给子项目**，否则 core 的 jar 没有版本号（`mc-qq-core.jar`）。
5. **Paper 26.1.2 的 API 已经变过**：`getCommand(String)` 不在 `org.bukkit.plugin.Plugin` 上了（在 `JavaPlugin`），
   `Server.broadcastMessage(String)` 被标记过时（改用 Adventure 的 `sendMessage(Component)`），
   而 `Server` 现在是 `ForwardingAudience`。这些都用 `javap -cp paper-api.jar` 逐个确认过。
6. **定位 deprecation 要用编译器，不要用 `javap -v | grep "Deprecated: true"`** —— 后者对
   `broadcastMessage` 没报出来，`-Xlint:deprecation` 一句话就点到了行号。bukkit 模块已常开这个开关。

另外：core 只 `compileOnly` SDK 与 snakeyaml，**不带**这些依赖；每个平台自己 shadow + relocate 进 jar
（`bundled(project(":core"))` 让 core 的类也进 shadow 配置）。日志走 core 自己的 `Log.Sink` 接缝 ——
MC 给 slf4j，Bukkit 给 `java.util.logging`，两边不能共用一种。

### 真机验证：Paper 26.1.2（2026-09-21，本次新增）

起了一个一次性 Paper 26.1.2（build 74，`fill.papermc.io`，sha256 校验过）把 bukkit 那个 jar 真跑了一次。

服务端侧（`bukkit/run/console.log`）：

```
[PluginInitializerManager] Initialized 1 plugin
Bukkit plugins (1):
 - mc-qq (0.1.0)
[mc-qq] Loading server plugin mc-qq v0.1.0
[mc-qq] Enabling mc-qq v0.1.0
[mc-qq] mc-qq loaded; the QQ bridge comes up with the server
[mc-qq] wrote the default config to plugins\mc-qq\config.yml; fill in app-id and the group openids
[mc-qq] config: bot main still has the REPLACE_ME app-id from the template, skipped
Done (15.573s)!
```

→ 插件被 Paper 接受（`plugin.yml` 与 `api-version: '26.1'` 有效）、`onEnable` 跑通、**core 整条链路跑通**
（配置模板落盘 + 解析出问题并上报）、日志接缝把 core 的行写进了服务端控制台。

命令侧（RCON，`qq status` / `qq reload`）：

```
$ qq status
[mc-qq] 平台 paper-26.1.2
[mc-qq] 配置里没有 bot：编辑 config/mc-qq/config.yml 后 /qq reload
[mc-qq] bot main still has the REPLACE_ME app-id from the template, skipped
$ qq reload
[mc-qq] 已重载配置
```

→ **`/qq status` 与 `/qq reload` 的实际执行证到了** —— 这两条从最早的 Fabric 轮次起就一直挂在"没证到"里
（loom 的 `runServer` 喂不进控制台命令）。命令的**答案**来自 core（`Bridge.statusLines()` / `Bridge.reload()`），
所以 Fabric 侧那份命令逻辑的风险也随之降下来了，差异只剩 Brigadier 的语法。

顺带把 Spigot 那个悬案查清了（静态证据，非运行时证明）：Paper 26.1.2 的
`io.papermc.paper.adventure.ChatProcessor` 里**同时**有现代与旧路径 —— `AsyncChatEvent`、`AbstractChatEvent`、
`ChatEvent`，以及 `AsyncPlayerChatEvent`、`PlayerChatEvent`，还有 `readLegacyModifications` / `legacyRenderer` /
`legacyFormat` 与 `MESSAGE_CHANGED`/`FORMAT_CHANGED`/`SENDER_CHANGED` 这几个标志位。
也就是说**旧事件在 26.1.2 的聊天处理链路里还在**，只是没有真人聊天去触发它。所以"以后要不要支持 Spigot"
的结论是：路子还在，但真要声称支持，得让一个玩家在 Paper 上打一句话实测。

复现方式：`bukkit/run/` 里放 `eula.txt`、`server.properties`（端口 25612 + 开 RCON）、把 jar 丢进 `plugins/`，
用 `java -jar paper-26.1.2-74.jar --nogui` 起，再用 `D:\Project\Java\.workbuddy-ai\refs\rcon.py`（自己写的小 RCON 客户端）
发命令。⚠️ `bukkit/run/` 现在占 **221M**（libraries 73M + cache 58M + paper jar 52M + world 6.4M），不要了可以直接删。

## 发布工作流（2026-09-21，本轮新增）

**之前的状态：没有。** 仓库里一个 CI 文件都没有，只有本地 `./gradlew clean build`。用户问起才发现 ——
我先前把它归到"有了 git 再写"，那个理由不成立（工作流文件放着等 push 就行）。

**仿照的对象是 `17TheWord/QueQiao` 的 `.github/workflows/build.yml`**（不是 `QueQiaoTool` —— 后者只是库，
仓库里只有 `tool/` 一个模块，平台实现是各自的仓库）。QueQiao 的发布链：`matrix.sh` 扫"平台×版本"目录出 JSON
→ 每格独立 `clean build` → `Kir-Antipov/mc-publish@v3.3` 按各自的 `loaders` / `game-versions`
发 Modrinth + CurseForge（`gtnh|cleanroom` 映射成 `forge`）→ vanilla 的 jar 单独传 GitHub Release。
`release.yml` 则负责在推 main 时"删掉同版本 Release 再重建"。

**本项目做了三个文件**：

| 文件 | 触发 | 干什么 |
| --- | --- | --- |
| `actions/set-java/action.yml` | 被调用 | JDK 25、Gradle 缓存、从 `gradle.properties` 导出 `VERSION` |
| `workflows/test.yml` | 推 main / PR | `./gradlew clean build` + 三个 jar 存 artifact |
| `workflows/release.yml` | 打 `v*` 标签 | 校验标签与 `mod_version` 一致 → 构建 → GitHub Release → 三个产物各一次 mc-publish |

**与 QueQiao 的四处差异（都是刻意的）**：

1. **不抄 matrix**：三个产物、一次构建就出全；矩阵是 QueQiao 有十几个格子才需要的。抄的是发布那一步的形态：
   一个产物一次 mc-publish，各自声明 `loaders`（`fabric` / `neoforge` / `paper folia`）与
   `game-versions`（`[26.1, 26.2]` —— 实测过的窗口，不写 26.3）。
2. **标签触发而不是推 main 触发**：三个产物同版本、一次构建，打标签比每次合并都发更不容易误发。
3. **多一步"标签与 `mod_version` 一致"的校验**：不一致就失败，免得产出的文件名与标签对不上。
4. **没配项目 id 时跳过发布而不是失败**：`if: env.MODRINTH_ID != ''`，这样在项目上架前也能先发 GitHub Release。

另外：标签带连字符（`v0.2.0-beta.1`）自动当预发布，Modrinth 的 `version-type` 也跟着变 beta。

⚠️ **验证程度**：这三个 YAML **只做了语法校验**（用服务端自带的 snakeyaml 解析，三个都过）。
GitHub Actions 本地跑不了，所以"逻辑对不对"要等第一次真跑。另外 snakeyaml 会把 `on:` 读成布尔 `true`
（YAML 1.1 的老毛病），GitHub 自己的解析器没这个问题，**不要为它改**。

## 许可证与发布前整理（2026-09-21，本轮新增）

**许可证定为 MIT**（与同作者的 QueQiaoTool 一致）。改动：`LICENSE`（MIT, 2026 17TheWord）、
`fabric.mod.json` 与 `neoforge.mods.toml` 里的 license 字段、新增 `THIRD-PARTY.md`。

**一个与选哪个许可证无关的合规缺口（已补）**：产物里**打包了 Apache-2.0 的库**
（qqbot-java-sdk 0.0.4、okhttp 4.12.0、okio 3.6.0、kotlin-stdlib 1.9.10、gson 2.11.0、snakeyaml 2.5、
error_prone_annotations 2.27.0、jetbrains annotations 13.0 —— 版本是从 `:bukkit:dependencies --configuration
bundled` 的解析结果核实的，我一开始凭印象写错了 okio 与 kotlin 的版本）。Apache-2.0 要求分发时附许可证副本、
保留 NOTICE，所以：

* 根 `build.gradle.kts` 里给所有 shadowJar 加了 `from(LICENSE)` 与 `from(THIRD-PARTY.md)`；
* 三个产物都验证过（`unzip -l | grep -cE " (LICENSE|THIRD-PARTY.md)$"` → 每个都是 2）。

**Spigot 边界从"会崩"改成"会说话"**：以前把插件丢进 Spigot 会在事件注册时抛 `NoClassDefFoundError`，
留下一句难懂的堆栈。现在 `onEnable` 先检查 Paper 的聊天事件类在不在，不在就打印
"需要 Paper 系服务端…插件已停用" 并自行停用。**注意**：这不是"支持 Spigot"，只是把不支持说得清楚。
Paper 上复验：正常启用（`平台 paper-26.2，主线程调度走 经典调度器`）、`/qq status` 正常。

## Folia：真机验证 + 一个被修掉的判断错误（2026-09-21，本轮新增）

Folia 此前只有"调度路径写了但没跑过"。它有公开发行版（`fill.papermc.io` 的 `folia` 项目），所以能实测：

```
[bootstrap] Loading Folia 26.1.2-8-ver/26.1.x@62dc0f2 for Minecraft 26.1.2
 - mc-qq (0.1.0)                                    ← 声明 folia-supported 之后被接受
[mc-qq] 平台 folia-26.1.2，主线程调度走 区域调度器
[mc-qq] mc-qq loaded; the QQ bridge comes up with the server
RCON running on 0.0.0.0:25678
Done (17.097s)!
```

RCON 上 `/qq status` / `/qq help` / `/qq test` 全部正常，平台标识是 `folia-26.1.2`（取自 `server.getName()`）。

### 顺手修掉的一个判断错误

原来的探针是"`server.getGlobalRegionScheduler()` 能不能拿到" —— **这个判据是错的**：Paper 也实现了那套调度器，
所以在 Paper 上它同样返回成功。真机日志直接暴露了这点：

```
[mc-qq] 平台 paper-26.2，主线程调度走 区域调度器（Folia 系）   ← 错：Paper 不是 Folia
```

改成**用只在 Folia 服务端里存在的类**判断（`io.papermc.paper.threadedregions.RegionizedServer` ——
对比过两个服务端 jar，Paper 里没有这个类）。修完两边各报各的：

```
[mc-qq] 平台 folia-26.1.2，主线程调度走 区域调度器
[mc-qq] 平台 paper-26.2，主线程调度走 经典调度器
```

**广播路径也按 Folia 的规则改了**：Folia 没有"唯一主线程"，每个玩家归自己的区域管，从别的区域碰他会抛。
所以 `broadcast` 在 Folia 上改为**按玩家**排到各自的 `player.getScheduler()`，而不是全局播一次。
`plugin.yml` 加了 `folia-supported: true`。

⚠️ **仍然没验的**：Folia 上的**广播**本身。要触发它需要一个真人玩家 + 一条入站 QQ 消息 ——
现在没玩家时按玩家循环跑零次，两条路径都不会报错，所以"跨区域访问会不会抛"这件事只能靠文档而不是实测。
（Folia 的调度规则是照着官方 API 写的，不是猜的，但没跑过。）

## `/qq test`：主动验证 QQ 那条链路（2026-09-21，本轮新增）

在此之前，服务端侧没有任何办法**主动**验证"配置的凭证与群 openid 对不对" —— 只能等真人聊一句，
或者等 QQ 来消息。新增 `/qq test`：往每个配置的群各发一条，走的是**和真实事件完全相同的发送路径**
（不一样的路径测不出真实问题）。

**它不阻塞主线程**：命令跑在服务端的命令线程上，而 QQ 往返不该卡在那里。所以它只负责排队，
每条的结果由日志报出（成功也记 info，不只是 debug）：

```
$ qq test
[mc-qq] bot main → 群 主群：已排队，结果见服务端日志
（日志）测试消息 → 群 主群：已发出 / 平台拒绝 err_code=… / 平台收下了，但进了人工审核
```

真机验证了两个"没配好"的分支（都没有凭证，正好）：

```
$ qq reload
[mc-qq] bot main [未注册] 群：[主群(收=true, 发=[CHAT, JOIN, QUIT, DEATH])]
[mc-qq] bot main: 环境变量 QQ_BOT_SECRET_NOT_SET 未设置，已跳过
$ qq test
[mc-qq] bot main：未注册（凭证缺失或启动失败），跳过
```

以及空配置：`[mc-qq] 配置里没有 bot，没有可测的群`。

**成本**：core 里一个类（`TestCommand`，17 行）+ `Bridge.test()` / `BridgeRuntime.test()`，
**两个 adapter 一行都没改** —— 命令树在 core 的直接红利。`QqSender.send` 改为返回一个 `Outcome`
（投递/审核/失败 + 一句话说明），事件路径忽略它、`/qq test` 用它。`plugin.yml` 的 `children` 多一个
`mcqq.test`。

新增 `TestCommandTest`（3 个，离线）：凭证缺失时点名而不是假装、空配置如实说、未启动时说未启动。
**测试 44 → 47。**

## 命令注册去重：Brigadier 是独立库，所以能放 core（2026-09-21，本轮新增）

fabric 与 neoforge 的 `QqCommands` 原本逐行相同（各约 60 行）。抽 `common` 模块不是唯一出路 ——
**Brigadier 是 Mojang 的独立库，不是 Minecraft 类**，而 26.1.2 的 `Commands.literal(String)` 反编译出来就是
两条指令（转调 `LiteralArgumentBuilder.literal`）。所以"遍历 core 的命令树、建 Brigadier 节点"整段可以泛型化后放进 core：

```java
// core：S 是平台自己的 sender 类型，core 只往回要一个 CommandSource
public static <S> void register(CommandDispatcher<S> dispatcher, CommandTree tree,
        Predicate<S> permitted, Function<S, CommandSource> sourceOf);
```

于是两个 adapter 各剩一行：

```java
BrigadierCommands.register(dispatcher, tree,
        Commands.hasPermission(Commands.LEVEL_GAMEMASTERS),
        FabricCommandSource::new);        // neoforge 那份只有 sender 类型不同
```

* core 只多一个 **compileOnly** 依赖：`com.mojang:brigadier:1.3.10`（**Maven Central 上没有**，
  在 Mojang 的 `libraries.minecraft.net`；1.3.10 就是 MC 26.1.2 自带的版本，从服务端的 `libraries/` 里看到的）。
* 没有新模块，core 里也没有 Minecraft 类；bukkit 侧完全不受影响（它不用 Brigadier）。
* **行数**：fabric 60 → 27、neoforge 60 → 27（还大半是注释），共享逻辑 81 行在 core 一份。
  以后加 Forge 那类平台，命令注册是 10 行而不是 60 行。

**验证**：NeoForge 开发服与 **Fabric 生产服**（加载打包产物）两边 `/qq help` 都列出全部四个子命令 ——
后者顺带说明这段共享代码在 shadow jar 里也正常。

## 调试开关 + 路由抽成纯函数（2026-09-21，本轮新增）

**A3（调试开关）**：配置加 `debug: false`。打开后，**每一个"为什么不转发"的判断分支都会写一行日志**，
转发成功也会打印实际内容：

```
不转发 CHAT → 群 主群：该群没订阅这个事件
不转发 CHAT → 群 主群：模板为空（= 静音）
不转发 CHAT → 群 主群：bot main 未注册（凭证缺失或启动失败）
转发 CHAT → 群 主群：[MC] Alice: 你好
已发往 QQ 群 主群：[MC] Alice: 你好
```

入站方向同样补了：群没绑定 / 只出不进 / 没有文字 / 平台重推 —— 以前这些分支是**静默**的，
而"消息没到"恰恰是最难查的问题。`Log.Sink` 多了 `debug` 一级（slf4j 走 `debug`，JUL 走 `FINE`）。

**顺手把路由抽成纯函数**：`BridgeRuntime.plan(event, values)` 返回"该发给谁、发什么"的列表，
`forward` 只负责查 bot 再投递。这样**路由决策可以离线测**（不需要服务端、bot 或 HTTP）：

新增 `ForwardPlanTest`（7 个）：只有订阅了的群收到、行由事件的值渲染、群级模板覆盖生效、
空模板 = 什么都不发、`§` 颜色码在给 QQ 之前被剥掉、`{platform}` 这类上下文占位符有值、
一个 bot 多个群各投一份且带着所属 bot。

**测试总数 37 → 44。** 至此 MC→QQ 这条链路上，除了"MC 到底会不会触发事件"（那要真人打一句话或协议级客户端）
与"QQ 平台那边"（要凭证），其余每一段都有覆盖。

## 打包产物验证：三个平台都加载过真正的 jar（2026-09-21，本轮新增）

在此之前只跑过开发服（Fabric 的 loom dev run / NeoForge 的 in-dev 目录），**加载的是 classes 目录而不是
shadow jar**，所以"relocate 之后的类加载是否被接受"一直没证。现在两个 mod 平台都补上了，外加一次 Bukkit 的
生产服务端（早先做过）：

| 平台 | 形态 | 结果 |
| --- | --- | --- |
| Paper 26.1.2 / 26.2 | 真服务端 + `plugins/` 里的 jar | 已验（更早那轮）：插件加载、命令可用 |
| Fabric 26.1.2 | **真服务端**（fabric-server-launch）+ `mods/` 里的 shadow jar | `- mc-qq 0.1.0`；`/qq status` → `平台 fabric-26.1.2` |
| NeoForge 26.1.2 | dev 启动器 + `run/mods/` 里的 shadow jar | `- mc_qq (jar(mods/mc-qq-neoforge-0.1.0.jar))`；`/qq status` → `平台 neoforge-26.1.2` |

**relocate 在生产环境可用的直接证据**：配置的读写走 `org.yaml.snakeyaml`，它在产物里被改成了
`com.example.mcqq.shaded.org.yaml.snakeyaml`。服务端能写出模板并解析出 `REPLACE_ME` 的问题，
说明被 relocate 的类真的加载并运行了 —— 不只是"jar 里没有未 relocate 的引用"这种静态检查。

### 怎么验打包产物（两边的做法不同）

* **Fabric**：搭一个真服务端。启动器 URL **必须带 installer 版本**：
  `https://meta.fabricmc.net/v2/versions/loader/<MC>/<loader>/<installer>/server/jar`
  —— 少一段就返回 9 字节的 `Not Found`（我第一次就这么踩了）。第一次启动要拉 50MB+ 依赖，可能超时，重试即可。
  `mods/` 里放 shadow jar + Fabric API。
* **NeoForge**：dev 启动器也能加载 jar，但**要先把 `neoForge { mods { } }` 块注释掉**，
  否则同一个 modId 会被发现两次（一次来自 in-dev 目录、一次来自 jar）。然后把 jar 丢进 `run/mods/`。
  这一条写进 `neoforge/build.gradle` 的注释里了。

### ⚠️ 踩的坑：`runServer` 不重建 shadow jar

我第一次验 NeoForge 产物时复制的是**旧 jar**（modId 修复之前构建的），于是 FML 报
`Invalid modId found ... mc-qq does not match the standard` / `not a valid mod file` —— 看起来像打包问题，
其实是产物过期。**验打包产物之前必须 `:neoforge:build`（而不是 `runServer`），并核对 jar 里的描述符。**

### 顺带：JIJ 能不能替代 relocate？（结论：不能）

用户问的。Fabric 与 NeoForge 都有 Jar-in-Jar，但**JIJ 只负责"把库带上"，不改包名**；relocate 负责"别撞车"。
查了 Paper 服务端自带的库（`libraries/`，152 个 jar）：**gson 2.13.2 / 2.14.0、snakeyaml 2.2 / 2.6 是服务端自带的**，
而我们的 SDK 依赖 gson 2.11、snakeyaml 2.5。只 JIJ 不 relocate 就是两套同名包抢同一个名字，
Fabric 是扁平类加载，谁赢取决于加载顺序 —— 行为随环境变。kotlin-stdlib 更是 mod 生态里最经典的撞车项。
JIJ 的真正优势是**去重**（两个 mod 都 JIJ 同一版本时可以只留一份，NeoForge 的 JarJar 还能按版本范围挑），
但 Bukkit 根本没有 JIJ，而我们希望四个平台只有一套打包故事。**保持 shadow + relocate。**

## neoforge 适配器：完成并真机验证（2026-09-21，本轮新增）

第三个平台，也是**文档里那条最大架构风险的答案**：**loom 与 moddev 在同一个 Gradle 构建里共存了**
（`:fabric:build` 与 `:neoforge:build` 同一次 `clean build` 都通过）。MULTIPLATFORM 第 7 节的风险 #2 解除。

```
neoforge/  McQqMod(@Mod) · NeoForgePlatform · NeoForgeCommandSource · QqCommands · McToQq
           build.gradle（Groovy）· META-INF/neoforge.mods.toml
```

* **这个模块用 Groovy DSL**：ModDevGradle 官方只发 Groovy 示例，照抄上游意味着下次升 NeoForge 是对上游做 diff，
  而不是把一份 Kotlin 翻译再翻一遍。其余模块仍是 `.kts`。
* 事件不用注解：`NeoForge.EVENT_BUS.addListener(事件类, 消费者)`，一行一个，不需要扫描带注解的类。
  命令挂在 `RegisterCommandsEvent` 上（它在服务端构建命令树时触发，那时桥接已经存在）。
* **平台面与 fabric 一样薄**：事件只交值（`Templates.values(...)`），命令只把参数交回 `CommandTree.run`。
  `QqCommands` 与 fabric 那份逐行相同（同为 Brigadier + `CommandSourceStack`）—— **重复约 95 行**，
  没有抽公共模块是因为唯一合适的家是"编译对 Minecraft 的模块"，等第四个 MC 原生平台出现再抽。

### 踩到的三件事

1. **NeoForge 的 modId 不允许连字符**（硬约束，FML 直接拒绝启动）：
   ```
   Invalid modId found in file ...classes\java\main - mc-qq does not match the standard:
   ^(?=.{2,64}$)[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)*$
   ```
   Fabric 与 Bukkit 都接受 `mc-qq`，所以只有 NeoForge 需要另一种拼法 →
   `Constants.MOD_ID_NEOFORGE = "mc_qq"`（`gradle.properties` 的 `neoforge_mod_id` 是它的构建侧对应）。
   `mods { }` 块的名字也必须等于 modId。**要不要干脆把三平台统一改成 `mc_qq`？** 那是品牌决定，改起来是 5 分钟的事。
2. **Groovy DSL：`bundled project(':core')` 不工作** —— 局部变量 `bundled` 遮蔽了依赖处理器上的动态方法，
   Groovy 会去 `Configuration.call(...)`。改成 `add('bundled', ...)`。（Kotlin DSL 那边没这个问题。）
3. **`SharedConstants.getCurrentVersion().getName()` 不存在**：26.x 的 `WorldVersion` 是记录式接口，
   用 `name()`。

### 真机验证（`./gradlew :neoforge:runServer`，开发服）

```
MC ↔ QQ Bot 0.1.0 (mc_qq)
 - mc_qq (composite(folder(classes/java/main), folder(resources/main)))
[mc-qq/] mc_qq loaded; the QQ bridge comes up with the server
Done (0.355s)!
[mc-qq/] wrote the default config to ...\neoforge\run\config\mc-qq\config.yml
[mc-qq/] config: bot main still has the REPLACE_ME app-id from the template, skipped
```

RCON 上跑命令树：

```
$ qq status
[mc-qq] 平台 neoforge-26.1.2
[mc-qq] 配置里没有 bot：编辑 config/mc-qq/config.yml 后 /qq reload
$ qq help
[mc-qq] /qq — QQ 桥接的主命令
[mc-qq] /qq status — …  /qq reload — …  /qq templates — …  /qq help — …
```

配置落在 `config/mc-qq/`，与 Fabric 侧**同构**（core 里 `configDir()` 拼的就是这个）—— 一份配置在两边通用。

### 还没证的

* **shadow jar 在 NeoForge 上没加载过**：开发服加载的是 `classes/java/main` + `resources/main`（moddev 的 in-dev 目录），
  不是打包后的 jar。所以"relocate 之后 NeoForge 的类加载是否接受"**仍未验证**（Fabric 侧同样是这个状态）。
  最省的验证办法：临时把 `mods { }` 块去掉、把 shadow jar 丢进 `neoforge/run/mods/`，跑一次 —— 那样加载的就是真正的产物。
* 首次构建 NeoForge 侧要跑一次反编译/打补丁（约 155s），之后走缓存（`neoforge/build/moddev/artifacts/`）。

## 消息模板：文案从代码搬进配置（2026-09-21，本轮新增）

**改动**：每条消息的文案不再是 Java 里的字符串，而是配置里的模板 + 具名占位符。

```
core/Templates.java     8 个模板键 + 占位符全集 + render/withContext/values + 拼错的占位符检测
core/command/sub/TemplatesCommand.java   /qq templates：当前生效的文案 + 可用占位符 + 可粘贴的写法
BridgeConfig            全局 templates: + 群级 templates:（逐键覆盖）+ 加载时校验
BridgeRuntime/QqToMc    按群渲染模板；空模板 = 不播报；MC→QQ 方向照旧剥掉 §
fabric/bukkit McToQq    不再拼句子，只交出值：Templates.values("player", name, "text", text)
```

* 键：`qq-chat` / `qq-attachment` / `qq-member-add` / `qq-member-remove` / `mc-chat` / `mc-join` /
  `mc-quit` / `mc-death`。占位符：`{group} {user} {text} {count} {member} {player} {killer} {platform} {time}`。
* **优先级：群级 > 全局 > 内置默认**；群级只写要覆盖的键；空串 = 静音。
* 拼错的占位符**不替换、原样显示**，并在加载时进 `problems()`；未知的模板键会被忽略并报出来。
* 这也把 `QUEQIAO-NOTES.md` 的 **B1（事件传结构化数据）** 一起解决了 —— 没有具名值就没有可填的占位符。

### A1「配置归并」：样例文件 + 补键 + 备份（用户提的形态，已实现）

我第一版是"不写用户文件，只在 `/qq status` 提示 + `/qq templates` 打印可粘贴写法"。用户指出更好的做法：
**把带注释的模板另存一份样例文件，然后放心去补真实文件**。已按这个改，它确实解决了注释问题：

```
config/mc-qq/
  config.yml          真正生效的；缺键时被补全
  config.example.yml  打包模板的副本，带全部注释，每次启动刷新（内容一致就不动）
  config.yml.bak      只在补过键时出现，是补之前那一版的逐字节副本
```

补进 `config.yml` 时先写两行头注释指向 `config.example.yml` 与 `.bak`，再 dump 整个 map。
**只补 `templates`**（那是默认值），绝不动 `bots`（那是示例 —— 归并它会往别人能跑的配置里塞一个
REPLACE_ME 的 bot）。补进去的值**本来就已通过内置默认生效**，所以这一步不改变行为，只是让用户看得见、改得动。

仍然保留：`/qq status` 的一行提示（只在文件里真的没有 `templates:` 段时出现，**不是 problem、不打 WARN**）
与 `/qq templates`（打印生效值 + 可粘贴写法）。

### 真机验证（Paper 26.2，两条路径都跑了）

1. **老配置升级**（服务器上那份是旧模板，没有 `templates:` 段）：

```
$ qq status
[mc-qq] 平台 paper-26.2
[mc-qq] 配置里没有 bot：编辑 config/mc-qq/config.yml 后 /qq reload
[mc-qq] 文案用的是内置默认（配置里没有 templates 段）；/qq templates 看当前生效的文案
$ qq templates
[mc-qq] 占位符：{count} {group} {killer} {member} {platform} {player} {text} {time} {user}
[mc-qq] qq-chat = §b[QQ {group}]§r {user}§7:§r {text}
... 8 行生效值 ...
[mc-qq] 配置里没有 templates 段（用的是内置默认）。要改就把下面这段粘进 config.yml，只写想改的键也行：
[mc-qq] templates:
[mc-qq]   qq-chat: "§b[QQ {group}]§r {user}§7:§r {text}"
...
```

2. **配置里真写了模板**（追加一段含 `mc-join: ""` 与一个拼错的 `{who}`，然后 `/qq reload`）：

```
[mc-qq] 全局 的模板 qq-chat 用了不存在的占位符 [who]（可用：killer, group, member, user, platform, player, count, text, time）
$ qq templates
[mc-qq] qq-chat = {who} 说：{text}          ← 照旧生效（报出来但不拦）
[mc-qq] mc-chat = [测试] {player}: {text}   ← 配置生效
[mc-qq] mc-join =                            ← 空串 = 静音
```

提示行在第二条里消失了（因为已经有 `templates:` 段），说明判定是按"文件里有没有这一段"。

⚠️ **还没验的**：模板渲染到真实事件上（需要玩家打一句话 / 真的 QQ 消息）。
渲染逻辑本身由 `TemplatesTest` 的 12 个用例离线覆盖。

## 26.2 窗口验证：26.1.2 编出来的东西跑在 26.2 上（2026-09-21）

文档里一直把"描述符写范围而不是锁版本"当成**先验假设**（见 MULTIPLATFORM 第 10.4 节）。现在两个平台都验了：

**Paper 侧**：同一个 `mc-qq-bukkit-0.1.0.jar`（编译对 paper-api `26.1.2.build.74-stable`）丢进
Paper **26.2 build 126**：

```
Bukkit plugins (1): - mc-qq (0.1.0)
[mc-qq] Enabling mc-qq v0.1.0
[mc-qq] mc-qq loaded; the QQ bridge comes up with the server
Done (13.310s)!
$ qq status
[mc-qq] 平台 paper-26.2
```

注意这次**没有**打 "wrote the default config" —— 因为 `plugins/mc-qq/config.yml` 是上一轮 26.1.2 留下的，
`loadOrCreate` 正确地没有覆盖它。这顺带证了"文件已存在就不写模板"。

**Fabric 侧**：用 `-Pminecraft_version=26.2 -Pfabric_api_version=0.161.0+26.2` 覆盖依赖，
**源码一行没改就编译通过**，然后 `runServer`：

```
- mc-qq 0.1.0                       ← 模组列表
Starting minecraft server version 26.2
(mc-qq) mc-qq loaded; the QQ bridge comes up with the server
Done (3.972s)!
(mc-qq) wrote the default config to .\config\mc-qq\config.yml
(mc-qq) config: bot main still has the REPLACE_ME app-id from the template, skipped
```

⚠️ 口径要说清：Fabric 那次是 **loom 的 dev run**（编译产物 + MC 26.2 的类路径），不是把 shadow jar 丢进
生产 Fabric 服务端。26.x 没有 remap，且 shadow jar 里只有 core 与 SDK（不含 MC），所以 dev 与生产在这件事上
差别很小 —— 但严格说，生产服务端那一步没跑过。

**还没验的窗口**：26.3（现在还是 pre，`paper-api 26.3-pre-2.build.0-alpha`）。26.1→26.2 通过只说明这一跳没问题。

### 还没证的（别当成已经成立）

* **聊天转发没有真人验证过**：Paper 上需要玩家打一句话才能看到 `AsyncChatEvent` 真的触发并转发到 QQ。
  目前只证到"监听器注册没有报错"。
* **Folia 没跑过** —— 调度路径写了（探测 global region scheduler），但 `plugin.yml` 里没写 `folia-supported`。
* **Spigot 支持只有静态证据**（见上），没有运行时证明；所以插件不声称支持 Spigot。
* fabric 侧仍没证的：真实群消息的双向往返、`author`/`mentions` 在真 payload 里的形状、审核结论轮询。



## 上游：qq-bot-java-sdk

| 项 | 状态 |
| --- | --- |
| 0.0.4 | 已在 Maven Central，pom/jar/sources/javadoc/`.asc`/`.module` 与 metadata 都核验过 |
| SDK 工作区 | `dev` @ `f3eee3e`，工作树干净；落后 `origin/main` 一个合并提交，下一轮开工先同步 |
| 本 mod 用到的面 | `Bots`、`@On`、`EventBus(Executor)`、`api().group().sendGroupMessage`、`audits()`、`selfId()` |
| 下一版候选 | 入站 `msg_id` 去重（mod 里先用本地 LRU 挡着，正是这个缺口的具体压力）、`BotPlugin`、`state`/`Session`/`pause()` |

## mod 侧：已完成

* **构建**：`clean build` 1m18s 全绿，8 个任务；产物 `build/libs/mc-qq-0.1.0.jar`（4,378,226 字节）。
* **测试**：9 个，全离线。`BridgeConfigTest` 6（配置解析、模板首写、占位符跳过、未知事件名报错）+
  `McToQqTest` 3（出站请求的线上形状、错误码与审核码只记日志不重试不抛）。
* **真机加载**：`./gradlew runServer`（Fabric Loader 0.19.5 + Fabric API 0.155.3+26.1.2）→ mod 注册、
  四个 MC 事件监听器注册、`SERVER_STARTED` 写出 `config/mc-qq/config.yml` 并把"还是 REPLACE_ME"报进日志。
* **成品 jar 单独验过**：常量池里 0 处未 relocate 引用；MC/Fabric 一个类都没被打进去；`org/slf4j/**` 已排除；
  直接从 jar 里造 client + 解析一条群消息 payload，字段照旧（`conversationId=GROUP1`、`content` 正确）。
* relocate 覆盖：SDK、okhttp3、okio、gson、snakeyaml、**kotlin**（OkHttp 带的 stdlib）。

## 这两天踩实的事实（写在这里免得再撞）

* Loom 的 no-remap 插件（`net.fabricmc.fabric-loom`）**没有** `modImplementation` 也**没有** `remapJar`
  ——从 loom jar 里的 `Constants$Configurations` + 一次实跑 dump 确认，不是猜的。
* 26.1 的两处改名：`source.hasPermission(2)` → `Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)`；
  `player.displayClientMessage(c, false)` → `player.sendSystemMessage(c)`。
* `Map.copyOf` / `Set.copyOf` 不保持顺序 → 群列表和事件列表会乱，`/qq status` 与转发循环都受影响；
  现在用不可变的 Linked 集合。这条是被测试抓出来的。
* loom 的 `runServer` **不吃管道里的控制台命令**，`--no-daemon` 也一样（daemon 模式下原版 `stop` 报同一句
  "An unexpected error occurred trying to execute that command"）。要驱动控制台得走 RCON。
* 本机 25565 被另一个 java 进程占着（不是我们的），dev run 用 `run/server.properties` 里的 **25611**。
  `run/eula.txt` 已写 `eula=true`，`online-mode=false` 仅供本地测试。

## 机器环境（不属于仓库，但决定能不能复现）

* JDK：`D:\SDK\jdk-25.0.4.1+1`（Temurin，手动装的；foojay 在这台机器上下载不了 JDK）；`JAVA_HOME` 仍是 21。
* `GRADLE_USER_HOME = E:\Build\Gradle`（caches、wrapper dists、loom 缓存都在那）。**Agent 起的 shell 看不到这个
  变量**，所以命令行要显式 `GRADLE_USER_HOME=E:/Build/Gradle ./gradlew …`，否则会新建一个没有 JDK 25 和代理的
  `C:\Users\<你>\.gradle`。
* 代理 `127.0.0.1:10808`（mixed：HTTP-CONNECT 与 SOCKS5 都应答）。Gradle 侧是机器级
  `gradle.properties` 的 `systemProp.*`；Maven 侧是 `~/.m2/settings.xml` 的 `<proxies>`。已实测
  `--refresh-dependencies` 全程走代理且一次不缺。
* `~/.m2/settings.xml` 的 `<localRepository>` = `E:\Build\Maven\Repository`；`MAVEN_USER_HOME` 只影响
  `mvnw` 的发行版缓存（Maven 自己不读它）。Gradle 完全不读 `settings.xml`。
* Maven 没装（`D:\SDK\maven.zip` 是 3.9.9 的截断包）；IDEA 没装，也不需要（`runServer` 命令行就能跑）。

## 多平台/多版本：这一轮的结论

* **多模块是被容器逼的，不是被代码逼的**：`fabric.mod.json` / `neoforge.mods.toml` / `mods.toml` /
  `paper-plugin.yml` 互不认账。但"多模块 ≠ 多套代码"。
* 切分比例是量过的：6 个源文件里只有 3 个碰 MC 类型，`BridgeConfig.java` **零** MC 依赖。
  core = `BridgeConfig` + `BridgeRuntime` + `QqToMc` + `McEvent` + 那 9 个测试；adapter = 事件源 + 命令注册 + 生命周期。
* **接缝保持窄**：`MinecraftPlatform{ broadcast, onMainThread, configDir, registerCommand, platformLabel }`
  + 一扇入站门 `reportEvent(McEvent, String)`；文本在各 adapter 拼好，`MinecraftServer`/`ServerPlayer`/`Component`
  一律不进 core。
* **不用 Architectury**：它最重的收益（抹平 intermediary/SRG）在 26.x 已无对象可抹，我们不碰注册表也不写 mixin。
* 平台矩阵（26.1.2，全部实测自各家 maven）：Fabric Loader 0.19.5 ｜ NeoForge `26.1.2.109` ｜
  Forge `26.1.2-64.1.2` ｜ Spigot `26.1.2-R0.1-SNAPSHOT` ｜ Paper `26.x build`。
  但 Spigot/Paper 同一套 Bukkit API（一个 adapter 覆盖），Forge 与 NeoForge 已分家（各一个）。
  → 真实工作量是 **3～4 个 adapter**，建议顺序：core 抽离 → bukkit → neoforge → forge 按需。
* 版本轴：一 MC minor 一分支，descriptor 写范围（现在已是 `>=26.1`）；26.x 全线 Java 25，所以 toolchain 不分层；
  SDK 版本只出现在根 `gradle.properties` 的 `qqbot_sdk_version` 一行。
* 兜底方案（零平台代码）：QQ 侧做成独立进程 + 日志 tail/RCON，覆盖面天然 100%，代价是解析文本与丢事件语义。

## 还没有证的（要真实凭证 + 真人进服）

* `/qq status`、`/qq reload` 的实际执行（含 `/qq reload` 换 bot 后不重复转发）。
* 真实群消息的双向往返；`author` / `mentions` 在真 payload 里的形状（SDK README 的"已知边界"同源）；
  多 bot 群里 `ToMe` 会不会误判；审核结论轮询（`audits()`）真机行为；单聊不转发的实际事件序列。

## 待你拍板

1. 包名 `com.example.mcqq` → `io.github.skiesworld.mcqq`？（现在改最便宜）
2. mc-qq 要不要 `git init`，放个人账号还是 org（groupId 保持 `io.github.skiesworld` 的理由见对话）。
3. 要不要现在动手做 core/SPI 抽离那一步（功能不动，只搬）。
4. 要不要开 RCON，把 `/qq status`、`/qq reload` 这两处唯一空白证掉。
