package org.pitch440.app;

/** Persistable, one-use response window. Times use elapsedRealtime, not wall time. */
public final class ReminderResponseWindow {
    public static final long WINDOW_MS = 60_000;
    private long armedAt;
    private final int boot;
    public ReminderResponseWindow(long armedAt, int boot) {
        this.armedAt = armedAt;
        this.boot = boot;
    }
    public boolean isOpen(long now, int currentBoot, boolean eligible) {
        return eligible && armedAt >= 0 && boot >= 0 && boot == currentBoot &&
            now >= armedAt && now - armedAt < WINDOW_MS;
    }
    public boolean consume(long now, int currentBoot, boolean eligible) {
        boolean open = isOpen(now, currentBoot, eligible);
        armedAt = -1;
        return open;
    }
}
