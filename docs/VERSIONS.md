# 加一个新的 MC 版本要做什么

大多数情况**代码一行都不用改** —— 改 `gradle.properties` 里的版本事实、真机跑一遍就完事。
因为 core 零 MC 依赖、描述符写的是版本**范围**、而 adapter 碰 MC 的地方只有十来处。
只有"API 被删/改了、旧写法也没了"时才需要动结构，见第四节。

## 一、先分清三种情况（成本差一个数量级）

| | 情况 | 例子 | 要做什么 |
| --- | --- | --- | --- |
| ① | 新版本，我们碰的 API 没变 | 26.1.2 → 26.2 | 改版本事实 → 真机跑 → 把窗口写进那个窗口的 `publish_game_versions_*`。**代码零改动。** |
| ② | API 变了，但旧写法还在（改名留别名、加了个重载） | 未遇到 | 改**那一个平台**的 adapter（一两处），core 不动，**仍是一个 jar 覆盖两版** |
| ③ | API 变了且旧写法没了（二进制不兼容） | 未遇到（26.1 的 `Commands.hasPermission(int)` 是这一类） | 一个 jar 覆盖不了 → **加一个"版本窗口"子项目**，见第四节 |

①已经实测过一次：编译对 26.1.2 的 bukkit jar **直接跑在 Paper 26.2 上**（`/qq status` 报 `平台 paper-26.2`）；
Fabric 侧用 26.2 的依赖**源码一行没改就编译通过并加载**。26.3 那一跳还没验。

## 二、为什么 MC 升级基本碰不到 core

`core` 是**零 MC 依赖**的（`compileOnly` 只有 brigadier 这一个 Mojang 独立库），
所以 MC 的 API 怎么变都碰不到业务逻辑 —— 配置、模板、路由、命令树、QQ 侧全在那儿。

真正碰 MC 的东西，每个平台就这么多：

| | 在哪 | 干什么 |
| --- | --- | --- |
| 5 个方法 | `MinecraftPlatform` 的实现 | `label()`、`configDir()`、`broadcast()`、`onMainThread()`、`registerCommands()` |
| 2 个方法 | `CommandSource` 的实现 | `hasPermission()`、`reply()` |
| 4 个监听 | 监听器类 | 聊天 / 进服 / 退服 / 死亡（只从事件里取值） |
| 1 行 | 平台入口 | `platform.registerCommands(bridge.commands())` |
| 1 份描述符 | `src/main/resources` | 依赖声明与版本范围 |

**所以一次 API 破坏的修复范围就是这么大** —— 不是"改代码"，是"改十来行"。

## 三、具体步骤（以 26.3 为例）

1. **改 `gradle.properties` 的版本事实**（唯一出处）：
   ```
   minecraft_version=26.3            # fabric 用（loom）
   loader_version=<最新的 fabric loader>
   fabric_api_version=<对应 26.3 的 Fabric API>
   paper_api_version=<对应 26.3 的 paper-api 坐标>
   neoforge_version=<26.3 的 NeoForge>
   forge_version=<26.3 的 Forge>
   ```
   顺带看 `minecraft_version_range`（描述符的范围）要不要跟着抬。
2. `./gradlew clean build -PwithForge=true` —— **编译器会精确指出哪几个调用断了**。
   （`-P` 会覆盖 `gradle.properties` 的同名属性，所以想先拿新版本试一遍可以直接在命令行给，
   不必改文件。）
3. 按断点位置分派：
   * **断在 `core`** → 那是设计错误（core 不该认识 MC），当场修，并补一个测试防它再犯；
   * **断在某个 adapter** → 按第一节的 ②/③ 处理（②就地改；③见第四节）。
4. **真机跑一遍**。测试服目录都配好了 `eula.txt` 与非默认端口 + RCON：
   `paper/paper-1.20.1/run`（Paper 26.2）、`paper/paper-1.20.1/run-1.20.1`（Paper 1.20.1）、
   `fabric/fabric-26.1/run`、`neoforge/neoforge-26.1/run`、`forge/forge-26.1/run`、`forge/forge-1.20.1/run`。
   跑 `/qq status` 看 `平台 xxx-<新版本>`，再跑 `/qq help`。
5. 把窗口写进 `publish_game_versions_*`（Modrinth / CurseForge 声称的范围），**只写实测过的**。
   每个窗口一份（`_26_1` / `_1_20_1`），不能共用：mod 平台的 jar 是按窗口编译的。
   （Bukkit 一族是例外 —— 那两份编译对 1.20.1，一个 jar 覆盖整段，见
   [MULTIPLATFORM.md](MULTIPLATFORM.md) 第三节。）
6. 若是 ③：在 `.github/workflows/platforms.yml` 的矩阵里加一行（含它那个窗口的 `game-versions`），
   其余 workflow 一个字不用改。

## 四、情况 ③：需要"版本窗口"时怎么做

**当前结构没有版本轴**：每个平台一个子项目，编译对**恰好一个** MC 版本。所以一个 jar 能覆盖
26.1 → 26.2，是因为我们碰的 API 没变（描述符写的是范围，同一个 jar 直接跑）；一旦某个 API 被删/改，
同一个 jar 就同时满足不了新旧两版。

届时的做法，按代价从小到大：

1. **尽量绕开那个 API**（首选）。我们的 MC 接触面很小，很多"变了"的方法都能用更保守的写法替代
   （这也是为什么命令用最保守的注册方式：`plugin.yml` + `onCommand`，而不是 Paper 的 Brigadier）。
2. **加一个同平台的"版本窗口"子项目**：**嵌套**在平台目录里，而不是平级 ——

   ```
   fabric/fabric-26.1/   ← 现在的（覆盖 [26.1, 26.2]）
   fabric/fabric-26.3/   ← 以后加这个，各 5 个文件
   ```

   窗口目录名用**窗口起点**，范围写在描述符里。用完全体（`fabric/fabric-26.1/`）而不是
   `fabric/26.1/`：**矩阵里要能直接看到名字**（每个窗口一格 job，`name` 就是 `fabric-26.1`），
   而且路径片段自解释。26.x 内部工具链是同代，所以**只是多一个子项目**，不需要独立构建
   （对比：跨代才要独立构建，见 [MULTIPLATFORM.md](MULTIPLATFORM.md) 第六节）。
   加完之后 `platforms.yml` 的矩阵加一行，test / release 自动带上它。
3. **版本条件源码**（同一子项目里按版本分 source set）：只在差异极小（一两个方法）时值得，
   否则 Gradle 那套 machinery 比复制 5 个文件更贵。
4. **反射兜底**（同一个方法只是改了名）：最省事也最脏，只在"就这一个方法、且不想维护两个子项目"时用。

**判据**：差异 ≤ 1 个方法 → 考虑 4 或 1；差异集中在"这个平台碰 MC 的那十来处" → 用 2（复制子项目），
因为那十来处本来就是"平台专属"的，复制它们不违反分层。
