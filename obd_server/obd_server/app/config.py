import os

from dotenv import load_dotenv

load_dotenv()

_db = os.environ["DATABASE_URL"].strip()
# Neon/Render дають рядок виду postgresql://... або postgres://...: драйверу psycopg 3 потрібен префікс postgresql+psycopg://
for _old in ("postgres://", "postgresql://"):
    if _db.startswith(_old):
        _db = "postgresql+psycopg://" + _db[len(_old):]
        break
DATABASE_URL = _db
SECRET_KEY = os.environ["SECRET_KEY"]
# Ключі зовнішніх довідників (необов'язкові): VIN через auto.dev, держномер через baza-gai.com.ua
AUTODEV_API_KEY = os.getenv("AUTODEV_API_KEY", "")
BAZAGAI_API_KEY = os.getenv("BAZAGAI_API_KEY", "")
# ШІ-пояснення прогнозу (необов'язково): ключ Anthropic API. Без ключа працює запасний шаблонний текст.
# Безкоштовна альтернатива: ключ Google Gemini (https://aistudio.google.com/apikey). Якщо задано, використовується першим.
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "")
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-3.8-flash")
ANTHROPIC_API_KEY = os.getenv("ANTHROPIC_API_KEY", "")
AI_MODEL = os.getenv("AI_MODEL", "claude-haiku-5-5")
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.getenv("ACCESS_TOKEN_EXPIRE_MINUTES", "1440"))

# Поїздка зараховується в базовий профіль, якщо вона достатньо довга й має достатньо записів
MIN_BASELINE_DURATION_S = 5 * 60
MIN_BASELINE_READINGS = 200
# Скільки поїздок оцінюємо лише за довідковою нормою, перш ніж підключити власний профіль
REFERENCE_TRIPS = 3
