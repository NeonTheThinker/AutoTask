package me.neonthethinker.autotask;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import me.neonthethinker.autotask.task.TaskManager;
import me.neonthethinker.autotask.utils.PlaceholderUtils;
import me.neonthethinker.autotask.utils.TickScheduler;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.neonthethinker.autotask.network.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

public class Autotask implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("autotask");
    private static Autotask instance;

    private volatile MinecraftServer server;
    private File autotasksFolder;
    private TaskManager taskManager;

    private final Map<String, Set<String>> editingByFile = new ConcurrentHashMap<>();

    @Override
    public void onInitialize() {
        instance = this;

        File configFolder = FabricLoader.getInstance().getConfigDir().resolve("AutoTask").toFile();
        if (!configFolder.exists()) {
            configFolder.mkdirs();
        }

        this.autotasksFolder = new File(configFolder, "autotasks");
        if (!autotasksFolder.exists()) {
            autotasksFolder.mkdirs();
        }

        saveDefaultTask("absoluto.yml");
        saveDefaultTask("incremental.yml");
        saveDefaultTask("ciclo.yml");

        PlaceholderUtils.init();
        TickScheduler.init();
        
        this.taskManager = new TaskManager(this);
        taskManager.reloadTasks();

        if (PlaceholderUtils.isPapiEnabled()) {
            LOGGER.info("AutoTask - PlaceholderAPI Activado");
        } else {
            LOGGER.info("AutoTask - PlaceholderAPI Desactivado.");
        }

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            this.server = server;
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TickScheduler.runTaskLater(() -> {
                taskManager.reloadTasks();
            }, 20L);
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (taskManager != null) {
                taskManager.cancelAllTasks();
            }
            TickScheduler.cancelAll();
            this.server = null;
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AutoTaskCommands.register(dispatcher);
            AutoTaskCommands.registerAllTaskPrefixes(dispatcher);
        });

        PayloadTypeRegistry.playS2C().register(AtpResponseFilesPayload.ID, AtpResponseFilesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AtpResponseContentPayload.ID, AtpResponseContentPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AtpResponseExecutedTasksPayload.ID, AtpResponseExecutedTasksPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AtpResponseEditingFilesPayload.ID, AtpResponseEditingFilesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(AtpFileSyncPayload.ID, AtpFileSyncPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AtpRequestFilesPayload.ID, AtpRequestFilesPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AtpRequestContentPayload.ID, AtpRequestContentPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AtpSaveFilePayload.ID, AtpSaveFilePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AtpRequestExecutedTasksPayload.ID, AtpRequestExecutedTasksPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AtpExecuteTaskPayload.ID, AtpExecuteTaskPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AtpManageFilePayload.ID, AtpManageFilePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AtpEditStatePayload.ID, AtpEditStatePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(AtpRequestFilesPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (taskManager != null) {
                    taskManager.reloadTasks(player.getCommandSource());
                }
                sendFilesPayload(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AtpRequestContentPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                try {
                    File file = new File(autotasksFolder, payload.relativePath()).getCanonicalFile();
                    java.nio.file.Path base = autotasksFolder.getCanonicalFile().toPath();
                    java.nio.file.Path target = file.toPath();

                    if (!target.startsWith(base)) {
                        return;
                    }

                    if (file.exists() && file.isFile()) {
                        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                        ServerPlayNetworking.send(player, new AtpResponseContentPayload(payload.relativePath(), content));
                    }
                } catch (IOException e) {
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AtpSaveFilePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                try {
                    File file = new File(autotasksFolder, payload.relativePath()).getCanonicalFile();
                    java.nio.file.Path base = autotasksFolder.getCanonicalFile().toPath();
                    java.nio.file.Path target = file.toPath();

                    if (!target.startsWith(base)) {
                        return;
                    }

                    if (payload.content().equals("[CREATE_DIR]")) {
                        file.mkdirs();
                    } else {
                        file.getParentFile().mkdirs();
                        Files.write(file.toPath(), payload.content().getBytes(StandardCharsets.UTF_8));

                        if (taskManager != null) {
                            taskManager.reloadSingleTask(payload.relativePath(), player.getCommandSource());
                        }
                        broadcastFileSync(payload.relativePath(), payload.content(), player, context.server());
                    }
                    sendFilesPayload(player);
                } catch (IOException e) {
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AtpRequestExecutedTasksPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (taskManager != null) {
                    String data = taskManager.getExecutedTasksData();
                    ServerPlayNetworking.send(player, new AtpResponseExecutedTasksPayload(data));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AtpExecuteTaskPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                if (taskManager != null) {
                    if (payload.startOffset() == -1) {
                        taskManager.stopTask(payload.taskName(), player.getCommandSource(), true);
                    } else {
                        taskManager.executeTask(player.getCommandSource(), payload.taskName(), payload.startOffset(), null);
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AtpManageFilePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                try {
                    java.nio.file.Path base = autotasksFolder.getCanonicalFile().toPath();
                    File sourceFile = new File(autotasksFolder, payload.sourcePath()).getCanonicalFile();
                    java.nio.file.Path sourcePath = sourceFile.toPath();

                    if (!sourcePath.startsWith(base)) {
                        return;
                    }

                    if (!sourceFile.exists()) {
                        return;
                    }

                    boolean changed = false;
                    String action = payload.action().toLowerCase();

                    if (action.equals("delete")) {
                        deleteFileOrDirectory(sourceFile);
                        changed = true;
                    } else if (action.equals("rename") || action.equals("move")) {
                        File targetFile = new File(autotasksFolder, payload.targetPath()).getCanonicalFile();
                        if (!targetFile.toPath().startsWith(base)) {
                            return;
                        }
                        targetFile.getParentFile().mkdirs();
                        if (sourceFile.renameTo(targetFile)) {
                            changed = true;
                        }
                    } else if (action.equals("duplicate")) {
                        File targetFile = new File(autotasksFolder, payload.targetPath()).getCanonicalFile();
                        if (!targetFile.toPath().startsWith(base)) {
                            return;
                        }
                        if (targetFile.exists()) {
                            return;
                        }
                        copyFileOrDirectory(sourceFile, targetFile);
                        changed = true;
                    }

                    if (changed) {
                        if (taskManager != null) {
                            taskManager.reloadTasks(player.getCommandSource());
                        }
                        sendFilesPayload(player);
                    }

                } catch (IOException e) {
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AtpEditStatePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            String playerName = player.getName().getString();
            context.server().execute(() -> {
                if (!player.hasPermissionLevel(2)) return;
                String path = payload.relativePath();
                if (payload.isEditing()) {
                    editingByFile.computeIfAbsent(path, k -> ConcurrentHashMap.newKeySet()).add(playerName);
                } else {
                    Set<String> editors = editingByFile.get(path);
                    if (editors != null) {
                        editors.remove(playerName);
                        if (editors.isEmpty()) editingByFile.remove(path);
                    }
                }
                for (ServerPlayerEntity p : context.server().getPlayerManager().getPlayerList()) {
                    sendEditingFilesPayload(p);
                }
            });
        });
    }

    private void broadcastFileSync(String relativePath, String content, ServerPlayerEntity sender, net.minecraft.server.MinecraftServer server) {
        Set<String> editors = editingByFile.get(relativePath);
        if (editors == null) return;
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (!p.equals(sender) && editors.contains(p.getName().getString())) {
                ServerPlayNetworking.send(p, new AtpFileSyncPayload(relativePath, content));
            }
        }
    }

    private void sendEditingFilesPayload(ServerPlayerEntity player) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Set<String>> entry : editingByFile.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                sb.append(entry.getKey()).append(":").append(String.join(",", entry.getValue())).append("\n");
            }
        }
        ServerPlayNetworking.send(player, new AtpResponseEditingFilesPayload(sb.toString()));
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

    private void sendFilesPayload(ServerPlayerEntity player) {
        if (!autotasksFolder.exists()) {
            autotasksFolder.mkdirs();
        }

        List<String> entries = new ArrayList<>();
        collectFiles(autotasksFolder, autotasksFolder, entries);
        String fileData = String.join("\n", entries);

        ServerPlayNetworking.send(player, new AtpResponseFilesPayload(fileData));
    }

    private void collectFiles(File root, File folder, List<String> entries) {
        File[] files = folder.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            try {
                String relativePath = root.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                boolean isDir = file.isDirectory();
                if (isDir) {
                    entries.add(relativePath + ":true:ok");
                    collectFiles(root, file, entries);
                } else if (file.getName().toLowerCase().endsWith(".yml")) {
                    var status = taskManager.getFileStatus(relativePath);
                    entries.add(relativePath + ":false:" + status.name().toLowerCase());
                }
            } catch (Exception e) {
                LOGGER.error("Error collecting file " + file.getPath(), e);
            }
        }
    }

    private void saveDefaultTask(String resourceName) {
        File taskFile = new File(autotasksFolder, resourceName);
        if (!taskFile.exists()) {
            try (InputStream is = Autotask.class.getClassLoader().getResourceAsStream("autotasks/" + resourceName)) {
                if (is != null) {
                    Files.copy(is, taskFile.toPath());
                } else {
                    LOGGER.warn("No se pudo encontrar el recurso: autotasks/" + resourceName);
                }
            } catch (Exception e) {
                LOGGER.error("Error al guardar el recurso " + resourceName, e);
            }
        }
    }

    public static Autotask getInstance() {
        return instance;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public File getAutotasksFolder() {
        return autotasksFolder;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }
}
