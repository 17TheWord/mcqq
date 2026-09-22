# 第三方组件

这个 mod 自己的代码是 [MIT](LICENSE)。但**打包出来的 jar 里含有别人的代码** —— 每个平台的产物都把依赖
打进去并改名（relocate），这样服务器只要丢一个文件，不需要额外装库。下面是它们的来源与许可证。

Apache-2.0 要求分发时附上许可证副本并保留 NOTICE，所以 `LICENSE` 与这份文件也会被打进 jar 里
（见根目录 `build.gradle.kts` 里给 shadowJar 加的那两行）。

## 打进 jar 的（会 relocate 到 `com.example.mcqq.shaded.*`）

| 组件 | 版本 | 许可证 | 说明 |
| --- | --- | --- | --- |
| [qqbot-java-sdk](https://github.com/skiesworld/qqbot-java-sdk) | 0.0.4 | Apache-2.0 | QQ 官方机器人 API v2 的 Java SDK，本项目的一半 |
| [OkHttp](https://square.github.io/okhttp/) | 4.12.0 | Apache-2.0 | SDK 的 HTTP 客户端 |
| [Okio](https://square.github.io/okio/) | 3.6.0 | Apache-2.0 | OkHttp 的 I/O 层（随 OkHttp 带入） |
| [Kotlin stdlib](https://kotlinlang.org/) | 1.9.10 | Apache-2.0 | OkHttp 4.x 是 Kotlin 写的（随 OkHttp 带入；stdlib-jdk7/jdk8/common 同版本） |
| [Gson](https://github.com/google/gson) | 2.11.0 | Apache-2.0 | SDK 的 JSON |
| [SnakeYAML](https://bitbucket.org/snakeyaml/snakeyaml) | 2.5 | Apache-2.0 | 读配置文件 |
| [error_prone_annotations](https://github.com/google/error-prone) | 2.27.0 | Apache-2.0 | Gson 的注解依赖 |
| [JetBrains annotations](https://github.com/JetBrains/java-annotations) | 13.0 | Apache-2.0 | 注解 |

## 不打包、由服务端提供的

| 组件 | 版本 | 许可证 | 说明 |
| --- | --- | --- | --- |
| slf4j-api | 2.0.13 | MIT | 只在 core 里 `compileOnly`，运行时用游戏自带的那份；产物里排除了它的类 |
| Brigadier | 1.3.10 | MIT | 只在 core 里 `compileOnly`；游戏自带 |
| Fabric API | 0.155.3+26.1.2 | Apache-2.0 | Fabric 侧的前置，服主自己装 |
| Paper / Folia / NeoForge API | 26.x | 各自的许可证 | 编译期依赖，运行时由服务端提供 |
| Spigot API | 26.1.2-R0.1-SNAPSHOT | GPL-3.0 | 同上；`bukkit-common` 编译对它是为了取 Bukkit 一族的最低公分母 |

## 为什么 relocate

服务端自己就带 gson 与 snakeyaml（版本还和我们依赖的不一样），mod 生态里 kotlin-stdlib 更是经典撞车项。
（完整取舍见项目仓库里的 `README.md` —— 这份文件会被打进 jar，所以这里不写相对链接。）Jar-in-Jar 只负责"把库带上"、不改包名，解决不了撞车；
所以这里统一用 shadow + relocate。
