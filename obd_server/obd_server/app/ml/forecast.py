"""Прогноз: тренд показників за кілька поїздок і ШІ-пояснення."""
from __future__ import annotations

import numpy as np
from sqlalchemy import select

from ..models import Forecast, TripEvent, Vehicle
from .ai_explain import explain
from .features import extract, is_nan
from .service import counted_trips, trip_readings

MIN_TRIPS = 3
HORIZON = 5            # на скільки поїздок уперед проєктуємо тренд

# (ключ, ознака, межа, напрям, заголовок uk, заголовок en)
METRICS = [
    ("ltft", "ltft_mean", 10.0, "up", "Паливна корекція LTFT, %", "Fuel trim LTFT, %"),
    ("coolant", "coolant_max", 112.0, "up", "Макс. температура охолоджувача, °C", "Peak coolant temperature, °C"),
    ("battery", "battery_mean", 12.4, "down", "Напруга бортової мережі, В", "Board voltage, V"),
]


MAX_TRUSTED_TRIPS = 30   # далі за цей горизонт тренду не довіряємо


def _series(db, vehicle_id: int, upto_trip_id: int | None = None):
    trips = counted_trips(db, vehicle_id, limit=30)
    if upto_trip_id is not None:
        ids = [t.id for t in trips]
        if upto_trip_id not in ids:
            raise ValueError("trip not counted")
        trips = trips[: ids.index(upto_trip_id) + 1]
    trips = trips[-12:]
    return trips, [extract(trip_readings(db, t)) for t in trips]


def build_trend(feats: list[dict[str, float]], locale: str) -> dict | None:
    best, best_risk = None, -1.0
    for key, feat, limit, direction, t_uk, t_en in METRICS:
        vals = []
        for f in feats:
            v = f.get(feat)
            if is_nan(v):
                continue
            vals.append(abs(v) if key == "ltft" else float(v))
        if len(vals) < MIN_TRIPS:
            continue
        x = np.arange(len(vals), dtype=float)
        slope = float(np.polyfit(x, np.asarray(vals), 1)[0])
        last = vals[-1]
        proj = last + slope * HORIZON
        if direction == "up":
            risk = max(last, proj) / limit
            n_to = (limit - last) / slope if slope > 1e-9 and last < limit else (0.0 if last >= limit else None)
        else:
            risk = limit / max(min(last, proj), 0.1)
            n_to = (last - limit) / -slope if slope < -1e-9 and last > limit else (0.0 if last <= limit else None)
        # межа далі за горизонт довіри або показник не наближається до неї — вважаємо стабільним,
        # інакше крихітний нахил дає випадкові «через 128 / 192 / 912 поїздок»
        stable = n_to is None or n_to > MAX_TRUSTED_TRIPS
        if stable:
            n_to = None
        if risk > best_risk:
            best_risk = risk
            best = {
                "stable": stable,
                "metric": key,
                "title": t_en if locale == "en" else t_uk,
                "values": [round(v, 2) for v in vals[-10:]],
                "threshold": limit,
                "projection": round(proj, 2),
                "slope": round(slope, 3),
                "trips_to_limit": None if n_to is None else round(n_to, 1),
                "limit_is_upper": direction == "up",
            }
    return best


def urgency_for(trend: dict | None, findings: list[dict]) -> str:
    level = 0
    if any(f["severity"] == "CRITICAL" for f in findings):
        level = 2
    elif any(f["severity"] == "WARNING" for f in findings):
        level = 1
    if trend and trend.get("trips_to_limit") is not None:
        n = trend["trips_to_limit"]
        if n <= 3:
            level = max(level, 2)
        elif n <= 10:
            level = max(level, 1)
    return ["LOW", "MEDIUM", "HIGH"][level]


def generate(db, vehicle: Vehicle, locale: str = "uk", trip_id: int | None = None) -> Forecast | None:
    """Прогноз за поїздками до trip_id включно (за замовчуванням — до останньої)."""
    trips, feats = _series(db, vehicle.id, trip_id)
    if len(trips) < MIN_TRIPS:
        return None
    trend = build_trend(feats, locale)
    if trend is not None:
        trend["based_on_trip_id"] = trips[-1].id
    last = trips[-1]
    events = db.scalars(select(TripEvent).where(TripEvent.trip_id == last.id,
                                                TripEvent.severity != "INFO")).all()
    findings = [{"code": e.event_type, "severity": e.severity, "detail": e.message} for e in events]
    urgency = urgency_for(trend, findings)
    payload = {
        "car": f"{vehicle.make} {vehicle.model} {vehicle.year}",
        "fuel": vehicle.fuel,
        "trips_analyzed": len(trips),
        "last_trip": {"health_index": last.health_index, "findings": findings},
        "trend": trend,
        "urgency": urgency,
    }
    text = explain(payload, locale)
    fc = Forecast(vehicle_id=vehicle.id, trend=trend or {}, ai_text=text, urgency=urgency)
    db.add(fc)
    db.commit()
    db.refresh(fc)
    return fc
