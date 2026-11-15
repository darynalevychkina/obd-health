from fastapi import APIRouter, BackgroundTasks, HTTPException, Response, status
from sqlalchemy import func, insert, select
from sqlalchemy.exc import IntegrityError

from ..config import MIN_BASELINE_DURATION_S, MIN_BASELINE_READINGS, REFERENCE_TRIPS
from ..db import SessionLocal
from ..deps import CurrentUser, DbSession
from ..ml import forecast as forecast_mod
from ..ml.scoring import analyze
from ..ml.service import history_features, to_reading
from ..models import Reading, Trip, TripEvent, Vehicle
from ..schemas import SamplePoint, TripDetailOut, TripEventOut, TripIn, TripOut

router = APIRouter(prefix="/trips", tags=["trips"])


def _own_vehicle(db, vehicle_id: int, user_id: int) -> Vehicle:
    vehicle = db.get(Vehicle, vehicle_id)
    if vehicle is None or vehicle.user_id != user_id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Авто не знайдено")
    return vehicle


@router.post("", response_model=TripOut, status_code=status.HTTP_201_CREATED)
def upload_trip(data: TripIn, response: Response, background: BackgroundTasks, db: DbSession, user: CurrentUser):
    """Приймає поїздку з телефона. Повторна відправка тієї самої поїздки
    (той самий client_trip_id) не створює дубль, а повертає вже збережену."""
    vehicle = _own_vehicle(db, data.vehicle_id, user.id)

    existing = db.scalar(select(Trip).where(Trip.client_trip_id == data.client_trip_id))
    if existing:
        if existing.vehicle_id != vehicle.id:
            raise HTTPException(status.HTTP_409_CONFLICT, "Цей ідентифікатор поїздки вже використано")
        response.status_code = status.HTTP_200_OK
        return existing

    if data.ended_at and data.ended_at < data.started_at:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Кінець поїздки раніше за початок")

    duration_s = (data.ended_at - data.started_at).total_seconds() if data.ended_at else 0
    counts_for_baseline = (
        duration_s >= MIN_BASELINE_DURATION_S and len(data.readings) >= MIN_BASELINE_READINGS
    )

    # Перші REFERENCE_TRIPS зарахованих поїздок оцінюємо лише за довідковою нормою,
    # далі підключається власний профіль авто
    counted = db.scalar(
        select(func.count()).select_from(Trip)
        .where(Trip.vehicle_id == vehicle.id, Trip.counts_for_baseline.is_(True))
    )
    mode = "REFERENCE" if counted < REFERENCE_TRIPS else "BLENDED"

    trip = Trip(
        client_trip_id=data.client_trip_id,
        vehicle_id=vehicle.id,
        started_at=data.started_at,
        ended_at=data.ended_at,
        exit_reason=data.exit_reason,
        distance_km=data.distance_km,
        readings_count=len(data.readings),
        counts_for_baseline=counts_for_baseline,
        mode=mode,
        # TODO: тут пізніше з'явиться розрахунок health_index (правила + ML)
    )
    db.add(trip)
    try:
        db.flush()
    except IntegrityError:
        # дві однакові відправки одночасно: друга просто повертає збережену
        db.rollback()
        saved = db.scalar(select(Trip).where(Trip.client_trip_id == data.client_trip_id))
        response.status_code = status.HTTP_200_OK
        return saved

    if data.readings:
        db.execute(insert(Reading), [{"trip_id": trip.id, **r.model_dump()} for r in data.readings])
    if data.events:
        db.execute(insert(TripEvent), [{"trip_id": trip.id, **e.model_dump()} for e in data.events])

    # оцінка стану: правила, а з 4-ї зарахованої поїздки ще й власний профіль авто (Isolation Forest)
    try:
        rows = [to_reading(r, data.started_at) for r in data.readings]
        hist = history_features(db, vehicle.id, trip.id) if mode == "BLENDED" else []
        health, findings, _ = analyze(rows, hist, vehicle.fuel, use_ml=(mode == "BLENDED"))
        trip.health_index = health
        ev_ts = data.ended_at or data.started_at
        if findings:
            db.execute(insert(TripEvent), [
                {"trip_id": trip.id, "ts": ev_ts, "event_type": f.code, "severity": f.severity, "message": f.message}
                for f in findings
            ])
    except Exception:
        # помилка аналізу не повинна втратити поїздку
        pass
    db.commit()
    db.refresh(trip)
    if trip.counts_for_baseline:
        background.add_task(_make_forecast, vehicle.id, user.locale)
    return trip


def _make_forecast(vehicle_id: int, locale: str) -> None:
    """Створює прогноз у фоні, щоб не затримувати відповідь телефону."""
    with SessionLocal() as db:
        try:
            vehicle = db.get(Vehicle, vehicle_id)
            if vehicle is not None:
                forecast_mod.generate(db, vehicle, locale)
        except Exception:
            db.rollback()


@router.get("", response_model=list[TripOut])
def list_trips(vehicle_id: int, db: DbSession, user: CurrentUser):
    _own_vehicle(db, vehicle_id, user.id)
    return db.scalars(
        select(Trip).where(Trip.vehicle_id == vehicle_id).order_by(Trip.started_at.desc())
    ).all()


@router.get("/{trip_id}/detail", response_model=TripDetailOut)
def trip_detail(trip_id: int, db: DbSession, user: CurrentUser):
    trip = db.get(Trip, trip_id)
    if trip is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Поїздку не знайдено")
    _own_vehicle(db, trip.vehicle_id, user.id)
    events = db.scalars(select(TripEvent).where(TripEvent.trip_id == trip.id).order_by(TripEvent.ts)).all()
    rows = db.execute(
        select(Reading.ts, Reading.coolant_temp_c, Reading.speed_kmh, Reading.rpm, Reading.engine_load_pct)
        .where(Reading.trip_id == trip.id).order_by(Reading.ts)).all()
    if len(rows) > 60:
        step = len(rows) / 60
        rows = [rows[int(i * step)] for i in range(60)]
    samples = [
        SamplePoint(
            t_s=(r.ts - trip.started_at).total_seconds(),
            coolant=r.coolant_temp_c, speed=r.speed_kmh, rpm=r.rpm,
            load=float(r.engine_load_pct) if r.engine_load_pct is not None else None,
        ) for r in rows
    ]
    return TripDetailOut(
        trip=TripOut.model_validate(trip),
        events=[TripEventOut.model_validate(e) for e in events],
        samples=samples,
    )


@router.get("/{trip_id}", response_model=TripOut)
def get_trip(trip_id: int, db: DbSession, user: CurrentUser):
    trip = db.get(Trip, trip_id)
    if trip is None:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Поїздку не знайдено")
    _own_vehicle(db, trip.vehicle_id, user.id)
    return trip
