package com.me.neonthethinker.autotask;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import com.me.neonthethinker.autotask.task.TaskManager;

import java.io.File;

public class AutoTasks extends JavaPlugin {
    private File autotasksFolder;
    private AutoTaskCommands commandHandler;
    private TaskManager taskManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.autotasksFolder = new File(getDataFolder(), "autotasks");
        if (!autotasksFolder.exists()) {
            autotasksFolder.mkdirs();
        }

        saveDefaultTask("absoluto.yml");
        saveDefaultTask("incremental.yml");
        this.taskManager = new TaskManager(this);
        this.commandHandler = new AutoTaskCommands(taskManager);
        registerCommands();
        taskManager.reloadTasks();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            taskManager.reloadTasks();
        }, 20L);
    }

    private void saveDefaultTask(String resourceName) {
        File taskFile = new File(autotasksFolder, resourceName);
        if (!taskFile.exists()) {
            try {
                saveResource("autotasks/" + resourceName, false);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Override
    public void onDisable() {
        if (taskManager != null) {
        taskManager.cancelAllTasks();
        }
    }

    private void registerCommands() {
        registerCommand("at");
        registerCommand("atstop");
        registerCommand("atstatus");
        registerCommand("atreload");
    }

    private void registerCommand(String name) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(commandHandler);
            cmd.setTabCompleter(commandHandler);
        }
    }

    public File getAutotasksFolder() {
        return autotasksFolder;
    }
}