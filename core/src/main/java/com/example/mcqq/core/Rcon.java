package com.example.mcqq.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * 用服务端自己的 RCON 跑命令，把回显拿回来。
 *
 * <p><b>为什么是 RCON 而不是在进程里直接派发</b>：CraftBukkit 明确拒绝非 Craft 的
 * {@code CommandSender} 去跑原版命令（{@code IllegalArgumentException: Cannot make ... a vanilla
 * command listener}），而"真正的发送者"的输出直接进日志，外面套一层收不到。RCON 是唯一一条
 * "发一条命令、拿回一段文本"的路，而且它对所有平台**都是同一套协议** —— 所以这里一处实现，
 * 五个适配器一行都不用改。
 *
 * <p>地址从服务端自己的 {@code server.properties} 读（{@code enable-rcon} / {@code rcon.port} /
 * {@code rcon.password}），所以运维不需要把密码再抄一份到我们的配置里；配置里那三个键是给
 * "RCON 不在默认位置"的情况用的。
 *
 * <p>协议本身很小：{@code [长度][序号][类型][正文][0x00][0x00]}，小端。类型 3 = 登录、2 = 命令。
 * 登录失败时服务端回一个序号为 -1 的包。
 */
final class Rcon {

    /** 登录用的类型。 */
    private static final int TYPE_LOGIN = 3;

    /** 命令与响应都用这个类型。 */
    private static final int TYPE_COMMAND = 2;

    /** 一条命令最多等多久。RCON 是回环连接，正常在毫秒级。 */
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** 服务端的 RCON 地址。 */
    record Endpoint(String host, int port, String password) {

        @Override
        public String toString() {
            return host + ":" + port;
        }
    }

    private Rcon() {
    }

    /**
     * 从 {@code server.properties} 里读 RCON 地址；没开 RCON 就是 empty。
     *
     * <p>单独拆出来是为了能离线测：给一份 properties，看读出什么。
     */
    static Optional<Endpoint> endpointFrom(Path serverProperties) {
        if (!Files.isReadable(serverProperties)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(serverProperties)) {
            properties.load(in);
        } catch (IOException e) {
            Log.debug("读不了 " + serverProperties + "：" + e.getMessage());
            return Optional.empty();
        }
        if (!"true".equalsIgnoreCase(properties.getProperty("enable-rcon", "").trim())) {
            return Optional.empty();
        }
        String password = properties.getProperty("rcon.password", "");
        if (password.isBlank()) {
            Log.warn("server.properties 里 enable-rcon=true 但 rcon.password 是空的，命令执行用不了");
            return Optional.empty();
        }
        int port;
        try {
            port = Integer.parseInt(properties.getProperty("rcon.port", "25575").trim());
        } catch (NumberFormatException e) {
            port = 25575;
        }
        // 一律连回环：我们和服务端在同一个进程里，没有理由走外面。
        return Optional.of(new Endpoint("127.0.0.1", port, password));
    }

    /**
     * 跑一条命令，返回回显（按行）。失败抛 {@link IOException}，调用方负责把它变成一句人话。
     */
    static List<String> run(Endpoint endpoint, String command) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.host(), endpoint.port()),
                    (int) TIMEOUT.toMillis());
            socket.setSoTimeout((int) TIMEOUT.toMillis());
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            write(out, 1, TYPE_LOGIN, endpoint.password());
            Response auth = read(in);
            if (auth.id() == -1) {
                throw new IOException("RCON 密码不对（server.properties 的 rcon.password）");
            }

            write(out, 2, TYPE_COMMAND, command);
            Response response = read(in);
            return split(response.body());
        }
    }

    /** 回显按行拆开，顺手去掉空行 —— RCON 的正文常常以换行结尾。 */
    private static List<String> split(String body) {
        List<String> lines = new ArrayList<>();
        for (String line : body.split("\r?\n")) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static void write(OutputStream out, int id, int type, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        // 长度字段算的是**它自己后面**的字节：序号 + 类型 + 正文 + 两个 0。
        int packetLength = 4 + 4 + payload.length + 2;
        ByteBuffer buffer = ByteBuffer.allocate(4 + packetLength).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(packetLength);
        buffer.putInt(id);
        buffer.putInt(type);
        buffer.put(payload);
        buffer.put((byte) 0);
        buffer.put((byte) 0);
        out.write(buffer.array());
        out.flush();
    }

    private record Response(int id, int type, String body) {
    }

    private static Response read(InputStream in) throws IOException {
        int length = readInt(in);
        if (length < 10 || length > 4096 + 10) {
            throw new IOException("RCON 回了不合理的包长 " + length);
        }
        byte[] rest = in.readNBytes(length);
        if (rest.length < length) {
            throw new EOFException("RCON 连接提前断了");
        }
        ByteBuffer buffer = ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN);
        int id = buffer.getInt();
        int type = buffer.getInt();
        // 正文后面是两个 0 字节，不算内容。
        byte[] body = new byte[length - 10];
        buffer.get(body);
        return new Response(id, type, new String(body, StandardCharsets.UTF_8));
    }

    private static int readInt(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(4);
        if (bytes.length < 4) {
            throw new EOFException("RCON 连接提前断了");
        }
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
