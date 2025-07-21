package owleaf;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.util.*;

public class CommandHandler implements TabExecutor {
    private final AutoTasks plugin;
    private final List<BukkitTask> activeTasks = new ArrayList<>();
    private Map<String, YamlConfiguration> tasks = new HashMap<>();

    public CommandHandler(AutoTasks plugin) {
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

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String taskName = args[0].toLowerCase();
        YamlConfiguration taskConfig = tasks.get(taskName);

        if (taskConfig == null) {
            sender.sendMessage("§cTarea no encontrada: " + taskName);
            sendAvailableTasks(sender);
            return true;
        }

        executeTask(sender, taskConfig);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], new ArrayList<>(tasks.keySet()), completions);
        }

        Collections.sort(completions);
        return completions;
    }

    private void executeTask(CommandSender sender, YamlConfiguration config) {
        List<Map<?, ?>> acciones = config.getMapList("acciones");
        if (acciones.isEmpty()) {
            sender.sendMessage("§cEsta tarea no tiene acciones configuradas");
            return;
        }

        BukkitScheduler scheduler = Bukkit.getScheduler();

        for (Map<?, ?> accion : acciones) {
            String delayStr = (String) accion.get("delay");
            List<?> comandos = (List<?>) accion.get("comandos");

            long baseTicks = parseTimeToTicks(delayStr);
            if (baseTicks <= 0) continue;

            programarComandos(scheduler, comandos, baseTicks);
        }

        sender.sendMessage("§aTarea iniciada con §e" + acciones.size() + " §aacciones programadas");
    }

    private void programarComandos(BukkitScheduler scheduler, List<?> comandos, long baseTicks) {
        for (Object comandoObj : comandos) {
            if (comandoObj instanceof String) {
                String comando = (String) comandoObj;
                scheduler.runTaskLater(plugin, () ->
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), comando),
                        baseTicks);
            }
            else if (comandoObj instanceof Map) {
                Map<?, ?> sequence = (Map<?, ?>) comandoObj;
                if (sequence.containsKey("sequence")) {
                    List<Map<?, ?>> steps = (List<Map<?, ?>>) sequence.get("sequence");

                    for (Map<?, ?> step : steps) {
                        long stepDelay = parseTimeToTicks((String) step.get("delay"));
                        String cmd = (String) step.get("cmd");

                        scheduler.runTaskLater(plugin, () ->
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd),
                                baseTicks + stepDelay);
                    }
                }
            }
        }
    }

    private void cancelAllTasks() {
        activeTasks.forEach(task -> {
            if (!task.isCancelled()) {
                task.cancel();
            }
        });
        activeTasks.clear();
    }

    private long parseTimeToTicks(String tiempo) {
        if (tiempo == null || tiempo.trim().isEmpty()) return 0;

        String[] partes = tiempo.split(":");
        if (partes.length != 3) return 0;

        try {
            int horas = Integer.parseInt(partes[0]);
            int minutos = Integer.parseInt(partes[1]);
            int segundos = Integer.parseInt(partes[2]);
            return (horas * 72000L) + (minutos * 1200L) + (segundos * 20L);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("Formato de tiempo inválido: " + tiempo);
            return 0;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6Uso: /at <tarea> §7- Ejecuta una tarea programada");
        sender.sendMessage("§6Uso: /atreload §7- Recarga las tareas");
        sendAvailableTasks(sender);
    }

    private void sendAvailableTasks(CommandSender sender) {
        if (tasks.isEmpty()) {
            sender.sendMessage("§cNo hay tareas disponibles. Crea archivos YML en plugins/AutoTasks/autotasks/");
        } else {
            sender.sendMessage("§6Tareas disponibles: §e" + String.join(", ", tasks.keySet()));
        }
    }
}