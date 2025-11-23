package owleaf.task;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import owleaf.AutoTasks;
import owleaf.utils.TimeUtils;
import owleaf.task.handler.CicloHandler;
import owleaf.task.handler.SecuenciaHandler;

import java.io.File;
import java.util.*;

public class TaskManager {
    private final AutoTasks plugin;
    private final Map<String, YamlConfiguration> tasks = new HashMap<>();
    private final Map<String, List<BukkitTask>> activeTasks = new HashMap<>();
    private final Map<String, Long> taskStartTimes = new HashMap<>();

    private final CicloHandler cicloHandler;
    private final SecuenciaHandler secuenciaHandler;

    public TaskManager(AutoTasks plugin) {
        this.plugin = plugin;
        this.cicloHandler = new CicloHandler(plugin);
        this.secuenciaHandler = new SecuenciaHandler(plugin);
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

        String modo = taskConfig.getString("modo", "incremental").toLowerCase();
        boolean esModoAbsoluto = modo.equals("absoluto");
        String utcOffset = taskConfig.getString("utc", "");

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

        if (esModoAbsoluto && !utcOffset.isEmpty()) {
            sender.sendMessage("§e" + TimeUtils.getCurrentTimeWithOffset(utcOffset));
        }

        List<BukkitTask> currentTasks = new ArrayList<>();
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

            if (accion.containsKey("comandos")) {
                if (baseTicks <= startOffset && !esModoAbsoluto) continue;
                if (baseTicks <= startOffset && esModoAbsoluto) continue;

                programarComandos(scheduler, (List<?>) accion.get("comandos"), scheduledTicks, currentTasks);

            } else if (accion.containsKey("ciclo")) {
                cicloHandler.programar(scheduler, (Map<?, ?>) accion.get("ciclo"), scheduledTicks, currentTasks);
            }
        }

        long maxDelay = calcularMaxDelay(acciones, startOffset, esModoAbsoluto, utcOffset);
        if (maxDelay >= 0) {
            currentTasks.add(scheduler.runTaskLater(plugin, () -> {
                Bukkit.getConsoleSender().sendMessage("§a" + completionMessage);

                this.stopTask(taskName, null, false);
            }, maxDelay));
        } else {
            if (!esModoAbsoluto && currentTasks.isEmpty()) {
                Bukkit.getConsoleSender().sendMessage("§a" + completionMessage);

                this.stopTask(taskName, null, false);
            }
        }

        activeTasks.put(taskName.toLowerCase(), currentTasks);
        taskStartTimes.put(taskName.toLowerCase(), System.currentTimeMillis());

        String modoMsg = esModoAbsoluto ? "absoluto" + (!utcOffset.isEmpty() ? " (UTC " + utcOffset + ")" : "") : "incremental";
        sender.sendMessage("§aTarea '" + taskName + "' iniciada en modo " + modoMsg + " con §e" + currentTasks.size() + " §aacciones programadas");
        return true;
    }

    private long calcularMaxDelay(List<Map<?, ?>> acciones, long startOffset, boolean esModoAbsoluto, String utcOffset) {
        long maxDelay = -1;
        for (Map<?, ?> accion : acciones) {
            if (accion.containsKey("stop")) continue;
            String delayStr = (String) accion.get("delay");
            long delay;
            if (esModoAbsoluto) {
                delay = TimeUtils.getSystemTimeDelay(delayStr, utcOffset);
                if (delay == -1) continue;
            } else {
                delay = TimeUtils.parseTimeToTicks(delayStr);
            }
            if (delay < startOffset) continue;
            long scheduledDelay = delay - startOffset;
            long accionMaxDelay = scheduledDelay;
            if (accion.containsKey("comandos")) {
                accionMaxDelay = calcularMaxDelayComandos(scheduledDelay, (List<?>) accion.get("comandos"));
            } else if (accion.containsKey("ciclo")) {
                accionMaxDelay = cicloHandler.calcularMaxDelay(scheduledDelay, (Map<?, ?>) accion.get("ciclo"));
            }
            if (accionMaxDelay > maxDelay) {
                maxDelay = accionMaxDelay;
            }
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
                    if (secMaxDelay > maxDelay) {
                        maxDelay = secMaxDelay;
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
                Map<?, ?> comandoMap = (Map<?, ?>) comandoObj;
                if (comandoMap.containsKey("secuencia")) {
                    secuenciaHandler.programar(scheduler, (List<Map<?, ?>>) comandoMap.get("secuencia"), delayTicks, taskList);
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