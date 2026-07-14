package me.neonthethinker.autotask.utils;

public class FabricTask {
    private final Runnable runnable;
    private long ticksRemaining;
    private volatile boolean cancelled = false;

    public FabricTask(Runnable runnable, long ticksRemaining) {
        this.runnable = runnable;
        this.ticksRemaining = ticksRemaining;
    }

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return this.cancelled;
    }

    public boolean tick() {
        if (cancelled) return true;
        ticksRemaining--;
        if (ticksRemaining <= 0) {
            runnable.run();
            return true;
        }
        return false;
    }
}
