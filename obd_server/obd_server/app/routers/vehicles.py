from datetime import datetime, time, timezone

from fastapi import APIRouter, HTTPException, status
from sqlalchemy import delete, func, select
from sqlalchemy.exc import IntegrityError

from ..colors import color_hex
from ..deps import CurrentUser, DbSession
from ..models import Forecast, Trip, Vehicle
from ..ml import forecast as forecast_mod
from ..schemas import ForecastOut, VehicleIn, VehicleOut

router = APIRouter(prefix="/vehicles", tags=["vehicles"])


def _to_out(db, v: Vehicle) -> VehicleOut:
    """Додає до авто розрахункові поля: кількість поїздок, останнє здоров'я, орієнтовний пробіг."""
    out = VehicleOut.model_validate(v)
    out.color_hex = color_hex(v.color)
    out.trips_count = db.scalar(select(func.count()).select_from(Trip).where(Trip.vehicle_id == v.id)) or 0
    out.baseline_trips = db.scalar(
        select(func.count()).select_from(Trip)
        .where(Trip.vehicle_id == v.id, Trip.counts_for_baseline.is_(True))) or 0
    last = db.scalars(select(Trip).where(Trip.vehicle_id == v.id).order_by(Trip.started_at.desc()).limit(1)).first()
    if last:
        out.last_trip_at = last.started_at
        out.last_health_index = last.health_index
    # пробіг = показник з панелі на день додавання + сума відстаней поїздок після цієї дати
    base_dt = datetime.combine(v.odometer_base_date, time.min, tzinfo=timezone.utc)
    driven = db.scalar(
        select(func.coalesce(func.sum(Trip.distance_km), 0))
        .where(Trip.vehicle_id == v.id, Trip.started_at >= base_dt)) or 0
    out.odometer_est_km = int(v.odometer_base_km + round(float(driven)))
    return out


def _own(db, vehicle_id: int, user_id: int) -> Vehicle:
    vehicle = db.get(Vehicle, vehicle_id)
    if vehicle is None or vehicle.user_id != user_id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Авто не знайдено")
    return vehicle


@router.get("", response_model=list[VehicleOut])
def list_vehicles(db: DbSession, user: CurrentUser):
    rows = db.scalars(select(Vehicle).where(Vehicle.user_id == user.id).order_by(Vehicle.id)).all()
    return [_to_out(db, v) for v in rows]


@router.post("", response_model=VehicleOut, status_code=status.HTTP_201_CREATED)
def add_vehicle(data: VehicleIn, db: DbSession, user: CurrentUser):
    vehicle = Vehicle(
        user_id=user.id,
        vin=data.vin,
        make=data.make,
        model=data.model,
        year=data.year,
        fuel=data.fuel,
        engine_vol_l=data.engine_vol_l,
        odometer_base_km=data.odometer_km,
        color=data.color,
    )
    db.add(vehicle)
    try:
        db.commit()
    except IntegrityError:
        db.rollback()
        raise HTTPException(status.HTTP_409_CONFLICT, "Авто з таким VIN уже є в гаражі")
    db.refresh(vehicle)
    return _to_out(db, vehicle)


@router.get("/{vehicle_id}", response_model=VehicleOut)
def get_vehicle(vehicle_id: int, db: DbSession, user: CurrentUser):
    return _to_out(db, _own(db, vehicle_id, user.id))


@router.get("/{vehicle_id}/forecast", response_model=ForecastOut | None)
def latest_forecast(vehicle_id: int, db: DbSession, user: CurrentUser):
    """Останній збережений прогноз або null, якщо його ще не створено."""
    _own(db, vehicle_id, user.id)
    return db.scalars(
        select(Forecast).where(Forecast.vehicle_id == vehicle_id)
        .order_by(Forecast.created_at.desc()).limit(1)).first()


@router.delete("/{vehicle_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_vehicle(vehicle_id: int, db: DbSession, user: CurrentUser):
    """Видаляє авто разом з усіма поїздками, показниками й прогнозами (каскадно в БД)."""
    _own(db, vehicle_id, user.id)
    # прямий DELETE: решту прибирає ON DELETE CASCADE у базі
    db.execute(delete(Vehicle).where(Vehicle.id == vehicle_id))
    db.commit()


@router.post("/{vehicle_id}/forecast", response_model=ForecastOut)
def create_forecast(vehicle_id: int, db: DbSession, user: CurrentUser, trip_id: int | None = None):
    """Створює новий прогноз (тренд за поїздками + пояснення ШІ).
    trip_id — побудувати прогноз на обраній минулій поїздці (враховуються поїздки до неї включно)."""
    vehicle = _own(db, vehicle_id, user.id)
    try:
        fc = forecast_mod.generate(db, vehicle, user.locale, trip_id)
    except ValueError:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Поїздку не знайдено серед зарахованих")
    if fc is None:
        raise HTTPException(status.HTTP_409_CONFLICT,
                            f"Для прогнозу потрібно щонайменше {forecast_mod.MIN_TRIPS} зарахованих поїздки")
    return fc
