import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI
from sqlalchemy import text

from .db import engine
from .routers import auth, catalog, trips, vehicles


@asynccontextmanager
async def lifespan(_app: FastAPI):
    # невелика авто-міграція: колір авто (з пошуку за номером)
    try:
        with engine.begin() as conn:
            conn.execute(text("ALTER TABLE vehicles ADD COLUMN IF NOT EXISTS color TEXT"))
    except Exception as e:  # база може бути ще порожньою (схему не застосовано) або недоступною
        logging.getLogger("uvicorn.error").warning("Авто-міграція пропущена: %s", e)
    yield


app = FastAPI(title="OBD Health API", version="0.1.0", lifespan=lifespan)

app.include_router(auth.router)
app.include_router(vehicles.router)
app.include_router(trips.router)
app.include_router(catalog.router)


@app.get("/health", tags=["service"])
def health():
    """Перевірка, що сервер працює і бачить базу даних."""
    with engine.connect() as conn:
        conn.execute(text("SELECT 1"))
    return {"status": "ok", "database": "connected"}
