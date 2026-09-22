# 多服务端 / 多版本方案（2026-09-21）

> 这是一份**方案文档**：结论在最前面，每条事实都标了来源，能被证伪的地方写清了"没证"。
> 本轮只做了阅读与核实，**没有实测任何平台的构建**（边界见第七节）。

## 一句话

26.1 去混淆之后，多版本最贵的那一项（映射）没了：Fabric 走 no-remap、NeoForge/Forge 直接吃 Mojang 名、
Bukkit 一族本来就不碰映射。所以对 mc-qq 来说，「多平台」的真实工作量是 **3～4 个 adapter**
（Bukkit 一族算一个），「多版本」是 **4 个 jar 各写一段版本范围**，不是 4×N 个 jar。
代码切分也不用新发明：`BridgeConfig` 已经零 MC 依赖，core 是现成的。

要回填低版本（1.20.1 / 1.21.x）也成立，但形态是**两个构建**而不是一个：
26.x 一代（Gradle 9 系）+ 26.0 之前一代（Forge 的 ForgeGradle 6 / Gradle 8 系，已实测），
core 用 release 17 同时服务两代。**不用效仿鹊桥的"每版本一个工程"** —— 那是三代工具链逼出来的，
见第八、九节。

> ✅ **2026-09-22 更新**：`core` 现在**真的是** release 17 了（`BridgeRuntime` 的虚拟线程换成了缓存线程池）。
> 在那之前它停在 21 而且降不下来，而本文好几处已经按 17 写了 —— 那段"文档写 17、实际 21"的经过记在
> **§8.6**，留着当样本。

**本轮的方向（第十节）**：先只做 26.x 一个窗口。实测 26.x 已占 35% 的服务器（样本 46,904 台），
且 26.2 的服务器比 26.1.2 多一倍多 —— 所以是"一个窗口 + 描述符写范围"，不是"锁一个版本"。
低版本按窗口后续再加，路径不锁死。

## 已落地（2026-09-21 下午）

前两步都做完了并验证，细节在 [PROGRESS.md](PROGRESS.md)：

1. **core 抽离**：`core/` + `fabric/`，`clean build` 全绿、9 个测试照旧全过；core 是 Java 21 字节码、0 个 MC 类；
   fabric 的 shadow jar 里 0 处未 relocate、core 的 15 个类已并入、MC 类 0 个。
2. **第二个平台 `bukkit/`**：Paper/Spigot/Folia 那个 adapter（Paper 路线）。同一个 core jar **一行没改**，
   同时被一个 mod 和一个插件各自打包 —— 接缝成立。bukkit jar 里 0 处未 relocate、`org/bukkit|io/papermc|net/kyori`
   0 个（paper-api 是 compileOnly）、`plugin.yml` 的版本已展开。
3. **真机验证（Paper 26.1.2 build 74）**：插件被 Paper 接受、`onEnable` 跑通、
   `plugins/mcqq/config.yml` 落盘、`/qq status` 与 `/qq reload` 通过 RCON 实际执行成功。
   **`/qq status` / `/qq reload` 这两条从最早的 Fabric 轮次起就挂着"没证到"（loom 喂不进控制台命令），
   现在在 bukkit 上证掉了** —— 命令的答案来自 core，所以 Fabric 侧那份风险也随之下降，差异只剩 Brigadier 语法。
4. **第三个平台 `neoforge/`**（2026-09-21）：loom 与 moddev 同构建通过；开发服加载成功
   （`MC ↔ QQ Bot 0.1.0 (mcqq)`）、配置落在 `config/mcqq/`（与 Fabric 同构）、
   `/qq status` 报 `平台 neoforge-26.1.2`、`/qq help` 列出全部子命令。
   **踩到一条硬约束：NeoForge 的 modId 不允许连字符**，所以三个平台统一用 `mcqq` 当 id（项目名仍是 `mc-qq`）。
5. **窗口验证（26.1.2 → 26.2）**：同一个 bukkit jar 跑在 Paper 26.2 上；Fabric 用 26.2 的依赖**源码一行没改**
   编译通过并加载。第 10.4 节那条"描述符写范围"的先验假设，现在这一跳是实测的（26.3 还没验）。

**还没证的**：聊天转发（要玩家真打一句话）、Folia 上的**广播**（插件加载与命令已验，广播要玩家+入站消息）、
Spigot 支持（只有静态证据：Paper 26.1.2 的 `ChatProcessor` 里旧路径还在）、26.3 那一跳。
**打包产物已经验过**：Paper（真服务端 + plugins/）、Fabric（真服务端 + mods/ 里的 shadow jar）、
NeoForge（dev 启动器 + run/mods/ 里的 jar）都加载并跑通了命令；relocate 在生产可用的直接证据是
配置读写走的正是被改名过的 snakeyaml。
详见 [PROGRESS.md](PROGRESS.md)。

**下一步候选**：① 找个人在 Paper 上打一句话，把聊天转发证掉；② 继续加 `neoforge/`（工具链已核实：
ModDevGradle 2.0.147 + 26.1.2.109）；③ 等 26.3 稳定后补那一跳。

---

## 一、核实过的平台事实（MC 26.1.2 线）

| 平台 | 构建插件（本轮核到的版本） | 依赖坐标 | 描述符 | 关键约束 |
| --- | --- | --- | --- | --- |
| Fabric | `net.fabricmc.fabric-loom`（no-remap，模板用 1.18.1，本仓 1.18.2） | `net.fabricmc:fabric-loader:0.19.5`<br>`net.fabricmc.fabric-api:fabric-api:0.155.3+26.1.2`（26.3 线已到 `0.160.6+26.3`） | `fabric.mod.json` | 无 `modImplementation` / 无 `remapJar`，依赖写 `implementation` |
| NeoForge | `net.neoforged.moddev` 2.0.147（ModDevGradle） | `26.1.2.109` | `META-INF/neoforge.mods.toml` | 与 Forge 已分家；**modId 不允许连字符**，所以 id 用 `mcqq`（不是项目名 `mc-qq`） |
| Forge | `net.minecraftforge.gradle` 7.x（ForgeGradle） | `26.1.2-64.1.3` | `META-INF/mods.toml` + `pack.mcmeta` | 26.x 一直在跟（还有 26.2/26.3）；事件 API 与 NeoForge 完全不同（每个事件自带静态 `BUS`）。模块已写好并编译验证，**构建卡在本机到 Mojang 的网速** |
| Forge | `net.minecraftforge.gradle` `[7.0.17,8)`（ForgeGradle 7） | `net.minecraftforge:forge:26.1.2-64.1.3` | `META-INF/mods.toml` | MDK 里**没有 mappings 行**，Java 25 toolchain |
| Bukkit 一族（Paper/Spigot/Folia） | 不需要 MC 工具链，`java` + shadow | `io.papermc.paper:paper-api:26.1.2.build.74-stable`<br>`org.spigotmc:spigot-api:26.1.2-R0.1-SNAPSHOT` | `plugin.yml` / `paper-plugin.yml`（可共存） | paper-api 是 **Java 25**（class major 69） |

证据（都能重跑）：

* **Forge**：下载 `https://maven.minecraftforge.net/net/minecraftforge/forge/26.1.2-64.1.3/forge-26.1.2-64.1.3-mdk.zip`
  解出来 `build.gradle` 第一段就是 `id 'net.minecraftforge.gradle' version '[7.0.17,8)'`，
  `java.toolchain.languageVersion = JavaLanguageVersion.of(25)`，依赖是
  `implementation minecraft.dependency('net.minecraftforge:forge:26.1.2-64.1.3')`。
  官方下载页当前 latest = `26.1.2-64.1.3`、recommended = `64.1.0`。**Forge 26.x 活着**，别信某些二手博客说它已死。
* **Paper**：`repo.papermc.io` 的 `paper-api` metadata 里 26.1 线是 `26.1.2.build.74-stable` 这种形式
  （**不是** `-R0.1-SNAPSHOT`，PROGRESS.md 里"Paper 26.x build"可以换成这个确切写法）。
  把 jar 拉下来看：`io/papermc/paper/event/player/AsyncChatEvent`、`org/bukkit/event/player/AsyncPlayerChatEvent`、
  `PlayerDeathEvent`、`PlayerJoinEvent`、`PlayerQuitEvent` 都在；
  `io/papermc/paper/threadedregions/scheduler/*`（Folia）和 `io/papermc/paper/command/brigadier/Commands` 也在。
* **Spigot**：`hub.spigotmc.org` 的 snapshots metadata 里 `26.1-R0.1-SNAPSHOT` 到 `26.3-R0.1-SNAPSHOT` 都在。
* **Fabric 去混淆后的边界**：官方 blog（2025-10-31）说得很清楚 —— **Intermediary 不再存在**，运行时就是 Mojang 名；
  Yarn 停止新版本维护（存量版本继续收贡献）；Loom 2.0 可能是"不含 remap 的瘦版本"，而"用新 Loom 支持老游戏版本"是最高优先级。
  → 26.x 这条线上不存在"抹平映射"这件事，这正是 Architectury 那类方案失去对象的原因。
* **Paper 描述符**：`paper-plugin.yml` 里 `dependencies` 分 `bootstrap`/`server` 两段（`load`/`required`/`join-classpath`），
  **不用 `commands` 字段**（命令走 Brigadier），并且插件之间**类加载隔离**；官方说 `plugin.yml` 与 `paper-plugin.yml` 可以同时放进一个 jar。

---

## 二、QueQiao / QueQiaoTool 的现成实现（读后摘要）

结论：**它做的是"平台×版本"两个轴都要覆盖、且版本跨度大到要混用 Java 8 和 Java 25 的场景**，
所以它选了"生成 N 个独立工程"，而不是"一个 Gradle 构建里的 N 个子项目"。这是关键区别。

### 接缝（很窄，和 mc-qq 想要的是一个形状）

```java
// QueQiaoTool：Java 8，发布到 GitHub Packages，被各平台实现引用
GlobalContext.init(boolean isModServer, String serverVersion, String serverType,
                   HandleApiService apiImpl, HandleCommandReturnMessageService cmdImpl);
GlobalContext.sendEvent(BaseEvent);      // 入站：平台事件 → 工具包 → WebSocket
GlobalContext.shutdown();
```

平台侧只需要实现 `HandleApiService` 的 4 个方法（`handleBroadcastMessage` / `handleSendTitleMessage` /
`handleSendActionBarMessage` / `handleSendPrivateMessage`）+ 一个命令回显接口，
再把各 `XxxAbstract` 子命令注册进去。事件模型、协议路由（`ProtocolRouter` + 7 个 handler）、
WebSocket（`WsClient`/`WsServer`/`WebsocketManager`）、Rcon、配置、本地化**全在工具包里**。

### 仓库布局（`origin` 是真源码，版本目录是产物）

```
QueQiao/
  tool/ModMultiVersionTool-1.5.7.jar     # 构建工具
  init.sh|ps1        # java -jar 跑工具
  matrix.sh|ps1      # 扫目录 → CI 矩阵 JSON
  version.txt 0.5.0 / tool_version.txt 0.6.8
  fabric/origin/…        fabric/fabric-1.16.5/… fabric/fabric-1.21.11/…   ← 版本目录里只有 gradlew + support_version.txt
  paper/origin/…         paper/paper-1.17.1/…        （support_version.txt 写 [1.17.1,)，给 Modrinth 当 game-versions）
  forge/ neoforge/ velocity/ folia/ cleanroom/ gtnh/ vanilla/ spigot/ …
```

`fabric/origin/gradle.properties` 里那段注释就是这套机制的说明书：

```properties
# IF <=fabric-1.16.5
#java_version=8
# ELSE IF >= fabric-1.20.5
#java_version=21
# ELSE
#java_version=17
# END IF
```

`fabric/origin/src/main/resources/fabric.mod.json5` 里同理按版本切 `"minecraft": [...]` 的数组。

### ModMultiVersionTool 的机制（读了源码，不是猜的）

* 纯 Kotlin 的**注释预处理 + 目录复制**（~750 行）：把 `loader/origin/**` 处理一遍写进 `loader/loader-<版本>/**`。
* 指令集（`Keys.kt`）：`IF` / `ELSE IF` / `ELSE` / `END IF` / `ONEWAY`（只单向，不回写 origin）/ `RENAME` /
  `EXCLUDE` / `ONLY` / `DEFINE` / `PRINT`；注释标记 `//` 或 `#` 都认。
* 变量表（`FileHelper.createMap`）：`$$` = 版本目录名（`fabric-1.16.5`）、`$loader`、`$folder`、
  `$fileName`、`$fileNameWithoutExtension` —— 这就是为什么条件能写成 `// IF <= fabric-1.16.5`。
* `README` 明说**不支持反向更新**（没有从版本目录回写 origin 的路）。
* CI：`matrix.sh` 扫目录生成 `{mc-version, mc-loader, publish-loaders, publish-version}` 矩阵，
  每个格子独立 `./gradlew clean build`，再交给 `mc-publish` 发 Modrinth/CurseForge。

### 和 Stonecutter 的本质差别

| | ModMultiVersionTool（QueQiao） | Stonecutter 0.9.8 |
| --- | --- | --- |
| 产物 | N 个**独立 Gradle 工程** | 一个构建里的 N 个**子项目** |
| Gradle/daemon | 每版本一套 wrapper，可以完全不同 | 一套 wrapper、一个 daemon |
| Java | 可以跨 8 ↔ 25（各工程自己的 toolchain） | 一个 daemon JDK + toolchain 编译目标 |
| 条件语法 | `// IF <= fabric-1.16.5` | `//? if >=1.21 {` … `//?}`，`elif`/`else`，可嵌套 |
| 多 loader | 靠 loader 目录 | 文档明说支持，且有 `constants.match(loader, "fabric","neoforge","forge")` 专门做 loader 常量 |
| IDE | 多工程，体验一般 | 有官方 IDEA 插件（注释高亮/切版本） |
| 回写 | 不支持 | 直接编辑版本目录即可（同一份源码） |

**选型判据**：版本跨度**大到原生工具链无法共存**（1.7.10 GTNH 的 Java 8 ↔ 26.x 的 Java 25，ForgeGradle 1.x ↔ 7.x）
→ 只能走 QueQiao 那套。跨度在"同一条大版本线内"（26.1 → 26.3）→ Stonecutter 或干脆 git 分支更省事。

---

## 三、mc-qq 的切分比例与接缝

现状（量过的）：6 个源文件 980 行，只有 3 个碰 MC 类型，`BridgeConfig`（289 行）**零** MC 依赖。

```
core/      BridgeConfig  BridgeRuntime  QqToMc  McEvent(枚举)  + 那 9 个测试
adapter/   事件源注册  + 命令注册  + 生命周期  + 平台标识
```

接缝（沿用 PROGRESS 的结论，补两点）：

```java
public interface MinecraftPlatform {
    String label();                  // "fabric-26.1.2" / "paper-26.1.2" / "neoforge-26.1.2"
    Path   configDir();              // FabricLoader.getConfigDir() / getDataFolder()
    void   broadcast(String line);   // QQ→MC：core 给带 § 的文本，adapter 负责渲染成 Component/Adventure/legacy
    void   onMainThread(Runnable r); // Fabric/NeoForge/Forge: server.execute；Bukkit: scheduler；Folia: global region scheduler
    void   registerCommand(CommandSpec spec);   // /qq status、/qq reload
}
```

* 出站门只有一个：`runtime.forward(McEvent, String)`（现有签名就能用）。
* **必须承认的取舍**：`§` 颜色码留在 core 里当"线格式"。这是为了让 core 零 MC 依赖付的代价 ——
  要更干净就得引入 `StyledLine{text,color}` 模型，现在不值。
* `MinecraftServer` / `ServerPlayer` / `Component` 一律不进 core（现状已经守住了）。

---

## 四、四条路线

### 路线 A（推荐）单仓多模块 + 各平台原生工具链

```
mc-qq/
  settings.gradle.kts          includeBuild("build-logic")；include("core","fabric","neoforge","forge","bukkit")
  build-logic/                 两个约定插件：mcqq-core / mcqq-loader
  core/                        java-library，release 17，零 MC；依赖 SDK + snakeyaml；9 个测试在这儿
  fabric/                      net.fabricmc.fabric-loom（no-remap）+ shadow
  neoforge/                    net.neoforged.moddev + shadow
  forge/                       net.minecraftforge.gradle 7 + shadow
  bukkit/                      java + paper-api/spigot-api compileOnly + shadow  ← 一个 jar 覆盖 Paper/Spigot/Folia
```

* core 是**普通 java-library 产物**（不是 MultiLoader 那种"把 common 源码编进每个 loader"）。
  理由：core 里没有任何 MC 类型，不需要靠 loader 的工具链去编译它；独立产物还能保住"9 个测试不加载 MC"这条现状。
  各平台 `implementation(project(":core"))`，打包时连 core + SDK + okhttp/gson/kotlin 一起收进去并 relocate。
* **四个平台都用 shadow + relocate，不用 Jar-in-Jar**。理由：Jar-in-Jar 不 relocate，kotlin-stdlib / okhttp
  会和别的 mod 撞；现在这份 `shadowJar` 配置（relocate 6 个包 + 排 slf4j）已经验过一次，复用它最便宜。
  （代价：Forge/NeoForge 的 module layer 对 relocate 后的类是否买账**要实测**，见第七节。）
* 每个平台子项目只写自己的东西：入口点、4 个事件的监听、命令注册、`onMainThread` 的实现、描述符。

### 路线 B QueQiao 式：每平台×版本一个独立工程 + ModMultiVersionTool

把 mc-qq 也做成 `fabric/origin/…` + `fabric/fabric-26.1.2/…` 这种布局，用同一个工具生成。

* **优点**：能跨 Java 版本、跨 Gradle 版本、跨工具链；CI 矩阵天然并行；用户已有一套跑通的流程。
* **缺点**：没有跨工程重构；每次改 core 要跑工具同步；wrapper 重复 N 份；IDE 里 N 个工程。
* **适用条件**：真的要去碰 1.16.5 及更老（Java 8 + Yarn/intermediary 时代工具链）。26.x 线内用它属于杀鸡用牛刀。

### 路线 C Stonecutter 单工程多版本多 loader

* **优点**：一套 wrapper/daemon；`//? if >=1.21 { … //?}` + `elif`/`else` + 可嵌套；`constants.match(loader, …)`
  是专门为多 loader 设计的；官方 IDEA 插件；文档自称 1300+ mod 在用（0.9.8，2026-08-31）。
* **缺点**：条件散进源码注释；一个 Gradle 构建里同时挂 loom + moddev + forgradle 三个插件，**冲突风险要实测**；
  跨 Java 大版本（8 ↔ 25）不适用。
* **适用条件**：要**同时**维护两个以上 MC minor（比如 26.1 与 26.3 的 API 真变了）。

### 路线 D（修正后）复用鹊桥：不发平台 jar，改发"QQ 侧"

**先修正我上一版的错误说法。** 你说的对：鹊桥的发布物**就是**一个 jar 丢进 `mods/` 或 `plugins/`，
独立终端程序是它给"未支持版本"的兜底（`queqiao_mcdr` 那类），不是它的主形态。
所以 D 的准确形态是：

```
D：[MC 服务器] 装 QueQiao 的 jar（别人维护的平台矩阵）+ 一段 config.yml
        ↑ WS（access_token 鉴权，server/client 两种模式）
   [独立进程] mc-qq-bridge：WS 客户端 → 现有 core → qq-bot-java-sdk → QQ
```

即：**你不发平台 jar，改发一个"QQ 侧"的进程**，平台覆盖直接继承鹊桥已经发布的那些 jar。
对终端用户来说这是**装两个东西**（鹊桥 + 你的进程），不是"一个 jar 丢进 mods/"。

技术上可行是有据的（读过 QueQiaoTool 与 paper 侧实现）：

* **入站**：`broadcast` 的 `message` 是 **`JsonElement`**（MC 消息组件 JSON），平台侧
  `PaperTool.buildComponent(...)` 渲染成 Component 再 `server.sendMessage(...)` → **颜色/加粗都能带**。
  鹊桥 config.yml 里就给了 `{"text":"[鹊桥]","color":"green","bold":true}` 的例子，
  所以 mc-qq 现在那句 `§b[QQ 群名]§r 昵称: 文本` 能原样表达。
* **出站**：`subscribe_event` 有 `player_chat / player_death / player_join / player_quit / player_command /
  player_advancement` 六项 —— 正好覆盖 mc-qq 要的四项；`PlayerChatEvent` 带
  `PlayerModel{nickname,uuid,address,health,max_health,is_op,x,y,z}` + `messageId` + `rawMessage` + `message`。
* **连接**：`websocket_server`（默认 127.0.0.1:8080）与 `websocket_client`（`url_list` + 重连）两种模式都有。

**结论：D 只在"你不想拥有平台矩阵"时才划算**（比如矩阵要长到十几格、或你要把 QQ 逻辑做成独立服务）。
只要你还想发"一个 jar 丢进 mods/"，D 就不成立 —— 而这正是你要的形态，所以**本轮不走 D**。
它的价值留在纸上：等哪天真遇到"某平台没人做、也来不及自己做"，那是唯一的零成本退路。
（未核实：`EventProcessor` 里 `player_chat` 的触发时机、`rawMessage` 与 `message` 的差别、
被别的插件改写或取消的消息是否照发。真要走 D 之前必须先读这三处。）

---

## 五、被排除的选项（附理由）

* **Architectury**：它最重的收益是抹平 intermediary/SRG 差异。26.x 已无对象可抹，且我们不碰注册表、不写 mixin。
* **Unimined**：单 source set 跑多 loader 的抽象层，代价是"loader 变了要等它跟"。26.x 各家都给了原生 Gradle 插件
  （loom / moddev / forgradle 7），中间再插一层不划算。
* **"一个 jar 通吃所有平台"**：描述符、类加载模型、入口点三处互不认账，不存在这种产物。
* **混合端替我们抹平平台**（Cardboard / Sinytra Connector）：Cardboard 目前停在 1.21.11（2026-08-22 更新），
  还没到 26.x；Connector 要服务端额外装、版本滞后。只能当"额外覆盖面"，不能当方案。
* **多版本用 git 分支**：26.x 线内其实可行（改动小），但每加一个版本就要在 N 个分支间 merge，
  版本一多就退化。可以作为"只维护一个 minor"时的临时做法。

---

## 六、版本轴：别把 loader × minor 相乘

* 26.x 全线 **Java 25**，所以 toolchain 不分层。
* 每个 jar 用**描述符写范围**：Fabric `depends.minecraft: ">=26.1"`（现状就是）、
  NeoForge `versionRange=[26.1,)`、Forge 同理、Bukkit `api-version: '26.1'` + 文档写支持范围
  （QueQiao 的 paper 就是 `api_version=1.17` + `support_version=[1.17.1,)`，一个 jar 吃一整段）。
* mc-qq 碰的 MC 面只有 **4 个事件 + 1 个命令 + 广播**，minor 之间变动的概率很低。
  → 现实预期是 **4 个 jar 覆盖 26.1～26.3**，只有当某个 minor 真改了签名时才加一个版本目录/条件块。
* **回填 1.21.x 及更早是独立决策**，成本结构和 26.x 完全不同：
  * 工具链要换回 Yarn/intermediary 时代（loom 的 legacy 插件 id、`modImplementation`、`remapJar`）；
  * Java 降到 21/17；
  * Bukkit 侧聊天事件差异（`AsyncPlayerChatEvent` 废弃 → Paper 的 `AsyncChatEvent`）、死亡消息类型差异；
  * **SDK 是 `--release 17` 编译的（README 明写 JDK 17+）**，所以 core 跑在 Java 8 上不行
    → 1.18 以前的版本（Java 8/16）要支持，就得先把 core 降到 Java 8 —— 这正是 QueQiaoTool 停在 Java 8 的原因。

---

## 七、风险与未验证（别当成已经成立）

1. **Forge/NeoForge 的 module layer 与 shadow+relocate 的兼容性没测过**。Fabric 那份"0 处未 relocate 引用"的结论
   不能平移过去 —— ModLauncher 的模块系统对 jar 内类路径有自己的判断。**这是路线 A 的第一个待证点**。
2. ~~**一个 Gradle 构建里同时挂 loom + moddev + forgradle 没测过**~~
   **已测（2026-09-21）：loom + moddev 共存没问题** —— `clean build` 里 `:fabric:build` 与 `:neoforge:build`
   一起过。forgradle 7 还没试过，但三者同属"原生工具链各管一个子项目"的模式，风险比原先估的低。
3. **Folia 的调度语义**：`Bukkit.getScheduler()` 在 Folia 上不可用，广播要走 global region scheduler；
   还需要 `plugin.yml` 里 `folia-supported: true`。adapter 的 `onMainThread` 要分三档（Bukkit / Folia / 其余）。
4. **Paper 的描述符选择**：`paper-plugin.yml` 不用 `commands` 字段（走 Brigadier）且插件间类加载隔离。
   如果同一个 jar 还要能在 Spigot 上跑，最稳的是两个描述符都放、命令只用 `plugin.yml` 那套。
5. **Bukkit 一族的实际版本跨度没量**。我只核实了 26.1.2 的 API 表面里有那几个类，
   没有验证 1.12→26.x 之间的兼容性（那要靠 XSeries 之类的工具，或干脆限定 `api-version`）。
6. **本轮没有任何构建实测**：所有结论来自读代码 + 拉 maven metadata + 拆 MDK/paper-api jar。
   建议第 1 步之前先做一个"空 adapter 能在目标平台编译并加载"的骨架验证，再搬代码。

---

## 八、低版本（1.20.1 / 1.21.x）能不能挂在路线 A 上

**能，但要把「版本轴」从「平台轴」里拆出来，并且只有 Bukkit 一族能做到"一个 jar 通吃"。**
下面是逐项核实的结果（不是推测）。

### 8.1 Java 轴：不付代价

MC 的 Java 分档是 **17**（1.18–1.20.4）、**21**（1.20.5–1.21.x）、**25**（26.x）。
SDK 是 `--release 17`，所以 **core 停在 release 17 就同时服务 1.20.1 和 26.x**。
（2026-09-22 之前 core 停在 21，因为 dispatcher 用了虚拟线程；那次清理见 §8.6。）
只有要下到 1.16.5 及更老（Java 8）才需要把 core 降到 8 —— 这正是 QueQiaoTool 停在 Java 8 的原因。
**这是路线 A 相对路线 B 的一个便宜点**：跨 Java 大版本这条最贵的轴，在 1.20.1 这个目标上根本不存在。

### 8.2 Bukkit 一族：一个 jar 覆盖 1.20.1 → 26.3（已核实）

做法是**编译对最老目标**（paper-api 1.20.1）+ 只用稳定 API + `api-version: '1.20'`。
我把两端的 jar 都拆开对比过，mc-qq 要用的 5 样东西**两端都在**：

| 需要的东西 | paper-api 1.20.1 | paper-api 26.1.2 |
| --- | --- | --- |
| `io.papermc.paper.event.player.AsyncChatEvent` | 在 | 在 |
| `org.bukkit.event.player.AsyncPlayerChatEvent` | 在 | 在（旧事件没被删） |
| `PlayerDeathEvent` / `PlayerJoinEvent` / `PlayerQuitEvent` | 在 | 在 |
| `Bukkit.broadcastMessage` | 在 | 在 |
| Folia 的 `threadedregions.scheduler.*` | 在 | 在 |
| 字节码目标 | Java 17（major 61） | Java 25（major 69） |

→ 只要不碰 NMS、不用只在 1.20.5+ 才出现的 API，这一个 jar 就能从 1.20.1 跑到 26.3。
**待补的验证**：`AsyncChatEvent` 的构造与方法签名在两端是否一致（类在 ≠ 签名一致），
两端各跑一次编译+加载即可。

### 8.3 Fabric：能，但要多一个"老工具链"子项目

1.20.1 是混淆时代，**必须换回 remap 的 loom**（legacy 插件 id `fabric-loom`）+ `mappings` +
  `modImplementation` + `remapJar`，与 26.x 用的 no-remap id 不是同一条路。
好消息：我查了 maven metadata，**两个插件 id 在同一个 loom 版本（1.18.1 / 1.18.2）下都在发布**
（`fabric-loom` 与 `net.fabricmc.fabric-loom`），说明"一个 loom 版本、两个子项目各挂一个 id"
在依赖层面成立。1.20.1 的 Fabric API 最新是 `0.92.12+1.20.1`。
**没验证的**：两个 id 同时出现在一个 Gradle 构建里会不会打架（共享静态状态/配置名）；
以及 loom 1.18 对 1.20.1 的 remap 链路是否完整可用。第一次做要先用一个空 mod 跑通。

### 8.4 Forge：**必须独立构建**（硬冲突，已核实）

两个官方 MDK 摆在一起就结束了 —— 我把三代都拉下来对比过：

| | Forge 1.20.1-47.4.23 | Forge 1.21-51.0.0 | Forge 26.1.2-64.1.3 |
| --- | --- | --- | --- |
| ForgeGradle | `[6.0,6.2)` | `[6.0.24,6.2)` | `[7.0.17,8)` |
| mappings | **有** | **有** | **没有** |
| Java | 17 | 21 | 25 |
| MDK 自带的 Gradle wrapper | **8.8** | **8.7** | **9.5.0** |

**分界线只有一条：26.0 之前 / 之后。** 所有 26.x 之前的 Forge 都是 ForgeGradle 6 + Gradle 8.x，
26.x 是 ForgeGradle 7 + Gradle 9.x。mc-qq 现在跑在 **9.7.1** 上 →
**两代不可能待在同一个 Gradle 构建里**，但"老的一代"自己内部是自洽的（1.20.1 与 1.21 可以同住）。
另外 **NeoForge 没有 1.20.1**（maven 上最早是 `20.2.12-beta`，从 1.20.2 起），1.20.1 只有 Forge 与 Fabric 两条路。

> 老构建还有一个连带约束：Gradle 8.8 跑不了 JDK 25，所以 legacy 那套的 daemon 要用 JDK 21
> （本机 `D:\SDK\OpenJDK-21` 有），toolchain 再编到 17/21。这条要在第一次跑的时候确认。

### 8.5 结论：A 的形状要改一处

```
mc-qq/                       Gradle 9.7.1，loom 1.18.2（no-remap）+ moddev 2.0.147
  core/                      release 17（1.20.1 与 26.x 通吃）
  fabric/  neoforge/  forge/ 26.x 线
  bukkit/                    ← 编译对 1.20.1，一个 jar 覆盖 1.20.1→26.3
legacy/                      独立构建（自己的 wrapper，Gradle 8.8 + ForgeGradle 6）
  forge-1.20.1/  （fabric-1.20.1 若能同构建共存就并进上面，不能就也搬进来）
```

判据很便宜：**下载目标版本的官方 MDK，看两行** —— `gradle-wrapper.properties` 的 `distributionUrl`
和 `plugins` 块里的工具链版本。要求同一代 → 可以并进主构建；不同代 → 独立构建。
1.21.x 也照这个判据逐项过一遍（NeoForge 1.21.1 用 ModDevGradle，Forge 1.21 那一代要先看 MDK）。

**所以对"要不要降"这件事的答案是**：降的成本主要不在代码（~~1.20.1 的 adapter 差异只有 PROGRESS 记的那两处改名~~
—— **这句低估了，实测见 §8.6**），而在**多一套老工具链的构建隔离**。
⚠️ **这一条后来也被 §8.6 推翻了**：1.20.1 的 Forge 有 ModDevGradle 那条与主构建同 artifact 的路，
`legacy/` 大概率不用建。而这个成本本来就可以按平台分批付的 ——
**先只加 bukkit 的 1.20.1 覆盖**（一个 jar，零额外工具链），就能把 1.20.1 这个最有人用的低版本吃掉大半，
Fabric/Forge 的 1.20.1 留到真有需求时再付。

### 8.6 forge-1.20.1 复核（2026-09-22 实测；结论当天被自己推翻过一次）

**问题**：给 Forge 加一个 1.20.1 窗口要怎么做、好不好做。

* **第一版结论**（当天早些时候）：代际差太大 → 必须建 `legacy/` 独立构建。
* **修正后的结论**：**不用建 legacy，而且已经落地并在真机跑通了**。1.20.1 的 Forge 有一条官方支持的、
  跟主构建**同 artifact 同版本**的路（`net.neoforged.moddev.legacyforge`）。真正要付的钱只有两笔：
  **core 降 17**（已做）和**适配器换事件模型**（已做，比预估小得多 —— 见"六"）。

第一版留着（见"一"），因为它记的代际事实仍然有用，判据也仍然是"下载目标版本的官方 MDK，看两行"。

#### 一、代际差是真实的，但它**不再**决定"必须独立构建"

| | 1.20.1 官方 MDK（ForgeGradle 那份） | 本项目现状 |
| --- | --- | --- |
| Gradle | **8.8** | 9.7.1 |
| ForgeGradle | `[6.0,6.2)` | `[7.0.17,8)` |
| Java | **17** | 25 |
| Forge | 47.4.23 | 26.1.2-64.1.3 |

**如果只有 ForgeGradle 一条路**，"独立构建"是硬结论，理由两条：

1. **同一个 plugin id 不能有两个版本** —— `net.minecraftforge.gradle` 在一个 build 里只能是一个，
   FG 6 与 FG 7 不可能共存。
2. **FG 7 强制 Gradle ≥ 9.3.0** —— 依据是 ForgeGradle 仓库 `FG_7.0` 分支的 commit
   *"Bump minimum Gradle to 9.3.0"*（原文理由：Gradle 9.3.0 含未包含在 rc 里的重要安全修复）。

但"用 FG 6 编 1.20.1"只是**一个**前提，而这个前提不必成立 —— 见下。

#### 二、推翻第一版的证据：`net.neoforged.moddev.legacyforge`

1. 官方 MDK 仓库 **`NeoForgeMDKs/MDK-Forge-1.20.1-ModDevGradle`** 用的是
   `id 'net.neoforged.moddev.legacyforge' version '2.0.91'`。
2. ModDevGradle 官方文档（`LEGACY.md`）原文：legacyforge 是
   *"released alongside the normal plugin with **the same version**"*，支持
   *"MinecraftForge and Vanilla Minecraft versions **1.17 up to 1.20.1**"*，并且是
   *"an 'addon' plugin, meaning it operates on top of the normal plugin"*。
3. **本地实证**：把缓存里的 `moddev-gradle-2.0.147.jar` 解开看 `META-INF/gradle-plugins/`，它**同时**提供

   ```
   net.neoforged.moddev              → ModDevPlugin           （neoforge-26.1 正在用）
   net.neoforged.moddev.legacyforge  → LegacyForgeModDevPlugin（给 Forge ≤ 1.20.1）
   ```

   同一个 artifact、同一个版本 → **不存在"同一 plugin id 两个版本"的冲突**。

→ `forge/forge-1.20.1` 可以就是主构建里的一个子项目，与 `neoforge-26.1` 共用 moddev-gradle 2.0.147。

**还没验的三条**（都是"跑一次 build 就知道"，比建一套 legacy 便宜得多）：

1. legacyforge 在 **Gradle 9.7.1** 上能不能跑 —— MDK 给的是 Gradle 8.14.5。主构建已在 9.7.1 上跑
   `net.neoforged.moddev`，但 legacy 那条路径干的活不同（mavenizer + SRG reobf）。
2. 它跟 **FG 7 同构建共存**有没有摩擦 —— `forge-26.1` 用 FG7，两家各带一个 mavenizer。
3. **SRG 重混淆**：1.20.1 的产物必须 reobf 到 SRG 才能在正式服跑。官方文档给的接法是
   `obfuscation { reobfuscate(tasks.named('shadowJar'), sourceSets.main) }` —— 我们发的正是 shadowJar。

#### 三、core 现在编不过 17（**这条是真的，已修**）

8.5 / 9.3 都写"core release 17"，但当时 `gradle.properties` 是 `core_java_release=21`。实测：

```
$ ./gradlew :core:build -Pcore_java_release=17
D:\...\core\BridgeRuntime.java:43: 错误: 找不到符号
        this.dispatcher = Executors.newVirtualThreadPerTaskExecutor();
```

**虚拟线程是 Java 21 API**，全仓 grep 确认 core 只有这一处用到 21 专属 API。1.20.1 跑在 Java 17 上
→ Java 21 字节码会直接 `UnsupportedClassVersionError`。所以这是**任何 1.20.1 路线（Forge 也好、
Bukkit 也好）都要先付的钱**，不是 forge 专属成本。

**已修**（2026-09-22，用户拍板）：`BridgeRuntime` 的 dispatcher 改成
`Executors.newCachedThreadPool(...)`（守护线程，名字 `mcqq-dispatch`），`core_java_release=17`。
选它是因为它保住了这段代码依赖的两个性质：**任务不等空闲线程**、**任务不被拒绝**。
虚拟线程在本项目的收益只是"任务很多时不炸"，而负载是"每个转发事件一个短任务"，平台线程池够用。

**验证**：`clean build` 绿（31 任务）、core 测试 **47 个用例 0 失败**、字节码分层正确 ——
`com/example/mcqq/core/*` 与 `com/example/mcqq/bukkit/common/*` = **61（Java 17）**，
`com/example/mcqq/paper/*` = **69（Java 25）**。

#### 四、适配器差异不是"两处改名"

`forge/forge-26.1` 用的是 **FG7 时代的事件模型**，1.20.1 是另一套。对照 `refs/QueQiao/forge/origin`
（一份真跨 1.16.5→1.21 的源码，用 `// IF >= forge-1.19` 这类标记）：

| | 26.x（我们现在的） | 1.20.1（鹊桥实测） |
| --- | --- | --- |
| 注册 | `ServerChatEvent.BUS.addListener(...)`（事件类自带静态 `BUS`） | `MinecraftForge.EVENT_BUS.register(this)` + `@SubscribeEvent` |
| 聊天取数 | `event.getUsername()` / `event.getRawText()` | `event.getPlayer()` / `event.getMessage().getString()`（`// IF < forge-1.21` 分支） |
| 入口构造器 | `McQqMod(FMLJavaModLoadingContext context)` | 无参构造 + `FMLJavaModLoadingContext.get()` |

影响 `McQqMod` / `McToQq` / `ForgePlatform`（三个文件合计 177 行里的多数）。
**好消息**：`ForgePlatform` 用的 `sendSystemMessage(Component)` **1.19+ 就有**（鹊桥 `// IF >= forge-1.19`），
`source.sendSuccess(Supplier<Component>, boolean)` 也是 1.19+ → 这两行不用改。
`getUsername()` 在 1.20.1 是否还在**未核实** —— 落地时编译一次就知道，不需要现在判断。

#### 五、forge 模块是"按需 include"的

`settings.gradle.kts` 里 forge **默认不在项目列表里**，要 `-PwithForge=true` 才加。
原因是 ForgeGradle 的 mavenizer 在**配置阶段**就要下载整套工具链，网速差的机器上会让**任何**
`./gradlew` 调用失败。**任何新的 forge 窗口都要走这个门控**（legacyforge 也要下工具链）。
如果将来真建了 legacy 构建，它同样要照抄。

#### 六、落地顺序（**已全部完成**，2026-09-22）

1. ~~core 去虚拟线程 → `core_java_release=17`~~ **已做**（见"三"）。
2. ~~在主构建里加 `forge/forge-1.20.1`，用 `net.neoforged.moddev.legacyforge`（2.0.147，与 neoforge 同版本）；
   先只建这一个模块、跑一次 build，回答"二"里那三条未验项~~ **已做**：三条全过 ——
   legacyforge 在 Gradle 9.7.1 上跑通了整条 NeoForm 管线（含反编译，首次 12m24s，之后 17s）；
   与 ForgeGradle 7 同构建共存（两个插件都加载，`:forge:forge-1.20.1` 下 0 个 FG7 的 remap 任务）；
   `reobfShadowJar` 真的把引用改成了 SRG 名（`getName()` → `m_7755_`，`javap` 实测）。
   **`legacy/` 这个目录不需要存在。**
3. ~~从 `forge/forge-26.1` 抄适配器，按"四"的表换事件模型~~ **已做**，而且比预估小：
   1.20.1 的 `ServerChatEvent` **也有** `getUsername()` / `getRawText()`
   （从 `forge-1.20.1-47.4.23-sources.jar` 读的），所以聊天那一行一个字没改；
   真正改的只有事件注册的接收者、`@Mod` 构造器、权限写法、`getCurrentVersion()` 的方法名。一轮编译即过。
4. ~~CI 加一格~~ **待做**：`release.yml` / `test.yml` 还要加 `-PwithForge=true` 的那一格，
   mc-publish 声明 `game-versions: [1.20.1, 1.20.2]`。

**真机验证**（这一代的验证方式与其它平台不同）：`-PjarOnly` 把**打包产物**放进 `run/mods/` 再起服 ——
因为 Forge 1.20.1 的 dev run 看不见兄弟项目的 classes 目录（它的模块系统只认 jar，
`build/moddev/serverLegacyClasspath.txt` 85 条全是 jar），而 `project(':core')` 解析成的是 classes 目录。
症状是 mod 加载成功、模组列表里也有，但构造器一碰 core 就 `NoClassDefFoundError`。
另外：**jar 里不能有没 relocate 的第三方包**，否则它的模块系统报
`ResolutionException: ... export package ... to module minecraft` —— 见 PROGRESS 那两条坑。

**万一第 2 步没过**（legacyforge 在 Gradle 9.7.1 上确实不行）—— 这条备份方案最终没用上，留档：
建 `legacy/`，自己的 wrapper（Gradle 8.8）+ FG `[6.0,6.2)` + Java 17 toolchain。那时共享 core 的第三个选择
仍然可用：legacy 直接把 core 的源码目录加进自己的 sourceSet
（`sourceSets.main.java.srcDir("../../core/src/main/java")` + core 那 4 个 `compileOnly` 依赖 ——
**core 的依赖全是 `compileOnly`，已核实**），代价是绕过 core 的构建脚本、core 加依赖要记得同步。

---

## 九、效仿鹊桥：抄哪几条，不抄哪几条

**"效仿"的正确粒度是"一窗口一 jar + 一工具链代一构建"，不是"一版本一工程"。** 证据如下。

### 9.1 鹊桥的版本目录是"兼容窗口"，不是"一个 MC 版本"

实测它写了 `support_version.txt` 的 8 个目录（文件内容是 Modrinth 的 game-versions 语法）：

| 目录 | 声明范围 |
| --- | --- |
| `fabric/fabric-1.20.1` | `[1.20.1, 1.20.2]` |
| `fabric/fabric-1.21.6` | `[1.21.6, 1.21.8]` |
| `fabric/fabric-1.21.9` | `[1.21.9, 1.21.10]` |
| `folia/folia-1.21.4` | `[1.21.4,)` |
| `paper/paper-1.17.1` | `[1.17.1,)` |
| `spigot/spigot-1.13` | `[1.13,)` |
| `velocity/velocity-3.3.0`、`-3.4.0` | `[1.20.1]` |

其余目录（`fabric-1.16.5`、`fabric-1.18.2`、`forge-*`、`neoforge-*`、`gtnh`、`cleanroom`、`vanilla` …）
**不写这个文件**，`matrix.sh` 就退回用目录名本身当范围（`supportVersion="$mcVersion"`）。
→ **一个目录 = 一个兼容窗口**。12 个 fabric 目录覆盖的是 1.16.5 → 1.21.11 整段，不是一个版本一个 jar。
这跟第六节"版本轴不是乘法"是同一件事，这里有了实测数据。

### 9.2 抄（价值最高，直接照搬）

1. **窗口化目录 + `support_version.txt`**：目录名写窗口起点，文件里写范围。加版本 = 加一个目录 + 一行范围。
2. **`mc-publish` 发布**：~~`matrix.sh` 扫目录出 JSON，每格独立 build~~ → **已实现（2026-09-21）**，
   但**没抄矩阵**：本项目三个产物、一次 `./gradlew build` 就出全，矩阵是 QueQiao 有十几个格子才需要的。
   抄的是发布那一步的形态 —— 一个产物一次 `Kir-Antipov/mc-publish@v3.3`，各自声明 `loaders` 与
   `game-versions`（`[26.1, 26.2]`）。见 `.github/workflows/release.yml` 与 `test.yml`。
3. **Issue 驱动的版本增长**：用户提一个版本 → 加一个窗口 → CI 出 jar。**覆盖的真正来源是这个，不是架构。**
   鹊桥 README 里就写着"没有找到合适的 Mod/Plugin 版本？欢迎提交 Issues"。
4. **发布物形态**：一个 jar 丢 `mods/` 或 `plugins/`。

### 9.3 不抄

1. **不抄"每平台×版本一个独立工程"** —— 那是**三代**工具链（Java 8↔25、Gradle 6↔9）逼出来的。
   mc-qq 只跨**两代**（26.x 的 Gradle 9 系 / 26.0 之前 Forge 的 Gradle 8 系），**两个构建就够**。
2. **不抄 ModMultiVersionTool** —— 它的注释预处理是为"跨代源码差异"服务的
   （`// IF <= fabric-1.16.5` 里换 Java 版本、换 mixin `compatibilityLevel`、换依赖坐标）。
   你两代之间的源码差异**不止**那两处改名（事件注册模型不同，见 §8.6），但规模仍是"手写"量级，
   比引入一个生成工具便宜。**窗口数超过 6 个左右再回头考虑它。**
3. **不抄 core 的 Java 8** —— QueQiaoTool 停在 Java 8 是为了 1.7.10/1.12.2；core 停在 **17** 就够
   （1.20.1 也是 17）。core 已于 2026-09-22 落到 17（去掉虚拟线程，见 §8.6）。

### 9.4 于是形状变成

```
mc-qq/                        Gradle 9.7.1 + JDK 25 daemon
  core/                       release 17（1.20.1 / 1.21.x / 26.x 通吃）← 2026-09-22 起真的是 17
  fabric/  neoforge/  forge/  26.x 窗口，描述符写范围
  bukkit/                     编译对 1.20.1 → 一个 jar 覆盖 1.20.1 → 26.3
legacy/                       Gradle 8.8 + JDK 21 daemon（独立构建，自己的 wrapper）
  fabric-1.20.1/  forge-1.20.1/  forge-1.21/  neoforge-1.21.1/
  每个窗口一个子项目 + support_version.txt
```

⚠️ **`legacy/` 那一块现在是个问号**：§8.6 发现 1.20.1 的 Forge 可以用 `net.neoforged.moddev.legacyforge`
（与主构建的 moddev 同 artifact 同版本）编，所以它可能根本不用存在。先按 §8.6"六"的第 2 步试一次，
再决定要不要这一层。

legacy 构建怎么共享 core，三个选择（**只有真建了 legacy 才需要选**）：

* (a) core 发到 Maven Central（你已经有这条链），legacy `implementation("io.github.skiesworld:mcqq-core:x")`
  —— 干净、解耦，但改 core 要发版；
* (b) legacy 的 settings 里 `include(":core")` + `project(":core").projectDir = file("../core")`
  —— 改 core 立刻生效，代价是 legacy 的 Gradle 8.8 也得能编 core 的 release 17 源码；
* (c) legacy 直接把 core 的**源码目录**加进自己的 sourceSet
  （`srcDir("../../core/src/main/java")` + core 那 4 个 `compileOnly` 依赖 —— core 的依赖**全是 compileOnly**，已核实）
  —— 最省，release 级别由 legacy 自己定；代价是绕过 core 的构建脚本，core 加依赖要记得同步。

我倾向 (b)，等窗口多了、或 legacy 需要独立发版时再换 (a)。（(c) 的取舍见 §8.6 五）

---

## 十、先只做 26.x 的可行性（含版本占比实测）

"只做 26，先看社区反响"这个方向，先看数据再判断。

### 10.1 现在的服务器都在什么版本上

数据源：**bStats 的 `minecraftVersion` 图表 API**，样本 = `TAB Reborn`（bStats id 5304），
**46,904 台服务器**（2026-09-21 拉取）：

```bash
curl -s "https://bstats.org/api/v1/plugins/5304/charts/minecraftVersion/data"
```

| 版本线 | 服务器数 | 占比 |
| --- | --- | --- |
| 1.21.x | 25,416 | 54.2% |
| **26.x** | **16,395** | **35.0%** |
| 1.20.x | 2,123 | 4.5% |
| ≤1.15 | 1,867 | 4.0% |
| 1.16–1.17 | 627 | 1.3% |
| 1.19.x | 313 | 0.7% |
| 1.18.x | 163 | 0.3% |

26.x 内部：**26.2 = 11,647 ｜ 26.1.2 = 4,230 ｜ 26.3 = 402 ｜ 26.1.1 = 105 ｜ 26.1 = 11**。
1.21.x 内部：**1.21.11 = 16,463**（单版本就占了 35.1%，比整个 26.x 还多一点）｜ 1.21.4 = 2,780 ｜ 1.21.8 = 2,018 ｜ 1.21.10 = 1,547 ｜ 1.21.1 = 1,466。

### 10.2 三条读数

1. **只做 26.x ≈ 覆盖 35% 的服务器**（这个样本口径），放弃约 60%。对"看反响"够用，但不是"大部分"。
2. **26.2 比 26.1.2 多一倍多** → 服务器跟新 minor 很快。所以"锁 26.1.2"是错的，
   **"26.x 一个窗口 + 描述符写 `>=26.1`"才对**（前提是 API 真的没断，见 10.4）。
3. **1.21.11 单版本 = 35%**，跟整个 26.x 一样多。这条比"1.21.x 占 54%"更有用：
   真要扩版本，第一优先不是"把 1.21.x 全做"，而是**加一个 1.21.11 窗口**（1.21.9–1.21.11 大概率同代工具链）。

⚠️ **样本口径**：这是 **Bukkit 插件侧**的样本，偏向 Paper/Spigot 的插件服。**模组服（NeoForge/Forge）的
26.x 占比大概率更低**（整合包跟版本更慢）。所以如果只做 26，mod 侧的覆盖面会比 35% 更差 ——
这条我没有数据，是估计，别当结论用。

### 10.3 只做 26.x 省掉了什么

| 省掉 | 说明 |
| --- | --- |
| legacy 构建 | 不用 Gradle 8.8 + JDK 21 daemon + ForgeGradle 6 + SRG/remap 那一整套 |
| 跨版本验证 | bukkit 不用"两端各跑一次" |
| 窗口机制 | 一个窗口，不需要 Stonecutter / 生成工具 |
| 平台数量 | 可以先只做 1～2 个平台 |

从"4 平台 × 2 代 + 窗口机制"降到"26.x 一个窗口 × 1～2 平台"，**工作量大致是 1/3 到 1/2**。
所以"落地更快"这个判断成立。

### 10.4 但即使只做 26，也要守两条写法（否则以后加版本会重写）

1. **描述符写范围，不锁版本**：`fabric.mod.json` 的 `depends.minecraft: ">=26.1"`（现状就是）、
   NeoForge/Forge 的 `versionRange=[26.1,)`、Paper 的 `api-version: '26.1'`。
   **这条已经从先验假设变成实测**（2026-09-21）：编译对 26.1.2 的 bukkit jar 直接跑在 Paper 26.2 上
   （`/qq status` 报 `平台 paper-26.2`）；Fabric 侧用 26.2 + Fabric API 0.161.0+26.2 **源码一行没改就编译通过并加载**。
   细节与口径见 [PROGRESS.md](PROGRESS.md)。**26.3 那一跳还没验**（现在还是 pre）。
2. **命令用最保守的注册方式**：`plugin.yml` 的 `commands:` + `onCommand`，**不要**用 Paper 的 Brigadier
   与 `paper-plugin.yml`。这是唯一一处"用了 26 独有 API 就得为以后降版本重写"的地方；
   其余（4 个事件、广播、配置目录、主线程 hop）在最老的 API 上也都有。

守这两条的代价几乎为零，收益是：**"先只做 26"和"以后低成本加 1.20.1"不冲突**。
真正的分叉点在 Fabric/Forge 的 legacy 构建，而不在 bukkit —— bukkit 那个"一个 jar 覆盖 1.20.1→26.3"
只差"改一行 compileOnly 坐标 + 别用 Brigadier"。

### 10.5 结论

**先只做 26.x 是合理的滩头策略**：省一半工作量、拿到 35% 的覆盖面、且不锁死以后扩窗口的路径
（第 9 节的窗口化流程 + 第 8 节的代际判据都留着）。观测点：Modrinth 的下载与版本筛选、
Issue 里要 1.20.1 / 1.21.11 的声音 —— 有声音就按窗口加，没声音就一直省着。

---

## 十一、待你拍板

1. **本轮范围**：建议**只做 26.x 一个窗口**（第十节），平台 **bukkit 一族 + fabric** 都已落地。
   下一步做哪个：真机验 bukkit / 加 neoforge / 验 26.2-26.3 窗口？
2. **两条写法**（第十节 10.4）：描述符写范围、命令不用 Brigadier —— 确认按这个来？
3. **平台顺序**：bukkit 一族先（覆盖面最大、零 MC 工具链），还是先把现有 fabric 挪进新骨架再扩？
4. **legacy 骨架什么时候搭**：现在搭一个"空 adapter 能在 Gradle 8.8 + ForgeGradle 6 下编译并加载"的
   最小工程（把最脏的未知先证掉），还是等真有 1.20.1 / 1.21.11 需求再搭？
5. **core 的共享方式**（只在要搭 legacy 时才有意义）：走 `projectDir = ../core`（改完立刻生效），
   还是直接发 Maven Central（解耦但要多一次发版）？
6. **路线 D 不用了**（你已确认发布物就是一个 jar）。但要不要在文档里留一条"某平台没人做时的退路"备忘？
7. 老问题（PROGRESS 里那两条）：包名 `com.example.mcqq` → `io.github.skiesworld.mcqq`？
   mc-qq 要不要 `git init`？

---

## 附：本轮参考过的仓库（克隆在 `.workbuddy-ai/refs/`，看完可删）

* `17TheWord/QueQiao`、`17TheWord/QueQiaoTool`、`kitUIN/ModMultiVersionTool`、`jaredlll08/MultiLoader-Template`（26.3 分支）
* 另有解包物用于核实工具链与 API 表面：`forge-mdk`（26.1.2-64.1.3）、`forge-mdk-121`（1.21-51.0.0）、
  `forge-mdk-1201`（1.20.1-47.4.23）、`paper-api.jar`（26.1.2.build.74-stable）、
  `paper-api-1.20.1.jar`（1.20.1-R0.1-20230921.165944-178）。
