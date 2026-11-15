"""Власний профіль авто: Isolation Forest на ознаках попередніх нормальних поїздок."""
from __future__ import annotations

import numpy as np
from sklearn.ensemble import IsolationForest

from .features import FEATURE_NAMES, to_vector
from .rules import Finding

MIN_HISTORY = 3
THR_MARGIN = 0.03
Z_MIN = 2.5
AUGMENT = 40           # скільки «розмитих» копій кожної поїздки додаємо для навчання
# мінімальний розкид ознаки (щоб крихітні відхилення не вважались аномаліями)
MIN_SPREAD = {
    "coolant_max": 3.0, "coolant_steady": 3.0, "warmup_s": 40.0, "ltft_mean": 1.0, "ltft_abs_max": 1.5,
    "stft_std": 0.8, "battery_mean": 0.25, "battery_min": 0.3, "idle_rpm": 40.0, "load_mean": 3.0,
    "maf_per_load": 0.05,
}


def _matrix(history: list[dict[str, float]]):
    X = np.vstack([to_vector(f) for f in history])
    med = np.array([np.median(c[~np.isnan(c)]) if (~np.isnan(c)).any() else 0.0 for c in X.T])
    idx = np.where(np.isnan(X))
    X[idx] = np.take(med, idx[1])
    return X, med


class Profile:
    """Профіль авто: середні, розкид і навчений Isolation Forest."""

    def __init__(self, history: list[dict[str, float]], seed: int = 0):
        self.ready = len(history) >= MIN_HISTORY
        if not self.ready:
            return
        X, self.med = _matrix(history)
        self.mean = X.mean(axis=0)
        self.spread = np.maximum(X.std(axis=0), np.asarray([MIN_SPREAD[k] for k in FEATURE_NAMES]))
        rng = np.random.default_rng(seed)
        # розмиваємо навчальні дані, щоб у лісу було з чого будувати межі
        noise = rng.normal(0, 1, size=(X.shape[0] * AUGMENT, X.shape[1])) * self.spread * 0.5
        Xa = np.vstack([X, np.repeat(X, AUGMENT, axis=0) + noise])
        self.forest = IsolationForest(n_estimators=100, contamination=0.02, random_state=seed)
        self.forest.fit((Xa - self.mean) / self.spread)
        # поріг: трохи нижче за найгіршу оцінку серед власних нормальних поїздок
        own = self.forest.decision_function((X - self.mean) / self.spread)
        self.thr = float(own.min()) - THR_MARGIN

    def score(self, current: dict[str, float]):
        """Повертає (знахідка або None, відхилення). Відхилення: список (ознака, z-оцінка)."""
        if not self.ready:
            return None, []
        x = to_vector(current)
        x = np.where(np.isnan(x), self.med, x)
        z = (x - self.mean) / self.spread
        score = float(self.forest.decision_function(z.reshape(1, -1))[0])
        flagged = score < self.thr and float(np.max(np.abs(z))) >= Z_MIN
        devs = sorted(((FEATURE_NAMES[i], float(z[i])) for i in range(len(z)) if abs(z[i]) >= 2.0),
                      key=lambda p: -abs(p[1]))[:3]
        if not flagged:
            return None, devs
        text = ", ".join(f"{k} {'+' if zv > 0 else ''}{zv:.1f}σ" for k, zv in devs) or "overall pattern"
        return Finding("ANOMALY_ML", "WARNING", f"differs from this car's baseline: {text}", abs(score), 0.0), devs


def score_trip(history: list[dict[str, float]], current: dict[str, float], seed: int = 0):
    return Profile(history, seed).score(current)
