package org.pitch440.app;

/** Pure, monotonic-time state machine: two separated peaks, once per reminder. */
public final class ReminderShakeGate {
    public static final long WINDOW_MS = 60_000;
    private long start = -1, deadline = -1, first = -1, last = -1;
    private boolean high;
    private double threshold = 2.4;

    public void arm(long now, double thresholdG) {
        disarm();
        start = now + 1500; // Let the notification motor settle.
        deadline = now + WINDOW_MS;
        threshold = thresholdG;
    }
    public void disarm() {
        start = deadline = first = last = -1;
        high = false;
    }
    public boolean isArmed(long now) {
        return deadline >= 0 && now < deadline;
    }
    public boolean sample(long now, double magnitudeG) {
        if (!isArmed(now)) { disarm(); return false; }
        if (now < start || now <= last || !Double.isFinite(magnitudeG)) return false;
        last = now;
        if (first >= 0 && now - first > 1000) first = -1;
        if (magnitudeG < 1.4) { high = false; return false; }
        if (magnitudeG < threshold || high) return false;
        high = true;
        if (first >= 0 && now - first >= 180 && now - first <= 1000) {
            disarm();
            return true;
        }
        if (first < 0) first = now;
        return false;
    }
}
