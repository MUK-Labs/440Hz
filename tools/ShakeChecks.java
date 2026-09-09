import org.pitch440.app.ReminderShakeGate;

public class ShakeChecks {
    static int checks;
    static void expect(boolean value, String name) {
        checks++;
        if (!value) throw new AssertionError(name);
    }
    static ReminderShakeGate armed() {
        ReminderShakeGate g = new ReminderShakeGate();
        g.arm(1000, 2.4);
        return g;
    }
    static boolean pair(ReminderShakeGate g, long t) {
        g.sample(t, 3.0);
        g.sample(t + 100, 1.0);
        return g.sample(t + 300, 3.0);
    }
    public static void main(String[] args) {
        ReminderShakeGate g = new ReminderShakeGate();
        expect(!pair(g, 3000), "never plays before a reminder");
        g = armed();
        expect(!pair(g, 1100), "notification vibration ignored during grace");
        expect(pair(g, 3000), "deliberate double shake triggers");
        expect(!pair(g, 4000), "one playback per reminder");
        g.arm(10000, 2.4);
        expect(pair(g, 12000), "next reminder rearms");
        g = armed();
        for (long t=2500; t<8000; t+=20) expect(!g.sample(t, 1.0 + 0.3*Math.sin(t)),
            "ordinary low-amplitude movement stays silent");
        expect(!g.sample(9000, 3), "single impact stays silent");
        for (long t=9020; t<10000; t+=20) expect(!g.sample(t, 3), "sustained acceleration is one peak");
        g = armed();
        g.sample(3000, 3); g.sample(3050, 1);
        expect(!g.sample(3100, 3), "rapid motor chatter does not trigger");
        g = armed();
        g.sample(3000, 3); g.sample(3100, 1);
        expect(!g.sample(4200, 3), "widely separated motions do not trigger");
        g = armed();
        expect(!pair(g, 61000), "expired window stays silent");
        g = armed();
        g.sample(60800, 3); g.sample(60900, 1);
        expect(!g.sample(61000, 3), "exact 60-second boundary expires");
        g = armed(); g.sample(3000, 3); g.disarm();
        expect(!pair(g, 4000), "cancel or manual playback clears window");
        g = armed(); g.sample(3000, 3); g.sample(3200, 1);
        expect(!g.sample(3100, 3), "out-of-order sample rejected");
        expect(!g.sample(3300, Double.NaN), "invalid reading rejected");
        g = new ReminderShakeGate(); g.arm(1000, 3.0);
        g.sample(3000, 2.5); g.sample(3100, 1);
        expect(!g.sample(3300, 2.5), "firm sensitivity requires stronger movement");
        g = new ReminderShakeGate(); g.arm(1000, 1.8);
        g.sample(3000, 2.0); g.sample(3100, 1);
        expect(g.sample(3300, 2.0), "gentle sensitivity accepts smaller gesture");
        System.out.println("Shake checks passed: " + checks);
    }
}
