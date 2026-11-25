"""Моделі описують таблиці, які вже створені файлом obd_schema.sql.
Тут вони лише відображаються на Python-класи, create_all() не потрібен."""
import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import (
    BigInteger, Boolean, Date, DateTime, ForeignKey, Integer, Numeric,
    SmallInteger, String, Text, func, text,
)
from sqlalchemy.dialects.postgresql import CITEXT, ENUM, JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base

# ENUM-типи вже існують у базі, тому create_type=False
FuelType = ENUM("PETROL", "DIESEL", "HYBRID", "ELECTRIC", "LPG",
                name="fuel_type", create_type=False)
ExitReason = ENUM("USER_STOPPED", "ENGINE_OFF", "ADAPTER_DISCONNECTED",
                  "ECU_TIMEOUT", "APP_INTERRUPTED",
                  name="trip_exit_reason", create_type=False)
AssessmentMode = ENUM("REFERENCE", "BLENDED",
                      name="assessment_mode", create_type=False)
Severity = ENUM("INFO", "WARNING", "CRITICAL",
                name="severity_level", create_type=False)
Urgency = ENUM("LOW", "MEDIUM", "HIGH",
               name="urgency_level", create_type=False)


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    email: Mapped[str] = mapped_column(CITEXT, unique=True)
    password_hash: Mapped[str] = mapped_column(Text)
    locale: Mapped[str] = mapped_column(String(5), server_default="uk")
    theme: Mapped[str] = mapped_column(String(6), server_default="system")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    vehicles: Mapped[list["Vehicle"]] = relationship(back_populates="user")


class Vehicle(Base):
    __tablename__ = "vehicles"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    vin: Mapped[str | None] = mapped_column(String(17))
    make: Mapped[str] = mapped_column(Text)
    model: Mapped[str] = mapped_column(Text)
    year: Mapped[int] = mapped_column(SmallInteger)
    fuel: Mapped[str] = mapped_column(FuelType)
    engine_vol_l: Mapped[Decimal | None] = mapped_column(Numeric(3, 1))
    odometer_base_km: Mapped[int] = mapped_column(Integer, server_default=text("0"))
    odometer_base_date: Mapped[date] = mapped_column(Date, server_default=func.current_date())
    reference_profile_id: Mapped[int | None] = mapped_column(Integer)
    color: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    user: Mapped[User] = relationship(back_populates="vehicles")
    trips: Mapped[list["Trip"]] = relationship(back_populates="vehicle")


class ReferenceProfile(Base):
    __tablename__ = "reference_profiles"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(Text)
    fuel: Mapped[str] = mapped_column(FuelType)
    engine_vol_min: Mapped[Decimal | None] = mapped_column(Numeric(3, 1))
    engine_vol_max: Mapped[Decimal | None] = mapped_column(Numeric(3, 1))
    year_from: Mapped[int | None] = mapped_column(SmallInteger)
    year_to: Mapped[int | None] = mapped_column(SmallInteger)


class ReferenceNorm(Base):
    __tablename__ = "reference_norms"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    profile_id: Mapped[int] = mapped_column(ForeignKey("reference_profiles.id", ondelete="CASCADE"))
    metric: Mapped[str] = mapped_column(String(32))
    state: Mapped[str] = mapped_column(String(16))
    min_value: Mapped[Decimal | None] = mapped_column(Numeric(8, 2))
    max_value: Mapped[Decimal | None] = mapped_column(Numeric(8, 2))
    source: Mapped[str] = mapped_column(Text)


class Trip(Base):
    __tablename__ = "trips"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    client_trip_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), unique=True)
    vehicle_id: Mapped[int] = mapped_column(ForeignKey("vehicles.id", ondelete="CASCADE"))
    started_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    ended_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    exit_reason: Mapped[str | None] = mapped_column(ExitReason)
    distance_km: Mapped[Decimal] = mapped_column(Numeric(8, 2), server_default=text("0"))
    readings_count: Mapped[int] = mapped_column(Integer, server_default=text("0"))
    counts_for_baseline: Mapped[bool] = mapped_column(Boolean, server_default=text("false"))
    mode: Mapped[str] = mapped_column(AssessmentMode, server_default="REFERENCE")
    health_index: Mapped[int | None] = mapped_column(SmallInteger)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    vehicle: Mapped[Vehicle] = relationship(back_populates="trips")


class Reading(Base):
    __tablename__ = "readings"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    trip_id: Mapped[int] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"))
    ts: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    rpm: Mapped[int | None] = mapped_column(Integer)
    speed_kmh: Mapped[int | None] = mapped_column(SmallInteger)
    coolant_temp_c: Mapped[int | None] = mapped_column(SmallInteger)
    engine_load_pct: Mapped[Decimal | None] = mapped_column(Numeric(5, 2))
    throttle_pct: Mapped[Decimal | None] = mapped_column(Numeric(5, 2))
    maf_gs: Mapped[Decimal | None] = mapped_column(Numeric(7, 2))
    stft_pct: Mapped[Decimal | None] = mapped_column(Numeric(5, 2))
    ltft_pct: Mapped[Decimal | None] = mapped_column(Numeric(5, 2))
    battery_v: Mapped[Decimal | None] = mapped_column(Numeric(4, 1))
    intake_temp_c: Mapped[int | None] = mapped_column(SmallInteger)


class TripEvent(Base):
    __tablename__ = "trip_events"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    trip_id: Mapped[int] = mapped_column(ForeignKey("trips.id", ondelete="CASCADE"))
    ts: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    event_type: Mapped[str] = mapped_column(String(32))
    severity: Mapped[str] = mapped_column(Severity, server_default="INFO")
    message: Mapped[str | None] = mapped_column(Text)


class Forecast(Base):
    __tablename__ = "forecasts"

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True)
    vehicle_id: Mapped[int] = mapped_column(ForeignKey("vehicles.id", ondelete="CASCADE"))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())
    trend: Mapped[dict] = mapped_column(JSONB)
    ai_text: Mapped[str] = mapped_column(Text)
    urgency: Mapped[str] = mapped_column(Urgency)
