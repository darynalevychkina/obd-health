"""Об'єднує правила й ML в один індекс здоров'я 0..100.

Індекс плавний: кожна ознака поїздки дає штраф, який росте разом із відхиленням від норми
(а не лише коли перетнуто межу), тому оцінки різні: 97, 91, 84, 62 тощо.
"""
from __future__ import annotations

from .features import extract, is_nan
from .model import Profile
from .rules import Finding, evaluate

MIN_READINGS_FOR_SCORE = 30
CAP_CRITICAL = 60      # є критична знахідка: оцінка не вища
CAP_WARNING = 88       # є попередження: оцінка не вища

# Кусково-лінійні штрафи: список точок (значення ознаки, штраф). Між точками штраф лінійний.
CURVES_UP = {          # чим більше значення, тим гірше
    "coolant_max": [(92, 0), (105, 8), (112, 20), (122, 35)],
    "warmup_s": [(300, 0), (900, 6), (1800, 14)],
    "ltft_abs_max": [(3, 0), (7, 8), (10, 20), (20, 35)],
    "stft_std": [(2, 0), (5, 8), (10, 15)],
}
CURVES_DOWN = {        # чим менше значення, тим гірше
    "coolant_steady": [(88, 0), (75, 8), (60, 16)],
    "battery_mean": [(13.7, 0), (13.0, 6), (12.4, 20), (11.5, 35)],
}
BATTERY_HIGH = [(14.5, 0), (15.0, 4), (15.5, 15), (16.5, 25)]
IDLE_LOW = [(700, 0), (550, 8), (400, 15)]
IDLE_HIGH = [(900, 0), (1100, 8), (1500, 15)]
ML_FLAGGED = 12
ML_SOFT_MAX = 6


def _ramp(v: float, pts: list[tuple[float, float]]) -> float:
    """Штраф за значенням v: точки впорядковані від «норми» (штраф 0) до гіршого."""
    x0, p0 = pts[0]
    ascending = pts[-1][0] > x0
    if (ascending and v <= x0) or (not ascending and v >= x0):
        return 0.0
    for (xa, pa), (xb, pb) in zip(pts, pts[1:]):
        inside = (xa <= v <= xb) if ascending else (xb <= v <= xa)
        if inside:
            return pa + (pb - pa) * (v - xa) / (xb - xa)
    return float(pts[-1][1])


def penalty_from_features(f: dict[str, float], norms: dict[str, float] | None = None) -> float:
    total = 0.0
    for k, pts in CURVES_UP.items():
        v = f.get(k)
        if not is_nan(v):
            total += _ramp(float(v), pts)
    for k, pts in CURVES_DOWN.items():
        v = f.get(k)
        if not is_nan(v):
            total += _ramp(float(v), pts)
    v = f.get("battery_mean")
    if not is_nan(v):
        total += _ramp(float(v), BATTERY_HIGH)
    v = f.get("idle_rpm")
    if not is_nan(v):
        lo, hi = IDLE_LOW, IDLE_HIGH
        if norms and "idle_low" in norms and "idle_high" in norms:
            # криві холостих ходу зсуваються під довідкову норму класу авто
            a, b = norms["idle_low"], norms["idle_high"]
            lo = [(a + 150, 0), (a, 8), (a - 150, 15)]
            hi = [(b - 200, 0), (b, 8), (b + 400, 15)]
        total += max(_ramp(float(v), lo), _ramp(float(v), hi))
    return total


def drift_penalty(profile: Profile, feats: dict[str, float]) -> float:
    """Наскільки поїздка відрізняється від звичної для цього авто (сума перевищень |z| над 1)."""
    import numpy as np
    from .features import to_vector
    x = to_vector(feats)
    x = np.where(np.isnan(x), profile.med, x)
    z = (x - profile.mean) / profile.spread
    return float(np.maximum(np.abs(z) - 1.0, 0.0).sum())


def health_index(findings: list[Finding], feats: dict[str, float] | None = None,
                 ml_drift: float = 0.0, norms: dict[str, float] | None = None) -> int:
    penalty = penalty_from_features(feats, norms) if feats else 0.0
    ml_flagged = any(f.code == "ANOMALY_ML" for f in findings)
    if ml_flagged:
        penalty += ML_FLAGGED
    else:
        # незначні відхилення від власного профілю авто трохи знижують оцінку
        penalty += min(ML_SOFT_MAX, ml_drift)
    score = 100.0 - penalty
    if any(f.severity == "CRITICAL" for f in findings):
        score = min(score, CAP_CRITICAL)
    elif any(f.severity == "WARNING" for f in findings):
        score = min(score, CAP_WARNING)
    return int(max(0, min(100, round(score))))


def analyze(rows, history_features: list[dict[str, float]], fuel: str, use_ml: bool,
            norms: dict[str, float] | None = None):
    """rows: список Reading цієї поїздки. history_features: ознаки попередніх нормальних поїздок.

    Повертає (індекс або None, список знахідок, ознаки поїздки).
    """
    if fuel == "ELECTRIC" or len(rows) < MIN_READINGS_FOR_SCORE:
        return None, [], {}
    feats = extract(rows)
    findings = evaluate(feats, fuel, norms)
    drift = 0.0
    if use_ml:
        profile = Profile(history_features)
        ml, _ = profile.score(feats)
        if ml is not None:
            findings.append(ml)
        if profile.ready:
            drift = drift_penalty(profile, feats)
    return health_index(findings, feats, drift, norms), findings, feats
