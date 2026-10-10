# 发布

## 一句话

**推一个 tag 就发一次。tag 本身就是发布版本。**

```bash
git tag v0.0.2        && git push origin v0.0.2         # 正式版
git tag v0.0.2-beta   && git push origin v0.0.2-beta    # 预发布（--prerelease，Modrinth 的 version-type 也是 beta）
```

版本号以 **tag** 为准：`v` 后面那串就是版本，经 `-Pmod_version` 一路进产物名**与描述符**
（`fabric.mod.json` / `mods.toml` / `plugin.yml`）。根 `gradle.properties` 的 `mod_version` 只是
**本地开发默认值**，发布时被 tag 覆盖。

```
推 tag v0.0.2  → release.yml：解 tag → 闸门 → 构建 → 建 release → 上架
开 PR          → test.yml 跑矩阵（每个平台一格，并行）
合并进 main    → 什么都不跑（发版是"推 tag"这个动作，不是合并的副作用）
```

## ⚠️ 两个容易踩的点

**① tag 必须由人推（或非 `GITHUB_TOKEN` 的凭证推）。**
用内置 `GITHUB_TOKEN` 推的 tag **不会触发其他工作流**（GitHub 防递归的规则），所以
"一个工作流打 tag、另一个挂 `on: push: tags`"那种写法，后者**永远不会跑**，而且看起来完全正常。
这也正是本工作流**不再挂 `push: main`** 的原因。

**② 闸门查的是 Release，不是 tag。**
这次运行本来就是 tag 触发的，tag 必然存在 —— 拿它当闸门等于没闸门。所以查的是
**这个 tag 有没有 Release**：有就整段跳过，挡住"跑了两次、第二次重复上架"。
要重发就先删掉那个 Release 与 tag，再重新推 tag。

## release.yml 的形状

| job | 干什么 |
| --- | --- |
| `platforms` | 复用 `platforms.yml`：跑 `.github/scripts/platform-matrix.sh` **扫目录**推导矩阵（顺带读出 `mod_version`） |
| `gate` | 解出 tag → `版本 = tag 去掉开头的 v` → 已有 Release 就整段跳过 |
| `build` | 在 **tag 那个提交**上 `./gradlew clean build -Pmod_version=<tag 版本>` → 存 artifact → `gh release create <tag> --generate-notes --verify-tag`（**只放变更日志，不带 jar**） |
| `publish` | 按矩阵每格跑一次 `mc-publish`（Modrinth / CurseForge）。没配项目 id 就整段跳过 |

## 手动重跑

`workflow_dispatch` 要填一个**已存在**的 tag —— 用于"上次跑到一半失败、想接着跑"。
（push 事件本来就带 tag，所以那条入口只给重跑用。）

## 上架到 Modrinth / CurseForge

先在两个平台上把项目建好，再在仓库 Settings → Secrets and variables → Actions 里配：

* **Variables**：`MODRINTH_ID` / `CURSEFORGE_ID`
* **Secrets**：`MODRINTH_TOKEN` / `CURSEFORGE_TOKEN`

⚠️ 是 **Variables** 里的 `MODRINTH_ID`（发布 job 的条件是 `vars.MODRINTH_ID != ''`），只有 token 不够。

**没配也不会失败** —— `publish` 整个 job 跳过，构建、Actions artifacts 与 GitHub Release 照常。
想先手动传，就从那次 Actions 运行的 artifacts 里下载对应的 jar。

⚠️ **GitHub Release 不带 jar** —— 下载入口只有 Modrinth / CurseForge（平台有社区、有分类与依赖信息，
下载量也算数；GitHub 再挂一份只会分流）。
⚠️ Actions artifacts **默认 90 天过期**，所以长期归档只有平台上那两份。

`game-versions` 是**每个窗口一份**，写在各自 `平台/窗口/gradle.properties` 的 `publish_game_versions` 里
（矩阵脚本读出来传给 mc-publish）。
⚠️ spigot / paper 两份是例外：它们**一个 jar 覆盖 1.20.1 → 26.x**，所以写的是**并集**
`[1.20.1, 1.20.2, 26.1, 26.2]` —— 只写 26.x 的话，1.20.1 的用户在 Modrinth 上搜不到。

## CI 跑在哪个镜像

`ubuntu-26.04`（**钉死**，不用 `ubuntu-latest`）：`-latest` 会在 GitHub 迁移时悄悄换掉底层系统，
而构建工具链对系统版本敏感。GitHub 弃用某个镜像时要手动把三处一起改。

## 加一个平台 / 窗口要动哪几处

**加一个窗口**：建一个 `平台/窗口/` 目录（含构建脚本 + `gradle.properties`，里面写
`publish_game_versions`），再在 `settings.gradle.kts` 里 `include` 一行。
**矩阵与产物路径都是扫目录推导的，CI 一个字都不用改。**

**加一个平台**：同上，另外 `.github/scripts/platform-matrix.sh` 里有一条 loader 命名规则
（默认就是平台名；Paper 那种要额外声明 Folia 的才需要写）。

> 以前要改四处：`settings.gradle.kts`、根 `gradle.properties`、`action.yml` 里手写的矩阵、
> `release.yml` 里两处硬编码产物路径。最后那处最容易漏、而且没人测它 —— 漏了就是
> "构建成功、release 里少一个 jar"。现在这些都不存在了。
