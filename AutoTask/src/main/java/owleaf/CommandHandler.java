package owleaf;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;

import java.util.*;

public class CommandHandler implements TabExecutor {
    private final AutoTasks plugin;
    private final TaskManager taskManager;

    public CommandHandler(AutoTasks plugin, TaskManager taskManager) {
        this.plugin = plugin;
        this.taskManager = taskManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String commandName = cmd.getName().toLowerCase();

        if (!checkSecurity(sender, commandName)) {
            return true;
        }

        switch (commandName) {
            case "at":
                return handleAtCommand(sender, args);
            case "atstop":
                return handleAtStopCommand(sender, args);
            case "atstatus":
                return handleAtStatusCommand(sender, args);
            case "atreload":
                taskManager.reloadTasks();
                sender.sendMessage("§aTareas recargadas correctamente!");
                return true;
            default:
                return false;
        }
    }

    private boolean checkSecurity(CommandSender sender, String commandName) {
        if (!sender.hasPermission("autotasks." + commandName.toLowerCase())) {
            sender.sendMessage("§cNo tienes permiso para este comando.");
            return false;
        }
        return true;
    }

    private boolean handleAtCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        String taskName = args[0].toLowerCase();
        long startOffset = 0;

        if (args.length >= 2) {
            startOffset = TimeUtils.parseTimeToTicks(args[1]);
        }

        if (!taskManager.executeTask(sender, taskName, startOffset)) {
            sender.sendMessage("§cTarea no encontrada: " + taskName);
            sendAvailableTasks(sender);
        }
        return true;
    }

    private boolean handleAtStopCommand(CommandSender sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("§6Uso: /atstop <tarea> §7- Detiene una tarea en ejecución");
            return true;
        }

        String taskName = args[0].toLowerCase();
        if (taskManager.stopTask(taskName, sender, true)) {
            sender.sendMessage("§aTarea '" + taskName + "' detenida correctamente");
        } else {
            sender.sendMessage("§cNo se encontró la tarea en ejecución: " + taskName);
            sendActiveTasks(sender);
        }
        return true;
    }

    private boolean handleAtStatusCommand(CommandSender sender, String[] args) {
        boolean useTicks = args.length > 0 && args[0].equalsIgnoreCase("ticks");

        Map<String, String> activeTasks = taskManager.getActiveTasksStatus(useTicks);
        if (activeTasks.isEmpty()) {
            sender.sendMessage("§cNo hay tareas activas en este momento");
            return true;
        }

        sender.sendMessage("§6---------Auto Task---------");
        activeTasks.forEach((name, time) ->
                sender.sendMessage("§7- §e" + name + " §7: §b" + time));
        sender.sendMessage("§6---------------------------");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        String commandName = cmd.getName().toLowerCase();
        List<String> completions = new ArrayList<>();

        if (!checkSecurity(sender, commandName)) {
            return completions;
        }

        switch (commandName) {
            case "at":
                if (args.length == 1) {
                    StringUtil.copyPartialMatches(args[0], new ArrayList<>(taskManager.getLoadedTasks()), completions);
                }
                break;
            case "atstop":
                if (args.length == 1) {
                    StringUtil.copyPartialMatches(args[0], new ArrayList<>(taskManager.getActiveTasks()), completions);
                }
                break;
            case "atstatus":
                if (args.length == 1) {
                    StringUtil.copyPartialMatches(args[0], Arrays.asList("ticks", "time"), completions);
                }
                break;
        }

        Collections.sort(completions);
        return completions;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6Uso: /at <tarea> [tiempo_inicio] §7- Ejecuta una tarea");
        sender.sendMessage("§6Uso: /atstop <tarea> §7- Detiene una tarea en ejecución");
        sender.sendMessage("§6Uso: /atstatus <ticks|time> §7- Muestra tareas activas");
        sender.sendMessage("§6Uso: /atreload §7- Recarga las tareas");
        sendAvailableTasks(sender);
    }

    private void sendAvailableTasks(CommandSender sender) {
        Set<String> tasks = taskManager.getLoadedTasks();
        if (tasks.isEmpty()) {
            sender.sendMessage("§cNo hay tareas disponibles. Crea archivos YML en plugins/AutoTasks/autotasks/");
        } else {
            sender.sendMessage("§6Tareas disponibles: §e" + String.join(", ", tasks));
        }
    }

    private void sendActiveTasks(CommandSender sender) {
        Set<String> tasks = taskManager.getActiveTasks();
        if (tasks.isEmpty()) {
            sender.sendMessage("§cNo hay tareas en ejecución");
        } else {
            sender.sendMessage("§6Tareas en ejecución: §e" + String.join(", ", tasks));
        }
    }
}