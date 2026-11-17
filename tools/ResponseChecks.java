import org.pitch440.app.ReminderResponseWindow;

public class ResponseChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        ReminderResponseWindow gate = new ReminderResponseWindow(-1, 10);
        check(!gate.consume(1000, 10, true), "No reminder: silent");
        gate = new ReminderResponseWindow(1000, 10);
        check(gate.isOpen(60999, 10, true), "Valid just before 60 seconds");
        check(!gate.consume(61000, 10, true), "Expired at exactly 60 seconds");
        gate = new ReminderResponseWindow(1000, 10);
        check(gate.consume(2000, 10, true), "First response plays");
        check(!gate.consume(2001, 10, true), "Second tap or shake stays silent");
        gate = new ReminderResponseWindow(1000, 10);
        check(!gate.consume(2000, 10, false), "Disabled, expired session or blocked response stays silent");
        check(!gate.consume(2001, 10, true), "Rejected response does not resurrect");
        gate = new ReminderResponseWindow(1000, 10);
        check(!gate.isOpen(999, 10, true), "Clock before arming is invalid");
        check(!gate.isOpen(2000, 11, true), "Reboot invalidates even overlapping elapsed time");
        check(!new ReminderResponseWindow(1000, -1).isOpen(2000, -1, true), "Unknown boot fails closed");
        // The persisted pair reconstructs the SAME deadline, not a fresh window.
        gate = new ReminderResponseWindow(1000, 10);
        check(gate.isOpen(60000, 10, true), "Process recreation preserves live deadline");
        check(!gate.isOpen(62000, 10, true), "Process recreation does not extend deadline");
        check(!new ReminderResponseWindow(-1, -1).isOpen(3000, 10, true), "Manual playback clears persisted token");
        System.out.println("Reminder response window checks passed");
    }
}
