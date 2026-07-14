package me.neonthethinker.autotask;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import me.neonthethinker.autotask.utils.TimeUtils;
import net.minecraft.world.World;

import java.util.*;

public class AutoTaskCommands {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {

        dispatcher.register(CommandManager.literal("at")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> {
                sendUsage(context.getSource());
                return 1;
            })
            .then(CommandManager.argument("task", StringArgumentType.string())
                .suggests((context, builder) -> {
                    for (String taskName : Autotask.getInstance().getTaskManager().getLoadedTasks()) {
                        builder.suggest(taskName);
                    }
                    return builder.buildFuture();
                })
                .executes(context -> handleAt(context.getSource(), StringArgumentType.getString(context, "task"), null, null))
                .then(CommandManager.argument("world_or_time", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        for (RegistryKey<World> key : context.getSource().getServer().getWorldRegistryKeys()) {
                            builder.suggest(key.getValue().toString());
                            builder.suggest(key.getValue().getPath());
                        }
                        return builder.buildFuture();
                    })
                    .executes(context -> handleAt(context.getSource(), StringArgumentType.getString(context, "task"), StringArgumentType.getString(context, "world_or_time"), null))
                    .then(CommandManager.argument("start_time", StringArgumentType.string())
                        .executes(context -> handleAt(context.getSource(), StringArgumentType.getString(context, "task"), StringArgumentType.getString(context, "world_or_time"), StringArgumentType.getString(context, "start_time")))
                    )
                )
            )
        );

        dispatcher.register(CommandManager.literal("atstop")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> {
                context.getSource().sendFeedback(() -> Text.literal("§6Uso: /atstop <tarea> §7- Detiene una tarea en ejecución"), false);
                return 1;
            })
            .then(CommandManager.argument("task", StringArgumentType.string())
                .suggests((context, builder) -> {
                    for (String taskName : Autotask.getInstance().getTaskManager().getActiveTasks()) {
                        builder.suggest(taskName);
                    }
                    return builder.buildFuture();
                })
                .executes(context -> {
                    String taskName = StringArgumentType.getString(context, "task");
                    ServerCommandSource source = context.getSource();
                    if (Autotask.getInstance().getTaskManager().stopTask(taskName, source, true)) {
                        source.sendFeedback(() -> Text.literal("§aTarea '" + taskName + "' detenida correctamente"), true);
                    } else {
                        source.sendError(Text.literal("§cNo se encontró la tarea en ejecución: " + taskName));
                        sendActiveTasks(source);
                    }
                    return 1;
                })
            )
        );

        dispatcher.register(CommandManager.literal("atstatus")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> handleAtStatus(context.getSource(), false))
            .then(CommandManager.literal("ticks")
                .executes(context -> handleAtStatus(context.getSource(), true))
            )
            .then(CommandManager.literal("time")
                .executes(context -> handleAtStatus(context.getSource(), false))
            )
        );

        dispatcher.register(CommandManager.literal("atreload")
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> {
                Autotask.getInstance().getTaskManager().reloadTasks(context.getSource());
                return 1;
            })
        );
    }

    private static int handleAt(ServerCommandSource source, String taskName, String worldOrTime, String startTimeStr) {
        String targetWorld = null;
        long startOffset = 0;

        if (worldOrTime != null) {
            boolean isWorld = false;
            for (RegistryKey<net.minecraft.world.World> key : source.getServer().getWorldRegistryKeys()) {
                String name = key.getValue().toString();
                String path = key.getValue().getPath();
                if (name.equalsIgnoreCase(worldOrTime) || path.equalsIgnoreCase(worldOrTime)) {
                    targetWorld = name;
                    isWorld = true;
                    break;
                }
            }

            if (isWorld) {
                if (startTimeStr != null) {
                    startOffset = TimeUtils.parseTimeToTicks(startTimeStr);
                }
            } else {
                startOffset = TimeUtils.parseTimeToTicks(worldOrTime);
            }
        }

        boolean success = Autotask.getInstance().getTaskManager().executeTask(source, taskName, startOffset, targetWorld);
        if (!success) {
            source.sendError(Text.literal("§cTarea no encontrada: " + taskName));
            sendAvailableTasks(source);
        }
        return success ? 1 : 0;
    }

    private static int handleAtStatus(ServerCommandSource source, boolean useTicks) {
        Map<String, String> activeTasks = Autotask.getInstance().getTaskManager().getActiveTasksStatus(useTicks);
        if (activeTasks.isEmpty()) {
            source.sendError(Text.literal("§cNo hay tareas activas en este momento"));
            return 1;
        }

        source.sendFeedback(() -> Text.literal("§6---------Auto Task---------"), false);
        activeTasks.forEach((name, time) ->
                source.sendFeedback(() -> Text.literal("§7- §e" + name + " §7: §b" + time), false));
        source.sendFeedback(() -> Text.literal("§6---------------------------"), false);
        return 1;
    }

    private static void sendUsage(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("§6Uso: /at <tarea> [mundo] [tiempo_inicio] §7- Ejecuta una tarea"), false);
        source.sendFeedback(() -> Text.literal("§6Uso: /atstop <tarea> §7- Detiene una tarea en ejecución"), false);
        source.sendFeedback(() -> Text.literal("§6Uso: /atstatus <ticks|time> §7- Muestra tareas activas"), false);
        source.sendFeedback(() -> Text.literal("§6Uso: /atreload §7- Recarga las tareas"), false);
        sendAvailableTasks(source);
    }

    private static void sendAvailableTasks(ServerCommandSource source) {
        Set<String> tasks = Autotask.getInstance().getTaskManager().getLoadedTasks();
        if (tasks.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§cNo hay tareas disponibles. Crea archivos YML en config/AutoTask/autotasks/"), false);
        } else {
            source.sendFeedback(() -> Text.literal("§6Tareas disponibles: §e" + String.join(", ", tasks)), false);
        }
    }

    private static void sendActiveTasks(ServerCommandSource source) {
        Set<String> tasks = Autotask.getInstance().getTaskManager().getActiveTasks();
        if (tasks.isEmpty()) {
            source.sendFeedback(() -> Text.literal("§cNo hay tareas en ejecución"), false);
        } else {
            source.sendFeedback(() -> Text.literal("§6Tareas en ejecución: §e" + String.join(", ", tasks)), false);
        }
    }

    public static void registerAllTaskPrefixes(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher) {
        me.neonthethinker.autotask.task.TaskManager tm = Autotask.getInstance().getTaskManager();
        if (tm == null) return;
        for (String taskName : tm.getLoadedTasks()) {
            me.neonthethinker.autotask.utils.YamlConfig config = tm.getTaskConfig(taskName);
            if (config != null) {
                // Root prefix (start prefix)
                String startPrefix = config.getString("prefix", null);
                if (startPrefix != null) {
                    registerDynamicPrefix(dispatcher, startPrefix, false, taskName);
                }
                // Stop prefix
                String stopPrefix = tm.getStopPrefix(taskName);
                if (stopPrefix != null) {
                    registerDynamicPrefix(dispatcher, stopPrefix, true, taskName);
                }
            }
        }
    }

    public static void registerDynamicPrefix(com.mojang.brigadier.CommandDispatcher<ServerCommandSource> dispatcher, String prefixStr, boolean isStop, String taskName) {
        if (prefixStr == null || prefixStr.trim().isEmpty()) return;
        String[] parts = prefixStr.trim().split("\\s+");
        if (parts.length == 0) return;

        var leaf = CommandManager.literal(parts[parts.length - 1])
            .requires(source -> source.hasPermissionLevel(2))
            .executes(context -> {
                ServerCommandSource source = context.getSource();
                if (isStop) {
                    if (Autotask.getInstance().getTaskManager().stopTask(taskName, source, true)) {
                        source.sendFeedback(() -> Text.literal("§aTarea '" + taskName + "' detenida correctamente"), true);
                    } else {
                        source.sendError(Text.literal("§cNo se encontró la tarea en ejecución: " + taskName));
                    }
                } else {
                    boolean success = Autotask.getInstance().getTaskManager().executeTask(source, taskName, 0, null);
                    if (!success) {
                        source.sendError(Text.literal("§cNo se pudo iniciar la tarea: " + taskName));
                    }
                }
                return 1;
            });

        com.mojang.brigadier.builder.LiteralArgumentBuilder<ServerCommandSource> builder = leaf;
        for (int i = parts.length - 2; i >= 0; i--) {
            builder = CommandManager.literal(parts[i])
                .requires(source -> source.hasPermissionLevel(2))
                .then(builder);
        }

        dispatcher.register(builder);
    }
}
