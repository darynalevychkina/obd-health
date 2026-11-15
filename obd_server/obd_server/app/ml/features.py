"""Перетворює «сирі» показники однієї поїздки на кілька чисел-ознак."""
from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np

# порядок ознак для моделі машинного навчання
FEATURE_NAMES = [
    "coolant_max", "coolant_steady", "warmup_s", "ltft_mean", "ltft_abs_max",
    "stft_std", "battery_mean", "battery_min", "idle_rpm", "load_mean", "maf_per_load",
]


@dataclass
class Reading:
    t: float                       # секунд від початку поїздки
    rpm: float | None = None
    speed: float | None = None
    coolant: float | None = None
    load: float | None = None
    maf: float | None = None
    stft: float | None = None
    ltft: float | None = None
    battery: float | None = None


def _vals(rows: list[Reading], attr: str, cond=None) -> np.ndarray:
    out = [getattr(r, attr) for r in rows if getattr(r, attr) is not None and (cond is None or cond(r))]
    return np.asarray(out, dtype=float)


def _nan(x: float | None) -> float:
    return float("nan") if x is None else float(x)


def extract(rows: list[Reading]) -> dict[str, float]:
    """Повертає словник ознак. Якщо даних для ознаки немає, значення NaN."""
    f = {k: float("nan") for k in FEATURE_NAMES}
    if not rows:
        return f
    rows = sorted(rows, key=lambda r: r.t)
    running = [r for r in rows if (r.rpm or 0) > 500]

    cool = _vals(rows, "coolant")
    if cool.size:
        f["coolant_max"] = float(cool.max())
        # час прогріву до 70 °C (лише якщо двигун стартував холодним)
        first = next((r for r in rows if r.coolant is not None), None)
        if first is not None and first.coolant < 60:
            hit = next((r for r in rows if r.coolant is not None and r.coolant >= 70), None)
            f["warmup_s"] = (hit.t - first.t) if hit else float(rows[-1].t - first.t)
        # усталена температура: після першого досягнення 80 °C
        hot = next((r for r in rows if r.coolant is not None and r.coolant >= 80), None)
        if hot is not None:
            steady = _vals(rows, "coolant", lambda r: r.t >= hot.t)
        else:
            steady = np.asarray([])
        # якщо двигун прогрітий із самого початку, усталену температуру беремо з усієї поїздки
        if first is not None and first.coolant >= 80:
            steady = cool
        # двигун так і не прогрівся до 80 °C за довгу поїздку: беремо хвіст поїздки
        if hot is None and cool.size >= 300:
            steady = cool[-100:]
        if steady.size >= 30:
            f["coolant_steady"] = float(steady.mean())

    lt = _vals(running, "ltft")
    if lt.size:
        f["ltft_mean"] = float(lt.mean())
        f["ltft_abs_max"] = float(np.abs(lt).max())
    st = _vals(running, "stft")
    if st.size >= 10:
        f["stft_std"] = float(st.std())
    bat = _vals(running, "battery")
    if bat.size:
        f["battery_mean"] = float(bat.mean())
        f["battery_min"] = float(bat.min())
    idle = _vals(rows, "rpm", lambda r: (r.speed == 0) and (r.coolant is None or r.coolant > 70) and (r.rpm or 0) > 0)
    if idle.size >= 12:
        f["idle_rpm"] = float(idle.mean())
    ld = _vals(running, "load")
    if ld.size:
        f["load_mean"] = float(ld.mean())
    maf = _vals(running, "maf")
    if maf.size and ld.size and ld.mean() > 1:
        f["maf_per_load"] = float(maf.mean() / ld.mean())
    return f


def to_vector(f: dict[str, float]) -> np.ndarray:
    return np.asarray([f.get(k, float("nan")) for k in FEATURE_NAMES], dtype=float)


def is_nan(x) -> bool:
    return x is None or (isinstance(x, float) and math.isnan(x))
