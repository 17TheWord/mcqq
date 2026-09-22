package com.example.mcqq.bukkit.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

/**
 * 一个只负责收集的 {@link CommandSender}：把命令的回显攒下来，别的事情都不做。
 *
 * <p>两处刻意做成"控制台等价物"：
 *
 * <ul>
 *   <li>{@code hasPermission} 一律 true —— 权限门在 core 里（"谁能执行"已经判过了），
 *       这里再判一次会让"某个群主可以执行 stop"这类配置失效；
 *   <li>{@code isOp} 返回 true，同理。
 * </ul>
 *
 * <p>剩下的接口方法（计分板标签、权限附件、locale）这条流程用不到，给最小实现。
 */
final class CollectingSender implements CommandSender {

    private final Server server;
    private final List<String> lines = new ArrayList<>();

    CollectingSender(Server server) {
        this.server = server;
    }

    /** 收集到的回显，按到达顺序。 */
    List<String> lines() {
        return lines;
    }

    @Override
    public void sendMessage(String message) {
        if (message != null && !message.isBlank()) {
            lines.add(message);
        }
    }

    @Override
    public void sendMessage(String... messages) {
        for (String message : messages) {
            sendMessage(message);
        }
    }

    @Override
    public void sendMessage(java.util.UUID sender, String message) {
        sendMessage(message);
    }

    @Override
    public void sendMessage(java.util.UUID sender, String... messages) {
        for (String message : messages) {
            sendMessage(message);
        }
    }

    @Override
    public Server getServer() {
        return server;
    }

    @Override
    public String getName() {
        return "mcqq";
    }

    @Override
    public boolean isPermissionSet(String name) {
        return true;
    }

    @Override
    public boolean isPermissionSet(Permission perm) {
        return true;
    }

    @Override
    public boolean hasPermission(String name) {
        return true;
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return true;
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeAttachment(PermissionAttachment attachment) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void recalculatePermissions() {
    }

    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return Set.of();
    }

    @Override
    public boolean isOp() {
        return true;
    }

    @Override
    public void setOp(boolean value) {
    }

    @Override
    public Spigot spigot() {
        return new Spigot();
    }

    /** 有些命令会把输出交给 spigot 变体；这里也收下来。 */
    class Spigot extends CommandSender.Spigot {

        @Override
        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent component) {
            CollectingSender.this.sendMessage(component.toPlainText());
        }

        @Override
        public void sendMessage(net.md_5.bungee.api.chat.BaseComponent... components) {
            for (net.md_5.bungee.api.chat.BaseComponent component : components) {
                sendMessage(component);
            }
        }
    }
}
