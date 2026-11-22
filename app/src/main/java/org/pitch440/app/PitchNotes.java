package org.pitch440.app;

/** The twelve equal-tempered pitch classes, played in octave 4. */
public final class PitchNotes {
    private PitchNotes() {}

    public static final int A = 9;
    public static final int ALL_MASK = (1 << 12) - 1;
    public static final int DEFAULT_MASK = 1 << A;
    private static final String[] LABELS = {
        "C", "C♯ / D♭", "D", "D♯ / E♭", "E", "F",
        "F♯ / G♭", "G", "G♯ / A♭", "A", "A♯ / B♭", "B"
    };
    private static final String[] DISPLAY_LABELS = {
        "C4", "C♯4 / D♭4", "D4", "D♯4 / E♭4", "E4", "F4",
        "F♯4 / G♭4", "G4", "G♯4 / A♭4", "A4", "A♯4 / B♭4", "B4"
    };

    public static boolean valid(int pitch) { return pitch >= 0 && pitch < 12; }
    public static int normalizePitch(int pitch) { return valid(pitch) ? pitch : A; }

    /** Corrupt or empty preferences fall back to the original A-only practice. */
    public static int normalizeMask(int mask) {
        int selected = mask & ALL_MASK;
        return selected == 0 ? DEFAULT_MASK : selected;
    }

    public static String label(int pitch) { return LABELS[normalizePitch(pitch)]; }
    public static String displayLabel(int pitch) { return DISPLAY_LABELS[normalizePitch(pitch)]; }
    public static double frequency(int pitch) {
        return 440.0 * Math.pow(2.0, (normalizePitch(pitch) - A) / 12.0);
    }

    /** Uniform choice among enabled notes; random normally comes from Math.random(). */
    public static int choose(int mask, double random) {
        int selected = normalizeMask(mask);
        int count = Integer.bitCount(selected);
        // Treat an invalid random input as the first item and clamp 1 to the last.
        double bounded = Double.isNaN(random) ? 0.0 : Math.max(0.0, Math.min(1.0, random));
        int ordinal = Math.min(count - 1, (int) (bounded * count));
        for (int pitch = 0; pitch < 12; pitch++) {
            if ((selected & (1 << pitch)) != 0 && ordinal-- == 0) return pitch;
        }
        return A;
    }
}
