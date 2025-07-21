package owleaf;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;

public class AutoTasks extends JavaPlugin {
    private File autotasksFolder;
    private CommandHandler commandHandler;

    @Override
    public void onEnable() {
        this.autotasksFolder = new File(getDataFolder(), "autotasks");
        if (!autotasksFolder.exists()) {
            autotasksFolder.mkdirs();
        }

        this.commandHandler = new CommandHandler(this);

        registerCommands();

        commandHandler.reloadTasks();

        Bukkit.getScheduler().runTaskLater(this, () -> {
            new Test(this).createTestFile();
            commandHandler.reloadTasks();
        }, 20L);
    }

    private void registerCommands() {
        PluginCommand atCommand = getCommand("at");
        if (atCommand != null) {
            atCommand.setExecutor(commandHandler);
            atCommand.setTabCompleter(commandHandler);
        }

        PluginCommand atReloadCommand = getCommand("atreload");
        if (atReloadCommand != null) {
            atReloadCommand.setExecutor((sender, command, label, args) -> {
                if (!sender.hasPermission("autotasks.reload")) {
                    sender.sendMessage("§cNo tienes permiso para esto.");
                    return true;
                }
                commandHandler.reloadTasks();
                sender.sendMessage("§aTareas recargadas correctamente!");
                return true;
            });
        }
    }

    public File getAutotasksFolder() {
        return autotasksFolder;
    }
}