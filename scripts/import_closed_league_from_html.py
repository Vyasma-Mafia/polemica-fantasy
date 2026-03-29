#!/usr/bin/env python3
"""
Импорт ростера «закрытой лиги» из HTML-вырезки (как в zl.html) в Polemica Fantasy.

1. Парсит строки таблицы: polemica user id (/profile/{id} или user_id в avatar URL),
   никнейм, относительный путь к аватару.
2. Создаёт турнир через Admin API.
3. Добавляет участников и заливает фото (скачивание с сайта Полемики → multipart на бэкенд).

Требуется: pip install -r scripts/requirements-import-closed-league.txt

Пример:
  export ADMIN_USERNAME=admin ADMIN_PASSWORD=secret
  python3 scripts/import_closed_league_from_html.py zl.html \\
    --api-base http://localhost:8080 \\
    --polemica-base https://polemicagame.com \\
    --tournament-name "Закрытая лига (импорт)"

Переменные окружения (перекрываются флагами):
  FANTASY_API_BASE, POLEMICA_SITE_BASE_URL, ADMIN_USERNAME, ADMIN_PASSWORD
"""

from __future__ import annotations

import argparse
import html as html_module
import os
import re
import sys
import time
from typing import Any

import requests

ROW_SPLIT = re.compile(r"<div[^>]*p-landing-closed__table-row[^>]*>", re.I)
PROFILE_HREF = re.compile(r'href="/profile/(\d+)"')
NICKNAME = re.compile(r"p-landing-closed__table-nickname\">([^<]+)</span>", re.I)
AVATAR_SRC = re.compile(r'src="(/image/user-real-avatar[^"]+)"', re.I)

# Ответ аватара → (content-type для upload, расширение файла в multipart)
CT_AND_EXT = {
    "image/jpeg": ("image/jpeg", "avatar.jpg"),
    "image/png": ("image/png", "avatar.png"),
    "image/webp": ("image/webp", "avatar.webp"),
}


def parse_players(fragment: str) -> list[dict[str, Any]]:
    chunks = ROW_SPLIT.split(fragment)
    seen: set[int] = set()
    out: list[dict[str, Any]] = []
    for part in chunks:
        m = PROFILE_HREF.search(part)
        if not m:
            continue
        uid = int(m.group(1))
        if uid in seen:
            continue
        mn = NICKNAME.search(part)
        nickname = html_module.unescape(mn.group(1).strip()) if mn else ""
        if not nickname:
            continue
        mi = AVATAR_SRC.search(part)
        avatar_rel = html_module.unescape(mi.group(1)) if mi else None
        seen.add(uid)
        out.append(
            {
                "polemicaUserId": uid,
                "nickname": nickname,
                "avatarPath": avatar_rel,
            }
        )
    return out


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


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("html_file", help="Путь к HTML (например zl.html)")
    p.add_argument(
        "--api-base",
        default=os.environ.get("FANTASY_API_BASE", "http://localhost:8080"),
        help="Базовый URL бэкенда (без /api)",
    )
    p.add_argument(
        "--polemica-base",
        default=os.environ.get("POLEMICA_SITE_BASE_URL", "https://polemicagame.com"),
        help="Сайт Полемики для скачивания /image/user-real-avatar?...",
    )
    p.add_argument("--tournament-name", required=True, help="Название нового турнира")
    p.add_argument(
        "--status",
        choices=("DRAFT", "ACTIVE", "FINISHED"),
        default="DRAFT",
        help="Статус турнира",
    )
    p.add_argument("--description", default=None, help="Описание турнира")
    p.add_argument("--dry-run", action="store_true", help="Только распарсить и вывести список")
    p.add_argument("--no-photos", action="store_true", help="Не скачивать и не заливать фото")
    p.add_argument(
        "--delay",
        type=float,
        default=0.15,
        help="Пауза между запросами к API (сек)",
    )
    p.add_argument(
        "--user",
        default=os.environ.get("ADMIN_USERNAME", "admin"),
        help="Admin Basic Auth username",
    )
    p.add_argument(
        "--password",
        default=os.environ.get("ADMIN_PASSWORD") or "",
        help="Admin Basic Auth password (лучше через env ADMIN_PASSWORD)",
    )
    args = p.parse_args()
    if not args.dry_run and not args.password:
        print("Задайте ADMIN_PASSWORD или --password", file=sys.stderr)
        return 1

    path = args.html_file
    with open(path, encoding="utf-8") as f:
        html = f.read()

    players = parse_players(html)
    if not players:
        print("Участники не найдены — проверьте разметку (классы p-landing-closed__table-row).", file=sys.stderr)
        return 1

    print(f"Найдено участников: {len(players)}")
    if args.dry_run:
        for row in players:
            print(f"  {row['polemicaUserId']:6d}  {row['nickname']!r}  {row['avatarPath']}")
        return 0

    api = args.api_base.rstrip("/")
    polemica = args.polemica_base.rstrip("/")
    auth = (args.user, args.password)
    s = requests.Session()
    s.auth = auth
    s.headers.setdefault("User-Agent", "polemica-fantasy-import/1.0")

    r = s.post(
        f"{api}/api/v1/admin/tournaments",
        json={
            "name": args.tournament_name,
            "description": args.description,
            "status": args.status,
        },
        timeout=60,
    )
    if not r.ok:
        print(f"Создание турнира: HTTP {r.status_code} {r.text}", file=sys.stderr)
        return 1
    tournament = r.json()
    tid = tournament["id"]
    print(f"Турнир создан: id={tid} name={tournament.get('name')!r}")

    for row in players:
        time.sleep(args.delay)
        body = {"polemicaUserId": row["polemicaUserId"], "nickname": row["nickname"]}
        pr = s.post(f"{api}/api/v1/admin/tournaments/{tid}/players", json=body, timeout=60)
        if not pr.ok:
            print(
                f"Игрок {row['polemicaUserId']} {row['nickname']!r}: HTTP {pr.status_code} {pr.text}",
                file=sys.stderr,
            )
            continue
        tp = pr.json()
        pid = tp["id"]
        print(f"  + игрок id={pid} polemicaUserId={row['polemicaUserId']} {row['nickname']!r}")

        if args.no_photos or not row["avatarPath"]:
            continue

        url = polemica + row["avatarPath"]
        time.sleep(args.delay)
        try:
            # Не через Session: у сессии Basic Auth админки — на polemicagame.com не отправлять.
            gr = requests.get(
                url,
                headers={"User-Agent": s.headers.get("User-Agent", "polemica-fantasy-import/1.0")},
                timeout=45,
            )
            if not gr.ok:
                print(f"    фото GET {url}: HTTP {gr.status_code}", file=sys.stderr)
                continue
            data = gr.content
            guessed = guess_image_type(data, gr.headers.get("Content-Type"))
            if not guessed:
                print(f"    фото: неизвестный тип ({gr.headers.get('Content-Type')})", file=sys.stderr)
                continue
            content_type, filename = guessed
            files = {"file": (filename, data, content_type)}
            time.sleep(args.delay)
            ur = s.post(
                f"{api}/api/v1/admin/tournaments/{tid}/players/{pid}/photo",
                files=files,
                timeout=120,
            )
            if not ur.ok:
                print(f"    фото upload: HTTP {ur.status_code} {ur.text}", file=sys.stderr)
            else:
                print(f"    фото загружено ({content_type})")
        except requests.RequestException as e:
            print(f"    фото ошибка: {e}", file=sys.stderr)

    print("Готово.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
