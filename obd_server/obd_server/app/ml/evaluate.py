"""Оцінка якості виявлення аномалій на синтетичних даних (для розділу курсової).

Запуск з папки сервера:   python -m app.ml.evaluate
Для кожного «авто» генеруємо кілька нормальних поїздок (профіль) і набір тестових поїздок:
частина нормальних, частина з уведеною несправністю. Порівнюємо лише правила з правилами + ML.
"""
from __future__ import annotations

import math
import random

from .features import Reading, extract
from .rules import evaluate as rules_eval
from .model import Profile

# несправність -> опис. «subtle» = відхилення від норми авто в межах жорстких порогів
FAULTS = {
    "overheat": False,
    "thermostat_open": False,
    "lean_drift": False,
    "weak_battery": False,
    "overcharge": False,
    "noisy_stft": False,
    "high_idle": False,
    "subtle_ltft": True,
    "subtle_coolant": True,
    "subtle_battery": True,
}


def gen_trip(rng: random.Random, car: dict, fault: str | None = None, n: int = 420) -> list[Reading]:
    rows = []
    phase = rng.uniform(0, 6)
    for i in range(n):
        speed = max(0, round(car["cruise"] + 38 * math.sin((i + phase * 10) / 45) + rng.uniform(-3, 3)))
        if i % 97 < 8:
            speed = 0                                            # зупинки на світлофорах
        cool = min(car["plateau"], 25 + i * car["warm_rate"] + rng.uniform(-0.6, 0.6))
        ltft = car["ltft"] + rng.uniform(-0.3, 0.3)
        stft = rng.uniform(-2.5, 2.5)
        bat = car["battery"] + rng.uniform(-0.2, 0.2)
        idle = car["idle"] + rng.uniform(-30, 30)
        rpm = idle if speed == 0 else 780 + speed * 27 + rng.uniform(-40, 40)
        load = 18 + speed * 0.42 + rng.uniform(-2, 2)
        maf = 3.2 + speed * 0.23 + rng.uniform(-0.4, 0.4)

        if fault == "overheat":
            cool = min(121, cool + max(0, i - 200) * 0.14)
        elif fault == "thermostat_open":
            cool = min(68 + rng.uniform(-1, 1), cool)
        elif fault == "lean_drift":
            ltft = car["ltft"] + 9 + i * 0.006
        elif fault == "weak_battery":
            bat = 12.2 + rng.uniform(-0.2, 0.2)
        elif fault == "overcharge":
            bat = 15.3 + rng.uniform(-0.1, 0.1)
        elif fault == "noisy_stft":
            stft = rng.uniform(-11, 11)
        elif fault == "high_idle":
            if speed == 0:
                rpm = 1350 + rng.uniform(-40, 40)
        elif fault == "subtle_ltft":
            ltft = car["ltft"] + 4.5 + rng.uniform(-0.3, 0.3)
        elif fault == "subtle_coolant":
            cool = min(car["plateau"] + 9, 25 + i * car["warm_rate"] + rng.uniform(-0.6, 0.6))
        elif fault == "subtle_battery":
            bat = car["battery"] - 0.8 + rng.uniform(-0.2, 0.2)

        rows.append(Reading(t=i, rpm=rpm, speed=speed, coolant=cool, load=load, maf=maf,
                            stft=stft, ltft=ltft, battery=bat))
    return rows


def new_car(rng: random.Random) -> dict:
    return {
        "cruise": rng.uniform(40, 55),
        "plateau": rng.uniform(86, 95),
        "warm_rate": rng.uniform(0.22, 0.32),
        "ltft": rng.uniform(-2.0, 2.5),
        "battery": rng.uniform(13.8, 14.4),
        "idle": rng.uniform(720, 880),
    }


def run(cars: int = 60, seed: int = 7) -> dict:
    rng = random.Random(seed)
    stats = {"rules": dict(tp=0, fp=0, tn=0, fn=0), "full": dict(tp=0, fp=0, tn=0, fn=0)}
    per_fault: dict[str, list[int]] = {k: [0, 0, 0] for k in FAULTS}   # [rules, full, total]
    for _ in range(cars):
        car = new_car(rng)
        hist = [extract(gen_trip(rng, car)) for _ in range(rng.randint(3, 5))]
        profile = Profile(hist, seed=rng.randint(0, 10_000))
        tests = [(None, gen_trip(rng, car)) for _ in range(len(FAULTS))]
        tests += [(k, gen_trip(rng, car, k)) for k in FAULTS]
        for fault, rows in tests:
            f = extract(rows)
            rule_hit = any(x.severity != "INFO" for x in rules_eval(f))
            ml, _ = profile.score(f)
            full_hit = rule_hit or ml is not None
            for name, hit in (("rules", rule_hit), ("full", full_hit)):
                s = stats[name]
                if fault is None:
                    s["fp" if hit else "tn"] += 1
                else:
                    s["tp" if hit else "fn"] += 1
            if fault:
                per_fault[fault][0] += rule_hit
                per_fault[fault][1] += full_hit
                per_fault[fault][2] += 1
    out = {}
    for name, s in stats.items():
        p = s["tp"] / (s["tp"] + s["fp"]) if s["tp"] + s["fp"] else 0.0
        r = s["tp"] / (s["tp"] + s["fn"]) if s["tp"] + s["fn"] else 0.0
        f1 = 2 * p * r / (p + r) if p + r else 0.0
        out[name] = {**s, "precision": p, "recall": r, "f1": f1}
    out["per_fault"] = per_fault
    return out


if __name__ == "__main__":
    res = run()
    for name in ("rules", "full"):
        r = res[name]
        label = "Лише правила" if name == "rules" else "Правила + Isolation Forest"
        print(f"{label}: TP={r['tp']} FP={r['fp']} TN={r['tn']} FN={r['fn']}  "
              f"precision={r['precision']:.3f} recall={r['recall']:.3f} F1={r['f1']:.3f}")
    print("\nВиявлено за типами несправностей (правила / правила+ML з усіх):")
    for k, (a, b, t) in res["per_fault"].items():
        print(f"  {k:16s} {a:3d} / {b:3d} з {t}")
