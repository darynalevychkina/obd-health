"""Пояснення прогнозу словами. Спершу мовна модель (Claude API), якщо є ключ; інакше шаблон."""
from __future__ import annotations

import json
import logging
import urllib.error
import urllib.request

log = logging.getLogger("uvicorn.error")

from ..config import AI_MODEL, ANTHROPIC_API_KEY, GEMINI_API_KEY, GEMINI_MODEL

SYSTEM = {
    "uk": (
        "Ти помічник з діагностики авто всередині застосунку. Ти отримуєш JSON з висновками, які вже "
        "порахувало програмне забезпечення. Поясни їх водієві-непрофесіоналу українською: 2-4 речення. "
        "Потім окремий рядок «Що перевірити:» і до 4 пунктів, кожен з нового рядка і починається з «- ». "
        "Не став категоричний діагноз, пиши «може вказувати на». Не вигадуй чисел, яких немає в даних. "
        "Якщо trend.stable = true, скажи, що показник стабільний і межа не загрожує, і не називай кількість поїздок. Не використовуй інше форматування."
    ),
    "en": (
        "You are a car diagnostics assistant inside an app. You receive JSON with findings already computed by "
        "software. Explain them to a non-expert driver in English: 2-4 sentences. Then a separate line "
        "'What to check:' and up to 4 items, each on its own line starting with '- '. Do not give a definitive "
        "diagnosis, say 'may indicate'. Do not invent numbers that are not in the data. If trend.stable is true, say the metric is stable and the limit is not at risk, and do not state a number of trips. No other formatting."
    ),
}

CHECKS = {
    "ltft": {
        "uk": ["Патрубки впуску на тріщини", "Датчик витрати повітря (MAF)", "Вакуумні шланги"],
        "en": ["Intake hoses for cracks", "Mass airflow (MAF) sensor", "Vacuum hoses"],
    },
    "coolant": {
        "uk": ["Рівень і стан антифризу", "Термостат і вентилятор радіатора", "Чистоту радіатора"],
        "en": ["Coolant level and condition", "Thermostat and radiator fan", "Radiator cleanliness"],
    },
    "battery": {
        "uk": ["Клеми й стан акумулятора", "Генератор і ремінь приводу"],
        "en": ["Battery terminals and condition", "Alternator and drive belt"],
    },
}

CODE_TEXT = {
    "COOLANT_HIGH": ("перегрів охолоджувача", "coolant overheating"),
    "COOLANT_LOW": ("двигун не виходить на робочу температуру", "engine does not reach operating temperature"),
    "WARMUP_SLOW": ("повільний прогрів", "slow warm-up"),
    "LTFT_HIGH": ("велика довгострокова корекція палива", "large long-term fuel trim"),
    "STFT_UNSTABLE": ("нестабільна суміш", "unstable air-fuel mixture"),
    "BATTERY_LOW": ("занижена напруга бортової мережі", "low board voltage"),
    "BATTERY_HIGH": ("завищена напруга бортової мережі", "high board voltage"),
    "IDLE_RPM": ("нестабільні оберти холостого ходу", "abnormal idle speed"),
    "ANOMALY_ML": ("поїздка відрізняється від звичної для цього авто", "trip differs from this car's usual pattern"),
}


def template_text(p: dict, locale: str) -> str:
    uk = locale != "en"
    findings = p["last_trip"]["findings"]
    trend = p.get("trend")
    parts = []
    if findings:
        names = []
        for f in findings:
            u, e = CODE_TEXT.get(f["code"], (f["code"], f["code"]))
            names.append(u if uk else e)
        names = list(dict.fromkeys(names))
        parts.append(("В останній поїздці виявлено: " if uk else "The last trip showed: ") + "; ".join(names) + ".")
    if trend and trend.get("stable"):
        if uk:
            parts.append(f"Показник «{trend['title']}» стабільний: помітного тренду до межі ({trend['threshold']}) немає.")
        else:
            parts.append(f"“{trend['title']}” is stable: no noticeable trend toward the limit ({trend['threshold']}).")
    elif trend and trend.get("slope") is not None:
        rising = trend["slope"] > 0
        n = trend.get("trips_to_limit")
        if uk:
            s = f"Показник «{trend['title']}» {'росте' if rising else 'падає'} приблизно на {abs(trend['slope']):.2f} за поїздку."
            if n is not None and n > 0:
                s += f" За такої динаміки межа ({trend['threshold']}) може бути досягнута приблизно через {n:.0f} поїздок."
            elif n == 0:
                s += f" Межу ({trend['threshold']}) уже досягнуто."
        else:
            s = f"“{trend['title']}” is {'rising' if rising else 'falling'} by about {abs(trend['slope']):.2f} per trip."
            if n is not None and n > 0:
                s += f" At this rate the limit ({trend['threshold']}) may be reached in about {n:.0f} trips."
            elif n == 0:
                s += f" The limit ({trend['threshold']}) has already been reached."
        parts.append(s)
    if not parts:
        parts.append("Помітних відхилень немає, показники в межах норми для цього авто." if uk
                     else "No noticeable deviations; readings are within the norm for this car.")
    checks = []
    if findings or (trend and (trend.get("trips_to_limit") is not None)):
        key = trend["metric"] if trend else None
        checks = CHECKS.get(key, {}).get("uk" if uk else "en", [])
    text = " ".join(parts)
    if checks:
        text += ("\n\nЩо перевірити:\n" if uk else "\n\nWhat to check:\n") + "\n".join(f"- {c}" for c in checks)
    return text


def _llm(p: dict, locale: str) -> str | None:
    if not ANTHROPIC_API_KEY:
        return None
    body = json.dumps({
        "model": AI_MODEL,
        "max_tokens": 500,
        "system": SYSTEM["en" if locale == "en" else "uk"],
        "messages": [{"role": "user", "content": json.dumps(p, ensure_ascii=False)}],
    }).encode("utf-8")
    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages", data=body, method="POST",
        headers={"x-api-key": ANTHROPIC_API_KEY, "anthropic-version": "2023-06-01",
                 "content-type": "application/json", "User-Agent": "OBD-Health/0.1"},
    )
    try:
        with urllib.request.urlopen(req, timeout=25) as r:
            data = json.loads(r.read().decode("utf-8"))
        text = "".join(b.get("text", "") for b in data.get("content", []) if b.get("type") == "text").strip()
        return text or None
    except Exception as e:
        log.warning("Claude API error: %s", e)
        return None


def _gemini(p: dict, locale: str) -> str | None:
    if not GEMINI_API_KEY:
        return None
    body = json.dumps({
        "systemInstruction": {"parts": [{"text": SYSTEM["en" if locale == "en" else "uk"]}]},
        "contents": [{"role": "user", "parts": [{"text": json.dumps(p, ensure_ascii=False)}]}],
        "generationConfig": {"maxOutputTokens": 1500, "temperature": 0.4},
    }).encode("utf-8")
    req = urllib.request.Request(
        f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent",
        data=body, method="POST",
        headers={"x-goog-api-key": GEMINI_API_KEY, "content-type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            data = json.loads(r.read().decode("utf-8"))
        parts = data["candidates"][0]["content"]["parts"]
        text = "".join(x.get("text", "") for x in parts).strip()
        return text or None
    except urllib.error.HTTPError as e:
        log.warning("Gemini API error %s: %s", e.code, e.read().decode("utf-8", "ignore")[:300])
        return None
    except Exception as e:
        log.warning("Gemini API error: %s", e)
        return None


def explain(p: dict, locale: str = "uk") -> str:
    return _gemini(p, locale) or _llm(p, locale) or template_text(p, locale)
