package com.me.neonthethinker.autotask;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import com.me.neonthethinker.autotask.task.TaskManager;
import com.me.neonthethinker.autotask.utils.TimeUtils;
import com.me.neonthethinker.autotask.utils.YamlConfig;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

public class AutoTaskCommands implements TabExecutor {
    private final TaskManager taskManager;

    public AutoTaskCommands(TaskManager taskManager) {
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
        String targetWorld = null;

        if (args.length >= 2) {
            World world = Bukkit.getWorld(args[1]);
            if (world != null) {
                targetWorld = world.getName();
                if (args.length >= 3) {
                    startOffset = TimeUtils.parseTimeToTicks(args[2]);
                }
            } else {
                startOffset = TimeUtils.parseTimeToTicks(args[1]);
            }
        }

        if (!taskManager.executeTask(sender, taskName, startOffset, targetWorld)) {
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
                } else if (args.length == 2) {
                    List<String> worldNames = Bukkit.getWorlds().stream().map(World::getName).collect(Collectors.toList());
                    StringUtil.copyPartialMatches(args[1], worldNames, completions);
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
        sender.sendMessage("§6Uso: /at <tarea> [mundo] [tiempo_inicio] §7- Ejecuta una tarea");
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

    public boolean handleDynamicCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("autotasks.use")) {
            sender.sendMessage("§cNo tienes permiso para este comando.");
            return true;
        }

        List<String> inputParts = new ArrayList<>();
        inputParts.add(label.toLowerCase());
        for (String arg : args) {
            inputParts.add(arg.toLowerCase());
        }

        String matchedTask = null;
        boolean isStop = false;
        int maxMatchLength = -1;

        for (String taskName : taskManager.getLoadedTasks()) {
            YamlConfig config = taskManager.getTaskConfig(taskName);
            if (config != null) {
                String startPrefix = config.getString("prefix", null);
                if (startPrefix != null && !startPrefix.trim().isEmpty()) {
                    List<String> prefixParts = parsePrefixParts(startPrefix);
                    if (isPrefixOf(prefixParts, inputParts)) {
                        if (prefixParts.size() > maxMatchLength) {
                            maxMatchLength = prefixParts.size();
                            matchedTask = taskName;
                            isStop = false;
                        }
                    }
                }
            }

            String stopPrefix = taskManager.getStopPrefix(taskName);
            if (stopPrefix != null && !stopPrefix.trim().isEmpty()) {
                List<String> stopPrefixParts = parsePrefixParts(stopPrefix);
                if (isPrefixOf(stopPrefixParts, inputParts)) {
                    if (stopPrefixParts.size() > maxMatchLength) {
                        maxMatchLength = stopPrefixParts.size();
                        matchedTask = taskName;
                        isStop = true;
                    }
                }
            }
        }

        if (matchedTask != null) {
            if (isStop) {
                if (taskManager.stopTask(matchedTask, sender, true)) {
                    sender.sendMessage("§aTarea '" + matchedTask + "' detenida correctamente");
                } else {
                    sender.sendMessage("§cNo se encontró la tarea en ejecución: " + matchedTask);
                }
            } else {
                boolean success = taskManager.executeTask(sender, matchedTask, 0, null);
                if (!success) {
                    sender.sendMessage("§cNo se pudo iniciar la tarea: " + matchedTask);
                }
            }
            return true;
        }

        return false;
    }

    public List<String> handleDynamicTabComplete(CommandSender sender, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("autotasks.use")) {
            return completions;
        }

        List<String> inputParts = new ArrayList<>();
        inputParts.add(alias.toLowerCase());
        for (int i = 0; i < args.length - 1; i++) {
            inputParts.add(args[i].toLowerCase());
        }
        String lastArg = args[args.length - 1].toLowerCase();

        for (String taskName : taskManager.getLoadedTasks()) {
            YamlConfig config = taskManager.getTaskConfig(taskName);
            if (config != null) {
                String startPrefix = config.getString("prefix", null);
                if (startPrefix != null && !startPrefix.trim().isEmpty()) {
                    List<String> prefixParts = parsePrefixParts(startPrefix);
                    addNextWordSuggestion(prefixParts, inputParts, lastArg, completions);
                }
            }

            String stopPrefix = taskManager.getStopPrefix(taskName);
            if (stopPrefix != null && !stopPrefix.trim().isEmpty()) {
                List<String> stopPrefixParts = parsePrefixParts(stopPrefix);
                addNextWordSuggestion(stopPrefixParts, inputParts, lastArg, completions);
            }
        }

        List<String> uniqueCompletions = new ArrayList<>(new LinkedHashSet<>(completions));
        Collections.sort(uniqueCompletions);
        return uniqueCompletions;
    }

    private void addNextWordSuggestion(List<String> prefixParts, List<String> inputParts, String lastArg, List<String> completions) {
        if (prefixParts.size() > inputParts.size()) {
            boolean match = true;
            for (int i = 0; i < inputParts.size(); i++) {
                if (!prefixParts.get(i).equals(inputParts.get(i))) {
                    match = false;
                    break;
                }
            }
            if (match) {
                String suggestion = prefixParts.get(inputParts.size());
                if (suggestion.startsWith(lastArg)) {
                    completions.add(suggestion);
                }
            }
        }
    }

    private List<String> parsePrefixParts(String prefixStr) {
        List<String> parts = new ArrayList<>();
        for (String part : prefixStr.trim().split("\\s+")) {
            parts.add(part.toLowerCase());
        }
        return parts;
    }

    private boolean isPrefixOf(List<String> prefix, List<String> input) {
        if (prefix.size() > input.size()) return false;
        for (int i = 0; i < prefix.size(); i++) {
            if (!prefix.get(i).equals(input.get(i))) {
                return false;
            }
        }
        return true;
    }

    public void registerAllTaskPrefixes() {
        for (String taskName : taskManager.getLoadedTasks()) {
            YamlConfig config = taskManager.getTaskConfig(taskName);
            if (config != null) {
                String startPrefix = config.getString("prefix", null);
                if (startPrefix != null && !startPrefix.trim().isEmpty()) {
                    String[] parts = startPrefix.trim().split("\\s+");
                    if (parts.length > 0) {
                        registerDynamicRoot(parts[0].toLowerCase());
                    }
                }
                String stopPrefix = taskManager.getStopPrefix(taskName);
                if (stopPrefix != null && !stopPrefix.trim().isEmpty()) {
                    String[] parts = stopPrefix.trim().split("\\s+");
                    if (parts.length > 0) {
                        registerDynamicRoot(parts[0].toLowerCase());
                    }
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                player.updateCommands();
            } catch (Exception ignored) {
            }
        }
    }

    private void registerDynamicRoot(String rootLabel) {
        if (rootLabel.equals("at") || rootLabel.equals("atstop") || rootLabel.equals("atstatus") || rootLabel.equals("atreload")) {
            return;
        }
        try {
            Field commandMapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            CommandMap commandMap = (CommandMap) commandMapField.get(Bukkit.getServer());

            Command existing = commandMap.getCommand(rootLabel);
            if (existing == null) {
                DynamicRootCommand cmd = new DynamicRootCommand(rootLabel, this);
                commandMap.register("autotask", cmd);
            }
        } catch (Exception e) {
            taskManager.getPlugin().getLogger().severe("Error registering dynamic root command: " + rootLabel);
            e.printStackTrace();
        }
    }

    private static class DynamicRootCommand extends Command {
        private final AutoTaskCommands executor;

        protected DynamicRootCommand(String name, AutoTaskCommands executor) {
            super(name);
            this.executor = executor;
            this.setPermission("autotasks.use");
            this.setPermissionMessage("§cNo tienes permiso para este comando.");
        }

        @Override
        public boolean execute(CommandSender sender, String commandLabel, String[] args) {
            return executor.handleDynamicCommand(sender, commandLabel, args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
            return executor.handleDynamicTabComplete(sender, alias, args);
        }
    }
}