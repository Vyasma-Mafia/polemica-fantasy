from __future__ import annotations

import hashlib
import json
import re
import unicodedata
from dataclasses import dataclass


ANNOUNCEMENT_TAG_RE = re.compile(r"(?<!\w)#анонс_(зл|лп)(?!\w)")
RESULT_TAG_RE = re.compile(r"(?<!\w)#результаты_(зл|лп)(?!\w)")
RUSSIAN_MONTH_RE = r"(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)"
DATE_RE = re.compile(
    rf"(?<!\d)(?:"
    rf"[0-3]?\d[./-][01]?\d(?:[./-](?:20)?\d{{2}})?"
    rf"|(?:[1-9]|[12]\d|3[01])\s+{RUSSIAN_MONTH_RE}(?:\s+20\d{{2}})?"
    rf")(?!\d)"
)
TIME_RE = re.compile(r"(?<!\d)(?:[01]?\d|2[0-3])[:.]\d{2}(?!\d)")
GAME_RE = re.compile(r"\bигра\s*(?:№|no)?\s*\d+\b")
WINNER_RE = re.compile(r"(?:\bпобеда\s*:?\s*(?:мафия|мафии|мирные|мирных)\b|\bпобедили\s+(?:мафия|мирные|мирные жители)\b|\bwinner\s*:?\s*[a-zа-яё]+)")
CLASSIFIER_RULE_VERSION = "2"


@dataclass(frozen=True)
class Classification:
    kind: str
    league: str | None
    reason: str


def normalize(text: str) -> str:
    return " ".join(unicodedata.normalize("NFKC", text or "").casefold().split())


def classify(text: str) -> Classification:
    value = normalize(text)
    announcement_tags = ANNOUNCEMENT_TAG_RE.findall(value)
    result_tags = RESULT_TAG_RE.findall(value)
    if len(announcement_tags) + len(result_tags) != 1:
        return Classification("IGNORE", None, "expected exactly one supported import hashtag")
    result_tag = RESULT_TAG_RE.search(value)
    if result_tag and GAME_RE.search(value) and WINNER_RE.search(value):
        league = result_tag.group(1).upper()
        return Classification("RESULT", league, "league tag plus game and winner markers")
    announcement_tag = ANNOUNCEMENT_TAG_RE.search(value)
    if announcement_tag and DATE_RE.search(value) and TIME_RE.search(value):
        league = announcement_tag.group(1).upper()
        return Classification("ANNOUNCEMENT", league, "league tag plus date and time")
    league = (result_tag or announcement_tag)
    if league:
        return Classification("IGNORE", league.group(1).upper(), "insufficient announcement/result structure")
    return Classification("IGNORE", None, "missing supported import hashtag")


def fingerprint(payload: dict[str, object]) -> str:
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()
