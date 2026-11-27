"""Regenerate the original offline score and effects (requires Python and ffmpeg)."""
import math
import random
import subprocess
from array import array
from pathlib import Path

RATE = 22050
OUTPUT = Path(__file__).resolve().parents[1] / "app/src/main/res/raw"
OUTPUT.mkdir(parents=True, exist_ok=True)

def note(track, begin, duration, midi, amplitude, voice="bell", seed=0):
    frequency = 440 * 2 ** ((midi - 69) / 12)
    start = int(begin * RATE)
    rng = random.Random(seed)
    for n in range(min(int(duration * RATE), len(track) - start)):
        t = n / RATE
        angle = 2 * math.pi * frequency * t
        edge = min(1, t / .016, (duration - t) / .06)
        if voice == "soft":
            value = (math.sin(angle) + .24 * math.sin(2 * angle)) * math.sin(math.pi * t / duration)
        elif voice == "wood":
            value = (.82 * math.sin(angle * (1 - t)) + .18 * rng.uniform(-1, 1)) * math.exp(-18 * t)
        else:
            value = (.73 * math.sin(angle) + .19 * math.sin(2 * angle) + .08 * math.sin(3.02 * angle)) * math.exp(-3 * t)
        track[start + n] += amplitude * edge * value

def write(name, track):
    raw = array("h", (round(max(-1, min(1, s)) * 32767) for s in track))
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "s16le", "-ar", str(RATE), "-ac", "1",
                    "-i", "-", "-c:a", "libvorbis", "-q:a", "4", OUTPUT / (name + ".ogg")],
                   input=raw.tobytes(), check=True)

def score(name, chords, lead, pace):
    bars = len(chords)
    duration = bars * 4 * pace
    track = [0.0] * int(duration * RATE)
    for bar, chord in enumerate(chords):
        for i, tone in enumerate(chord):
            note(track, bar * 4 * pace, 3.9 * pace, tone - (12 if i == 0 else 0), .13 if i == 0 else .055, "soft")
        for beat, interval in enumerate((0, 1, 2, 1, 0, 1, 2, 1)):
            note(track, (bar * 4 + beat * .5) * pace, min(.68, (4 - beat * .5) * pace), chord[interval] + 12, .095)
        for beat, pitch in lead[bar].items():
            note(track, (bar * 4 + beat) * pace, min(1.05, (4 - beat) * pace), pitch, .16)
    write(name, track)

score("music_menu", [(60,64,67),(57,60,64),(53,57,60),(55,59,62),
                     (60,64,67),(57,60,64),(53,57,60),(55,59,62)],
      [{0:79,1:76,2.5:72},{0:81,1.5:79,3:76},{0:77,1.5:76,3:72},{0:74,2:71},
       {0:84,1.5:79,3:76},{0:81,1:84,2.5:79},{0:77,2:76,3:72},{0:74,1.5:79,3:71}], .51)
score("music_play", [(62,65,69),(58,62,65),(65,69,72),(60,64,67),
                     (62,65,69),(58,62,65),(67,70,74),(60,64,67)],
      [{1:77,3:81},{1:77,2.5:74},{0:81,2:84},{1:79,2.5:76},
       {0:81,3:77},{1:82,3:77},{0:86,2:82},{1:79,3:76}], .62)
effects = {
    "click": (74, 81), "ink": (76, 81, 85), "collision": (43, 39), "step": (41,),
    "portal": (63, 70, 75, 82), "win": (67, 72, 76, 79, 84),
    "fail": (70, 67, 62), "star": (85, 90),
    "finale": (60, 67, 72, 76, 79, 84, 88, 91)
}
for name, pitches in effects.items():
    spacing = .08 if name in ("portal", "win", "fail", "finale") else .032
    duration = .32 if name in ("portal", "win", "fail", "finale") else .13
    track = [0.0] * int((spacing * len(pitches) + duration) * RATE)
    for i, pitch in enumerate(pitches):
        note(track, spacing * i, duration, pitch, .48, "wood" if name in ("step", "collision") else "bell", i)
    write("fx_" + name, track)
