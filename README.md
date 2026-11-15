# OBD Health

Веб-застосунок для моніторингу технічного стану автомобіля та виявлення аномалій
за допомогою алгоритмів машинного навчання (курсова робота).

ELM327 (Bluetooth) → Android-застосунок → FastAPI → PostgreSQL; на сервері правила + Isolation Forest
дають індекс здоров'я 0–100, а ШІ (Gemini/Claude) пояснює прогноз.

| Папка | Вміст |
|---|---|
| `app/` | Android-застосунок (Kotlin, Jetpack Compose) |
| `obd_server/obd_server/` | Сервер (FastAPI, SQLAlchemy, ML); запуск і розгортання: `README.md`, `DEPLOY.md` |
| `sql/obd_schema.sql` | Схема бази PostgreSQL |
| `render.yaml` | Конфігурація розгортання на Render |

Ключі (`.env`) у репозиторій не потрапляють; зразок у `obd_server/obd_server/.env.example`.
