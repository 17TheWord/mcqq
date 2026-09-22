# 平台重构规划：拆 bukkit 一族 + 嵌套版本窗口 + 抽公共配置

> 起因：用户指出「bukkit 目录的产物无法在 Spigot 跑吗？这可能不符合我的本意」，并提出
> ① 拆成 `spigot/ paper/ folia/` 三个目录 ② 改成 `fabric/fabric-xxx/` 的嵌套形状 ③ 抽出公共配置。
> 这份文档先给数据，再给形状与步骤，最后列代价与待拍板项。

## 一、调研：现在服务器都在用什么

**方法**：bStats 的 `serverSoftware` 图（每个插件一张，聚合起来当市场样本）。
样本 = 5 个装了 bStats 的插件（TAB Reborn 等），合计 **46,482 台服务器**。
（bStats 的 API 现在要带 User-Agent，否则 403。）

| 服务端软件 | 占比 | 说明 |
| --- | --- | --- |
| **Paper 系（含 Purpur / Pufferfish / Airplane 等分支）** | **88.1%** | 压倒性多数 |
| **Spigot 系（含 CraftBukkit）** | **6.1%** | 约 2800 台 —— 这些**现在完全跑不了我们的插件** |
| Folia | 0.9% | 加上 Canvas（Folia 分支，0.6%）≈ **1.5%** |
| 混合端 Arclight / Mohist / CatServer | 1.1% + 0.6% + 0.2% ≈ **1.9%** | Forge + Bukkit 层，只可能跑得动 Spigot 变体 |
| 其它（Leaves / Ketting / Titanium …） | < 1% | 多为 Paper/Folia 分支 |

⚠️ **样本偏差**：这是"装了这几个插件的服务器"，会比全体更偏 Paper（愿意装插件的服更可能上 Paper）。
所以 6.1% 这个数字按**量级**看：Spigot 确实还有一小块真实市场，但不是大头。

**结论**：Spigot 值得支持（6% 不是噪声），但**不能为它牺牲 Paper 侧的体验**（88%）。
Folia 1.5% 值得覆盖，而它**不需要单独目录**（见第三节）。

## 二、bukkit 一族的 API 差异清单（决定拆几个目录）

| | Spigot | Paper | Folia |
| --- | --- | --- | --- |
| 聊天事件 | `AsyncPlayerChatEvent`（老 API，已废弃但仍在） | `AsyncChatEvent`（`io.papermc.paper.event.player`） | 同 Paper |
| 调度 | 经典 `BukkitScheduler` | 经典（Paper 也实现了区域调度器） | **区域调度器，没有唯一主线程**；碰别的区域的玩家会抛 |
| 描述符 | `api-version` | `api-version` | 再加 `folia-supported: true` |
| Adventure（`Component`） | **待核实**（若没有，改用 `§` 传统字符串即可） | 自带 | 自带 |
| `getCommand(String)` | 在 `JavaPlugin` 上 | 同 | 同 |

**关键结论：Folia 不需要单独目录。** 这一条是**实测**的 —— 同一个带 `folia-supported: true` 的 jar
在 Paper 26.2 与 Folia 26.1.2 上都跑过（`/qq status` 分别报 `平台 paper-26.2` 与 `平台 folia-26.1.2`），
调度差异是**运行期探测**解决的（`Class.forName("io.papermc.paper.threadedregions.RegionizedServer")`）。

所以三种做法：

| 方案 | 产物 | 代价 | 好处 |
| --- | --- | --- | --- |
| **A. spigot + paper**（推荐） | 2 个 | paper 那份要带 Folia 探测（已有，10 行） | 产物少；Folia 用户拿 paper 那份就能跑（已验证） |
| B. spigot + paper + folia | 3 个 | `folia/` 与 `paper/` **只差描述符一个字段**，等于复制 | 每个变体零运行期探测，概念上更"一个平台一个目录" |
| C. 只有 spigot（一个 jar 全吃） | 1 个 | 必须用已废弃的 `AsyncPlayerChatEvent`；**Paper 是否仍触发它未实测** —— 一旦 Paper 哪天不触发了，88% 的市场聊天就哑了 | 产物最少 |

**倾向 A**：Spigot 那份用老事件（它本来就在 Spigot 上），Paper 那份用新事件（不碰废弃 API），
Folia 靠 paper 那份 + 运行期探测覆盖。**如果用户坚持"零运行期探测"，那就 B** ——
但要知道 `folia/` 与 `paper/` 的差异只有一行描述符。

## 三、目录形状：平台/窗口嵌套

```
core/                        零 MC 依赖
bukkit-common/               ← 新增：三个 bukkit 变体共享（编译对 spigot-api，最低公分母）
spigot/spigot-26.1/          ← 新增：AsyncPlayerChatEvent，不用任何 Paper 专有 API
paper/paper-26.1/            ← 现在的 bukkit/：AsyncChatEvent + folia-supported
fabric/fabric-26.1/          ← 现在的 fabric/
neoforge/neoforge-26.1/
forge/forge-26.1/
```

* **窗口目录名用窗口起点**（QueQiao 也这样：`fabric/fabric-1.20.1/` 覆盖 1.20.1→1.20.2），
  范围写在描述符里（`[26.1,)`）。以后加 26.3 就是**加一个同级目录**，不动别的。
* **为什么嵌套而不是平级**：`fabric-26.3/` 和 `fabric/` 并排会让人分不清平台与窗口（用户指出的）。
* **和 QueQiao 的差别**：那边每个窗口要不同的工具链代际（1.16.5 用 Gradle 6），所以每格是**独立构建**；
  我们在 26.x 内部工具链同代，**同一个 Gradle 构建里放多个窗口**即可 —— 只是目录组织。
* **`bukkit-common/` 值不值**：三个变体约 85% 的代码是同一份（`XxxPlatform` 的 5 个方法、
  `XxxCommandSource`、`QqCommands`、4 个监听里的 3 个、`plugin.yml` 的大半）。抽出来的话每个变体只剩
  「聊天监听 + 描述符」两处；不抽的话就是 5 个小文件复制三遍。**倾向抽**，但它是这一步里唯一
  引入新模块的动作，也可以留到真有第三个变体时再抽。

## 四、公共配置抽取（用户点的这一块）

| 抽什么 | 现在在哪 | 抽到哪 |
| --- | --- | --- |
| 版本事实（`mod_version` / 各平台坐标 / MC 窗口） | `gradle.properties` ✓ 已经是一处 | 不动；`platforms.yml` 只读 |
| **描述符的公共字段**：显示名、描述、作者、许可证、仓库/issue 链接 | **4 份描述符里各写一遍** | 进 `descriptors.properties`（UTF-8），各描述符用 `${...}` expand（现在只 expand 了 version/mod_id/范围）。**不要放 `gradle.properties`** —— 那是 ISO-8859-1 读的，非 ASCII 会双重编码 |
| **打包与 relocate**：`bundled` 配置、SDK/snakeyaml 依赖、shadowJar 的 relocate 列表与排除项、LICENSE/THIRD-PARTY 注入 | **fabric / neoforge / forge 三份 build 文件里各写一遍** | 一个约定插件（`buildSrc/` 或 `gradle/bundle.gradle.kts`），各平台 `apply` |
| 平台清单（名字/子项目/jar/loaders/额外参数） | `platforms.yml` ✓ 已经是一处 | 不动 |
| 依赖版本的集中声明 | `gradle.properties` + `property("...")` ✓ | 可选升级成 `gradle/libs.versions.toml`（version catalog，Gradle 官方推荐，类型安全） |

**关于鹊桥的 `version.txt`**：**我们不需要它。** 那是它给"每个窗口一个独立工程"准备的 ——
每个窗口有自己的 `gradle.properties`，所以版本号得单独放一个文件让 CI 读。
我们只有一个 `gradle.properties`，CI 用 `grep` 读它（`platforms.yml` 已经这么做了），
**等价且少一个出处**。

## 五、迁移步骤（每步都能单独验证）

> **已定的四条**（2026-09-22 用户拍板）：拆 **2 个**（spigot + paper，Folia 靠 paper + 运行期探测）；
> **做** `bukkit-common/`；命名用**完全体** `fabric/fabric-26.1`（矩阵里要能直接看到名字）；
> **不上** version catalog。

1. ✅ **纯搬家**（零功能变化，**已完成**）：
   `fabric/` → `fabric/fabric-26.1/`、`neoforge/` → `neoforge/neoforge-26.1/`、
   `forge/` → `forge/forge-26.1/`、`bukkit/` → `paper/paper-26.1/`；
   改 `settings.gradle.kts`（`include("平台:窗口")`）、`platforms.yml` 矩阵、`release.yml` 附件路径、文档路径；
   **产物名改成带平台与窗口**（`mc-qq-fabric-26.1-<版本>.jar` ……）—— 不加窗口的话同一个平台的两个窗口会撞名。
   **验证**：`clean build` 全绿（47 测试）+ Paper 真机（`平台 paper-26.2`、命令全通）。
   顺带修掉一处早先引入的错消息：`/qq status` 里显示的配置路径少了 `config/` 前缀，现在用平台给的真实路径。
2. ✅ **抽 `bukkit-common/`**（**已完成**）：编译对 **spigot-api**，paper 变体从 6 个文件削到 **3 个**
   （入口 + 聊天监听 + Adventure/Folia 的渲染与调度），共享的 5 个类进 `bukkit-common/`。
   关键设计：**发送消息做成抽象钩子 `sendLine(CommandSender, String)`** —— 核心产出的是 `§` 色码字符串，
   Paper 用 `LegacyComponentSerializer` 渲染成 Component、Spigot 直接 `sendMessage(String)`，
   共享代码两边都不碰。
   **验证**：`clean build` 全绿 + Paper 26.2 真机（`平台 paper-26.2`、命令全通）；
   jar 里确认是 5 个 `bukkit/common` 类 + 3 个 `paper` 类。
   **顺带核实掉一条待核实项**：**spigot-api 不带 Adventure**（它的传递依赖里只有老的 `bungeecord-chat`，
   没有 kyori）→ 所以共享代码不能用 `Component`，上面那个钩子不是可选而是必需。
3. ✅ **加 `spigot/spigot-26.1/`**（**已完成，但只在 Paper 上验了"装错 jar"那条路径**）：
   三个文件（入口 + 老聊天事件监听 + `§` 字符串渲染），其余全来自 `bukkit-common`。
   **编译对 spigot-api 通过** —— 这本身就证明了 `AsyncPlayerChatEvent` 与 `getMessage()` 在那边存在。
   **拒绝路径已实测**：把 spigot 那份装到 Paper 26.2 上，它打印
   "这个 jar 是给 Spigot / CraftBukkit 的……插件已停用" 并停用自己 ✓。
   这也把"Paper 是否仍触发 `AsyncPlayerChatEvent`"这个未知**绕过去了** —— 设计上不让这种混用发生。
   ⚠️ **还没验的**：它**从没在真 Spigot 服务端上跑过**（BuildTools 现场编译，而本机到 Mojang ~5KB/s）。
   编译通过 + 拒绝路径通过，但"在 Spigot 上真能转发聊天"仍待验证（放 CI 或由用户跑一次）。
   **验证**：需要一个**真 Spigot 服务端**（BuildTools 现场编译，约 10 分钟）—— 这是新增的验证成本。
4. ✅ **抽公共配置**（**已完成**）：
   * **描述符的公共字段**（显示名 / 作者 / 许可证 / 仓库链接 / 描述）进 **`descriptors.properties`**
     （**UTF-8**；一开始放进 `gradle.properties`，但那是 ISO-8859-1 读的，非 ASCII 双重编码后
     YAML 1.1 会拒绝 `plugin.yml` —— 详见 `PROGRESS.md` 那条 bug 记录），
     五个描述符改用 `${...}`，各自的 `processResources` 一次 `expand(resourceFacts)` 填进去。
     平台的差异（`plugin.yml` 那句"这是哪份 jar"）仍留在各自描述符里。
   * **打包与 relocate**（`bundled` 配置、SDK/SnakeYAML 依赖、shadowJar 的改名列表与排除项、
     LICENSE/THIRD-PARTY 注入）收到**根构建**的 `plugins.withId("com.gradleup.shadow")` 块里 ——
     五个平台模块从"各写一遍"变成"各写自己那一行 `add("bundled", project(":core"))`"。
     （没有新模块，也没用 `buildSrc`：根构建的 `subprojects` 块本来就在配置子项目之前跑。）
   **验证**：`clean build` 全绿（31 个任务）；四个能构建的 jar 里描述符**全部展开**（`grep -c '\${'` = 0）、
   字段值正确（`id: mcqq`、`name: MC ↔ QQ Bot`、`authors: [SkiesWorld]`、`license: MIT`、中文描述）、
   `LICENSE` + `THIRD-PARTY.md` 仍在每个 jar 里。
   ⚠️ forge 的描述符展开没单独验（它不在本机构建里，原因见第一节）；用的是与 neoforge 完全相同的机制。
   **踩到的坑**：Kotlin DSL 里 `tasks.processResources { property("mod_id") }` 会去 **task** 上找属性 ✗
   （Groovy 会回落到 project，所以 Groovy 那份没这个问题）→ 把 `resourceFacts` 提到顶层再喂给 `expand`。
5. **CI**：矩阵加 spigot 一格（`platforms.yml` 加一行），其余 workflow 不动。

## 六、代价与风险（如实）

* **产物变多**：+1（方案 A）或 +2（方案 B）个 jar，用户要选对；选错会**明确报错**（已有那套探测）。
* **Spigot 侧的验证成本**：本机没有现成 Spigot 服务端，要 BuildTools 编译（几分钟到十几分钟，
  且本机到 Mojang 的网速很差 —— 实测 ~5KB/s）。
* **待核实项的状态**：
  1. ✅ **已核实：spigot-api 不带 Adventure**（传递依赖里只有 `bungeecord-chat`）→ 共享代码用 `§` 字符串，
     Paper 变体覆盖成 Component（见第 2 步）。
  2. ⬜ **Paper 是否仍触发 `AsyncPlayerChatEvent`** —— 仍未实测；只影响"把 spigot 变体装到 Paper 上"这种混用。
  3. ✅ **已核实：`Server.getMinecraftVersion()` 是 Paper 独有的**（spigot-api 里没有）——
     抽共享模块时编译器直接抓到的；基类改用 `Bukkit.getBukkitVersion()` 再切掉后缀，Paper 覆盖成更干净的那个。
* **搬家那一步会动测试服目录**（`run/` 跟着模块走）—— 反正它们都在 `.gitignore` 里。

## 七、待你拍板

1. **bukkit 一族拆 2 个（spigot + paper，推荐）还是 3 个（+folia）**？—— 3 个的话 `folia/` 与 `paper/`
   只差描述符一行，属于"为了零运行期探测而复制"。
2. **`bukkit-common/` 抽不抽**？（抽了每个变体只剩聊天监听 + 描述符）
3. **目录命名**：`fabric/fabric-26.1/`（重复平台名，但路径片段自解释）还是 `fabric/26.1/`（更短）？
4. **要不要上 `libs.versions.toml`**（version catalog）？—— 比 `gradle.properties` 更规范，但要动所有 build 文件。
