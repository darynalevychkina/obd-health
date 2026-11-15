"""Довідник авто: марки, моделі та розшифровка VIN через відкриту базу NHTSA vPIC.
Результати кешуються в пам'яті, щоб не смикати зовнішній сервіс щоразу."""
import json
import re
import urllib.error
import urllib.parse
import urllib.request
from functools import lru_cache

from fastapi import APIRouter, HTTPException, status
from pydantic import BaseModel

from ..colors import color_hex, normalize_color
from ..config import AUTODEV_API_KEY, BAZAGAI_API_KEY
from ..deps import CurrentUser

router = APIRouter(prefix="/catalog", tags=["catalog"])

VPIC = "https://vpic.nhtsa.dot.gov/api/vehicles"
VIN_RE = re.compile(r"^[A-HJ-NPR-Z0-9]{17}$")


def _get(url: str, headers: dict | None = None, not_found_ok: bool = False) -> dict | None:
    # багато сервісів блокують стандартний User-Agent Python, тому представляємося браузером
    hdrs = {"User-Agent": "Mozilla/5.0 (OBD Health)", **(headers or {})}
    req = urllib.request.Request(url, headers=hdrs)
    try:
        with urllib.request.urlopen(req, timeout=12) as r:
            return json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        if e.code == 404 and not_found_ok:
            return None
        host = urllib.parse.urlparse(url).netloc
        if e.code in (401, 403):
            raise HTTPException(status.HTTP_502_BAD_GATEWAY,
                                f"{host} відхилив запит (код {e.code}): ключ недійсний, вичерпано ліміт або доступ заборонено")
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, f"{host} недоступний (код {e.code})")
    except Exception:
        raise HTTPException(status.HTTP_502_BAD_GATEWAY, "Довідник авто тимчасово недоступний")


def _pretty(name: str) -> str:
    """TOYOTA -> Toyota, BMW -> BMW, MERCEDES-BENZ -> Mercedes-Benz"""
    name = name.strip()
    if len(name) <= 3:
        return name.upper()
    return re.sub(r"[A-Za-z]+", lambda m: m.group(0).capitalize(), name)


@lru_cache(maxsize=1)
def _makes() -> tuple[str, ...]:
    data = _get(f"{VPIC}/GetMakesForVehicleType/car?format=json")
    names = {_pretty(x["MakeName"]) for x in data.get("Results", []) if x.get("MakeName")}
    return tuple(sorted(names))


@lru_cache(maxsize=256)
def _models(make: str) -> tuple[str, ...]:
    url = f"{VPIC}/GetModelsForMake/{urllib.parse.quote(make)}?format=json"
    data = _get(url)
    names = {x["Model_Name"].strip() for x in data.get("Results", []) if x.get("Model_Name")}
    return tuple(sorted(names))


@router.get("/makes", response_model=list[str])
def makes(user: CurrentUser):
    return list(_makes())


@router.get("/models", response_model=list[str])
def models(make: str, user: CurrentUser):
    return list(_models(make.strip().lower()))


class VinOut(BaseModel):
    vin: str
    make: str | None = None
    model: str | None = None
    year: int | None = None
    fuel: str | None = None          # PETROL / DIESEL / HYBRID / ELECTRIC / LPG
    engine_vol_l: float | None = None
    color: str | None = None
    color_hex: str | None = None


def _fuel(row: dict) -> str | None:
    primary = (row.get("FuelTypePrimary") or "").lower()
    elec = (row.get("ElectrificationLevel") or "").lower()
    if "hev" in elec or "hybrid" in elec:
        return "HYBRID"
    if "diesel" in primary:
        return "DIESEL"
    if "electric" in primary:
        return "ELECTRIC"
    if "gasoline" in primary or "flex" in primary:
        return "PETROL"
    if "liquefied" in primary or "propane" in primary or "natural gas" in primary:
        return "LPG"
    return None


def _fuel_from_text(text: str) -> str | None:
    t = text.lower()
    if "hybrid" in t or "phev" in t or "hev" in t.split():
        return "HYBRID"
    if "diesel" in t or "дизел" in t:
        return "DIESEL"
    if "electric" in t or "bev" in t.split() or "електро" in t:
        return "ELECTRIC"
    if "lpg" in t or "propane" in t or "gas" in t and "gasoline" not in t or "газ" in t:
        return "LPG"
    if "gasoline" in t or "petrol" in t or "бензин" in t or "ffv" in t:
        return "PETROL"
    return None


def _decode_autodev(vin: str) -> VinOut | None:
    data = _get(f"https://api.auto.dev/vin/{vin}?ambiguity=true",
                headers={"Authorization": f"Bearer {AUTODEV_API_KEY}", "Accept": "application/json"},
                not_found_ok=True)
    if not data:
        return None
    veh = data.get("vehicle") or {}
    make = data.get("make") or veh.get("make")
    model = data.get("model") or veh.get("model")
    year = veh.get("year")
    engine = str(data.get("engine") or "")
    m = re.search(r"(\d\.\d)\s*L", engine)
    return VinOut(vin=vin, make=make, model=model,
                  year=int(year) if str(year or "").isdigit() else None,
                  fuel=_fuel_from_text(engine), engine_vol_l=float(m.group(1)) if m else None)


def _decode_nhtsa(vin: str) -> VinOut:
    data = _get(f"{VPIC}/DecodeVinValues/{vin}?format=json")
    rows = data.get("Results") or [{}]
    row = rows[0]
    make = (row.get("Make") or "").strip()
    model = (row.get("Model") or "").strip()
    year = int(row["ModelYear"]) if str(row.get("ModelYear") or "").isdigit() else None
    try:
        vol = round(float(row.get("DisplacementL")), 1) if row.get("DisplacementL") else None
    except ValueError:
        vol = None
    return VinOut(vin=vin, make=_pretty(make) if make else None, model=model or None,
                  year=year, fuel=_fuel(row), engine_vol_l=vol)


@lru_cache(maxsize=512)
def _decode(vin: str) -> VinOut:
    """Спершу auto.dev (якщо є ключ), інакше або якщо там нічого немає, відкрита база NHTSA."""
    if AUTODEV_API_KEY:
        try:
            out = _decode_autodev(vin)
            if out and out.make and out.model and out.year:
                return out
        except HTTPException:
            pass
    return _decode_nhtsa(vin)


@router.get("/vin/{vin}", response_model=VinOut)
def decode_vin(vin: str, user: CurrentUser):
    vin = vin.strip().upper()
    if not VIN_RE.match(vin):
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY,
                            "VIN має містити 17 символів (латиниця й цифри, без I, O, Q)")
    out = _decode(vin)
    if not out.make and not out.model:
        raise HTTPException(status.HTTP_404_NOT_FOUND, "За цим VIN нічого не знайдено")
    # колір декодери VIN не віддають: пробуємо взяти його в базі ГАІ
    if not out.color and BAZAGAI_API_KEY:
        out.color = _gai_vin_color(vin)
        out.color_hex = color_hex(out.color)
    return out


# ---------- пошук за державним номером (baza-gai.com.ua) ----------
_CYR_TO_LAT = str.maketrans("АВСЕНІКМОРТХ", "ABCEHIKMOPTX")


def _norm_plate(p: str) -> str:
    return re.sub(r"[^A-Z0-9]", "", p.upper().translate(_CYR_TO_LAT))


def _gai_get(path: str) -> dict | None:
    return _get(f"https://baza-gai.com.ua/{path}",
                headers={"Accept": "application/json", "X-Api-Key": BAZAGAI_API_KEY},
                not_found_ok=True)


@lru_cache(maxsize=512)
def _gai_vin_color(vin: str) -> str | None:
    """Колір авто за VIN з бази ГАІ. Будь-яка помилка означає просто «кольору немає»."""
    try:
        data = _gai_get(f"vin/{vin}")
    except Exception:
        return None
    if not data:
        return None
    ops = data.get("operations") or []
    op = ops[0] if ops and isinstance(ops[0], dict) else {}
    craw = op.get("color") or data.get("color")
    return normalize_color(*(craw.values() if isinstance(craw, dict) else [craw]))


@lru_cache(maxsize=512)
def _plate_lookup(plate: str) -> VinOut | None:
    data = _gai_get(f"nomer/{plate}")
    if not data:
        return None
    ops = data.get("operations") or []
    op = ops[0] if ops and isinstance(ops[0], dict) else {}
    fuel_raw = op.get("fuel")
    fuel_txt = " ".join(str(v) for v in fuel_raw.values()) if isinstance(fuel_raw, dict) else str(fuel_raw or "")
    vol = op.get("engine_volume") or op.get("engine_vol") or op.get("volume")
    try:
        vol = round(float(vol), 1) if vol else None
    except (TypeError, ValueError):
        vol = None
    # за потреби об'єм у куб. см переводимо в літри
    if vol and vol > 20:
        vol = round(vol / 1000, 1)
    year = data.get("model_year") or data.get("year")
    vin = data.get("vin")
    craw = op.get("color") or data.get("color")
    cname = normalize_color(*(craw.values() if isinstance(craw, dict) else [craw]))
    return VinOut(
        vin=vin if isinstance(vin, str) and VIN_RE.match(vin.upper()) else "",
        make=_pretty(str(data.get("vendor"))) if data.get("vendor") else None,
        model=str(data.get("model")) if data.get("model") else None,
        year=int(year) if str(year or "").isdigit() else None,
        fuel=_fuel_from_text(fuel_txt),
        engine_vol_l=vol,
        color=cname,
        color_hex=color_hex(cname),
    )


@router.get("/plate/{plate}", response_model=VinOut)
def lookup_plate(plate: str, user: CurrentUser):
    if not BAZAGAI_API_KEY:
        raise HTTPException(status.HTTP_503_SERVICE_UNAVAILABLE, "Пошук за номером не налаштовано на сервері")
    p = _norm_plate(plate)
    if not 5 <= len(p) <= 10:
        raise HTTPException(status.HTTP_422_UNPROCESSABLE_ENTITY, "Некоректний державний номер")
    out = _plate_lookup(p)
    if out is None or not (out.make or out.model):
        raise HTTPException(status.HTTP_404_NOT_FOUND, "За цим номером нічого не знайдено")
    return out
