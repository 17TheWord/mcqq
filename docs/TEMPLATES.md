# 消息模板与占位符

文案是配置，不是代码：每条播报的内容来自 `templates:` 里的模板，占位符的值由 core 从事件里填。
这份文档讲模板怎么组织、配置层级怎么合并、第三方占位符（PAPI 那类）怎么接。

## 要点

1. **占位符和"事件里传结构化数据"是同一件事的两半** —— 没有具名数据就没有可填的占位符。
2. **配置层级是"全局默认 + 群级逐键覆盖"**，不是二选一，也不是群级整段替换。
3. **第三方占位符用"软依赖 + 一个平台钩子"接**，不要 JIJ 进 jar（许可证与平台面两条都不允许）。

## 一、模板怎么组织

**扁平键**，一个键一件事。不要嵌套 —— 嵌套会让"群级覆盖"的合并逻辑变成深合并，得不偿失：

```yaml
templates:
  # QQ -> MC
  qq-chat:          "§b[QQ {group}]§r {user}§7:§r {text}"
  qq-attachment:    "§b[QQ {group}]§r {user} 发了 {count} 个附件（文字里不含它们）"
  qq-member-add:    "§8[QQ {group}] {member} 进了群§r"
  qq-member-remove: "§8[QQ {group}] {member} 退了群§r"
  # MC -> QQ
  mc-chat:  "[MC] {player}: {text}"
  mc-join:  "[MC] {player} 加入了世界"
  mc-quit:  "[MC] {player} 离开了世界"
  mc-death: "[MC] {player} 死亡（{killer}）"
```

* 键名与 `send-to-qq: [chat, join, quit, death]` **对齐**（`mc-chat` ↔ `chat`），服主不用学两套词。
* 占位符全部由 core 自己填，**平台零成本**：
  `{group}` 群标签、`{user}` / `{member}` QQ 侧昵称或尾号、`{text}` 正文、`{count}` 附件数、
  `{player}` 玩家名、`{killer}` 凶手名、`{platform}` 平台标识、`{time}` 时间。
* **`§` 颜色码**：QQ→MC 方向原样交给 adapter 渲染；MC→QQ 方向**剥掉**（QQ 渲染不了 `§`）。
* **未知占位符要报出来**：`{plyer}` 这种拼错进 `BridgeConfig.problems()`（`/qq status` 里能看到），
  **不能静默渲染成空** —— 服主会以为功能坏了。

## 二、配置层级：全局默认 + 群级逐键覆盖

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| 只有全局 | 简单；多数人只有一个群 | 多群场景下没法"主群带 MC 前缀、公告群只要玩家名、外服群要英文" |
| 只有群级 | 完全自由 | 每个群都得写一遍全套；加一个全局键要对每个群改一次 |
| **全局 + 群级逐键覆盖** | 默认好用、需要时才覆盖；新增全局键对所有群自动生效 | 合并逻辑 + "改哪一层生效"要写清楚 |

群级只写要覆盖的键：

```yaml
templates:
  mc-chat: "[MC] {player}: {text}"        # 全局默认

bots:
  - id: main
    groups:
      - group-openid: "..."
        label: MC 主群
        templates:
          mc-chat: "[主群] {player}: {text}"   # 只覆盖这一个键，其余继承全局
      - group-openid: "..."
        label: 公告群
        receive-from-qq: false
        templates:
          mc-join: ""                          # 空串 = 不播报这条
```

三条实现要求：

1. **逐键覆盖，不是整段替换。** 群级写一个 `mc-chat`，不能把全局的 `mc-join` 一起吃掉。
2. **空串是"静音"，不是"回退到全局"。** 否则"这个群不要播报进服"就没法表达 ——
   把 `join` 从 `send-to-qq` 里删掉只管 MC→QQ 方向。
3. **改哪一层生效要能被查**：出问题时最快的问题是"这条消息用的是哪个模板"。
   实现是 `/qq templates`（core 里一个类，**两个 adapter 一行都不用改** —— 这正是命令树放 core 的收益）。

## 三、第三方占位符（PAPI 那类）

### 有哪些

Bukkit 系和 mod 系是**两套不同的库**（同一套 API 的移植）：

| 平台 | 库 | 许可证 |
| --- | --- | --- |
| Fabric / Quilt | **Text Placeholder API**（Patbox 原版） | LGPL-3.0-only |
| NeoForge | **Placeholder API NeoForge**（OffsetMonkey538 的移植） | LGPL-3.0-only |
| Paper / Spigot / Folia | **PlaceholderAPI**（HelpChat） | GPL-3.0-only |
| Paper | **Placeholder API Paper**（Patbox 那套的移植） | 未核实 |

* 语法**不统一**：Patbox 系是 `%modid:type%` / `%modid:type data%`；HelpChat PAPI 是
  `%expansion_placeholder%`（如 `%player_health%`，230+ expansion）。两者都用 `%...%`，
  所以**我们自己的占位符必须换一对括号**。
* 两个库都是"别人装了才存在"的东西，而且都是强 copyleft（LGPL / GPL）。

### 怎么接

**好处**：把"能填什么"交给生态。服主写 `%player_health%`、`%server_tps%` 就能拿到几百个现成数据。

**代价（四条，都是真的）**：

1. **软依赖 → 行为随环境变**：同一份配置，装了库的服和没装的服表现不同。
2. **每个平台一个解析入口**：Fabric/NeoForge 是 Patbox 系的 API，Bukkit 是 HelpChat 的
   `PlaceholderAPI.setPlaceholders(...)` —— **平台面 +1 个方法**，与"平台面越薄越好"直接冲突。
3. **许可证不允许我们 JIJ**：LGPL/GPL 都要求用户能替换该库。只能软依赖（这其实也更合理 ——
   别的 mod 也要用同一个库）。
4. **线程**：MC→QQ 那条在主线程上，第三方解析可能很慢（`%server_tps%` 之类要去问别的插件）；
   QQ→MC 那条本来就在桥接自己的线程上，没事。

**接法：加一个平台钩子，而不是加 N 个取值方法。**

```java
// 平台侧可选实现：默认只填 core 的占位符；adapter 想接第三方就在自己那边接
String resolvePlaceholders(String template, EventContext context);
```

这样 Fabric/NeoForge 接 Patbox 系、Bukkit 接 HelpChat PAPI，**core 不需要认识任何一个库**。
它比"给血量加一个 `playerHealth()`、给坐标加一个 `playerPosition()`"好得多 —— **一个钩子换 N 个方法**。

**语法分工（关键）**：`{name}` 永远是我们的（core 解析，一定能填）；
`%xxx%` 只在平台钩子接上时才解析，**没接上就原样显示** —— 服主一眼能看出"这个占位符没人管"，
比静默变空好得多。这也是为什么我们的占位符不能用 `%...%`。
