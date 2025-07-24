package owleaf;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class AutoTasks extends JavaPlugin {
    private File autotasksFolder;
    private CommandHandler commandHandler;
    private FileConfiguration config;
    private TaskManager taskManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.taskManager = new TaskManager(this);

        this.autotasksFolder = new File(getDataFolder(), "autotasks");
        if (!autotasksFolder.exists()) {
            autotasksFolder.mkdirs();
        }

        this.commandHandler = new CommandHandler(this, taskManager);
        registerCommands();
        taskManager.reloadTasks();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            new Test(this).createTestFile();
            taskManager.reloadTasks();
        }, 20L);
    }


    @Override
    public void onDisable() {
        taskManager.cancelAllTasks();
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
            cmd.setPermissionMessage("§cNo tienes permiso para este comando");
        }
    }

    public File getAutotasksFolder() {
        return autotasksFolder;
    }

    public TaskManager getTaskManager() {
        return taskManager;
    }
}