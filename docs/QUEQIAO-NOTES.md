# 从 QueQiaoTool 抄什么（清单）

> 目的：**让每个平台要写的东西越少越好**。这份清单是按这个标准把 `17TheWord/QueQiaoTool` 读了一遍的结果，
> 每条都标了"它做了什么 / 我们抄成什么 / 成本 / 值不值得"。读的是克隆在 `.workbuddy-ai/refs/QueQiaoTool` 的源码。

## 一、先看基线：mc-qq 的平台现在要实现的全部东西

| 项 | 内容 |
| --- | --- |
| `MinecraftPlatform` | `label` / `configDir` / `broadcast` / `onMainThread` / `registerCommands`（5） |
| `CommandSource` | `hasPermission` / `reply`（2，每次调用现场包一个） |
| `Log.Sink` | `info` / `warn` / `error`（3） |
| 入口与事件 | 主类 + 4 个事件监听 + 生命周期挂钩（start/stop） |
| 描述符 | `fabric.mod.json` / `plugin.yml`（+ 以后的 `neoforge.mods.toml`） |
| 打包 | shadow + relocate 的 build 脚本 |

≈ **10 个方法 + 一次注册 + 一份描述符**。

## 二、对照：鹊桥的平台面

| 项 | 内容 |
| --- | --- |
| `GlobalContext.init` | `(isModServer, serverVersion, serverType, apiImpl, cmdImpl)` —— 一次调用 |
| `HandleApiService` | `handleBroadcastMessage` / `handleSendTitleMessage` / `handleSendActionBarMessage` / `handleSendPrivateMessage`（4） |
| `HandleCommandReturnMessageService` | `sendReturnMessage` / `hasPermission`（2） |
| 平台侧其余 | 入口点 + 事件监听（mixin / 事件）+ 描述符 + 需要平台能力的子命令实现 |

≈ **6 个方法 + 一次 init**。

**结论先说**：两边的平台面都在 10 个方法上下，**再往下压收益递减** —— 我们已经把它压到"包一个 sender 上下文
+ 转发"的程度了。鹊桥真正值得抄的不是"更薄"，而是**把"用户会变的东西"从代码里挪出来**（见第三节 A 档与 B 档）。
另外它把 `serverVersion` / `serverType` 塞进 `init` 参数、我们让平台实现 `label()`，是同一件事的两种写法，无所谓优劣。

## 三、清单

### A 档：建议现在抄，成本低

**A1. 配置归并 + 自动备份**（`config/CommonConfig.java`：`synchronizeConfigFile` / `synchronizeAndLoadConfig` /
`normalizeMap` / `backupConfig`）

* 它做的**不只是**"文件不存在就写模板"：把**代码里的默认值与用户已有的文件做归并** —— 新增的键自动补进用户文件、
  用户删掉的键保留、写回前先存一份 `.bak`。
* 我们抄成：**样例文件 + 补键 + 备份**（用户提的形态，比"不写回"更好）：
  * `config.example.yml`：打包模板的副本，**带全部注释**，每次启动刷新 —— 注释的家在这里，不在生效文件里。
  * `config.yml`：缺键时被补全，补前先写 `config.yml.bak`（逐字节副本），并在文件头指向上面两个。
  * 只补 `templates`（默认值），**不动 `bots`**（那是示例，归并它会往别人能跑的配置里塞 REPLACE_ME 的 bot）。
  * 补进去的值本来就已生效，所以这一步**不改变行为**，只是让用户看得见、改得动。
* 状态：**已实现**（2026-09-21，见 PROGRESS），Paper 26.2 真机验过（补键/备份/样例/往返读回都对）。
* 成本：~80 行 + 测试（纯 Java，core 里做，离线可测）。

**A2. 事件回调的异常兜底**（它有两处：`SubCommand.execute` 与 `ProtocolRouter.route` 都 try/catch 成响应）

* 我们只抄了命令那一处。adapter 的事件监听现在直接 `bridge.forward(...)`，**core 里没有兜底** ——
  一旦 `forward` 抛异常，异常会打到 MC 的事件总线（轻则刷日志，重则影响那个 tick）。
* 我们抄成：`Bridge.forward` / `BridgeRuntime.forward` 里 try/catch，只记日志。
* 成本：几行。**值**：MC 侧的异常代价比"少转发一条消息"大得多。

**A3. 统一的调试开关**（`utils/Tool.debugLog` + 配置项 `debug`）—— **已实现**（2026-09-21）：`debug: false`，

* 它做：一个开关控制详细日志（收到什么、路由到哪、为什么跳过）。
* 我们抄成：`Log.debug(...)` + 配置 `debug: false`；"为什么这条消息没转发"是最高频的排障问题。
  **每个丢弃分支都有一行**（群没订阅 / 模板为空 / bot 未注册 / 群没绑定 / 平台重推），
  转发成功也打印实际内容。`Log.Sink` 加一个 `debug` 方法（slf4j 走 debug，JUL 走 FINE）。

### B 档：有价值，但要你先拍板（会动接口）

**B1. 事件传"结构化数据"而不是拼好的文本**（`event/model/TranslateModel{key, args[], text}`，
它在 0.6.0 做了这个**重大变更**，理由是支持多语言）

* 它做：死亡/成就事件里**不放成品句子**，放"翻译键 + 参数 + 回退文本"，由接收端本地化。
* 我们的取舍正好相反：`forward(McEvent, String)`，文本在 adapter 里拼好（为了接缝窄、core 不认识 MC 类型）。
* **这是一个真实的分歧点**：
  * 如果 QQ 侧永远只需要"一行中文"，现在这样最省；
  * 如果 QQ 侧想自己排版（@ 人、加粗玩家名、按群不同措辞、以后加英文），就必须传结构化数据
    （事件类型 + 具名参数，例如 `who=Alice` / `killer=Zombie` / `group=主群`）。
* 成本：中等（`McEvent` + 一个 `EventData`/`Map<String,Object>`；两个 adapter 的事件回调各改几行）。
* **建议**：现在改便宜，以后改贵。哪怕暂时只在 core 里多带一份参数、adapter 仍旧拼文本，也值得先把数据带出来。

**B2. 本地化服务**（`localize/LanguageService`：从 `config/<mod>/translate/*.json` 读、支持 reload、
缺失键只警告一次、递归参数）

* 它做：平台零成本拿到 i18n；服主可以改文案、加英文。
* 我们抄成：core 的文案（"已重载配置"、"没有子命令 …"）改成从 `config/mc-qq/lang/*.json` 读，
  缺文件时用内置默认（内置默认就是现在的硬编码中文，行为不变）。
* 依赖 B1（文案结构化才好翻译），所以排在 B1 之后。
* 成本：中等（~100 行 + 一个默认语言文件）。

**B3. 服务端状态采集**（`protocol/handler/status/ServerStatusCollector` / `SystemMetricsCollector` / `MinecraftPingClient`）

* 它做：从进程内 ping 自己、读系统指标，`get_status` 返回 TPS / 玩家数 / 内存。
* 我们抄成：`/qq status` 多一行"TPS 19.8 · 玩家 3 · 内存 1.2G"。
* ⚠️ **注意方向相反**：这会让平台面**变大**（每个平台要实现一个 `serverStats()`，各平台读 TPS 的 API 不同）。
  它是"加功能"，不是"减负"。**先别做**，除非有人要。

### C 档：现在不抄，写清为什么

| 设计 | 它解决什么 | 为什么现在不抄 | 什么时候再抄 |
| --- | --- | --- | --- |
| `ProtocolRouter` + `AbstractProtocolHandler` + `Response`/`ResponseEnum` + `ProtocolException` | 入站 API 有 8 个，需要"api 名 → handler"的路由表 + 统一响应封套 | mc-qq 现在**没有入站 API**（QQ 侧是我们主动调 SDK），没有东西可路由 | 走路线 D（QQ 侧独立进程说鹊桥协议），或以后允许外部程序调它广播 |
| `HandleProtocolMessage`（HTTP 与 WS 共用一个入口） | 传输无关的消息入口 | 同上 | 同上 |
| `WebsocketManager` / `WsClient` / `WsServer`（含重连、客户端列表、reconnect 命令） | 自己管长连接 | `qqbot-java-sdk` 已经管了网关与重连 | 不抄 |
| `RconClient`（内置 RCON） | 用 RCON 执行命令/取状态 | 我们不需要执行 MC 命令 | 不抄 |
| `event/base/BaseEvent` + `PlayerModel` 等 JSON 模型 | 事件跨进程传输 | 进程内不需要序列化（**但 `TranslateModel` 见 B1**） | 走路线 D 时 |
| `GlobalContext` 静态门面（一堆 `getXxx()`） | 让平台少传参数 | **静态全局状态，测试不友好**；我们用 `Bridge` 实例注入，能在单测里直接构造 | 不抄 |
| `Object sender` | 让 core 不认识平台的 sender 类型 | 用两方法接口代替，少一次 cast、多一层编译期保护 | 已决定不抄 |
| `SubscribeEventConfig` | 按事件订阅开关 | 我们已有（`send-to-qq: [chat, join, quit, death]`） | 已等价 |
| `ignored_commands` / `isRegisterOrLoginCommand` | 过滤登录/注册等噪音命令 | 我们不转发玩家命令 | 以后加 command 事件时抄 |

## 四、如果只做三件事

1. **A1 配置归并 + 备份** —— 越早越便宜，配置项只会变多。
2. **A2 事件回调的异常兜底** —— 几行，换掉"MC 侧被 core 的异常影响"这个风险。
3. **拍板 B1**：事件传结构化数据还是拼好的文本。这条决定 B2（本地化）与"QQ 侧排版"能不能做，
   而**现在改便宜，以后改贵**。

## 五、一句总结

平台面已经压到 ~10 个方法，**再压是递减收益**。鹊桥那套里真正耐用的部分不是"更薄"，而是
**把用户会变的东西从代码里挪出来**：配置项（A1）、文案（B2）、事件数据（B1）。
这三件事的价值会随着平台数量与版本数量一起增长，而不是随它们被摊薄。
