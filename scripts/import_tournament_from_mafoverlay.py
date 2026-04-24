#!/usr/bin/env python3
"""
Импорт ростера турнира из страницы MafOverlay (участники + фото с их CDN/S3).

Источник разметки — overlay-проект: admin/participants.html рендерит карточки с
data-photo-url на role-indicator MAIN (см. ../overlay относительно каталога mafia).

Примеры URL страницы:
  https://mafoverlay.ru/admin/photos/tournaments/POLEMICA/5029

Важно: Admin API Fantasy ожидает polemica_user_id — скрипт рассчитан на source=POLEMICA
(число после # в карточке = id игрока Полемики). Для GOMAFIA/MAFIAUNIVERSE страницы MafOverlay
формат другой; при необходимости расширьте парсер под их разметку.

Режимы:
  create — новый турнир в Polemica Fantasy + участники и фото
  update — существующий fantasy-турнир (--fantasy-tournament-id): добавить новых,
           опционально подтянуть фото с MafOverlay для тех, у кого в Fantasy ещё нет
           фото (--refresh-photos; уже существующие в системе фото не перезаписываются)
  reprocess-photos — для уже загруженных: скачать текущее photoUrl в Fantasy, убрать фон
           (rembg), залить снова (нужен pip install rembg onnxruntime; без MafOverlay)

Существующие фотографии: если у игрока в Fantasy уже задана фотография (в т.ч. из прошлого
турнира, т.к. она хранится на Fantasy-игроке), скрипт не скачивает с MafOverlay и не
перезаписывает. В конце (stderr) — предупреждения по ростеру, у кого в Fantasy нет фото
и краткое [MafOverlay] при --dry-run, если в разметке нет ссылки на снимок.

Требования: pip install -r scripts/requirements-import-mafoverlay.txt
Опция --remove-bg: дополнительно pip install rembg onnxruntime (тяжёлые пакеты)

Переменные окружения (как в import_closed_league_from_html.py):
  FANTASY_API_BASE, ADMIN_USERNAME, ADMIN_PASSWORD
  MAFOVERLAY_BASE — базовый URL MafOverlay (по умолчанию https://mafoverlay.ru)
"""

from __future__ import annotations

import argparse
import html as html_module
import os
import re
import sys
import time
from typing import Any
from urllib.parse import urlparse

import requests

PLAYER_CARD_RE = re.compile(r'<div class="player-card">', re.I)
MAIN_PHOTO_RE = re.compile(
    r'data-photo-type="MAIN"[^>]*\sdata-photo-url="([^"]+)"',
    re.I,
)
# Альтернативный порядок атрибутов
MAIN_PHOTO_RE_ALT = re.compile(
    r'data-photo-url="([^"]+)"[^>]*data-photo-type="MAIN"',
    re.I,
)
NAME_BLOCK_RE = re.compile(
    r'class="player-card__name"[^>]*>([\s\S]*?)</a>',
    re.I,
)
NICKNAME_RE = re.compile(r'<span style="color:\s*black;">([^<]+)</span>', re.I)
POLEMICA_ID_RE = re.compile(r"#(\d+)\s*</span>", re.I)

CT_AND_EXT = {
    "image/jpeg": ("image/jpeg", "avatar.jpg"),
    "image/png": ("image/png", "avatar.png"),
    "image/webp": ("image/webp", "avatar.webp"),
}


def parse_participants_from_mafoverlay_html(html: str) -> list[dict[str, Any]]:
    """Извлекает polemicaUserId, nickname, photo_url (если есть) из HTML страницы."""
    chunks = PLAYER_CARD_RE.split(html)
    seen: set[int] = set()
    out: list[dict[str, Any]] = []
    for part in chunks:
        part = part.strip()
        if not part:
            continue
        m_name = NAME_BLOCK_RE.search(part)
        if not m_name:
            continue
        inner = m_name.group(1)
        mn = NICKNAME_RE.search(inner)
        mid = POLEMICA_ID_RE.search(inner)
        if not mn or not mid:
            continue
        uid = int(mid.group(1))
        if uid in seen:
            continue
        nickname = html_module.unescape(mn.group(1).strip())
        if not nickname:
            continue
        mp = MAIN_PHOTO_RE.search(part) or MAIN_PHOTO_RE_ALT.search(part)
        photo_url = html_module.unescape(mp.group(1).strip()) if mp else None
        seen.add(uid)
        out.append(
            {
                "polemicaUserId": uid,
                "nickname": nickname,
                "photoUrl": photo_url,
            }
        )
    return out


def _player_has_photo_in_fantasy(tp: dict[str, Any]) -> bool:
    u = tp.get("photoUrl")
    return u is not None and str(u).strip() != ""


def _warn_mafoverlay_missing_photo_links(roster: list[dict[str, Any]]) -> None:
    for row in roster:
        if not row.get("photoUrl"):
            print(
                f"  [MafOverlay] нет ссылки на фото: polemica={row['polemicaUserId']} {row['nickname']!r}",
                file=sys.stderr,
            )


def _report_fantasy_missing_photos(
    api: str,
    session: requests.Session,
    tournament_id: int,
    roster: list[dict[str, Any]],
) -> int:
    """Сравнивает ростер MafOverlay с ответом GET турнира; предупреждает, у кого в Fantasy нет фото."""
    r = session.get(f"{api}/api/v1/admin/tournaments/{tournament_id}", timeout=60)
    if not r.ok:
        print(
            f"  Не удалось проверить фото: GET tournament HTTP {r.status_code} {r.text[:300]}",
            file=sys.stderr,
        )
        return 0
    by_pid = {p["polemicaUserId"]: p for p in r.json().get("players", [])}
    n = 0
    for row in roster:
        pid = row["polemicaUserId"]
        p = by_pid.get(pid)
        if not p:
            print(
                f"  ВНИМАНИЕ: игрока нет в турнире (не найден в Fantasy): polemica={pid} {row['nickname']!r}",
                file=sys.stderr,
            )
            continue
        if not _player_has_photo_in_fantasy(p):
            n += 1
            reason = "нет URL в MafOverlay" if not row.get("photoUrl") else "загрузка с MafOverlay не сработала"
            print(
                f"  ВНИМАНИЕ: в Fantasy нет фотографии ({reason}): polemica={pid} {row['nickname']!r} tournamentPlayerId={p.get('id')}",
                file=sys.stderr,
            )
    return n


def guess_image_type(data: bytes, response_ct: str | None) -> tuple[str, str] | None:
    ct = (response_ct or "").split(";")[0].strip().lower()
    if ct in CT_AND_EXT:
        return CT_AND_EXT[ct]
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return CT_AND_EXT["image/png"]
    if data[:2] == b"\xff\xd8":
        return CT_AND_EXT["image/jpeg"]
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return CT_AND_EXT["image/webp"]
    return None


def maybe_remove_background(image_bytes: bytes) -> bytes:
    try:
        from rembg import remove  # type: ignore[import-untyped]
    except ImportError:
        print(
            "Для --remove-bg установите: pip install rembg onnxruntime",
            file=sys.stderr,
        )
        return image_bytes
    return remove(image_bytes)


def fetch_image(url: str, session: requests.Session, delay: float) -> tuple[bytes, str | None] | None:
    time.sleep(delay)
    try:
        r = session.get(url, timeout=90, allow_redirects=True)
        if not r.ok:
            print(f"    GET {url}: HTTP {r.status_code}", file=sys.stderr)
            return None
        return r.content, r.headers.get("Content-Type")
    except requests.RequestException as e:
        print(f"    GET {url}: {e}", file=sys.stderr)
        return None


def upload_player_photo(
    api: str,
    session: requests.Session,
    tournament_id: int,
    tournament_player_id: int,
    image_bytes: bytes,
    content_type: str,
    filename: str,
    delay: float,
) -> bool:
    time.sleep(delay)
    files = {"file": (filename, image_bytes, content_type)}
    ur = session.post(
        f"{api}/api/v1/admin/tournaments/{tournament_id}/players/{tournament_player_id}/photo",
        files=files,
        timeout=120,
    )
    if not ur.ok:
        print(f"    фото upload: HTTP {ur.status_code} {ur.text}", file=sys.stderr)
        return False
    return True


def _rembg_import() -> bool:
    try:
        import rembg  # noqa: F401
    except ImportError:
        return False
    return True


def run_reprocess_photos(args: argparse.Namespace) -> int:
    """
    Скачать существующие фото в турнире по photoUrl (публичный URL из API), убрать фон, загрузить.
    """
    if not _rembg_import():
        print(
            "reprocess-photos: установите: pip install rembg onnxruntime",
            file=sys.stderr,
        )
        return 1

    api = args.api_base.rstrip("/")
    s = requests.Session()
    s.auth = (args.user, args.password)
    s.headers.setdefault("User-Agent", "polemica-fantasy-mafoverlay-import/1.0")

    ftid = args.fantasy_tournament_id
    detail = s.get(f"{api}/api/v1/admin/tournaments/{ftid}", timeout=60)
    if not detail.ok:
        print(f"GET tournament: HTTP {detail.status_code} {detail.text[:500]}", file=sys.stderr)
        return 1

    players: list[dict[str, Any]] = detail.json().get("players", [])
    if not players:
        print("В турнире нет участников.", file=sys.stderr)
        return 0

    img_session = requests.Session()
    img_session.headers.setdefault("User-Agent", s.headers.get("User-Agent", "polemica-fantasy/1.0"))
    n_ok = 0
    n_skip = 0
    n_fail = 0

    for p in players:
        nickname = p.get("nickname", "")
        tpid = p.get("id")
        url = p.get("photoUrl")
        if not _player_has_photo_in_fantasy(p) or not url:
            print(f"  пропуск: нет фото: tournamentPlayerId={tpid} {nickname!r}")
            n_skip += 1
            continue
        u = str(url).strip()
        if urlparse(u).scheme not in ("http", "https"):
            print(f"  пропуск: неверный photoUrl: tournamentPlayerId={tpid} {u!r}", file=sys.stderr)
            n_fail += 1
            continue

        got = fetch_image(u, img_session, args.delay)
        if not got:
            n_fail += 1
            continue
        data, _rct = got
        data = maybe_remove_background(data)
        content_type, filename = "image/png", "avatar.png"
        if upload_player_photo(
            api,
            s,
            ftid,
            int(tpid),
            data,
            content_type,
            filename,
            args.delay,
        ):
            n_ok += 1
            print(f"  фон убран и перезалито: {nickname!r} (tournamentPlayerId={tpid})")
        else:
            n_fail += 1

    print(
        f"Готово: перезалито с rembg: {n_ok}, без фото: {n_skip}, ошибок: {n_fail}.",
    )
    return 0 if n_fail == 0 else 1


def run_create(args: argparse.Namespace) -> int:
    mafoverlay_base = args.mafoverlay_base.rstrip("/")
    path = f"/admin/photos/tournaments/{args.source}/{args.competition_id}"
    page_url = f"{mafoverlay_base}{path}"

    auth_ov = None
    if args.mafoverlay_user:
        auth_ov = (args.mafoverlay_user, args.mafoverlay_password or "")

    ms = requests.Session()
    ms.headers.setdefault("User-Agent", "polemica-fantasy-mafoverlay-import/1.0")
    if args.mafoverlay_cookie:
        ms.headers["Cookie"] = args.mafoverlay_cookie

    r = ms.get(page_url, auth=auth_ov, timeout=60)
    if not r.ok:
        print(f"MafOverlay страница: HTTP {r.status_code} {r.text[:500]}", file=sys.stderr)
        return 1

    players = parse_participants_from_mafoverlay_html(r.text)
    if not players:
        print("Участники не распознаны. Проверьте URL, доступ (логин/cookie) и разметку.", file=sys.stderr)
        return 1

    print(f"С MafOverlay загружено участников: {len(players)}")
    if args.dry_run:
        for row in players:
            print(f"  {row['polemicaUserId']:8d}  {row['nickname']!r}  {row['photoUrl']}")
        _warn_mafoverlay_missing_photo_links(players)
        return 0

    api = args.api_base.rstrip("/")
    auth = (args.user, args.password)
    s = requests.Session()
    s.auth = auth
    s.headers.setdefault("User-Agent", ms.headers["User-Agent"])

    kind = args.kind
    polemica_competition_id = args.polemica_competition_id
    if polemica_competition_id is None and kind == "POLEMICA_COMPETITION":
        polemica_competition_id = args.competition_id

    body: dict[str, Any] = {
        "name": args.tournament_name,
        "description": args.description,
        "status": args.status,
        "kind": kind,
    }
    if kind == "POLEMICA_COMPETITION":
        body["polemicaCompetitionId"] = polemica_competition_id

    tr = s.post(f"{api}/api/v1/admin/tournaments", json=body, timeout=60)
    if not tr.ok:
        print(f"Создание турнира: HTTP {tr.status_code} {tr.text}", file=sys.stderr)
        return 1
    tournament = tr.json()
    tid = tournament["id"]
    print(f"Турнир создан: id={tid} name={tournament.get('name')!r}")

    return _sync_players(api, s, tid, players, args)


def run_update(args: argparse.Namespace) -> int:
    mafoverlay_base = args.mafoverlay_base.rstrip("/")
    page_url = f"{mafoverlay_base}/admin/photos/tournaments/{args.source}/{args.competition_id}"

    auth_ov = None
    if args.mafoverlay_user:
        auth_ov = (args.mafoverlay_user, args.mafoverlay_password or "")

    ms = requests.Session()
    ms.headers.setdefault("User-Agent", "polemica-fantasy-mafoverlay-import/1.0")
    if args.mafoverlay_cookie:
        ms.headers["Cookie"] = args.mafoverlay_cookie

    r = ms.get(page_url, auth=auth_ov, timeout=60)
    if not r.ok:
        print(f"MafOverlay страница: HTTP {r.status_code}", file=sys.stderr)
        return 1

    players = parse_participants_from_mafoverlay_html(r.text)
    if not players:
        print("Участники не распознаны.", file=sys.stderr)
        return 1

    print(f"С MafOverlay: {len(players)} участников")

    if args.dry_run:
        for row in players:
            print(f"  {row['polemicaUserId']:8d}  {row['nickname']!r}  {row['photoUrl']}")
        _warn_mafoverlay_missing_photo_links(players)
        return 0

    api = args.api_base.rstrip("/")
    s = requests.Session()
    s.auth = (args.user, args.password)
    s.headers.setdefault("User-Agent", ms.headers["User-Agent"])

    ftid = args.fantasy_tournament_id
    detail = s.get(f"{api}/api/v1/admin/tournaments/{ftid}", timeout=60)
    if not detail.ok:
        print(f"GET tournament: HTTP {detail.status_code} {detail.text}", file=sys.stderr)
        return 1

    existing_by_pid = {p["polemicaUserId"]: p for p in detail.json().get("players", [])}

    # Добавить отсутствующих
    to_add = [row for row in players if row["polemicaUserId"] not in existing_by_pid]
    for row in to_add:
        time.sleep(args.delay)
        body = {"polemicaUserId": row["polemicaUserId"], "nickname": row["nickname"]}
        pr = s.post(f"{api}/api/v1/admin/tournaments/{ftid}/players", json=body, timeout=60)
        if not pr.ok:
            print(f"Добавление {row['polemicaUserId']}: HTTP {pr.status_code} {pr.text}", file=sys.stderr)
            continue
        tp = pr.json()
        existing_by_pid[row["polemicaUserId"]] = tp
        print(f"  + новый игрок tournamentPlayerId={tp['id']} polemica={row['polemicaUserId']} {row['nickname']!r}")

    # Обновить деталь после добавлений
    if to_add:
        detail = s.get(f"{api}/api/v1/admin/tournaments/{ftid}", timeout=60)
        if detail.ok:
            existing_by_pid = {p["polemicaUserId"]: p for p in detail.json().get("players", [])}

    if args.refresh_photos:
        photo_targets = list(players)
    else:
        photo_targets = list(to_add)
        if not photo_targets:
            print(
                "Новых участников нет. Укажите --refresh-photos, чтобы подтянуть фото с MafOverlay у тех, у кого в Fantasy ещё нет (уже существующие в системе фото не перезаписываются).",
            )

    if args.no_photos:
        print("Готово (--no-photos).")
        _report_fantasy_missing_photos(api, s, ftid, players)
        return 0

    if not photo_targets:
        _report_fantasy_missing_photos(api, s, ftid, players)
        return 0

    return _upload_photos_for_rows(
        api, s, ftid, photo_targets, existing_by_pid, args, full_roster=players
    )


def _sync_players(
    api: str,
    session: requests.Session,
    tournament_id: int,
    players: list[dict[str, Any]],
    args: argparse.Namespace,
) -> int:
    existing: dict[int, dict[str, Any]] = {}
    for row in players:
        time.sleep(args.delay)
        body = {"polemicaUserId": row["polemicaUserId"], "nickname": row["nickname"]}
        pr = session.post(
            f"{api}/api/v1/admin/tournaments/{tournament_id}/players",
            json=body,
            timeout=60,
        )
        if not pr.ok:
            print(
                f"Игрок {row['polemicaUserId']} {row['nickname']!r}: HTTP {pr.status_code} {pr.text}",
                file=sys.stderr,
            )
            continue
        tp = pr.json()
        existing[row["polemicaUserId"]] = tp
        print(f"  + игрок tournamentPlayerId={tp['id']} polemica={row['polemicaUserId']} {row['nickname']!r}")

    if args.no_photos:
        print("Готово (--no-photos).")
        _report_fantasy_missing_photos(api, session, tournament_id, players)
        return 0
    return _upload_photos_for_rows(
        api, session, tournament_id, players, existing, args, full_roster=players
    )


def _upload_photos_for_rows(
    api: str,
    session: requests.Session,
    tournament_id: int,
    players: list[dict[str, Any]],
    polemica_to_tp: dict[int, dict[str, Any]],
    args: argparse.Namespace,
    full_roster: list[dict[str, Any]] | None = None,
) -> int:
    roster_for_report = full_roster if full_roster is not None else players
    img_session = requests.Session()
    img_session.headers.setdefault("User-Agent", session.headers.get("User-Agent", "polemica-fantasy-import/1.0"))

    skipped_fantasy_has_photo = 0
    for row in players:
        tp = polemica_to_tp.get(row["polemicaUserId"])
        if not tp:
            continue
        if _player_has_photo_in_fantasy(tp):
            if row.get("photoUrl"):
                skipped_fantasy_has_photo += 1
            continue
        if not row.get("photoUrl"):
            continue
        pid = tp["id"]
        url = row["photoUrl"]
        if urlparse(url).scheme not in ("http", "https"):
            print(f"    пропуск фото: неверный URL {url!r}", file=sys.stderr)
            continue

        got = fetch_image(url, img_session, args.delay)
        if not got:
            continue
        data, rct = got
        if args.remove_bg:
            data = maybe_remove_background(data)
        guessed = guess_image_type(data, rct)
        if not guessed:
            print(f"    фото: неизвестный тип ({rct})", file=sys.stderr)
            continue
        content_type, filename = guessed
        if args.remove_bg:
            filename = "avatar.png"
            content_type = "image/png"

        ok = upload_player_photo(
            api, session, tournament_id, pid, data, content_type, filename, args.delay
        )
        if ok:
            print(f"    фото загружено для {row['nickname']!r} ({content_type})")

    if skipped_fantasy_has_photo:
        print(
            f"  Пропущено (в Fantasy уже есть фото, не перезаписываем): {skipped_fantasy_has_photo}",
        )

    _report_fantasy_missing_photos(api, session, tournament_id, roster_for_report)
    print("Готово.")
    return 0


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="command", required=True)

    api_only = argparse.ArgumentParser(add_help=False)
    api_only.add_argument(
        "--api-base",
        default=os.environ.get("FANTASY_API_BASE", "http://localhost:8080"),
        help="Базовый URL бэкенда Polemica Fantasy",
    )
    api_only.add_argument(
        "--delay",
        type=float,
        default=0.2,
        help="Пауза между HTTP-запросами (сек)",
    )
    api_only.add_argument(
        "--user",
        default=os.environ.get("ADMIN_USERNAME", "admin"),
        help="Admin Basic Auth (Fantasy)",
    )
    api_only.add_argument(
        "--password",
        default=os.environ.get("ADMIN_PASSWORD") or "",
        help="Admin Basic Auth password",
    )

    common = argparse.ArgumentParser(add_help=False)
    common.add_argument(
        "--api-base",
        default=os.environ.get("FANTASY_API_BASE", "http://localhost:8080"),
        help="Базовый URL бэкенда Polemica Fantasy",
    )
    common.add_argument(
        "--mafoverlay-base",
        default=os.environ.get("MAFOVERLAY_BASE", "https://mafoverlay.ru"),
        help="Базовый URL MafOverlay",
    )
    common.add_argument(
        "--source",
        default="POLEMICA",
        help="Источник в пути /admin/photos/tournaments/{SOURCE}/{id} (POLEMICA, GOMAFIA, …)",
    )
    common.add_argument(
        "competition_id",
        type=int,
        help="ID турнира в URL MafOverlay (например 5029)",
    )
    common.add_argument("--dry-run", action="store_true", help="Только спарсить участников, без API Fantasy")
    common.add_argument("--no-photos", action="store_true", help="Не скачивать и не заливать фото")
    common.add_argument(
        "--delay",
        type=float,
        default=0.2,
        help="Пауза между HTTP-запросами (сек)",
    )
    common.add_argument(
        "--user",
        default=os.environ.get("ADMIN_USERNAME", "admin"),
        help="Admin Basic Auth (Fantasy)",
    )
    common.add_argument(
        "--password",
        default=os.environ.get("ADMIN_PASSWORD") or "",
        help="Admin Basic Auth password",
    )
    common.add_argument(
        "--mafoverlay-user",
        default=os.environ.get("MAFOVERLAY_USER"),
        help="HTTP Basic для MafOverlay (если включён на сервере)",
    )
    common.add_argument(
        "--mafoverlay-password",
        default=os.environ.get("MAFOVERLAY_PASSWORD") or "",
    )
    common.add_argument(
        "--mafoverlay-cookie",
        default=os.environ.get("MAFOVERLAY_COOKIE"),
        help="Заголовок Cookie для MafOverlay (если доступ по сессии)",
    )
    common.add_argument(
        "--remove-bg",
        action="store_true",
        help="Убрать фон через rembg перед загрузкой (pip install rembg onnxruntime)",
    )

    c = sub.add_parser("create", parents=[common], help="Создать турнир и импортировать ростер")
    c.add_argument("--tournament-name", required=True, help="Название в Polemica Fantasy")
    c.add_argument("--description", default=None)
    c.add_argument(
        "--status",
        choices=("DRAFT", "ACTIVE", "FINISHED"),
        default="DRAFT",
    )
    c.add_argument(
        "--kind",
        choices=("STANDALONE", "POLEMICA_COMPETITION"),
        default="POLEMICA_COMPETITION",
        help="По умолчанию POLEMICA_COMPETITION с polemicaCompetitionId = competition_id",
    )
    c.add_argument(
        "--polemica-competition-id",
        type=int,
        default=None,
        help="Явный polemicaCompetitionId (иначе берётся competition_id из URL MafOverlay)",
    )

    u = sub.add_parser("update", parents=[common], help="Добавить новых в существующий fantasy-турнир")
    u.add_argument(
        "--fantasy-tournament-id",
        type=int,
        required=True,
        help="ID турнира в Polemica Fantasy",
    )
    u.add_argument(
        "--refresh-photos",
        action="store_true",
        help="Попробовать взять фото с MafOverlay для всех в ростере, у кого в Fantasy ещё нет фото (существующие в системе не затираются; без флага — только вновь добавленные из MafOverlay)",
    )

    rp = sub.add_parser(
        "reprocess-photos",
        parents=[api_only],
        help="Скачать текущие фото в турнире, убрать фон (rembg) и залить снова",
    )
    rp.add_argument(
        "--fantasy-tournament-id",
        type=int,
        required=True,
        help="ID турнира в Polemica Fantasy (тот, что в URL / выводе create)",
    )
    return p


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    if not getattr(args, "dry_run", False) and not args.password and args.command in (
        "create",
        "update",
        "reprocess-photos",
    ):
        print(
            "Задайте ADMIN_PASSWORD или --password для create, update, reprocess-photos.",
            file=sys.stderr,
        )
        return 1

    if args.command == "create":
        return run_create(args)
    if args.command == "update":
        return run_update(args)
    if args.command == "reprocess-photos":
        return run_reprocess_photos(args)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
