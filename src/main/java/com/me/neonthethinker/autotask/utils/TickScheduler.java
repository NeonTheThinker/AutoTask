package com.me.neonthethinker.autotask.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TickScheduler {
    private static final ConcurrentLinkedQueue<PluginTask> pendingTasks = new ConcurrentLinkedQueue<>();
    private static final List<PluginTask> activeTasks = new ArrayList<>();

    public static PluginTask runTaskLater(Runnable runnable, long delayTicks) {
        PluginTask task = new PluginTask(runnable, delayTicks);
        pendingTasks.add(task);
        return task;
    }

    public static void tick() {
        PluginTask pending;
        while ((pending = pendingTasks.poll()) != null) {
            if (!pending.isCancelled()) {
                activeTasks.add(pending);
            }
        }

        if (activeTasks.isEmpty()) return;

        Iterator<PluginTask> it = activeTasks.iterator();
        while (it.hasNext()) {
            PluginTask task = it.next();
            try {
                if (task.tick()) {
                    it.remove();
                }
            } catch (Exception e) {
                e.printStackTrace();
                it.remove();
            }
        }
    }

    public static void cancelAll() {
        PluginTask pending;
        while ((pending = pendingTasks.poll()) != null) {
            pending.cancel();
        }
        for (PluginTask task : activeTasks) {
            task.cancel();
        }
        activeTasks.clear();
    }
}
