package com.me.neonthethinker.autotask.network;

import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import com.me.neonthethinker.autotask.AutoTasks;
import com.me.neonthethinker.autotask.utils.FriendlyByteBuf;
import com.me.neonthethinker.autotask.task.TaskManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PluginMessageManager implements PluginMessageListener {
    private final AutoTasks plugin;

    public PluginMessageManager(AutoTasks plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!player.hasPermission("autotasks.manage")) {
            return;
        }

        try {
            FriendlyByteBuf buf = new FriendlyByteBuf(message);
            switch (channel) {
                case "autotask_panel:request_files": {
                    if (plugin.getTaskManager() != null) {
                        plugin.getTaskManager().reloadTasks();
                    }
                    sendFilesPayload(player);
                    break;
                }
                case "autotask_panel:request_content": {
                    String relativePath = buf.readString();
                    try {
                        File file = new File(plugin.getAutotasksFolder(), relativePath).getCanonicalFile();
                        File base = plugin.getAutotasksFolder().getCanonicalFile();
                        if (!file.toPath().startsWith(base.toPath())) {
                            return;
                        }
                        if (file.exists() && file.isFile()) {
                            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                            sendResponseContent(player, relativePath, content);
                        }
                    } catch (IOException ignored) {}
                    break;
                }
                case "autotask_panel:save_file": {
                    String relativePath = buf.readString();
                    String content = buf.readString();
                    try {
                        File file = new File(plugin.getAutotasksFolder(), relativePath).getCanonicalFile();
                        File base = plugin.getAutotasksFolder().getCanonicalFile();
                        if (!file.toPath().startsWith(base.toPath())) {
                            return;
                        }

                        if (content.equals("[CREATE_DIR]")) {
                            file.mkdirs();
                        } else {
                            file.getParentFile().mkdirs();
                            Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));

                            if (plugin.getTaskManager() != null) {
                                plugin.getTaskManager().reloadSingleTask(relativePath, player);
                            }
                            broadcastFileSync(relativePath, content, player);
                        }
                        sendFilesPayload(player);
                    } catch (IOException ignored) {}
                    break;
                }
                case "autotask_panel:request_executed_tasks": {
                    if (plugin.getTaskManager() != null) {
                        String data = plugin.getTaskManager().getExecutedTasksData();
                        sendResponseExecutedTasks(player, data);
                    }
                    break;
                }
                case "autotask_panel:execute_task": {
                    String taskName = buf.readString();
                    long startOffset = buf.readVarLong();
                    if (plugin.getTaskManager() != null) {
                        if (startOffset == -1) {
                            plugin.getTaskManager().stopTask(taskName, player, true);
                        } else {
                            plugin.getTaskManager().executeTask(player, taskName, startOffset, null);
                        }
                    }
                    break;
                }
                case "autotaskp:manage_file": {
                    String action = buf.readString();
                    String sourcePath = buf.readString();
                    String targetPath = buf.readString();
                    try {
                        File base = plugin.getAutotasksFolder().getCanonicalFile();
                        File sourceFile = new File(plugin.getAutotasksFolder(), sourcePath).getCanonicalFile();

                        if (!sourceFile.toPath().startsWith(base.toPath())) {
                            return;
                        }
                        if (!sourceFile.exists()) {
                            return;
                        }

                        boolean changed = false;
                        String act = action.toLowerCase();

                        if (act.equals("delete")) {
                            deleteFileOrDirectory(sourceFile);
                            changed = true;
                        } else if (act.equals("rename") || act.equals("move")) {
                            File targetFile = new File(plugin.getAutotasksFolder(), targetPath).getCanonicalFile();
                            if (!targetFile.toPath().startsWith(base.toPath())) {
                                return;
                            }
                            targetFile.getParentFile().mkdirs();
                            if (sourceFile.renameTo(targetFile)) {
                                changed = true;
                            }
                        } else if (act.equals("duplicate")) {
                            File targetFile = new File(plugin.getAutotasksFolder(), targetPath).getCanonicalFile();
                            if (!targetFile.toPath().startsWith(base.toPath())) {
                                return;
                            }
                            if (targetFile.exists()) {
                                return;
                            }
                            copyFileOrDirectory(sourceFile, targetFile);
                            changed = true;
                        }

                        if (changed) {
                            if (plugin.getTaskManager() != null) {
                                plugin.getTaskManager().reloadTasks();
                            }
                            sendFilesPayload(player);
                        }
                    } catch (IOException ignored) {}
                    break;
                }
                case "autotaskp:edit_state": {
                    String relativePath = buf.readString();
                    boolean isEditing = buf.readBoolean();
                    String playerName = player.getName();
                    if (isEditing) {
                        plugin.getEditingByFile().computeIfAbsent(relativePath, k -> java.util.concurrent.ConcurrentHashMap.newKeySet()).add(playerName);
                    } else {
                        Set<String> editors = plugin.getEditingByFile().get(relativePath);
                        if (editors != null) {
                            editors.remove(playerName);
                            if (editors.isEmpty()) {
                                plugin.getEditingByFile().remove(relativePath);
                            }
                        }
                    }
                    for (Player p : plugin.getServer().getOnlinePlayers()) {
                        if (p.hasPermission("autotasks.manage")) {
                            sendEditingFilesPayload(p);
                        }
                    }
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendFilesPayload(Player player) {
        try {
            FriendlyByteBuf out = new FriendlyByteBuf();
            List<String> entries = new ArrayList<>();
            collectFiles(plugin.getAutotasksFolder(), plugin.getAutotasksFolder(), entries);
            String fileData = String.join("\n", entries);
            out.writeString(fileData);
            player.sendPluginMessage(plugin, "autotask_panel:response_files", out.toBytes());
        } catch (IOException ignored) {}
    }

    private void collectFiles(File root, File folder, List<String> entries) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            try {
                String relativePath = root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                boolean isDir = file.isDirectory();
                if (isDir) {
                    entries.add(relativePath + ":true:ok");
                    collectFiles(root, file, entries);
                } else if (file.getName().toLowerCase().endsWith(".yml")) {
                    TaskManager.FileStatus status = plugin.getTaskManager().getFileStatus(relativePath);
                    entries.add(relativePath + ":false:" + status.name().toLowerCase());
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error collecting file " + file.getPath() + ": " + e.getMessage());
            }
        }
    }

    public void sendResponseContent(Player player, String relativePath, String content) {
        try {
            FriendlyByteBuf out = new FriendlyByteBuf();
            out.writeString(relativePath);
            out.writeString(content);
            player.sendPluginMessage(plugin, "autotask_panel:response_content", out.toBytes());
        } catch (IOException ignored) {}
    }

    public void sendResponseExecutedTasks(Player player, String data) {
        try {
            FriendlyByteBuf out = new FriendlyByteBuf();
            out.writeString(data);
            player.sendPluginMessage(plugin, "autotask_panel:response_executed_tasks", out.toBytes());
        } catch (IOException ignored) {}
    }

    public void sendEditingFilesPayload(Player player) {
        try {
            FriendlyByteBuf out = new FriendlyByteBuf();
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, Set<String>> entry : plugin.getEditingByFile().entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    sb.append(entry.getKey()).append(":").append(String.join(",", entry.getValue())).append("\n");
                }
            }
            out.writeString(sb.toString());
            player.sendPluginMessage(plugin, "autotaskp:editing_files", out.toBytes());
        } catch (IOException ignored) {}
    }

    public void broadcastFileSync(String relativePath, String content, Player sender) {
        Set<String> editors = plugin.getEditingByFile().get(relativePath);
        if (editors == null) return;
        try {
            FriendlyByteBuf out = new FriendlyByteBuf();
            out.writeString(relativePath);
            out.writeString(content);
            byte[] bytes = out.toBytes();

            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!p.equals(sender) && editors.contains(p.getName()) && p.hasPermission("autotasks.manage")) {
                    p.sendPluginMessage(plugin, "autotaskp:file_sync", bytes);
                }
            }
        } catch (IOException ignored) {}
    }

    private void deleteFileOrDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteFileOrDirectory(f);
                }
            }
        }
        file.delete();
    }

    private void copyFileOrDirectory(File source, File target) throws IOException {
        if (source.isDirectory()) {
            target.mkdirs();
            File[] files = source.listFiles();
            if (files != null) {
                for (File f : files) {
                    copyFileOrDirectory(f, new File(target, f.getName()));
                }
            }
        } else {
            target.getParentFile().mkdirs();
            Files.copy(source.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
