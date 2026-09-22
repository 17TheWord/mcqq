# 多平台 / 多版本

这份文档讲三件事：**现在是什么形状**、**每个平台的构建事实**、**加平台或加版本时要知道的坑**。

## 一、形状

```
mc-qq/                        Gradle 9.7.1 + JDK 25 daemon
  core/                       零 MC 依赖；release 17 —— 一份 core 同时服务 1.20.1 与 26.x
  bukkit-common/              Bukkit 一族的共享部分，编译对 spigot-api
  fabric/fabric-26.1/
  neoforge/neoforge-26.1/
  forge/forge-26.1/           ← 按需 include，见下
  forge/forge-1.20.1/         ← 同上
  paper/paper-1.20.1/
  spigot/spigot-1.20.1/
```

项目路径是 `平台:窗口`（目录同名）。**窗口名用窗口起点**：`fabric-26.1` 覆盖 `[26.1, 26.2]`，
范围写在描述符里，不写在这里。

`forge:*` 是**按需**加进来的：ForgeGradle 的 mavenizer 在**配置阶段**就要下载整套 Forge 工具链，
而 Gradle 会配置所有 include 的项目 —— 它一旦在项目列表里，网速差的机器上**任何** `./gradlew`
调用都会失败，连另外几个平台也编不了。所以默认不带 forge，要它加 `-PwithForge=true`（CI 就是这么调的）。

core 唯一认识的 MC 形状就是这条接缝：

```java
public interface MinecraftPlatform {
    String label();                  // "fabric-26.1.2" / "paper-1.20.1" / "neoforge-26.1.2"
    Path   configDir();
    void   broadcast(String line);   // QQ→MC：core 给带 § 的文本，adapter 负责渲染成各自的形式
    void   onMainThread(Runnable r);
    void   registerCommands(CommandTree tree);
}
```

* 出站只有一个门：`Bridge.forward(McEvent, Map<String, String>)`。
* **必须承认的取舍**：`§` 颜色码留在 core 里当"线格式"。这是 core 零 MC 依赖的代价 ——
  要更干净就得引入 `StyledLine{text, color}` 模型，现在不值。
* `MinecraftServer` / `ServerPlayer` / `Component` 一律不进 core。

## 二、平台事实

| 平台 | 构建插件 | 依赖坐标 | 描述符 | Java | 关键约束 |
| --- | --- | --- | --- | --- | --- |
| Fabric | `net.fabricmc.fabric-loom`（no-remap） | `fabric-loader:0.19.5`<br>`fabric-api:0.155.3+26.1.2` | `fabric.mod.json` | 25 | 去混淆后无 `modImplementation` / 无 `remapJar`，依赖写 `implementation` |
| NeoForge | `net.neoforged.moddev` 2.0.147 | `26.1.2.109` | `META-INF/neoforge.mods.toml` | 25 | 与 Forge 已分家；**modId 不允许连字符** |
| Forge 26.x | `net.minecraftforge.gradle` `[7.0.17,8)` | `net.minecraftforge:forge:26.1.2-64.1.3` | `META-INF/mods.toml` + `pack.mcmeta` | 25 | MDK 里**没有 mappings 行**；事件 API 与 NeoForge 完全不同（每个事件自带静态 `BUS`） |
| Forge 1.20.1 | `net.neoforged.moddev.legacyforge`（同 moddev 的版本） | `47.4.23` | 同上 | 17 | 见第六节 |
| Paper / Spigot | 不需要 MC 工具链，`java` + shadow | `paper-api` / `spigot-api` | `plugin.yml` | 17 | 见第三节 |

仓库地址：`maven.fabricmc.net`、`maven.neoforged.net`、`maven.minecraftforge.net`、
`repo.papermc.io`、`hub.spigotmc.org`。

几个容易搞错的点：

* **Paper 的版本号有两种写法**：26.x 是 `26.1.2.build.74-stable`，1.20.1 那代是 `1.20.1-R0.1-SNAPSHOT`。
* **Spigot 只发 snapshots**：`26.1-R0.1-SNAPSHOT` … `26.3-R0.1-SNAPSHOT`、`1.20.1-R0.1-SNAPSHOT`。
* **NeoForge 没有 1.20.1**（maven 上最早是 `20.2.12-beta`，从 1.20.2 起）。1.20.1 只有 Forge 与 Fabric 两条路。
* **Paper 的描述符**：`paper-plugin.yml` 的 `dependencies` 分 `bootstrap` / `server` 两段，
  不用 `commands` 字段（命令走 Brigadier），且插件之间类加载隔离。`plugin.yml` 与它可以同时放进一个 jar。
  mc-qq **只用 `plugin.yml` 那套**（理由见第七节）。

## 三、Bukkit 一族：编译对最老目标，一个 jar 覆盖 1.20.1 → 26.x

做法是**编译对最老的目标**（`paper-api:1.20.1-R0.1-SNAPSHOT` / `spigot-api:1.20.1-R0.1-SNAPSHOT`）
+ 只用稳定 API + `options.release = 17` + `api-version: '1.20'`。

反着做（编译对最新）**只会缩小覆盖范围**，不损失功能 —— 所以没有理由那么做。
代价是只能用 1.20.1 就有的 API；mc-qq 用到的全是十来年没动过的东西：

| 需要的东西 | paper-api 1.20.1 | paper-api 26.x |
| --- | --- | --- |
| `io.papermc.paper.event.player.AsyncChatEvent` | 在 | 在 |
| `org.bukkit.event.player.AsyncPlayerChatEvent` | 在 | 在（旧事件没被删） |
| `PlayerDeathEvent` / `PlayerJoinEvent` / `PlayerQuitEvent` | 在 | 在 |
| `Bukkit.broadcastMessage` | 在 | 在 |
| Folia 的 `threadedregions.scheduler.*` | 在 | 在 |
| `io.papermc.paper.command.brigadier.Commands` | 在 | 在（**但不要用**，见第七节） |

⚠️ **`api-version` 的语法是 `major.minor`，不是完整的 MC 版本。** 写 `'1.20.1'` 会被 Paper 1.20.1
以 `InvalidPluginException: Unsupported API version 1.20.1` 拒绝加载；要写 **`'1.20'`**。
（26.x 写 `'26.1'` 恰好合法，因为那正好是 major.minor —— 别因此以为完整版本号也行。）

## 四、Fabric

一个窗口。去混淆之后**不存在"抹平映射"这件事**：运行时就是 Mojang 名，所以没有 `modImplementation`、
没有 `remapJar`，依赖写 `implementation`。

**1.20.1 是混淆时代**，要做的话得换回 remap 的 loom（legacy 插件 id `fabric-loom`）+ `mappings` +
`modImplementation` + `remapJar`，与 26.x 用的 no-remap id 不是同一条路。
两个 id 在同一个 loom 版本下都在发布，但**两个 id 同时出现在一个 Gradle 构建里会不会打架没验证过**
（共享静态状态 / 配置名）；loom 对 1.20.1 的 remap 链路是否完整可用也没验证。第一次做要先用空 mod 跑通。

## 五、NeoForge

一个窗口，`net.neoforged.moddev`。**modId 不允许连字符**，所以 mod id 用 `mcqq`
（项目名是 `mc-qq`，两者不是一回事）。

## 六、Forge

**两个窗口，都在主构建里**：`forge/forge-26.1/` 用 ForgeGradle，`forge/forge-1.20.1/` 用
ModDevGradle 的 `net.neoforged.moddev.legacyforge`。

`legacyforge` 与 neoforge 用的 `net.neoforged.moddev` 是**同一个 artifact 同一个版本**，
所以 1.20.1 只是同一构建里的另一个窗口，**不需要一套独立的 legacy 构建**（独立的 Gradle wrapper +
Gradle 8.8 + JDK 21 daemon + ForgeGradle 6 那一整套）。

判据很便宜：**下载目标版本的官方 MDK，看两行** —— `gradle-wrapper.properties` 的 `distributionUrl`
和 `plugins` 块里的工具链版本。要求同一代 → 可以并进主构建；不同代 → 才需要独立构建。
（1.20.1 / 1.21 的 Forge 官方 MDK 是 ForgeGradle 6 + Gradle 8.x，26.x 是 ForgeGradle 7 + Gradle 9.x。）

⚠️ **1.20.1 的 dev run 有两个坑**，都在 `forge/forge-1.20.1/build.gradle` 里写了：

* **dev run 看不见兄弟项目的 classes 目录**。它的模块系统把类路径建成一串 **jar**，而 `project(':core')`
  在同一构建内解析成的是 classes 目录 → 被丢掉。症状极迷惑：mod 加载成功、模组列表里也有，
  构造器一碰 core 的类就 `NoClassDefFoundError`。所以这个窗口的验证方式是验**打包产物**
  （`installDevJar` + `-PjarOnly`），不是常规的"source set 当 mod"。
* **塞进 dev 服的必须是未 reobf 的 jar**（`build/devlibs/`）。dev run 的 launchTarget 是
  `forgeserveruserdev`，那里游戏类用的是 **named** 映射；`build/libs/` 那份已经 reobf 成 SRG 了，
  放进去会在第一次调 Minecraft API 时 `NoSuchMethodError`。那份是给**正式服**用的。
  → 所以这个平台的验证要分两条路：**代码对不对**看 dev 服，**reobf 对不对**看字节码并与
  `build/moddev/artifacts/namedToIntermediate.tsrg` 对照。

## 七、加版本 / 加平台时要守的规则

1. **描述符写范围，不锁版本。** Fabric `depends.minecraft: ">=26.1"`、NeoForge / Forge 的
   `versionRange=[26.1,)`、Bukkit 的 `api-version` 写**最低**支持的那一代。
   这样"一个窗口 + 描述符写范围"就能吃掉一整段 minor，而不是锁一个版本。
2. **命令用最保守的注册方式**：`plugin.yml` 的 `commands:` + `onCommand`，**不要**用 Paper 的
   Brigadier 与 `paper-plugin.yml`。这是唯一一处"用了 26 独有 API 就得为以后降版本重写"的地方；
   其余（4 个事件、广播、配置目录、主线程 hop）在最老的 API 上也都有。
3. **版本轴不要相乘。** 一个平台一个窗口，窗口内靠描述符的范围覆盖 minor；
   只有某个 minor 真的改了签名时才加窗口。不要为"loader × minor"的每个组合建工程。
4. **Java 分档**：1.18–1.20.4 = 17、1.20.5–1.21.x = 21、26.x = 25。
   `core` 停在 **17** 就同时服务 1.20.1 与 26.x；只有要下到 1.16.5 及更老（Java 8）才需要降到 8。
   **SDK 是 `--release 17` 编译的（要求 JDK 17+）**，所以 core 跑在 Java 8 上不行。

## 八、各平台踩过的坑

* **Fabric / NeoForge / Forge 的 jar 里不能有没 relocate 的第三方包。** 服务端自己就带 gson 与
  snakeyaml（版本还和我们依赖的不一样），mod 生态里 kotlin-stdlib 更是经典撞车项。统一 shadow + relocate。
  Forge 的模块系统撞车时报的是 `ResolutionException: ... export package ... to module minecraft`。
* **`plugin.yml` 不能带 YAML 1.1 不认的东西**（比如没加引号的 `version: 0.0.1` 之外的怪写法），
  否则 Bukkit 直接拒绝加载；`api-version` 必须是 major.minor（见第三节）。
* **Folia**：`Bukkit.getScheduler()` 在 Folia 上不可用，广播要走 global region scheduler，
  并且 `plugin.yml` 里要写 `folia-supported: true`。adapter 的 `onMainThread` 要分档
  （Bukkit / Folia / 其余）。Folia 的探测不能靠"要一个 region scheduler"—— Paper 也有那套 API，
  两者都会应答；真正的区别是服务端 jar 里有没有 `io.papermc.paper.threadedregions.RegionizedServer`。
* **Forge 26.x 活着**（官方下载页 latest 一直在跟），别信说它已死的二手博客。

## 九、怎么验

* **真机验证用 RCON**，不要靠 stdin 管道：`refs/rcon.py <port> <password> "<cmd>"`。
  只看 `Done (x.xs)!` 会漏掉"命令能跑但一调 API 就炸"这类问题。
* **测试服目录**（都在 `.gitignore` 里）：`paper/paper-1.20.1/run`（Paper 26.2）、`run-1.20.1`
  （Paper 1.20.1，端口 25631 / RCON 25685）、`forge/forge-1.20.1/run`。
  跑测试服**用绝对路径**：`cd` 之后的相对路径很容易落到模块目录，服务端会找不到 jar
  或把 world 生成在错的地方。
* **`clean` 很贵**：它会清掉 `build/moddev`，ModDevGradle 于是重跑一遍 NeoForm 反编译 ——
  一次 `clean build` 从约 40 秒变成 8～12 分钟。别随手 clean。
* 各平台的 Java 版本不同，1.20.1 的服务端要 **JDK 17**；本机没有的话，
  Gradle toolchain 拉的那个在 `<GRADLE_USER_HOME>/jdks/` 下可以直接用。
