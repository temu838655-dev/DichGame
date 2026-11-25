"""Run on a supported Python host before porting the Nano graphs to Android."""
import argparse
import json
import pathlib
import time
import wave

def main():
    p = argparse.ArgumentParser()
    p.add_argument("--out", type=pathlib.Path, required=True)
    p.add_argument("--steps", type=int, choices=[8, 16], default=8)
    a = p.parse_args()
    from vieneu import Vieneu
    a.out.mkdir(parents=True, exist_ok=False)
    start = time.perf_counter()
    tts = Vieneu(mode="v3nano")
    load = time.perf_counter() - start
    voices = tts.list_preset_voices()
    results = []
    for index, (label, voice_id) in enumerate(voices):
        start = time.perf_counter()
        audio = tts.infer("Chúng ta phải rời khỏi nơi này ngay!", voice=voice_id, steps=a.steps, sway=-1)
        elapsed = time.perf_counter() - start
        path = a.out / f"voice-{index}.wav"
        tts.save(audio, str(path))
        with wave.open(str(path), "rb") as f:
            rate = f.getframerate()
            duration = f.getnframes() / rate
            if rate != 24000 or duration <= 0:
                raise ValueError("Unexpected Nano output")
        results.append(dict(label=label, voice=voice_id, seconds=elapsed, duration=duration, rtf=elapsed/duration))
    (a.out / "report.json").write_text(json.dumps(dict(load_seconds=load, steps=a.steps, voices=results), ensure_ascii=False, indent=2), encoding="utf-8")

if __name__ == "__main__":
    main()
