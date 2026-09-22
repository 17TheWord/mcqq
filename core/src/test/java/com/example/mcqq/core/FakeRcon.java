package com.example.mcqq.core;

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

    /**
     * 一个照协议应答的小服务端 —— 只够把客户端那半边测清楚。
     *
     * <p>协议：{@code [长度][序号][类型][正文][0x00][0x00]}，小端。登录成功回原序号，失败回 -1；
     * 命令用类型 2 发、类型 0 回。
     */
    final class FakeRcon implements AutoCloseable {

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