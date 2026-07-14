package me.neonthethinker.autotask.task;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import me.neonthethinker.autotask.Autotask;
import me.neonthethinker.autotask.AutoTaskCommands;
import me.neonthethinker.autotask.utils.TimeUtils;
import me.neonthethinker.autotask.utils.PlaceholderUtils;
import me.neonthethinker.autotask.utils.YamlConfig;
import me.neonthethinker.autotask.utils.FabricTask;
import me.neonthethinker.autotask.utils.TickScheduler;
import me.neonthethinker.autotask.task.handler.CicloHandler;
import me.neonthethinker.autotask.task.handler.SecuenciaHandler;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class TaskManager {
    private final Autotask mod;
    private final Map<String, YamlConfig> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<FabricTask>> activeTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> taskStartTimes = new ConcurrentHashMap<>();
    private final Map<String, Long> taskStartOffsets = new ConcurrentHashMap<>();
    private final Map<String, net.minecraft.server.network.ServerPlayerEntity> taskTriggerPlayers = new ConcurrentHashMap<>();
    private final Map<String, String> taskTargetWorlds = new ConcurrentHashMap<>();

    public enum FileStatus {
        OK, ERROR, DUPLICATE
    }
    private final Map<String, FileStatus> fileStatuses = new ConcurrentHashMap<>();

    public FileStatus getFileStatus(String relativePath) {
        return fileStatuses.getOrDefault(relativePath.replace('\\', '/').toLowerCase(), FileStatus.OK);
    }

    private final CicloHandler cicloHandler;
    private final SecuenciaHandler secuenciaHandler;

    public TaskManager(Autotask mod) {
        this.mod = mod;
        this.cicloHandler = new CicloHandler();
        this.secuenciaHandler = new SecuenciaHandler();
    }

    public void reloadTasks() {
        reloadTasks(null);
    }

    public void reloadTasks(ServerCommandSource sender) {
        Map<String, Long> runningTaskTimes = new HashMap<>(taskStartTimes);
        Map<String, Long> runningTaskOffsets = new HashMap<>(taskStartOffsets);

        tasks.clear();
        fileStatuses.clear();

        File autotasksFolder = mod.getAutotasksFolder();
        if (!autotasksFolder.exists()) return;

        List<File> yamlFiles = new ArrayList<>();
        findYamlFiles(autotasksFolder, yamlFiles);

        yamlFiles.sort((f1, f2) -> f1.getAbsolutePath().compareToIgnoreCase(f2.getAbsolutePath()));

        List<String> duplicates = new ArrayList<>();
        for (File file : yamlFiles) {
            String filename = file.getName();
            String taskName = filename;
            if (filename.toLowerCase().endsWith(".yml")) {
                taskName = filename.substring(0, filename.length() - 4);
            }
            String taskKey = taskName.toLowerCase();
            String relPath = getRelativePath(autotasksFolder, file).replace('\\', '/').toLowerCase();

            if (tasks.containsKey(taskKey)) {
                duplicates.add(relPath);
                fileStatuses.put(relPath, FileStatus.DUPLICATE);
                Autotask.LOGGER.warn("AutoTask - Tarea duplicada omitida: " + relPath + " (ya existe otra con el mismo nombre)");
            } else {
                YamlConfig cfg = YamlConfig.loadConfiguration(file);
                if (cfg.hasSyntaxError()) {
                    fileStatuses.put(relPath, FileStatus.ERROR);
                } else {
                    fileStatuses.put(relPath, FileStatus.OK);
                }
                tasks.put(taskKey, cfg);
            }
        }

        for (Map.Entry<String, Long> entry : runningTaskTimes.entrySet()) {
            String key = entry.getKey();
            long startTime = entry.getValue();
            long offset = runningTaskOffsets.getOrDefault(key, 0L);
            hotReloadTask(key, startTime, offset);
        }

        if (sender != null) {
            sender.sendFeedback(() -> Text.literal("§aTareas recargadas correctamente!"), true);
            if (!duplicates.isEmpty()) {
                sender.sendFeedback(() -> Text.literal("§cSe detectaron archivos YML duplicados con el mismo nombre que fueron omitidos:"), false);
                for (String dup : duplicates) {
                    sender.sendFeedback(() -> Text.literal("§c- " + dup), false);
                }
            }
        } else {
            if (!duplicates.isEmpty()) {
                Autotask.LOGGER.warn("AutoTask - Archivos duplicados omitidos: " + String.join(", ", duplicates));
            }
        }
        updateCommandsAndSendTree();
    }

    private void updateCommandsAndSendTree() {
        MinecraftServer server = mod.getServer();
        if (server != null) {
            AutoTaskCommands.registerAllTaskPrefixes(server.getCommandManager().getDispatcher());
            for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                server.getPlayerManager().sendCommandTree(player);
            }
        }
    }

    public void reloadSingleTask(String relativePath, ServerCommandSource sender) {
        File autotasksFolder = mod.getAutotasksFolder();
        File file = new File(autotasksFolder, relativePath);
        
        String filename = file.getName();
        String taskName = filename;
        if (filename.toLowerCase().endsWith(".yml")) {
            taskName = filename.substring(0, filename.length() - 4);
        }
        final String finalTaskName = taskName;
        String taskKey = taskName.toLowerCase();
        String relPath = relativePath.replace('\\', '/').toLowerCase();

        boolean isDeleted = !file.exists();
        
        tasks.remove(taskKey);
        fileStatuses.remove(relPath);

        if (!isDeleted) {
            boolean nameConflict = false;
            for (Map.Entry<String, FileStatus> entry : fileStatuses.entrySet()) {
                String existingRelPath = entry.getKey();
                if (!existingRelPath.equals(relPath)) {
                    String existingName = new File(existingRelPath).getName();
                    if (existingName.toLowerCase().endsWith(".yml")) {
                        existingName = existingName.substring(0, existingName.length() - 4);
                    }
                    if (existingName.toLowerCase().equals(taskKey)) {
                        nameConflict = true;
                        break;
                    }
                }
            }

            if (nameConflict) {
                fileStatuses.put(relPath, FileStatus.DUPLICATE);
                Autotask.LOGGER.warn("AutoTask - Tarea duplicada omitida al recargar: " + relPath);
                if (sender != null) {
                    sender.sendFeedback(() -> Text.literal("§cEl archivo '" + relativePath + "' tiene un nombre duplicado y fue omitido."), false);
                }
            } else {
                YamlConfig cfg = YamlConfig.loadConfiguration(file);
                if (cfg.hasSyntaxError()) {
                    fileStatuses.put(relPath, FileStatus.ERROR);
                } else {
                    fileStatuses.put(relPath, FileStatus.OK);
                }
                tasks.put(taskKey, cfg);
            }
        }

        Long startTime = taskStartTimes.get(taskKey);
        if (startTime != null) {
            long offset = taskStartOffsets.getOrDefault(taskKey, 0L);
            hotReloadTask(taskKey, startTime, offset);
        }

        if (sender != null) {
            if (!isDeleted) {
                sender.sendFeedback(() -> Text.literal("§aTarea '" + finalTaskName + "' recargada correctamente."), true);
            }
        }
        updateCommandsAndSendTree();
    }

    private void hotReloadTask(String taskKey, long startTime, long originalStartOffset) {
        YamlConfig taskConfig = tasks.get(taskKey);
        if (taskConfig == null) {
            stopTask(taskKey);
            return;
        }

        List<FabricTask> oldTasks = activeTasks.remove(taskKey);
        if (oldTasks != null) {
            for (FabricTask t : oldTasks) {
                t.cancel();
            }
        }

        final boolean globalUsePapi = taskConfig.getBoolean("placeholder", false);
        final String modo = taskConfig.getString("modo", "incremental").toLowerCase();
        final boolean esModoAbsoluto = modo.equals("absoluto");
        final String utcOffset = taskConfig.getString("utc", "");
        final List<Map<?, ?>> acciones = taskConfig.getMapList("acciones");

        if (acciones.isEmpty()) {
            return;
        }

        final StopActionInfo stopInfo = parseStopAction(acciones);

        long elapsedTicks = (System.currentTimeMillis() - startTime) / 50;

        List<FabricTask> currentTasks = new ArrayList<>();
        final net.minecraft.server.network.ServerPlayerEntity triggerPlayer = taskTriggerPlayers.get(taskKey);
        String targetWorld = taskTargetWorlds.get(taskKey);
        if (targetWorld != null && targetWorld.isEmpty()) {
            targetWorld = null;
        }
        final String finalTargetWorld = targetWorld;

        for (Map<?, ?> accion : acciones) {
            if (accion.containsKey("stop")) continue;

            String delayStr = (String) accion.get("delay");
            long baseTicks;

            if (esModoAbsoluto) {
                baseTicks = TimeUtils.getSystemTimeDelay(delayStr, utcOffset);
                if (baseTicks == -1) continue;
            } else {
                baseTicks = TimeUtils.parseTimeToTicks(delayStr);
            }

            if (baseTicks < 0) continue;
            long scheduledTicks = (baseTicks - originalStartOffset) - elapsedTicks;

            if (scheduledTicks > 0) {
                boolean actionUsePapi = globalUsePapi;
                if (accion.containsKey("papi")) {
                    Object papiObj = accion.get("papi");
                    if (papiObj instanceof Boolean) {
                        actionUsePapi = (Boolean) papiObj;
                    }
                }

                if (accion.containsKey("comandos")) {
                    programarComandos((List<?>) accion.get("comandos"), scheduledTicks, currentTasks, finalTargetWorld, actionUsePapi, false, triggerPlayer);
                }
                if (accion.containsKey("comandos_jugador")) {
                    programarComandos((List<?>) accion.get("comandos_jugador"), scheduledTicks, currentTasks, finalTargetWorld, actionUsePapi, true, triggerPlayer);
                }
                if (accion.containsKey("ciclo")) {
                    cicloHandler.programar((Map<?, ?>) accion.get("ciclo"), scheduledTicks, currentTasks, finalTargetWorld, actionUsePapi, triggerPlayer);
                }
            }
        }

        long maxDelay = calcularMaxDelay(acciones, originalStartOffset, esModoAbsoluto, utcOffset) - elapsedTicks;

        activeTasks.put(taskKey, currentTasks);

        if (maxDelay > 0) {
            currentTasks.add(TickScheduler.runTaskLater(() -> {
                MinecraftServer server = Autotask.getInstance().getServer();
                if (server != null) {
                    if (stopInfo.mensaje != null && !stopInfo.mensaje.isEmpty()) {
                        server.sendMessage(Text.literal("§a" + stopInfo.mensaje));
                    }
                }
                stopTask(taskKey);
            }, maxDelay));
        }
    }

    private void findYamlFiles(File folder, List<File> yamlFiles) {
        File[] files = folder.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                findYamlFiles(file, yamlFiles);
            } else if (file.isFile() && file.getName().toLowerCase().endsWith(".yml")) {
                yamlFiles.add(file);
            }
        }
    }

    private String getRelativePath(File base, File file) {
        try {
            return base.toPath().relativize(file.toPath()).toString().replace('\\', '/');
        } catch (Exception e) {
            return file.getName();
        }
    }

    public boolean executeTask(ServerCommandSource sender, String taskName, long startOffset, String targetWorld) {
        final String key = taskName.toLowerCase();
        YamlConfig taskConfig = tasks.get(key);
        if (taskConfig == null) {
            sender.sendError(Text.literal("§cTarea no encontrada: " + taskName));
            return false;
        }

        final boolean globalUsePapi = taskConfig.getBoolean("placeholder", false);
        final String modo = taskConfig.getString("modo", "incremental").toLowerCase();
        final boolean esModoAbsoluto = modo.equals("absoluto");
        final String utcOffset = taskConfig.getString("utc", "");
        final List<Map<?, ?>> acciones = taskConfig.getMapList("acciones");

        if (acciones.isEmpty()) {
            sender.sendError(Text.literal("§cEsta tarea no tiene acciones configuradas"));
            return false;
        }

        boolean hasStopMarker = false;
        for (Map<?, ?> accion : acciones) {
            if (accion.containsKey("stop")) {
                hasStopMarker = true;
                break;
            }
        }

        if (!hasStopMarker) {
            sender.sendError(Text.literal("§cEsta tarea no tiene marcador 'stop' y no se puede ejecutar"));
            return false;
        }

        final net.minecraft.server.network.ServerPlayerEntity triggerPlayer = sender.getPlayer();
        final StopActionInfo stopInfo = parseStopAction(acciones);

        final String startMessage = taskConfig.getString("mensaje", null);
        if (startMessage != null && !startMessage.isEmpty()) {
            String processed = PlaceholderUtils.replace(triggerPlayer, startMessage);
            sender.sendFeedback(() -> Text.literal("§a" + processed), true);
        }

        CompletableFuture.runAsync(() -> {

            List<FabricTask> currentTasks = new ArrayList<>();

            for (Map<?, ?> accion : acciones) {
                if (accion.containsKey("stop")) continue;

                String delayStr = (String) accion.get("delay");
                long baseTicks;

                if (esModoAbsoluto) {
                    baseTicks = TimeUtils.getSystemTimeDelay(delayStr, utcOffset);
                    if (baseTicks == -1) continue;
                } else {
                    baseTicks = TimeUtils.parseTimeToTicks(delayStr);
                }

                if (baseTicks < 0) continue;
                long scheduledTicks = Math.max(0, baseTicks - startOffset);

                boolean actionUsePapi = globalUsePapi;
                if (accion.containsKey("papi")) {
                    Object papiObj = accion.get("papi");
                    if (papiObj instanceof Boolean) {
                        actionUsePapi = (Boolean) papiObj;
                    }
                }

                if (accion.containsKey("comandos")) {
                    if (!(baseTicks <= startOffset && !esModoAbsoluto)) {
                        programarComandos((List<?>) accion.get("comandos"), scheduledTicks, currentTasks, targetWorld, actionUsePapi, false, triggerPlayer);
                    }
                }
                if (accion.containsKey("comandos_jugador")) {
                    if (!(baseTicks <= startOffset && !esModoAbsoluto)) {
                        programarComandos((List<?>) accion.get("comandos_jugador"), scheduledTicks, currentTasks, targetWorld, actionUsePapi, true, triggerPlayer);
                    }
                }
                if (accion.containsKey("ciclo")) {
                    cicloHandler.programar((Map<?, ?>) accion.get("ciclo"), scheduledTicks, currentTasks, targetWorld, actionUsePapi, triggerPlayer);
                }
            }

            long maxDelay = calcularMaxDelay(acciones, startOffset, esModoAbsoluto, utcOffset);

            activeTasks.put(key, currentTasks);
            taskStartTimes.put(key, System.currentTimeMillis());
            taskStartOffsets.put(key, startOffset);
            if (triggerPlayer != null) {
                taskTriggerPlayers.put(key, triggerPlayer);
            }
            taskTargetWorlds.put(key, targetWorld != null ? targetWorld : "");

            if (maxDelay >= 0) {
                currentTasks.add(TickScheduler.runTaskLater(() -> {
                    MinecraftServer server = Autotask.getInstance().getServer();
                    if (server != null) {
                        if (stopInfo.mensaje != null && !stopInfo.mensaje.isEmpty()) {
                            server.sendMessage(Text.literal("§a" + stopInfo.mensaje));
                        }
                    }
                    stopTask(taskName);
                }, maxDelay));
            }

            String worldMsg = (targetWorld != null) ? " en mundo §b" + targetWorld : "";
            String modoMsg = esModoAbsoluto ? "absoluto" : "incremental";
            int taskCount = currentTasks.size();
            sender.sendFeedback(() -> Text.literal("§aTarea '" + taskName + "' iniciada en modo " + modoMsg + worldMsg + " con §e" + taskCount + " §aacciones."), true);
        });

        return true;
    }

    private void programarComandos(List<?> comandos, long delayTicks, List<FabricTask> taskList, String targetWorld, boolean usePapi, boolean asPlayer, net.minecraft.server.network.ServerPlayerEntity triggerPlayer) {
        for (Object comandoObj : comandos) {
            if (comandoObj instanceof String) {
                String comando = (String) comandoObj;
                taskList.add(TickScheduler.runTaskLater(() ->
                        dispatchCommand(comando, targetWorld, usePapi, asPlayer, triggerPlayer), delayTicks));
            }
            else if (comandoObj instanceof Map) {
                Map<?, ?> comandoMap = (Map<?, ?>) comandoObj;
                if (comandoMap.containsKey("secuencia")) {
                    secuenciaHandler.programar((List<Map<?, ?>>) comandoMap.get("secuencia"), delayTicks, taskList, targetWorld, usePapi, triggerPlayer);
                }
            }
        }
    }

    public static void dispatchCommand(String command, String targetWorld, boolean usePapi, boolean asPlayer, ServerPlayerEntity triggerPlayer) {
        String finalCommand = command.trim();
        if (finalCommand.startsWith("/")) {
            finalCommand = finalCommand.substring(1);
        }

        MinecraftServer server = Autotask.getInstance().getServer();
        if (server == null) return;

        if (asPlayer) {
            ServerPlayerEntity playerToRun = null;
            if (triggerPlayer != null) {
                playerToRun = server.getPlayerManager().getPlayer(triggerPlayer.getUuid());
            }
            if (playerToRun != null) {
                String playerCommand = finalCommand;
                if (usePapi) {
                    playerCommand = PlaceholderUtils.replace(playerToRun, playerCommand);
                }
                if (targetWorld != null && !targetWorld.isEmpty()) {
                    String dimension = targetWorld.toLowerCase();
                    if (!dimension.contains(":")) dimension = "minecraft:" + dimension;
                    playerCommand = "execute in " + dimension + " run " + playerCommand;
                }
                server.getCommandManager().executeWithPrefix(playerToRun.getCommandSource(), playerCommand);
            } else {
                Autotask.LOGGER.warn("[AutoTask] No se pudo ejecutar el comando de jugador porque el jugador iniciador no está conectado o es nulo.");
            }
        } else {
            String consoleCommand = finalCommand;
            if (usePapi) {
                consoleCommand = PlaceholderUtils.replace(null, consoleCommand);
            }
            if (targetWorld != null && !targetWorld.isEmpty()) {
                String dimension = targetWorld.toLowerCase();
                if (!dimension.contains(":")) dimension = "minecraft:" + dimension;
                consoleCommand = "execute in " + dimension + " run " + consoleCommand;
            }
            server.getCommandManager().executeWithPrefix(server.getCommandSource(), consoleCommand);
        }
    }

    private long calcularMaxDelay(List<Map<?, ?>> acciones, long startOffset, boolean esModoAbsoluto, String utcOffset) {
        long maxDelay = -1;
        for (Map<?, ?> accion : acciones) {
            if (accion.containsKey("stop")) continue;
            String delayStr = (String) accion.get("delay");
            long delay = esModoAbsoluto ? TimeUtils.getSystemTimeDelay(delayStr, utcOffset) : TimeUtils.parseTimeToTicks(delayStr);
            if (delay == -1 || delay < startOffset) continue;
            long scheduledDelay = delay - startOffset;
            long accionMaxDelay = scheduledDelay;
            if (accion.containsKey("comandos")) {
                long d = calcularMaxDelayComandos(scheduledDelay, (List<?>) accion.get("comandos"));
                if (d > accionMaxDelay) accionMaxDelay = d;
            }
            if (accion.containsKey("comandos_jugador")) {
                long d = calcularMaxDelayComandos(scheduledDelay, (List<?>) accion.get("comandos_jugador"));
                if (d > accionMaxDelay) accionMaxDelay = d;
            }
            if (accion.containsKey("ciclo")) {
                long d = cicloHandler.calcularMaxDelay(scheduledDelay, (Map<?, ?>) accion.get("ciclo"));
                if (d > accionMaxDelay) accionMaxDelay = d;
            }
            if (accionMaxDelay > maxDelay) maxDelay = accionMaxDelay;
        }
        return maxDelay;
    }

    private long calcularMaxDelayComandos(long baseDelayTicks, List<?> comandos) {
        long maxDelay = baseDelayTicks;
        for (Object comando : comandos) {
            if (comando instanceof Map) {
                Map<?, ?> seq = (Map<?, ?>) comando;
                if (seq.containsKey("secuencia")) {
                    long secMaxDelay = secuenciaHandler.calcularMaxDelay(baseDelayTicks, (List<Map<?, ?>>) seq.get("secuencia"));
                    if (secMaxDelay > maxDelay) maxDelay = secMaxDelay;
                }
            }
        }
        return maxDelay;
    }

    public boolean stopTask(String taskName, ServerCommandSource sender, boolean announce) {
        final String key = taskName.toLowerCase();
        List<FabricTask> removed = activeTasks.remove(key);
        if (removed == null) return false;

        net.minecraft.server.network.ServerPlayerEntity triggerPlayer = taskTriggerPlayers.remove(key);
        String targetWorld = taskTargetWorlds.remove(key);
        if (targetWorld != null && targetWorld.isEmpty()) {
            targetWorld = null;
        }

        taskStartTimes.remove(key);
        taskStartOffsets.remove(key);

        for (FabricTask task : removed) {
            task.cancel();
        }

        YamlConfig taskConfig = tasks.get(key);
        if (taskConfig != null) {
            StopActionInfo stopInfo = parseStopAction(taskConfig.getMapList("acciones"));
            MinecraftServer server = Autotask.getInstance().getServer();
            if (server != null) {
                if (stopInfo.mensaje != null && !stopInfo.mensaje.isEmpty()) {
                    String processedMsg = PlaceholderUtils.replace(triggerPlayer, stopInfo.mensaje);
                    ServerCommandSource feedbackSource = (triggerPlayer != null && server.getPlayerManager().getPlayer(triggerPlayer.getUuid()) != null) 
                        ? triggerPlayer.getCommandSource() 
                        : server.getCommandSource();
                    feedbackSource.sendFeedback(() -> Text.literal("§a" + processedMsg), true);
                }
                if (!stopInfo.comandos.isEmpty()) {
                    for (Object cmdObj : stopInfo.comandos) {
                        String cmd = String.valueOf(cmdObj);
                        String processed = PlaceholderUtils.replace(triggerPlayer, cmd);
                        String finalCmd = processed;
                        if (targetWorld != null) {
                            finalCmd = "execute in " + targetWorld + " run " + processed;
                        }
                        server.getCommandManager().executeWithPrefix(server.getCommandSource(), finalCmd);
                    }
                }
                if (!stopInfo.comandosJugador.isEmpty() && triggerPlayer != null) {
                    for (Object cmdObj : stopInfo.comandosJugador) {
                        String cmd = String.valueOf(cmdObj);
                        String processed = PlaceholderUtils.replace(triggerPlayer, cmd);
                        server.getCommandManager().executeWithPrefix(triggerPlayer.getCommandSource(), processed);
                    }
                }
            }
        }

        return true;
    }

    public boolean stopTask(String taskName) {
        final String key = taskName.toLowerCase();
        List<FabricTask> removed = activeTasks.remove(key);
        if (removed == null) return false;
        taskStartTimes.remove(key);
        taskStartOffsets.remove(key);
        taskTriggerPlayers.remove(key);
        taskTargetWorlds.remove(key);
        for (FabricTask task : removed) {
            task.cancel();
        }
        return true;
    }

    public Map<String, String> getActiveTasksStatus(boolean useTicks) {
        Map<String, String> status = new HashMap<>();
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<String, List<FabricTask>> entry : activeTasks.entrySet()) {
            String name = entry.getKey();
            Long startTime = taskStartTimes.get(name);
            if (startTime != null) {
                long elapsedTicks = (currentTime - startTime) / 50;
                status.put(name, useTicks ? "t" + elapsedTicks : TimeUtils.ticksToTime(elapsedTicks));
            }
        }
        return status;
    }

    public void cancelAllTasks() {
        for (List<FabricTask> taskList : activeTasks.values()) {
            for (FabricTask task : taskList) {
                task.cancel();
            }
        }
        activeTasks.clear();
        taskStartTimes.clear();
        taskStartOffsets.clear();
        taskTriggerPlayers.clear();
        taskTargetWorlds.clear();
    }

    public String getExecutedTasksData() {
        List<String> list = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> entry : taskStartTimes.entrySet()) {
            String key = entry.getKey();
            long startTime = entry.getValue();
            long elapsedSeconds = (now - startTime) / 1000;
            
            YamlConfig cfg = tasks.get(key);
            int activeActionIndex = -1;
            boolean esAbsoluto = false;
            String timeStr = "";
            
            if (cfg != null) {
                String modo = cfg.getString("modo", "incremental").toLowerCase();
                esAbsoluto = modo.equals("absoluto");
                String utcOffset = cfg.getString("utc", "");
                
                if (esAbsoluto) {
                    timeStr = TimeUtils.getCurrentTimeFormatted(utcOffset);
                }
                
                List<Map<?, ?>> acciones = cfg.getMapList("acciones");
                
                long elapsedTicks = (now - startTime) / 50;
                for (int i = 0; i < acciones.size(); i++) {
                    Map<?, ?> accion = acciones.get(i);
                    long actionTicks = 0;
                    if (accion.containsKey("stop")) {
                        actionTicks = calcularMaxDelay(acciones, 0, esAbsoluto, utcOffset);
                    } else {
                        String delayStr = (String) accion.get("delay");
                        if (esAbsoluto) {
                            actionTicks = TimeUtils.getAbsoluteTicksOffset(delayStr, startTime, utcOffset);
                        } else {
                            actionTicks = TimeUtils.parseTimeToTicks(delayStr);
                        }
                    }
                    if (actionTicks >= 0 && elapsedTicks >= actionTicks) {
                        activeActionIndex = i;
                    }
                }
            }
            list.add(key + ":" + elapsedSeconds + ":" + activeActionIndex + ":" + esAbsoluto + ":" + timeStr);
        }
        return String.join("\n", list);
    }

    public Set<String> getLoadedTasks() {
        return tasks.keySet();
    }

    public YamlConfig getTaskConfig(String taskName) {
        return tasks.get(taskName.toLowerCase());
    }

    public String getStopPrefix(String taskName) {
        YamlConfig config = getTaskConfig(taskName);
        if (config == null) return null;
        List<Map<?, ?>> acciones = config.getMapList("acciones");
        StopActionInfo stopInfo = parseStopAction(acciones);
        return stopInfo.prefix;
    }

    public Set<String> getActiveTasks() {
        return activeTasks.keySet();
    }

    private static class StopActionInfo {
        String mensaje = null;
        String prefix = null;
        List<String> comandos = new ArrayList<>();
        List<String> comandosJugador = new ArrayList<>();
    }

    private StopActionInfo parseStopAction(List<Map<?, ?>> acciones) {
        StopActionInfo info = new StopActionInfo();
        for (Map<?, ?> accion : acciones) {
            if (accion.containsKey("stop")) {
                Object stopVal = accion.get("stop");
                if (stopVal instanceof String) {
                    info.mensaje = (String) stopVal;
                } else if (stopVal instanceof Map) {
                    Map<?, ?> stopMap = (Map<?, ?>) stopVal;
                    Object msgVal = stopMap.get("mensaje");
                    if (msgVal instanceof String) {
                        info.mensaje = (String) msgVal;
                    }
                    Object prefixVal = stopMap.get("prefix");
                    if (prefixVal instanceof String) {
                        info.prefix = (String) prefixVal;
                    }
                    Object cmdVal = stopMap.get("comandos");
                    if (cmdVal instanceof List) {
                        for (Object c : (List<?>) cmdVal) {
                            info.comandos.add(String.valueOf(c));
                        }
                    }
                    Object cmdJugVal = stopMap.get("comandos_jugador");
                    if (cmdJugVal instanceof List) {
                        for (Object c : (List<?>) cmdJugVal) {
                            info.comandosJugador.add(String.valueOf(c));
                        }
                    }
                }
                
                Object cmdVal = accion.get("comandos");
                if (cmdVal instanceof List) {
                    for (Object c : (List<?>) cmdVal) {
                        info.comandos.add(String.valueOf(c));
                    }
                }
                Object cmdJugVal = accion.get("comandos_jugador");
                if (cmdJugVal instanceof List) {
                    for (Object c : (List<?>) cmdJugVal) {
                        info.comandosJugador.add(String.valueOf(c));
                    }
                }
                break;
            }
        }
        return info;
    }
}
