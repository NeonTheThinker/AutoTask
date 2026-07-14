package me.neonthethinker.autotask.utils;

import me.neonthethinker.autotask.Autotask;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TickScheduler {
    private static final ConcurrentLinkedQueue<FabricTask> pendingTasks = new ConcurrentLinkedQueue<>();
    private static final List<FabricTask> activeTasks = new ArrayList<>();

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    public static FabricTask runTaskLater(Runnable runnable, long delayTicks) {
        FabricTask task = new FabricTask(runnable, delayTicks);
        pendingTasks.add(task);
        return task;
    }

    private static void tick() {
        FabricTask pending;
        while ((pending = pendingTasks.poll()) != null) {
            if (!pending.isCancelled()) {
                activeTasks.add(pending);
            }
        }

        if (activeTasks.isEmpty()) return;

        Iterator<FabricTask> it = activeTasks.iterator();
        while (it.hasNext()) {
            FabricTask task = it.next();
            try {
                if (task.tick()) {
                    it.remove();
                }
            } catch (Exception e) {
                Autotask.LOGGER.error("Error ticking scheduled task", e);
                it.remove();
            }
        }
    }

    public static void cancelAll() {
        FabricTask pending;
        while ((pending = pendingTasks.poll()) != null) {
            pending.cancel();
        }
        for (FabricTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
