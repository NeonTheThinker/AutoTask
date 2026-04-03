package com.neonthethinker.autotask.task.handler;

import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import com.neonthethinker.autotask.AutoTasks;
import com.neonthethinker.autotask.task.TaskManager;
import com.neonthethinker.autotask.utils.TimeUtils;

import java.util.List;
import java.util.Map;


public class SecuenciaHandler {

    private final AutoTasks plugin;

    public SecuenciaHandler(AutoTasks plugin) {
        this.plugin = plugin;
    }

    public void programar(BukkitScheduler scheduler, List<Map<?, ?>> steps, long baseDelayTicks, List<BukkitTask> taskList, String targetWorld) {

        for (Map<?, ?> step : steps) {
            long stepDelay = TimeUtils.parseTimeToTicks((String) step.get("delay"));
            Object comandosStep = step.get("comandos");
            long totalDelay = baseDelayTicks + stepDelay;

            if (comandosStep instanceof String) {
                String cmd = (String) comandosStep;

                taskList.add(scheduler.runTaskLater(plugin, () ->
                                TaskManager.dispatchCommand(cmd, targetWorld),
                        totalDelay));
            } else if (comandosStep instanceof List) {
                for (Object cmd : (List<?>) comandosStep) {
                    if (cmd instanceof String) {
                        String cmdStr = (String) cmd;

                        taskList.add(scheduler.runTaskLater(plugin, () ->
                                        TaskManager.dispatchCommand(cmdStr, targetWorld),
                                totalDelay));
                    }
                }
            }
        }
    }

    public long calcularMaxDelay(long baseDelayTicks, List<Map<?, ?>> steps) {
        long maxDelay = baseDelayTicks;
        for (Map<?, ?> step : steps) {
            long stepDelay = TimeUtils.parseTimeToTicks((String) step.get("delay"));
            long totalDelay = baseDelayTicks + stepDelay;
            if (totalDelay > maxDelay) {
                maxDelay = totalDelay;
            }
        }
        return maxDelay;
    }
}