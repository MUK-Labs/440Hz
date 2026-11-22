"""Generate the twelve bundled octave-4 reference tones using the standard library.

A4 retains the original a440.wav filename, waveform, envelope and exact bytes.
All notes use the same three-second sound, calibrated to equal temperament A4=440.
"""
import io
import math
from pathlib import Path
import struct
import wave

OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
NAMES = ("c4", "cs4", "d4", "ds4", "e4", "f4", "fs4", "g4", "gs4", "a440", "as4", "b4")
RATE = 44100
SECONDS = 3


def tone_bytes(frequency: float) -> bytes:
    output = io.BytesIO()
    with wave.open(output, "wb") as wav:
        wav.setparams((1, 2, RATE, 0, "NONE", "not compressed"))
        samples = []
        for n in range(RATE * SECONDS):
            t = n / RATE
            envelope = min(1, t / 0.012) * math.exp(-t / 1.15) * min(1, (SECONDS - t) / 0.08)
            samples.append(struct.pack("<h", round(0.65 * 32767 * envelope * math.sin(2 * math.pi * frequency * t))))
        wav.writeframes(b"".join(samples))
    return output.getvalue()


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    for pitch, name in enumerate(NAMES):
        data = tone_bytes(440.0 * 2 ** ((pitch - 9) / 12))
        path = OUT / f"{name}.wav"
        if name == "a440" and path.exists():
            if path.read_bytes() != data:
                raise ValueError("The original A440 reference differs; refusing to replace it")
        else:
            path.write_bytes(data)
        print(path)
