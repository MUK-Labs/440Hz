"""Generate the bundled, original A440 reference. Python standard library only."""
import math
import struct
import wave
from pathlib import Path
out = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/a440.wav'
rate = 44100
seconds = 3
with wave.open(str(out), 'wb') as wav:
    wav.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
    samples = []
    for n in range(rate * seconds):
        t = n / rate
        envelope = min(1, t / 0.012) * math.exp(-t / 1.15) * min(1, (seconds - t) / 0.08)
        samples.append(struct.pack('<h', round(0.65 * 32767 * envelope * math.sin(2 * math.pi * 440 * t))))
    wav.writeframes(b''.join(samples))
print(out)
