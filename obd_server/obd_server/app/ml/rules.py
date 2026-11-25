"""Жорсткі правила: перевірка ознак поїздки за довідковими нормами."""
from __future__ import annotations

from dataclasses import dataclass

from .features import is_nan


@dataclass
class Finding:
    code: str            # машинний код (event_type у БД)
    severity: str        # INFO / WARNING / CRITICAL
    message: str         # деталі: значення і межа
    value: float | None = None
    limit: float | None = None


# Довідкова норма за замовчуванням (бензин, дизель, гібрид, газ).
# Для окремих марок сюди можна додати власні значення.
NORMS = {
    "coolant_warn": 105.0, "coolant_crit": 112.0,
    "warmup_warn_s": 900.0,
    "steady_low": 75.0,
    "ltft_warn": 7.0, "ltft_crit": 10.0,
    "stft_std_warn": 5.0,
    "battery_low_warn": 13.0, "battery_low_crit": 12.4,
    "battery_high_warn": 15.0, "battery_high_crit": 15.5,
    "idle_low": 550.0, "idle_high": 1100.0,
}


def evaluate(f: dict[str, float], fuel: str = "PETROL", norms: dict[str, float] | None = None) -> list[Finding]:
    n = {**NORMS, **(norms or {})}
    out: list[Finding] = []
    if fuel == "ELECTRIC":
        return out

    v = f.get("coolant_max")
    if not is_nan(v):
        if v > n["coolant_crit"]:
            out.append(Finding("COOLANT_HIGH", "CRITICAL", f"max {v:.0f} °C (limit {n['coolant_crit']:.0f})", v, n["coolant_crit"]))
        elif v > n["coolant_warn"]:
            out.append(Finding("COOLANT_HIGH", "WARNING", f"max {v:.0f} °C (limit {n['coolant_warn']:.0f})", v, n["coolant_warn"]))

    v = f.get("warmup_s")
    if not is_nan(v) and v > n["warmup_warn_s"]:
        out.append(Finding("WARMUP_SLOW", "WARNING", f"{v / 60:.0f} min to 70 °C", v, n["warmup_warn_s"]))

    v = f.get("coolant_steady")
    if not is_nan(v) and v < n["steady_low"]:
        out.append(Finding("COOLANT_LOW", "WARNING", f"steady {v:.0f} °C (expected 80-100)", v, n["steady_low"]))

    v = f.get("ltft_abs_max")
    if not is_nan(v):
        if v > n["ltft_crit"]:
            out.append(Finding("LTFT_HIGH", "CRITICAL", f"LTFT {v:.1f} % (limit {n['ltft_crit']:.0f})", v, n["ltft_crit"]))
        elif v > n["ltft_warn"]:
            out.append(Finding("LTFT_HIGH", "WARNING", f"LTFT {v:.1f} % (limit {n['ltft_warn']:.0f})", v, n["ltft_warn"]))

    v = f.get("stft_std")
    if not is_nan(v) and v > n["stft_std_warn"]:
        out.append(Finding("STFT_UNSTABLE", "WARNING", f"STFT spread {v:.1f} % (limit {n['stft_std_warn']:.0f})", v, n["stft_std_warn"]))

    v = f.get("battery_mean")
    if not is_nan(v):
        if v < n["battery_low_crit"]:
            out.append(Finding("BATTERY_LOW", "CRITICAL", f"{v:.1f} V (limit {n['battery_low_crit']:.1f})", v, n["battery_low_crit"]))
        elif v < n["battery_low_warn"]:
            out.append(Finding("BATTERY_LOW", "WARNING", f"{v:.1f} V (limit {n['battery_low_warn']:.1f})", v, n["battery_low_warn"]))
        elif v > n["battery_high_crit"]:
            out.append(Finding("BATTERY_HIGH", "CRITICAL", f"{v:.1f} V (limit {n['battery_high_crit']:.1f})", v, n["battery_high_crit"]))
        elif v > n["battery_high_warn"]:
            out.append(Finding("BATTERY_HIGH", "WARNING", f"{v:.1f} V (limit {n['battery_high_warn']:.1f})", v, n["battery_high_warn"]))

    v = f.get("idle_rpm")
    if not is_nan(v):
        if v > n["idle_high"]:
            out.append(Finding("IDLE_RPM", "WARNING", f"idle {v:.0f} rpm (limit {n['idle_high']:.0f})", v, n["idle_high"]))
        elif v < n["idle_low"]:
            out.append(Finding("IDLE_RPM", "WARNING", f"idle {v:.0f} rpm (min {n['idle_low']:.0f})", v, n["idle_low"]))
    return out
