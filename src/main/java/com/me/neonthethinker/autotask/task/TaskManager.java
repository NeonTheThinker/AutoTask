package com.me.neonthethinker.autotask.task;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import com.me.neonthethinker.autotask.AutoTasks;
import com.me.neonthethinker.autotask.utils.TimeUtils;
import com.me.neonthethinker.autotask.utils.PlaceholderUtils;
import com.me.neonthethinker.autotask.task.handler.CicloHandler;
import com.me.neonthethinker.autotask.task.handler.SecuenciaHandler;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TaskManager {
    private final AutoTasks plugin;
    private final Map<String, YamlConfiguration> tasks = new ConcurrentHashMap<>();
    private final Map<String, List<BukkitTask>> activeTasks = new ConcurrentHashMap<>();
    private final Map<String, Long> taskStartTimes = new ConcurrentHashMap<>();

    private final CicloHandler cicloHandler;
    private final SecuenciaHandler secuenciaHandler;

    public TaskManager(AutoTasks plugin) {
        this.plugin = plugin;
        this.cicloHandler = new CicloHandler(plugin);
        this.secuenciaHandler = new SecuenciaHandler(plugin);
    }

    public void reloadTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                cancelAllTasks();
                tasks.clear();

                File autotasksFolder = plugin.getAutotasksFolder();
                if (autotasksFolder.exists()) {
                    File[] files = autotasksFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
                    if (files != null) {
                        for (File file : files) {
                            String taskName = file.getName().replace(".yml", "").replace(".YML", "");
                            tasks.put(taskName.toLowerCase(), YamlConfiguration.loadConfiguration(file));
                        }
                    }
                }
            }
        }.runTaskAsynchronously(plugin);
    }

    public boolean executeTask(CommandSender sender, String taskName, long startOffset, String targetWorld) {
        YamlConfiguration taskConfig = tasks.get(taskName.toLowerCase());
        if (taskConfig == null) {
            sender.sendMessage("§cTarea no encontrada: " + taskName);
            return false;
        }

        final boolean globalUsePapi = taskConfig.getBoolean("placeholder", false);
        final String modo = taskConfig.getString("modo", "incremental").toLowerCase();
        final boolean esModoAbsoluto = modo.equals("absoluto");
        final String utcOffset = taskConfig.getString("utc", "");
        final List<Map<?, ?>> acciones = taskConfig.getMapList("acciones");

        if (acciones.isEmpty()) {
            sender.sendMessage("§cEsta tarea no tiene acciones configuradas");
            return false;
        }

        final String completionMessage = acciones.stream()
                .filter(accion -> accion.containsKey("stop"))
                .map(accion -> (String) accion.get("stop"))
                .findFirst()
                .orElse(null);

        if (completionMessage == null) {
            sender.sendMessage("§cEsta tarea no tiene marcador 'stop' y no se puede ejecutar");
            return false;
        }

        new BukkitRunnable() {
            @Override
            public void run() {

                List<BukkitTask> currentTasks = Collections.synchronizedList(new ArrayList<>());
                BukkitScheduler scheduler = Bukkit.getScheduler();

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
                        if (baseTicks <= startOffset && !esModoAbsoluto) continue;
                        programarComandos(scheduler, (List<?>) accion.get("comandos"), scheduledTicks, currentTasks, targetWorld, actionUsePapi);
                    } else if (accion.containsKey("ciclo")) {
                        cicloHandler.programar(scheduler, (Map<?, ?>) accion.get("ciclo"), scheduledTicks, currentTasks, targetWorld, actionUsePapi);
                    }
                }

                long maxDelay = calcularMaxDelay(acciones, startOffset, esModoAbsoluto, utcOffset);

                activeTasks.put(taskName.toLowerCase(), currentTasks);
                taskStartTimes.put(taskName.toLowerCase(), System.currentTimeMillis());

                if (maxDelay >= 0) {
                    currentTasks.add(scheduler.runTaskLater(plugin, () -> {
                        Bukkit.getConsoleSender().sendMessage("§a" + completionMessage);
                        stopTask(taskName);
                    }, maxDelay));
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    String worldMsg = (targetWorld != null) ? " en mundo §b" + targetWorld : "";
                    String modoMsg = esModoAbsoluto ? "absoluto" : "incremental";
                    sender.sendMessage("§aTarea '" + taskName + "' iniciada en modo " + modoMsg + worldMsg + " con §e" + currentTasks.size() + " §aacciones.");
                });
            }
        }.runTaskAsynchronously(plugin);

        return true;
    }

    private void programarComandos(BukkitScheduler scheduler, List<?> comandos, long delayTicks, List<BukkitTask> taskList, String targetWorld, boolean usePapi) {
        for (Object comandoObj : comandos) {
            if (comandoObj instanceof String) {
                String comando = (String) comandoObj;

                taskList.add(scheduler.runTaskLater(plugin, () ->
                        dispatchCommand(comando, targetWorld, usePapi), delayTicks));
            }
            else if (comandoObj instanceof Map) {
                Map<?, ?> comandoMap = (Map<?, ?>) comandoObj;
                if (comandoMap.containsKey("secuencia")) {
                    secuenciaHandler.programar(scheduler, (List<Map<?, ?>>) comandoMap.get("secuencia"), delayTicks, taskList, targetWorld, usePapi);
                }
            }
        }
    }

    public static void dispatchCommand(String command, String targetWorld, boolean usePapi) {
        String finalCommand = command;
        if (usePapi) {
            finalCommand = PlaceholderUtils.replace(null, finalCommand);
        }
        if (targetWorld != null && !targetWorld.isEmpty()) {
            String dimension = targetWorld.toLowerCase();
            if (!dimension.contains(":")) dimension = "minecraft:" + dimension;
            finalCommand = "execute in " + dimension + " run " + finalCommand;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
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
                accionMaxDelay = calcularMaxDelayComandos(scheduledDelay, (List<?>) accion.get("comandos"));
            } else if (accion.containsKey("ciclo")) {
                accionMaxDelay = cicloHandler.calcularMaxDelay(scheduledDelay, (Map<?, ?>) accion.get("ciclo"));
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

    public boolean stopTask(String taskName, CommandSender sender, boolean announce) {
        List<BukkitTask> tasks = activeTasks.remove(taskName.toLowerCase());
        if (tasks == null) return false;
        taskStartTimes.remove(taskName.toLowerCase());
        tasks.forEach(task -> {
            if (!task.isCancelled()) task.cancel();
        });
        return true;
    }

    public boolean stopTask(String taskName) {
        return stopTask(taskName, Bukkit.getConsoleSender(), false);
    }

    public Map<String, String> getActiveTasksStatus(boolean useTicks) {
        Map<String, String> status = new HashMap<>();
        long currentTime = System.currentTimeMillis();
        activeTasks.forEach((name, tasks) -> {
            Long startTime = taskStartTimes.get(name);
            if (startTime != null) {
                long elapsedTicks = (currentTime - startTime) / 50;
                status.put(name, useTicks ? "t" + elapsedTicks : TimeUtils.ticksToTime(elapsedTicks));
            }
        });
        return status;
    }

    public void cancelAllTasks() {
        activeTasks.values().forEach(tasks -> tasks.forEach(task -> {
            if (!task.isCancelled()) task.cancel();
        }));
        activeTasks.clear();
        taskStartTimes.clear();
    }

    public Set<String> getLoadedTasks() {
        return tasks.keySet();
    }

    public Set<String> getActiveTasks() {
        return activeTasks.keySet();
    }
}