from sqlalchemy import create_engine
from sqlalchemy.orm import DeclarativeBase, sessionmaker

from .config import DATABASE_URL

# pool_pre_ping + pool_recycle: безкоштовна база (Neon) засинає, тож старі з'єднання перевіряємо й оновлюємо
engine = create_engine(DATABASE_URL, pool_pre_ping=True, pool_recycle=240, pool_size=5, max_overflow=5)
SessionLocal = sessionmaker(bind=engine, autoflush=False)


class Base(DeclarativeBase):
    pass
