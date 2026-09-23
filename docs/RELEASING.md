# 发布

## 一句话

**改 `gradle.properties` 的 `mod_version`，合进 `main`，就发一次。**
版本号没变就不会发 —— 所以"发版"是一个明确的动作，不是每次合并的副作用。

```
推 dev        → 什么都不跑
PR (dev→main) → test.yml 跑矩阵（每个平台一格，并行）
合并进 main   → release.yml：读版本 → 闸门 → 构建 → 建 release → 上架
```

## release.yml 的形状

| job | 干什么 |
| --- | --- |
| `platforms` | 复用 `platforms.yml`：从 `gradle.properties` 读出版本事实，并生成平台矩阵 |
| `gate` | **版本号变了才发**：`v<版本>` 的标签已存在 → 整个工作流跳过 |
| `build` | 一次 `./gradlew clean build -PwithForge=true` 出全部产物 → 存 artifact → `gh release create v<版本> --generate-notes` 并附上 6 个 jar |
| `publish` | 按矩阵每格跑一次 `mc-publish`（Modrinth / CurseForge）。没配项目 id 就整段跳过 |

## ⚠️ 两个容易踩的点

**① 触发条件必须是 `push: main`，不能是 tag。**
用内置 `GITHUB_TOKEN` 推的 tag **不会触发其他工作流**（GitHub 防递归的规则）。
所以"一个工作流打标签、另一个挂 `on: push: tags`"那种写法，后者**永远不会跑**，
而且看起来完全正常。标签由 `gh release create` 在当前 HEAD 上顺手打。

**② 闸门查的是标签，不是 release。**
标签才是"这个版本发过没有"的唯一依据，也顺带挡住"只打了标签、没建 release"那种半成品状态。

## 在分支上发测试版

`release.yml` 的**手动入口可以选分支** —— Actions → Release → Run workflow 里那个
"Use workflow from" 下拉框选任何分支，跑的是**那个分支上的工作流文件**。

所以 feature 分支上想发个测试版：

1. 在**那个分支**上把 `gradle.properties` 的 `mod_version` 改成带后缀的版本
   （`0.0.1-beta.1` / `0.0.1-rc.1` —— **必须带后缀**）
2. Actions → Release → Run workflow → 选那个分支

**带后缀 = 预发布**：`gh release create --prerelease`，Modrinth 上的 version-type 也是 beta。

⚠️ **非 main 分支上不允许发不带后缀的版本**（gate 会直接 `::error::` 并停）。
原因：那样 `v0.0.1` 这个**正式标签会打在 feature 分支上**，之后真正的 `0.0.1`
就永远发不出去了（闸门会一直跳过）。

⚠️ **非 main 分支不上架商店** —— `publish` job 加了 `github.ref_name == 'main'`，
分支上的测试版只留 GitHub Release（从那次运行的 artifacts 里也能下 jar）。
想让分支版本也上架，把那个条件去掉即可。

⚠️ **别忘了改回来**：分支合进 main 之前，把 `mod_version` 改回正式版本号
（或者下一次发版时改），否则 main 上会带着一个 `-beta.N` 的版本。

## 在分支上发测试版

`release.yml` 的**手动入口可以选分支** —— Actions → Release → Run workflow 里那个
"Use workflow from" 下拉框选任何分支，跑的是**那个分支上的工作流文件**。

所以 feature 分支上想发个测试版：

1. 在**那个分支**上把 `gradle.properties` 的 `mod_version` 改成带后缀的版本
   （`0.0.1-beta.1` / `0.0.1-rc.1` —— **必须带后缀**）
2. Actions → Release → Run workflow → 选那个分支

**带后缀 = 预发布**：`gh release create --prerelease`，Modrinth 上的 version-type 也是 beta。

⚠️ **非 main 分支上不允许发不带后缀的版本**（gate 会直接 `::error::` 并停）。
原因：那样 `v0.0.1` 这个**正式标签会打在 feature 分支上**，之后真正的 `0.0.1`
就永远发不出去了（闸门会一直跳过）。

⚠️ **非 main 分支不上架商店** —— `publish` job 加了 `github.ref_name == 'main'`，
分支上的测试版只留 GitHub Release（从那次运行的 artifacts 里也能下 jar）。
想让分支版本也上架，把那个条件去掉即可。

⚠️ **别忘了改回来**：分支合进 main 之前，把 `mod_version` 改回正式版本号
（或者下一次发版时改），否则 main 上会带着一个 `-beta.N` 的版本。

## 要重发同一个版本

闸门会挡住（标签还在）。做法是**先删掉那个标签**，再手动跑一次 `release.yml`（`workflow_dispatch`）：

```bash
git push --delete origin v0.0.1     # 删标签（release 也要在 GitHub 上删掉）
```

**不做"先删再建"的自动重发** —— 那会变成"每次合并都重发一次"，
而且往 Maven Central 发时同一个版本根本发不上去。

## 上架到 Modrinth / CurseForge

先在两个平台上把项目建好，再在仓库 Settings → Secrets and variables → Actions 里配：

* Variables：`MODRINTH_ID` / `CURSEFORGE_ID`
* Secrets：`MODRINTH_TOKEN` / `CURSEFORGE_TOKEN`

**没配也不会失败** —— `publish` 整个 job 跳过，构建、Actions artifacts 与 GitHub Release 照常。
想先手动传，就从那次 Actions 运行的 artifacts 里下载对应的 jar。

`game-versions` 是**每个窗口一份**（`publish_game_versions_26_1` / `_1_20_1`）。
⚠️ spigot / paper 两份是例外：它们**一个 jar 覆盖 1.20.1 → 26.x**，
所以 `game-versions` 是**并集**（`[1.20.1, 1.20.2, 26.1, 26.2]`）——
只写 26.x 的话，1.20.1 的用户在 Modrinth 上搜不到。
⚠️ 两份列表合成**一个**数组：直接拼会得到 `[..],[..]`，mc-publish 不认。

## CI 跑在哪个镜像

`ubuntu-26.04`（**钉死**，不用 `ubuntu-latest`）：`-latest` 会在 GitHub 迁移时悄悄换掉底层系统，
而构建工具链对系统版本敏感。GitHub 弃用某个镜像时要手动把三处一起改。

## 加一个平台 / 窗口要动哪几处

1. `settings.gradle.kts` 的 `include`
2. `.github/actions/platform-matrix/action.yml` 的矩阵（**一行**：名字、子项目、jar 路径、loaders、game-versions、额外参数）
3. `release.yml` 的两处产物路径：`upload-artifact` 的 `path:` 与 `gh release create` 的 `JARS`

第 3 处最容易漏，而且没人测它 —— 漏了会"构建成功、release 里少一个 jar"。
