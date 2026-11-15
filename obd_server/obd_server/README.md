# OBD Health: сервер (FastAPI + PostgreSQL)

## Запуск (Windows)

1. Відкрий командний рядок у папці `obd_server` і створи віртуальне середовище:
   ```
   python -m venv .venv
   .venv\Scripts\activate
   pip install -r requirements.txt
   ```
2. Скопіюй `.env.example` у `.env` і впиши пароль від `obd_admin` та `SECRET_KEY`.
   Ключ можна згенерувати так: `python -c "import secrets; print(secrets.token_hex(32))"`
3. Запусти сервер:
   ```
   uvicorn app.main:app --reload
   ```
4. Відкрий у браузері http://127.0.0.1:8000/docs. Це інтерактивна документація,
   з неї можна пробувати всі запити.

## Перевірка

1. `GET /health` має повернути `{"status":"ok","database":"connected"}`.
2. `POST /auth/register`: створи користувача (email і пароль від 8 символів).
3. Натисни кнопку **Authorize** вгорі сторінки: у полі username впиши email, у password пароль.
4. `POST /vehicles`: додай авто. Приклад тіла:
   ```json
   {"make": "Toyota", "model": "Corolla", "year": 2012, "fuel": "PETROL", "engine_vol_l": 1.6, "odometer_km": 148000}
   ```
5. `POST /trips`: відправ поїздку (потрібен `vehicle_id` з попереднього кроку і довільний UUID як `client_trip_id`).
   Повторна відправка з тим самим `client_trip_id` не створює дубль.

## Ендпоінти

| Метод | Адреса | Що робить |
|---|---|---|
| GET | /health | перевірка сервера й бази |
| POST | /auth/register | реєстрація |
| POST | /auth/login | вхід, повертає токен |
| GET | /auth/me | дані поточного користувача |
| GET, POST | /vehicles | список і додавання авто |
| GET | /vehicles/{id} | одне авто |
| POST | /trips | прийом поїздки з телефона (записи та події одним запитом) |
| GET | /trips?vehicle_id= | поїздки авто |
| GET | /trips/{id} | одна поїздка |

## Логіка оцінки

Поїздка зараховується в базовий профіль, якщо вона триває щонайменше 5 хвилин і має
щонайменше 200 записів (налаштування в `app/config.py`). Перші три зараховані поїздки
отримують режим `REFERENCE` (оцінка за довідковою нормою), далі `BLENDED`.
Розрахунок `health_index` та виявлення аномалій додаються пізніше.
