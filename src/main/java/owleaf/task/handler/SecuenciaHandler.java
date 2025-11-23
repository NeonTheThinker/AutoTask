package owleaf.task.handler;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import owleaf.AutoTasks;
import owleaf.utils.TimeUtils;

import java.util.List;
import java.util.Map;


public class SecuenciaHandler {

    private final AutoTasks plugin;

    public SecuenciaHandler(AutoTasks plugin) {
        this.plugin = plugin;
    }

    public void programar(BukkitScheduler scheduler, List<Map<?, ?>> steps, long baseDelayTicks, List<BukkitTask> taskList) {

        for (Map<?, ?> step : steps) {
            long stepDelay = TimeUtils.parseTimeToTicks((String) step.get("delay"));
            Object comandosStep = step.get("comandos");
            long totalDelay = baseDelayTicks + stepDelay;

            if (comandosStep instanceof String) {
                String cmd = (String) comandosStep;

                taskList.add(scheduler.runTaskLater(plugin, () ->
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd),
                        totalDelay));
            } else if (comandosStep instanceof List) {
                for (Object cmd : (List<?>) comandosStep) {
                    if (cmd instanceof String) {

                        String cmdStr = (String) cmd;

                        taskList.add(scheduler.runTaskLater(plugin, () ->
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmdStr),
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