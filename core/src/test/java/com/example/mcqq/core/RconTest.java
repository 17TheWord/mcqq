package com.example.mcqq.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * RCON 这条路的两个半边：地址怎么读出来，以及协议本身对不对。
 *
 * <p>协议那半边**对着一个真的 socket 测** —— 测试里自己按协议应答，等于把客户端与"服务端"各写一遍，
 * 两边对上了才算数。这样不用起 Minecraft 也能把这条路的成败模式钉住。
 */
class RconTest {

    @TempDir
    Path dir;

    private Path properties(String content) throws IOException {
        Path file = dir.resolve("server.properties");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void readsTheEndpointFromServerProperties() throws Exception {
        Optional<Rcon.Endpoint> endpoint = Rcon.endpointFrom(properties("""
                online-mode=false
                enable-rcon=true
                rcon.port=25675
                rcon.password=hunter2
                """));

        assertTrue(endpoint.isPresent());
        assertEquals("127.0.0.1", endpoint.get().host(), "一律连回环，没理由走外面");
        assertEquals(25675, endpoint.get().port());
        assertEquals("hunter2", endpoint.get().password());
    }

    @Test
    void rconOffOrHalfConfiguredMeansNoEndpoint() throws Exception {
        assertTrue(Rcon.endpointFrom(properties("enable-rcon=false\nrcon.password=x\n")).isEmpty(),
                "没开就没有");
        assertTrue(Rcon.endpointFrom(properties("rcon.password=x\n")).isEmpty(),
                "整行都没有也没有");
        assertTrue(Rcon.endpointFrom(properties("enable-rcon=true\nrcon.password=\n")).isEmpty(),
                "开了但密码是空的 —— 那连不上，当没有处理");
        assertTrue(Rcon.endpointFrom(dir.resolve("不存在.properties")).isEmpty(),
                "文件不在也没有");
    }

    @Test
    void runsACommandAndComesBackWithTheOutput() throws Exception {
        try (FakeRcon fake = new FakeRcon("hunter2", "There are 0 of a max of 20 players online")) {
            List<String> lines = Rcon.run(new Rcon.Endpoint("127.0.0.1", fake.port(), "hunter2"), "list");

            assertEquals(List.of("There are 0 of a max of 20 players online"), lines);
            assertEquals("list", fake.lastCommand(), "发过去的正文要是那条命令");
        }
    }

    @Test
    void aMultiLineAnswerIsSplitIntoLines() throws Exception {
        try (FakeRcon fake = new FakeRcon("hunter2", "第一行\n第二行\n\n第三行\n")) {
            assertEquals(List.of("第一行", "第二行", "第三行"),
                    Rcon.run(new Rcon.Endpoint("127.0.0.1", fake.port(), "hunter2"), "help"));
        }
    }

    @Test
    void aWrongPasswordSaysSoInsteadOfHanging() throws Exception {
        try (FakeRcon fake = new FakeRcon("hunter2", "irrelevant")) {
            IOException thrown = assertThrows(IOException.class,
                    () -> Rcon.run(new Rcon.Endpoint("127.0.0.1", fake.port(), "猜的"), "list"));
            assertTrue(thrown.getMessage().contains("密码"), thrown.getMessage());
        }
    }

    @Test
    void aServerThatIsNotThereFailsInsteadOfHanging() {
        // 关掉的端口：连接被拒，报错而不是等下去。
        assertThrows(IOException.class,
                () -> Rcon.run(new Rcon.Endpoint("127.0.0.1", 1, "x"), "list"));
    }

    /**
     * 一个照协议应答的小服务端 —— 只够把客户端那半边测清楚。
     *
     * <p>协议：{@code [长度][序号][类型][正文][0x00][0x00]}，小端。登录成功回原序号，失败回 -1；
     * 命令用类型 2 发、类型 0 回。
     */
    private static final class FakeRcon implements AutoCloseable {

        private final ServerSocket server;
        private final String password;
        private final String output;
        private volatile String lastCommand = "";

        FakeRcon(String password, String output) throws IOException {
            this.password = password;
            this.output = output;
            this.server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            Thread thread = new Thread(this::serve, "fake-rcon");
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        String lastCommand() {
            return lastCommand;
        }

        private void serve() {
            try (Socket socket = server.accept()) {
                InputStream in = socket.getInputStream();
                OutputStream out = socket.getOutputStream();

                Packet login = readPacket(in);
                if (!password.equals(login.body())) {
                    writePacket(out, -1, 2, "");
                    return;
                }
                writePacket(out, login.id(), 2, "");

                Packet command = readPacket(in);
                lastCommand = command.body();
                writePacket(out, command.id(), 0, output);
            } catch (IOException ignored) {
                // 测试里不在乎：客户端提前断开是正常路径之一
            }
        }

        private record Packet(int id, int type, String body) {
        }

        private static Packet readPacket(InputStream in) throws IOException {
            byte[] lengthBytes = in.readNBytes(4);
            if (lengthBytes.length < 4) {
                throw new EOFException("客户端没发完就断了");
            }
            int length = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
            byte[] rest = in.readNBytes(length);
            if (rest.length < length) {
                throw new EOFException("客户端没发完就断了");
            }
            ByteBuffer buffer = ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN);
            int id = buffer.getInt();
            int type = buffer.getInt();
            byte[] text = new byte[length - 10];
            buffer.get(text);
            return new Packet(id, type, new String(text, StandardCharsets.UTF_8));
        }

        private static void writePacket(OutputStream out, int id, int type, String text)
                throws IOException {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            // 长度字段算的是它自己后面的字节：序号 + 类型 + 正文 + 两个 0。
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

        @Override
        public void close() throws IOException {
            server.close();
        }
    }
}
