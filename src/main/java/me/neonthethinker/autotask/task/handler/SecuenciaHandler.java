package me.neonthethinker.autotask.task.handler;

import me.neonthethinker.autotask.task.TaskManager;
import me.neonthethinker.autotask.utils.FabricTask;
import me.neonthethinker.autotask.utils.TickScheduler;
import me.neonthethinker.autotask.utils.TimeUtils;

import java.util.List;
import java.util.Map;

public class SecuenciaHandler {

    public SecuenciaHandler() {
    }

    public void programar(List<Map<?, ?>> steps, long baseDelayTicks, List<FabricTask> taskList, String targetWorld, boolean usePapi, net.minecraft.server.network.ServerPlayerEntity triggerPlayer) {
        for (Map<?, ?> step : steps) {
            long stepDelay = TimeUtils.parseTimeToTicks((String) step.get("delay"));
            long totalDelay = baseDelayTicks + stepDelay;

            if (step.containsKey("comandos")) {
                programarPasoComandos(step.get("comandos"), totalDelay, taskList, targetWorld, usePapi, false, triggerPlayer);
            }
            if (step.containsKey("comandos_jugador")) {
                programarPasoComandos(step.get("comandos_jugador"), totalDelay, taskList, targetWorld, usePapi, true, triggerPlayer);
            }
        }
    }

    private void programarPasoComandos(Object comandosStep, long totalDelay, List<FabricTask> taskList, String targetWorld, boolean usePapi, boolean asPlayer, net.minecraft.server.network.ServerPlayerEntity triggerPlayer) {
        if (comandosStep instanceof String) {
            String cmd = (String) comandosStep;
            taskList.add(TickScheduler.runTaskLater(() ->
                            TaskManager.dispatchCommand(cmd, targetWorld, usePapi, asPlayer, triggerPlayer),
                    totalDelay));
        } else if (comandosStep instanceof List) {
            for (Object cmd : (List<?>) comandosStep) {
                if (cmd instanceof String) {
                    String cmdStr = (String) cmd;
                    taskList.add(TickScheduler.runTaskLater(() ->
                                    TaskManager.dispatchCommand(cmdStr, targetWorld, usePapi, asPlayer, triggerPlayer),
                            totalDelay));
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
