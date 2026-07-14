package com.me.neonthethinker.autotask.task.handler;

import com.me.neonthethinker.autotask.utils.PluginTask;
import com.me.neonthethinker.autotask.utils.TickScheduler;
import com.me.neonthethinker.autotask.utils.TimeUtils;
import com.me.neonthethinker.autotask.task.TaskManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SecuenciaHandler {

    public SecuenciaHandler() {
    }

    public void programar(List<Map<?, ?>> steps, long baseDelayTicks, List<PluginTask> taskList, String targetWorld, boolean usePapi, UUID triggerPlayerUuid) {
        for (Map<?, ?> step : steps) {
            long stepDelay = TimeUtils.parseTimeToTicks((String) step.get("delay"));
            long totalDelay = baseDelayTicks + stepDelay;

            if (step.containsKey("comandos")) {
                programarPasoComandos(step.get("comandos"), totalDelay, taskList, targetWorld, usePapi, false, triggerPlayerUuid);
            }
            if (step.containsKey("comandos_jugador")) {
                programarPasoComandos(step.get("comandos_jugador"), totalDelay, taskList, targetWorld, usePapi, true, triggerPlayerUuid);
            }
        }
    }

    private void programarPasoComandos(Object comandosStep, long totalDelay, List<PluginTask> taskList, String targetWorld, boolean usePapi, boolean asPlayer, UUID triggerPlayerUuid) {
        if (comandosStep instanceof String) {
            String cmd = (String) comandosStep;
            taskList.add(TickScheduler.runTaskLater(() ->
                            TaskManager.dispatchCommand(cmd, targetWorld, usePapi, asPlayer, triggerPlayerUuid),
                    totalDelay));
        } else if (comandosStep instanceof List) {
            for (Object cmd : (List<?>) comandosStep) {
                if (cmd instanceof String) {
                    String cmdStr = (String) cmd;
                    taskList.add(TickScheduler.runTaskLater(() ->
                                    TaskManager.dispatchCommand(cmdStr, targetWorld, usePapi, asPlayer, triggerPlayerUuid),
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