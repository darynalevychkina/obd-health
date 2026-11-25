"""Довідкова (теоретична) база норм: за нею оцінюються перші поїздки авто,
поки немає власного профілю. Норми лежать у таблицях reference_profiles / reference_norms."""
from __future__ import annotations

import json
import logging
import urllib.request

from sqlalchemy import select

from ..db import Base, engine
from ..models import ReferenceNorm, ReferenceProfile, Vehicle

log = logging.getLogger("uvicorn.error")

SRC = "Орієнтовні типові значення (експертна оцінка автора); уточнюються власним профілем авто"

# (назва, паливо, об'єм від, об'єм до, рік від, рік до,
#  холостий (мін, макс), АКБ під час руху (мін, макс), ОР двигуна (мін усталена, макс), LTFT (±))
PROFILES = [
    ("Бензин, малий об'єм (до 1.4 л)", "PETROL", 0.0, 1.4, None, None, (700, 1000), (13.2, 14.9), (80, 105), 7),
    ("Бензин, середній об'єм (1.5–2.5 л)", "PETROL", 1.5, 2.5, None, None, (650, 950), (13.2, 14.9), (80, 105), 7),
    ("Бензин, великий об'єм (понад 2.5 л)", "PETROL", 2.6, 9.9, None, None, (600, 900), (13.2, 14.9), (80, 108), 8),
    ("Дизель", "DIESEL", None, None, None, None, (700, 950), (13.2, 14.8), (80, 105), 10),
    ("Гібрид (ДВЗ + електромотор)", "HYBRID", None, None, None, None, (500, 1100), (12.6, 15.0), (75, 105), 8),
    ("Автогаз (LPG)", "LPG", None, None, None, None, (650, 1000), (13.2, 14.9), (80, 105), 8),
]


def seed(db) -> None:
    """Створює таблиці (якщо їх ще немає) і наповнює довідник один раз."""
    Base.metadata.create_all(engine, tables=[ReferenceProfile.__table__, ReferenceNorm.__table__])
    if db.scalar(select(ReferenceProfile.id).limit(1)) is not None:
        return
    for name, fuel, v0, v1, y0, y1, idle, bat, cool, ltft in PROFILES:
        p = ReferenceProfile(name=name, fuel=fuel, engine_vol_min=v0, engine_vol_max=v1, year_from=y0, year_to=y1)
        db.add(p)
        db.flush()
        for metric, state, lo, hi in [
            ("idle_rpm", "idle", idle[0], idle[1]),
            ("battery_v", "driving", bat[0], bat[1]),
            ("coolant_temp_c", "driving", cool[0], cool[1]),
            ("ltft_pct", "driving", -ltft, ltft),
        ]:
            db.add(ReferenceNorm(profile_id=p.id, metric=metric, state=state,
                                 min_value=lo, max_value=hi, source=SRC))
    db.commit()
    log.info("Довідкові норми: додано %d профілів", len(PROFILES))


VPIC_URL = "https://vpic.nhtsa.dot.gov/api/vehicles/DecodeVinValues/{vin}?format=json"


def decode_vin(vin: str) -> dict:
    """Об'єм двигуна за VIN із відкритого API NHTSA vPIC. Порожній словник, якщо даних немає."""
    try:
        with urllib.request.urlopen(VPIC_URL.format(vin=vin), timeout=4) as r:
            row = json.load(r)["Results"][0]
        vol = float(row.get("DisplacementL") or 0)
        return {"engine_vol_l": round(vol, 1)} if 0.5 <= vol <= 9.9 else {}
    except Exception as e:  # немає мережі, невідомий VIN, зміна формату
        log.warning("vPIC недоступний: %s", e)
        return {}


def pick_profile(db, vehicle: Vehicle) -> ReferenceProfile | None:
    cands = db.scalars(select(ReferenceProfile).where(ReferenceProfile.fuel == vehicle.fuel)).all()
    vol = float(vehicle.engine_vol_l) if vehicle.engine_vol_l is not None else None
    best = None
    for p in cands:
        if vol is not None and p.engine_vol_min is not None and p.engine_vol_max is not None:
            if not (float(p.engine_vol_min) <= vol <= float(p.engine_vol_max)):
                continue
        if p.year_from and vehicle.year < p.year_from:
            continue
        if p.year_to and vehicle.year > p.year_to:
            continue
        best = p
        break
    if best is None and cands:
        # об'єм невідомий або між діапазонами: беремо середній профіль цього палива
        best = cands[min(1, len(cands) - 1)] if vehicle.fuel == "PETROL" else cands[0]
    return best


def norms_for(db, vehicle: Vehicle) -> dict[str, float]:
    """Повертає словник норм для rules.evaluate (порожній, якщо довідник недоступний)."""
    try:
        prof = db.get(ReferenceProfile, vehicle.reference_profile_id) if vehicle.reference_profile_id else None
        if prof is None:
            if vehicle.engine_vol_l is None and vehicle.vin:
                info = decode_vin(vehicle.vin)
                if info:
                    vehicle.engine_vol_l = info["engine_vol_l"]
            prof = pick_profile(db, vehicle)
            if prof is None:
                return {}
            vehicle.reference_profile_id = prof.id
        rows = {(n.metric, n.state): n for n in
                db.scalars(select(ReferenceNorm).where(ReferenceNorm.profile_id == prof.id)).all()}
    except Exception:
        db.rollback()
        return {}
    out: dict[str, float] = {}
    n = rows.get(("idle_rpm", "idle"))
    if n:
        out["idle_low"], out["idle_high"] = float(n.min_value), float(n.max_value)
    n = rows.get(("battery_v", "driving"))
    if n:
        lo, hi = float(n.min_value), float(n.max_value)
        out.update(battery_low_warn=lo, battery_low_crit=lo - 0.6, battery_high_warn=hi, battery_high_crit=hi + 0.5)
    n = rows.get(("coolant_temp_c", "driving"))
    if n:
        lo, hi = float(n.min_value), float(n.max_value)
        out.update(steady_low=lo - 5, coolant_warn=hi, coolant_crit=hi + 7)
    n = rows.get(("ltft_pct", "driving"))
    if n:
        hi = float(n.max_value)
        out.update(ltft_warn=hi, ltft_crit=hi + 3)
    return out
