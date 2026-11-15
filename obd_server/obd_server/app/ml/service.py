"""Зв'язок ML-модуля з базою даних."""
from __future__ import annotations

from sqlalchemy import select

from ..config import REFERENCE_TRIPS
from ..models import Reading as ReadingRow
from ..models import Trip
from .features import Reading, extract

HEALTHY_MIN = 70       # у навчальну історію беремо лише поїздки без помітних проблем


def to_reading(r, started_at) -> Reading:
    f = lambda v: None if v is None else float(v)
    return Reading(
        t=(r.ts - started_at).total_seconds(),
        rpm=f(r.rpm), speed=f(r.speed_kmh), coolant=f(r.coolant_temp_c), load=f(r.engine_load_pct),
        maf=f(r.maf_gs), stft=f(r.stft_pct), ltft=f(r.ltft_pct), battery=f(r.battery_v),
    )


def trip_readings(db, trip: Trip) -> list[Reading]:
    rows = db.scalars(select(ReadingRow).where(ReadingRow.trip_id == trip.id).order_by(ReadingRow.ts)).all()
    return [to_reading(r, trip.started_at) for r in rows]


def counted_trips(db, vehicle_id: int, exclude_trip_id: int | None = None, limit: int = 30) -> list[Trip]:
    q = select(Trip).where(Trip.vehicle_id == vehicle_id, Trip.counts_for_baseline.is_(True))
    if exclude_trip_id is not None:
        q = q.where(Trip.id != exclude_trip_id)
    rows = db.scalars(q.order_by(Trip.started_at.desc()).limit(limit)).all()
    return list(reversed(rows))        # від старих до нових


def history_features(db, vehicle_id: int, exclude_trip_id: int | None = None) -> list[dict[str, float]]:
    out = []
    for t in counted_trips(db, vehicle_id, exclude_trip_id):
        if t.health_index is not None and t.health_index < HEALTHY_MIN:
            continue
        out.append(extract(trip_readings(db, t)))
    return out
