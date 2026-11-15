"""Колір авто: з тексту довідника (укр/рос/англ) у коротку назву й HEX для малюнка в застосунку."""
from __future__ import annotations

# (назва, hex, ключові слова в нижньому регістрі)
_COLORS = [
    ("white", "#F1F1EE", ("white", "біл", "бел")),
    ("black", "#1D1F22", ("black", "чорн", "черн")),
    ("silver", "#B8BDC4", ("silver", "срібл", "серебр")),
    ("gray", "#7D838B", ("gray", "grey", "сір", "сер", "graphite", "графіт", "графит")),
    ("red", "#D63B34", ("red", "червон", "красн")),
    ("burgundy", "#7A1F2B", ("burgundy", "bordeaux", "вишнев", "бордов", "бордо", "maroon")),
    ("orange", "#EE8A22", ("orange", "помаранч", "оранж")),
    ("yellow", "#EDC72A", ("yellow", "жовт", "желт", "gold", "золот")),
    ("green", "#2E8B57", ("green", "зелен")),
    ("blue", "#2F6FD6", ("blue", "синій", "синя", "синє", "синь", "синий", "синяя", "синее", "голуб", "блакит", "cyan")),
    ("brown", "#7A5230", ("brown", "коричн", "бронз", "bronze")),
    ("beige", "#D8C7A0", ("beige", "беж", "кремов", "cream", "пісоч", "песоч")),
    ("purple", "#7C4DB8", ("purple", "violet", "фіолет", "фиолет", "бузков", "сиренев")),
    ("pink", "#E58BB0", ("pink", "рожев", "розов")),
]


def normalize_color(*texts) -> str | None:
    """Приймає будь-які фрагменти (рядки, значення словника) і повертає назву кольору або None."""
    blob = " ".join(str(t).lower() for t in texts if t)
    if not blob:
        return None
    # перевага довшим/специфічнішим збігам (вишневий раніше за червоний)
    for name, _hex, keys in sorted(_COLORS, key=lambda c: c[0] != "burgundy"):
        if any(k in blob for k in keys):
            return name
    return None


def color_hex(name: str | None) -> str | None:
    if not name:
        return None
    for n, h, _ in _COLORS:
        if n == name:
            return h
    return None
