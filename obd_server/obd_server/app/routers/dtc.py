"""Коди несправностей (DTC), прочитані з ЕБУ: збереження історії та опис зі словника."""
from datetime import datetime, timezone
from typing import Literal

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel, Field
from sqlalchemy import select

from ..deps import CurrentUser, DbSession
from ..dtc_codes import CODES, describe_unknown
from ..models import DtcDictionary, DtcEvent, Vehicle

router = APIRouter(prefix="/vehicles", tags=["dtc"])

CODE_PATTERN = r"^[PCBU][0-3][0-9A-F]{3}$"


class DtcCodeIn(BaseModel):
    code: str = Field(pattern=CODE_PATTERN)
    kind: Literal["STORED", "PENDING", "PERMANENT"] = "STORED"


class DtcReportIn(BaseModel):
    mil_on: bool = False
    codes: list[DtcCodeIn] = Field(default_factory=list, max_length=64)


class DtcItemOut(BaseModel):
    code: str
    kind: str | None
    description_uk: str
    description_en: str
    read_at: datetime | None = None
    active: bool = True


class DtcReportOut(BaseModel):
    read_at: datetime
    mil_on: bool
    items: list[DtcItemOut]


def _own(db, vehicle_id: int, user_id: int) -> Vehicle:
    v = db.get(Vehicle, vehicle_id)
    if v is None or v.user_id != user_id:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "Авто не знайдено")
    return v


def seed_dictionary(db) -> None:
    """Додає у довідник типові коди, яких там ще немає."""
    have = set(db.scalars(select(DtcDictionary.code)).all())
    new = [DtcDictionary(code=c, description_uk=uk, description_en=en)
           for c, (uk, en) in CODES.items() if c not in have]
    if new:
        db.add_all(new)
        db.commit()


def _ensure_codes(db, codes: set[str]) -> dict[str, DtcDictionary]:
    rows = {r.code: r for r in db.scalars(select(DtcDictionary).where(DtcDictionary.code.in_(codes))).all()}
    for c in codes - rows.keys():
        uk, en = CODES.get(c) or describe_unknown(c)
        row = DtcDictionary(code=c, description_uk=uk, description_en=en)
        db.add(row)
        rows[c] = row
    db.flush()
    return rows


@router.post("/{vehicle_id}/dtc", response_model=DtcReportOut)
def save_report(vehicle_id: int, data: DtcReportIn, db: DbSession, user: CurrentUser):
    """Зберігає результат читання кодів. Коди, які були активні раніше, а тепер зникли, позначаються як зниклі."""
    _own(db, vehicle_id, user.id)
    now = datetime.now(timezone.utc)
    present = {c.code for c in data.codes}
    dic = _ensure_codes(db, present)

    open_events = db.scalars(select(DtcEvent).where(
        DtcEvent.vehicle_id == vehicle_id, DtcEvent.cleared_at.is_(None))).all()
    already = {e.code for e in open_events}
    for e in open_events:
        if e.code not in present:
            e.cleared_at = now          # у ЕБУ цього коду вже немає
    seen: set[tuple[str, str]] = set()
    for c in data.codes:
        if (c.code, c.kind) in seen:
            continue
        seen.add((c.code, c.kind))
        if c.code not in already:       # новий код
            db.add(DtcEvent(vehicle_id=vehicle_id, code=c.code, kind=c.kind, read_at=now))
    db.commit()

    items = [DtcItemOut(code=code, kind=kind, description_uk=dic[code].description_uk,
                        description_en=dic[code].description_en, read_at=now)
             for code, kind in sorted(seen)]
    return DtcReportOut(read_at=now, mil_on=data.mil_on, items=items)


@router.get("/{vehicle_id}/dtc", response_model=list[DtcItemOut])
def history(vehicle_id: int, db: DbSession, user: CurrentUser):
    """Історія кодів авто: спершу активні, далі зниклі (останні 50)."""
    _own(db, vehicle_id, user.id)
    rows = db.execute(
        select(DtcEvent, DtcDictionary).join(DtcDictionary, DtcDictionary.code == DtcEvent.code)
        .where(DtcEvent.vehicle_id == vehicle_id).order_by(DtcEvent.read_at.desc()).limit(50)).all()
    out = [DtcItemOut(code=e.code, kind=e.kind, description_uk=d.description_uk,
                      description_en=d.description_en, read_at=e.read_at, active=e.cleared_at is None)
           for e, d in rows]
    out.sort(key=lambda i: not i.active)
    return out
