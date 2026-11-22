import org.pitch440.app.PitchNotes;

public class PitchChecks {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(PitchNotes.frequency(PitchNotes.A) == 440.0, "A4 must remain exactly 440 Hz");
        check(Math.abs(PitchNotes.frequency(0) - 261.6255653005986) < 1e-10, "C4 tuning");
        check(Math.abs(PitchNotes.frequency(11) - 493.8833012561241) < 1e-10, "B4 tuning");
        check(PitchNotes.normalizeMask(0) == PitchNotes.DEFAULT_MASK, "Empty pool defaults to A");
        check(PitchNotes.normalizeMask(1 << 20) == PitchNotes.DEFAULT_MASK, "Unknown bits default to A");
        check(PitchNotes.normalizeMask((1 << 2) | (1 << 20)) == (1 << 2), "Unknown bits stripped");
        check(PitchNotes.normalizePitch(-1) == PitchNotes.A && PitchNotes.normalizePitch(12) == PitchNotes.A,
            "Out-of-range pitches default to A");
        check(PitchNotes.label(1).contains("C♯") && PitchNotes.label(1).contains("D♭"), "Enharmonic names shown together");

        int pool = (1 << 0) | (1 << 4) | (1 << 9);
        int[] counts = new int[12];
        for (int i = 0; i < 1200; i++) {
            double random = (i + 0.5) / 1200.0;
            int selected = PitchNotes.choose(pool, random);
            check((pool & (1 << selected)) != 0, "No unselected pitch may be chosen");
            counts[selected]++;
            check(PitchNotes.choose(0, random) == PitchNotes.A, "Default remains A at every draw");
            for (int pitch = 0; pitch < 12; pitch++) {
                check(PitchNotes.choose(1 << pitch, random) == pitch, "A single-note pool remains stable");
            }
        }
        check(counts[0] == 400 && counts[4] == 400 && counts[9] == 400, "Uniform selection across enabled notes");
        check(PitchNotes.choose(pool, -1) == 0 && PitchNotes.choose(pool, Double.NaN) == 0, "Low/invalid random input clamped");
        check(PitchNotes.choose(pool, 1) == 9 && PitchNotes.choose(pool, Double.POSITIVE_INFINITY) == 9,
            "High random input clamped");
        for (int pitch = 0; pitch < 12; pitch++) {
            check(PitchNotes.choose(PitchNotes.ALL_MASK, (pitch + 0.5) / 12.0) == pitch, "All 12 pitches reachable");
            check(PitchNotes.displayLabel(pitch).contains("4"), "Reference octave is explicit");
            if (pitch > 0) check(Math.abs(PitchNotes.frequency(pitch) / PitchNotes.frequency(pitch - 1)
                - Math.pow(2.0, 1.0 / 12.0)) < 1e-12, "Adjacent notes are equal-tempered semitones");
        }
        System.out.println("Pitch checks passed");
    }
}
