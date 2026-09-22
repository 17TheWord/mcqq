package com.example.mcqq.core;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * 用服务端自己的 RCON 跑一条命令。
 *
 * <p>位置与失败话术都在这一处，所以 {@code /qq run}（运维自测）与 QQ 侧那条路（{@link McCommands}）
 * 走的是同一份实现 —— 自测得出的结论对那条路成立。
 *
 * <p>为什么是 RCON 而不是在进程里派发，见 {@link Rcon}。
 */
final class ServerConsole {

    private ServerConsole() {
    }

    /**
     * 服务端自己的 {@code server.properties}。
     *
     * <p>{@code configDir()} 在 Bukkit 上是 {@code plugins/}、在 mod 平台上是 {@code config/}，
     * 两者的**父目录都是服务端的工作目录** —— 所以不用为这件事再加一个平台方法。
     */
    static Path propertiesOf(MinecraftPlatform platform) {
        Path configDir = platform.configDir();
        Path serverDir = configDir.getParent();
        if (serverDir == null) {
            // 有些平台给的是相对路径（父目录为 null）。服务端的工作目录就是服务端目录，用它兜底。
            serverDir = Paths.get("").toAbsolutePath();
        }
        return serverDir.resolve("server.properties");
    }

    /** 跑一条命令，返回回显。**任何失败都变成一句话**，不往上抛 —— 调用方是别人的消息循环。 */
    static List<String> run(MinecraftPlatform platform, String command) {
        Optional<Rcon.Endpoint> endpoint = Rcon.endpointFrom(propertiesOf(platform));
        if (endpoint.isEmpty()) {
            return List.of("服务端没开 RCON，命令执行用不了 —— 在 server.properties 里设 "
                    + "enable-rcon=true 与 rcon.password=<密码>，重启服务器后生效");
        }
        try {
            List<String> lines = Rcon.run(endpoint.get(), command);
            return lines.isEmpty() ? List.of("（这条命令没有回显）") : lines;
        } catch (IOException e) {
            // 最常见的两种：服务端没在跑 RCON，或者这条命令**本身就是从 RCON 发过来的** ——
            // 服务端的 RCON 一次只服务一个连接，自己等自己必然超时。
            return List.of("命令执行失败：" + e.getMessage()
                    + "（如果这条命令是经 RCON 发来的，那就是 RCON 正忙着自己这条连接；"
                    + "换到游戏内或控制台执行）");
        }
    }
}
