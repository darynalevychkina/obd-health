"""Pydantic-схеми: що сервер приймає й що віддає."""
import uuid
from datetime import date
from typing import Any
from decimal import Decimal
from typing import Literal

from pydantic import AwareDatetime, BaseModel, ConfigDict, EmailStr, Field

FuelLiteral = Literal["PETROL", "DIESEL", "HYBRID", "ELECTRIC", "LPG"]
ExitReasonLiteral = Literal[
    "USER_STOPPED", "ENGINE_OFF", "ADAPTER_DISCONNECTED", "ECU_TIMEOUT", "APP_INTERRUPTED"
]
SeverityLiteral = Literal["INFO", "WARNING", "CRITICAL"]


# ---------- Автентифікація ----------
class SettingsIn(BaseModel):
    locale: Literal["uk", "en"] | None = None
    theme: Literal["light", "dark", "system"] | None = None


class RegisterIn(BaseModel):
    email: EmailStr
    password: str = Field(min_length=8, max_length=64)


class TokenOut(BaseModel):
    access_token: str
    token_type: str = "bearer"


class UserOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: EmailStr
    locale: str
    theme: str


# ---------- Авто ----------
class VehicleIn(BaseModel):
    vin: str | None = Field(default=None, min_length=17, max_length=17, pattern=r"^[A-HJ-NPR-Z0-9]{17}$")
    make: str = Field(min_length=1)
    model: str = Field(min_length=1)
    year: int = Field(ge=1980, le=2100)
    fuel: FuelLiteral
    engine_vol_l: Decimal | None = Field(default=None, ge=0, le=9.9)
    odometer_km: int = Field(default=0, ge=0)   # пробіг з панелі приладів
    color: str | None = Field(default=None, max_length=20)


class VehicleOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    vin: str | None
    make: str
    model: str
    year: int
    fuel: FuelLiteral
    engine_vol_l: Decimal | None
    odometer_base_km: int
    odometer_base_date: date
    color: str | None = None
    color_hex: str | None = None
    # розраховані поля (заповнює сервер)
    odometer_est_km: int = 0
    trips_count: int = 0
    baseline_trips: int = 0
    last_trip_at: AwareDatetime | None = None
    last_health_index: int | None = None


# ---------- Поїздки ----------
class ReadingIn(BaseModel):
    ts: AwareDatetime
    rpm: int | None = None
    speed_kmh: int | None = None
    coolant_temp_c: int | None = None
    engine_load_pct: Decimal | None = None
    throttle_pct: Decimal | None = None
    maf_gs: Decimal | None = None
    stft_pct: Decimal | None = None
    ltft_pct: Decimal | None = None
    battery_v: Decimal | None = None
    intake_temp_c: int | None = None


class TripEventIn(BaseModel):
    ts: AwareDatetime
    event_type: str = Field(max_length=32)
    severity: SeverityLiteral = "INFO"
    message: str | None = None


class TripIn(BaseModel):
    client_trip_id: uuid.UUID            # id, який згенерував телефон
    vehicle_id: int
    started_at: AwareDatetime
    ended_at: AwareDatetime | None = None
    exit_reason: ExitReasonLiteral | None = None
    distance_km: Decimal = Field(default=Decimal("0"), ge=0)
    readings: list[ReadingIn] = []
    events: list[TripEventIn] = []


class TripOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    client_trip_id: uuid.UUID
    vehicle_id: int
    started_at: AwareDatetime
    ended_at: AwareDatetime | None
    exit_reason: ExitReasonLiteral | None
    distance_km: Decimal
    readings_count: int
    counts_for_baseline: bool
    mode: Literal["REFERENCE", "BLENDED"]
    health_index: int | None


class TripEventOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    ts: AwareDatetime
    event_type: str
    severity: SeverityLiteral
    message: str | None


class SamplePoint(BaseModel):
    t_s: float                    # секунд від початку поїздки
    coolant: int | None = None
    speed: int | None = None
    rpm: int | None = None
    load: float | None = None


class TripDetailOut(BaseModel):
    trip: TripOut
    events: list[TripEventOut]
    samples: list[SamplePoint]    # проріджені точки для графіка (до ~60)


class ForecastOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    created_at: AwareDatetime
    trend: Any
    ai_text: str
    urgency: Literal["LOW", "MEDIUM", "HIGH"]
