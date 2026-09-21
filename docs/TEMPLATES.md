# 消息模板、占位符与配置层级（设计建议）

> **状态：阶段 1 已实现并真机验证**（2026-09-21）。落地细节与验证输出见 [PROGRESS.md](PROGRESS.md)。
> 阶段 2（第三方占位符）**没做**，第 5 节留着当方案。
> 待拍板的 4 条已按本文的建议定下：8 个键全做、群级覆盖一开始就做、阶段 2 暂缓、空串 = 静音。

> 起因：现在的文案是**硬编码在 Java 里**的（`QqToMc` 拼 `§b[QQ 群名]§r 昵称: 文本`，
> adapter 拼 `玩家: 文本`）。服主改不了。这份文档评估"改成模板 + 占位符"该怎么落地、
> 配置该按什么层级组织、以及要不要接第三方占位符（PAPI 那类）。
> 下面每条事实都标了来源；没核实的写"未核实"。

## 一、结论先说

1. **是，就是变量占位。** 而且它和 `QUEQIAO-NOTES.md` 的 B1（事件传结构化数据）是**同一件事的两半** ——
   没有具名数据就没有可填的占位符，所以两件事一起做，不要分两次。
2. **配置层级推荐"全局默认 + 群级逐键覆盖"**（不是二选一，也不是群级整段替换）。理由见第三节。
3. **第三方占位符（PAPI 那类）值得接，但要用"软依赖 + 一个平台钩子"接，不要 JIJ 进 jar**。
   事实与理由见第四、五节。

## 二、模板怎么组织

**扁平键**，一个键一件事。不要嵌套（嵌套会让"群级覆盖"的合并逻辑变成深合并，得不偿失）：

```yaml
templates:
  # QQ -> MC
  qq-chat:        "§b[QQ {group}]§r {user}§7:§r {text}"
  qq-attachment:  "§b[QQ {group}]§r {user} 发了 {count} 个附件（文字里不含它们）"
  qq-member-add:  "§8[QQ {group}] {member} 进了群§r"
  qq-member-remove: "§8[QQ {group}] {member} 退了群§r"
  # MC -> QQ
  mc-chat:  "[MC] {player}: {text}"
  mc-join:  "[MC] {player} 加入了世界"
  mc-quit:  "[MC] {player} 离开了世界"
  mc-death: "[MC] {player} 死亡（{killer}）"
```

* 键名与现有配置的 `send-to-qq: [chat, join, quit, death]` **对齐**（`mc-chat` ↔ `chat`），
  这样服主不用学两套词。
* 占位符集合（阶段 1，全部由 core 自己填，**平台零成本**）：
  `{group}` 群标签、`{user}`/`{member}` QQ 侧昵称或尾号、`{text}` 正文、`{count}` 附件数、
  `{player}` 玩家名、`{killer}` 凶手名、`{server}` 服务器名、`{time}` 时间。
* **`§` 颜色码**：QQ→MC 方向原样交给 adapter 渲染（现状）；MC→QQ 方向**照旧剥掉**
  （QQ 渲染不了 `§`，这是现有行为，模板不改变它）。
* **未知占位符要报出来**：`{plyer}` 这种拼错，进 `BridgeConfig.problems()`（`/qq status` 里能看到），
  **不能静默渲染成空** —— 服主会以为功能坏了。

## 三、配置层级：推荐"全局默认 + 群级逐键覆盖"

| 方案 | 优点 | 缺点 |
| --- | --- | --- |
| 只有全局 | 简单；90% 的人只有一个群 | 多群场景下没法"主群带 MC 前缀、公告群只要玩家名、外服群要英文" |
| 只有群级 | 完全自由 | 每个群都得写一遍全套；加一个全局键要对每个群改一次 |
| **全局 + 群级逐键覆盖**（推荐） | 默认好用、需要时才覆盖；新增全局键对所有群自动生效 | 合并逻辑 + "改哪一层生效"要写清楚 |

形状（群级只写要覆盖的键）：

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
          mc-join: ""                          # 空串 = 不播报这条（比"删键"表达力更强）
```

三条实现要求：

1. **逐键覆盖，不是整段替换。** 群级写一个 `mc-chat`，不能把全局的 `mc-join` 一起吃掉。
2. **空串是"静音"**，不是"回退到全局" —— 否则"这个群不要播报进服"就没法表达（现在的办法是把 `join`
   从 `send-to-qq` 里删掉，但那条路只管 MC→QQ 方向）。
3. **改哪一层生效要能被查**：出问题时最快的问题是"这条消息用的是哪个模板"。
   最小做法是 `/qq status` 不动、靠文档；更好的做法是复用现成的命令树加一个 `/qq templates`
   （core 里一个类，**两个 adapter 一行都不用改** —— 这正是命令树放 core 的收益）。

## 四、第三方占位符：核实过的事实

你印象里 PAPI 是插件，这个印象在 2026 年只对一半 —— **它仍然只有 Bukkit 系**，
但 Fabric/NeoForge 有**另一套**（Patbox 的），而且三个平台是同一套 API 的移植：

| 平台 | 库 | 许可证 | 支持的 26.x | 来源 |
| --- | --- | --- | --- | --- |
| Fabric / Quilt | **Text Placeholder API**（Patbox 原版） | LGPL-3.0-only | 26.1.x / 26.2 / 26.3（向下到 1.17） | Modrinth 项目页，两周前更新 |
| NeoForge | **Placeholder API NeoForge**（OffsetMonkey538 的非官方移植） | LGPL-3.0-only | 26.1.x / 26.2 / 26.3（+1.21.11） | Modrinth 项目页，昨天更新 |
| Paper / Spigot / Folia | **PlaceholderAPI**（HelpChat，就是你说的那个） | GPL-3.0-only | 26.1.x / 26.2 | Modrinth 项目页，3 个月前更新 |
| Paper | **Placeholder API Paper**（Patbox 那套的 Paper 移植） | 未核实 | 未核实 | 只在 NeoForge 移植的说明里被提到 |

* 语法**不统一**：Patbox 系是 `%modid:type%` / `%modid:type data%`；HelpChat PAPI 是 `%expansion_placeholder%`
  （如 `%player_health%`，230+ expansion）。两者都用 `%...%`，所以**我们自己的占位符必须换一对括号**。
* 两个库都是"别人装了才存在"的东西，而且都是强 copyleft（LGPL / GPL）。

## 五、评估：要不要接，怎么接

**好处**：把"能填什么"交给生态。服主写 `%player_health%`、`%server_tps%`、`%vault_eco_balance%`
就能拿到几百个现成数据 —— 这些我们自己做不完，也不该做。

**代价（四条，都是真的）**：

1. **软依赖 → 行为随环境变**：同一份配置，装了库的服和没装的服表现不同。这是最大的坑。
2. **每个平台一个解析入口**：Fabric/NeoForge 是 Patbox 系的 API，Bukkit 是 HelpChat 的
   `PlaceholderAPI.setPlaceholders(...)`，**平台面 +1 个方法**（与"平台面越薄越好"直接冲突）。
3. **许可证不允许我们 JIJ**：LGPL/GPL 都要求用户能替换该库，嵌进我们的 jar 很尴尬。
   只能软依赖（这其实也更合理 —— 别的 mod 也要用同一个库）。
4. **线程**：MC→QQ 那条在主线程上，第三方解析可能很慢（`%server_tps%` 之类要去问别的插件）；
   QQ→MC 那条本来就在桥接自己的线程上，没事。

**建议的两阶段**：

* **阶段 1（现在做）**：只做**我们自己的 `{...}` 占位符**，值全部来自事件本身
  （`{player}`/`{text}`/`{group}`/`{killer}`/`{time}`/`{server}`）。零平台成本、跨平台行为完全一致、
  离线可测。这一档就能满足"服主想改文案"的绝大多数需求。
* **阶段 2（有需求再做）**：加**一个**平台钩子，而不是加 N 个取值方法：

  ```java
  // 平台侧可选实现：默认只填 core 的占位符；adapter 想接第三方就在自己那边接
  String resolvePlaceholders(String template, EventContext context);
  ```

  这样 Fabric/NeoForge 接 Patbox 系、Bukkit 接 HelpChat PAPI（或 Paper 移植），**core 不需要认识任何一个库**。
  注意它比"给血量加一个 `playerHealth()`、给坐标加一个 `playerPosition()`"好得多 ——
  **一个钩子换 N 个方法**，平台面只 +1。

**语法分工（关键）**：`{name}` 永远是我们的（core 解析，一定能填）；
`%xxx%` 只在平台钩子接上时才解析，**没接上就原样显示**。服主一眼能看出"这个占位符没人管"，
比静默变空好得多。这也是为什么我们的占位符不能用 `%...%`。

## 六、落地结果与遗留

**已做**：8 个模板键、`{...}` 占位符、全局 + 群级逐键覆盖、空串静音、拼错占位符报错、
`/qq templates` 命令、配置模板里带注释的示例。测试 12 个（`TemplatesTest`），Paper 26.2 上两条路径都跑过。

**A1 的最终形态**（我最初只做"内存归并 + 提示"，用户提了更好的）：**样例文件 + 补键 + 备份** ——
`config.example.yml` 是带全部注释的模板副本（每次启动刷新，注释的家在这里），
`config.yml` 缺键时被补全（补前写 `config.yml.bak`），只补 `templates` 不动 `bots`。
补进去的值本来就已生效，所以不改变行为。详见 PROGRESS。

**遗留**：
1. 阶段 2（第三方占位符）没做；要接的话就是第 5 节那个 `resolvePlaceholders` 钩子。
2. 模板渲染到**真实事件**上还没验过（需要玩家打一句话 / 真的 QQ 消息）；渲染逻辑由单测覆盖。
3. 群级覆盖目前只在 `templates` 上做了。`send-to-qq` / `receive-from-qq` 本来就是群级，语义一致。
