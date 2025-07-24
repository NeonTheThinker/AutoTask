package owleaf;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

public class TaskManager {
    private final AutoTasks plugin;
    private final Map<String, YamlConfiguration> tasks = new HashMap<>();
    private final Map<String, List<BukkitTask>> activeTasks = new HashMap<>();
    private final Map<String, Long> taskStartTimes = new HashMap<>();

    public TaskManager(AutoTasks plugin) {
        this.plugin = plugin;
    }

    public void reloadTasks() {
        cancelAllTasks();
        tasks.clear();

        File autotasksFolder = plugin.getAutotasksFolder();
        if (autotasksFolder.exists()) {
            File[] files = autotasksFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String taskName = file.getName().replace(".yml", "").replace(".YML", "");
                    tasks.put(taskName.toLowerCase(), YamlConfiguration.loadConfiguration(file));
                    plugin.getLogger().info("Tarea cargada: " + taskName);
                }
            }
        }
    }

    public boolean executeTask(CommandSender sender, String taskName, long startOffset) {
        YamlConfiguration taskConfig = tasks.get(taskName.toLowerCase());
        if (taskConfig == null) {
            sender.sendMessage("§cTarea no encontrada: " + taskName);
            return false;
        }

        List<Map<?, ?>> acciones = taskConfig.getMapList("acciones");
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

        List<BukkitTask> currentTasks = new ArrayList<>();
        BukkitScheduler scheduler = Bukkit.getScheduler();

        for (Map<?, ?> accion : acciones) {
            if (accion.containsKey("stop")) continue;

            String delayStr = (String) accion.get("delay");
            List<?> comandos = (List<?>) accion.get("comandos");
            long baseTicks = TimeUtils.parseTimeToTicks(delayStr);

            if (baseTicks <= startOffset) continue;

            programarComandos(scheduler, comandos, baseTicks - startOffset, currentTasks);
        }

        long maxDelay = calcularMaxDelay(acciones, startOffset);
        currentTasks.add(scheduler.runTaskLater(plugin, () -> {
            Bukkit.getConsoleSender().sendMessage("§a" + completionMessage);
            this.stopTask(taskName, null, false);
        }, maxDelay));

        activeTasks.put(taskName.toLowerCase(), currentTasks);
        taskStartTimes.put(taskName.toLowerCase(), System.currentTimeMillis());
        sender.sendMessage("§aTarea '" + taskName + "' iniciada con §e" + currentTasks.size() + " §aacciones programadas");
        return true;
    }

    private long calcularMaxDelay(List<Map<?, ?>> acciones, long startOffset) {
        long maxDelay = 0;
        for (Map<?, ?> accion : acciones) {
            if (accion.containsKey("stop")) continue;

            String delayStr = (String) accion.get("delay");
            long delay = TimeUtils.parseTimeToTicks(delayStr) - startOffset;

            if (delay > maxDelay) {
                maxDelay = delay;
            }

            List<?> comandos = (List<?>) accion.get("comandos");
            for (Object comando : comandos) {
                if (comando instanceof Map) {
                    Map<?, ?> seq = (Map<?, ?>) comando;
                    if (seq.containsKey("sequence")) {
                        List<Map<?, ?>> steps = (List<Map<?, ?>>) seq.get("sequence");
                        for (Map<?, ?> step : steps) {
                            long stepDelay = TimeUtils.parseTimeToTicks((String) step.get("delay"));
                            if (delay + stepDelay > maxDelay) {
                                maxDelay = delay + stepDelay;
                            }
                        }
                    }
                }
            }
        }
        return maxDelay;
    }

    private void programarComandos(BukkitScheduler scheduler, List<?> comandos, long delayTicks, List<BukkitTask> taskList) {
        for (Object comandoObj : comandos) {
            if (comandoObj instanceof String) {
                String comando = (String) comandoObj;
                taskList.add(scheduler.runTaskLater(plugin, () ->
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando),
                        delayTicks));
            }
            else if (comandoObj instanceof Map) {
                Map<?, ?> sequence = (Map<?, ?>) comandoObj;
                if (sequence.containsKey("sequence")) {
                    List<Map<?, ?>> steps = (List<Map<?, ?>>) sequence.get("sequence");

                    for (Map<?, ?> step : steps) {
                        long stepDelay = TimeUtils.parseTimeToTicks((String) step.get("delay"));
                        Object comandosStep = step.get("comandos");

                        if (comandosStep instanceof String) {
                            String cmd = (String) comandosStep;
                            taskList.add(scheduler.runTaskLater(plugin, () ->
                                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd),
                                    delayTicks + stepDelay));
                        }
                        else if (comandosStep instanceof List) {
                            for (Object cmd : (List<?>) comandosStep) {
                                if (cmd instanceof String) {
                                    taskList.add(scheduler.runTaskLater(plugin, () ->
                                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), (String) cmd),
                                            delayTicks + stepDelay));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean stopTask(String taskName, CommandSender sender, boolean showCompletionMessage) {
        List<BukkitTask> tasks = activeTasks.remove(taskName.toLowerCase());
        if (tasks == null) return false;

        YamlConfiguration config = getTaskConfig(taskName);
        String completionMessage = null;

        if (config != null && showCompletionMessage) {
            completionMessage = config.getMapList("acciones").stream()
                    .filter(accion -> accion.containsKey("stop"))
                    .map(accion -> (String) accion.get("stop"))
                    .findFirst()
                    .orElse(null);
        }

        if (completionMessage != null) {
            Bukkit.getConsoleSender().sendMessage("§a" + completionMessage);
            if (sender != null && sender != Bukkit.getConsoleSender()) {
                sender.sendMessage("§a" + completionMessage);
            }
        }

        taskStartTimes.remove(taskName.toLowerCase());
        tasks.forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        return true;
    }

    public Map<String, String> getActiveTasksStatus(boolean useTicks) {
        Map<String, String> status = new HashMap<>();
        long currentTime = System.currentTimeMillis();

        activeTasks.forEach((name, tasks) -> {
            Long startTime = taskStartTimes.get(name);
            if (startTime != null) {
                long elapsedTicks = (currentTime - startTime) / 50;
                if (useTicks) {
                    status.put(name, "t" + elapsedTicks);
                } else {
                    status.put(name, TimeUtils.ticksToTime(elapsedTicks));
                }
            }
        });

        return status;
    }

    public void cancelAllTasks() {
        activeTasks.values().forEach(tasks -> tasks.forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
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

    public YamlConfiguration getTaskConfig(String taskName) {
        return tasks.get(taskName.toLowerCase());
    }
}