package com.me.neonthethinker.autotask;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import com.me.neonthethinker.autotask.task.TaskManager;
import com.me.neonthethinker.autotask.utils.PlaceholderUtils;
import com.me.neonthethinker.autotask.utils.TickScheduler;
import com.me.neonthethinker.autotask.network.PluginMessageManager;

import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AutoTasks extends JavaPlugin {
    private File autotasksFolder;
    private AutoTaskCommands commandHandler;
    private TaskManager taskManager;
    private BukkitTask tickTask;
    private PluginMessageManager messageManager;

    private final Map<String, Set<String>> editingByFile = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PlaceholderUtils.init();

        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        this.autotasksFolder = new File(getDataFolder(), "autotasks");
        if (!autotasksFolder.exists()) {
            autotasksFolder.mkdirs();
        }

        saveDefaultTask("absoluto.yml");
        saveDefaultTask("incremental.yml");
        saveDefaultTask("ciclo.yml");

        PlaceholderUtils.init();
        
        this.taskManager = new TaskManager(this);
        this.commandHandler = new AutoTaskCommands(taskManager);
        
        registerCommands();

        this.tickTask = Bukkit.getScheduler().runTaskTimer(this, TickScheduler::tick, 1L, 1L);

        this.messageManager = new PluginMessageManager(this);
        registerPluginMessaging();

        taskManager.reloadTasks();

        if (PlaceholderUtils.isPapiEnabled()) {
            getLogger().info("AutoTask - PlaceholderAPI Activado");
        } else {
            getLogger().info("AutoTask - PlaceholderAPI Desactivado.");
        }

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
        
        if (this.tickTask != null) {
            this.tickTask.cancel();
            this.tickTask = null;
        }
        TickScheduler.cancelAll();

        unregisterPluginMessaging();
    }

    private void registerPluginMessaging() {
        getServer().getMessenger().registerOutgoingPluginChannel(this, "autotask_panel:response_files");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "autotask_panel:response_content");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "autotask_panel:response_executed_tasks");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "autotaskp:editing_files");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "autotaskp:file_sync");

        getServer().getMessenger().registerIncomingPluginChannel(this, "autotask_panel:request_files", messageManager);
        getServer().getMessenger().registerIncomingPluginChannel(this, "autotask_panel:request_content", messageManager);
        getServer().getMessenger().registerIncomingPluginChannel(this, "autotask_panel:save_file", messageManager);
        getServer().getMessenger().registerIncomingPluginChannel(this, "autotask_panel:request_executed_tasks", messageManager);
        getServer().getMessenger().registerIncomingPluginChannel(this, "autotask_panel:execute_task", messageManager);
        getServer().getMessenger().registerIncomingPluginChannel(this, "autotaskp:manage_file", messageManager);
        getServer().getMessenger().registerIncomingPluginChannel(this, "autotaskp:edit_state", messageManager);
    }

    private void unregisterPluginMessaging() {
        getServer().getMessenger().unregisterOutgoingPluginChannel(this);
        getServer().getMessenger().unregisterIncomingPluginChannel(this);
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

    public TaskManager getTaskManager() {
        return taskManager;
    }

    public AutoTaskCommands getCommandHandler() {
        return commandHandler;
    }

    public Map<String, Set<String>> getEditingByFile() {
        return editingByFile;
    }
}