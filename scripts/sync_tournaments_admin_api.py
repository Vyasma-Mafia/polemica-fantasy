#!/usr/bin/env python3
"""
Копирование турниров (метаданные + ростер + паки карт) с внешнего Admin API на локальное
через HTTP Basic Auth.

Порядок: для каждого исходного tournament id — создать турнир на dest, добавить игроков
(теми же polemicaUserId / nickname), по возможности применить excludedFromPackPool (PATCH),
загрузить фото (GET photoUrl, POST …/photo), затем создать паки. playerIds в паках маппятся
с prod fantasy_player id на local по polemicaUserId.

Если PATCH …/players/{id} даёт 405, локальный бэкенд, скорее всего, собран из старого образа —
пересоберите `polemica-fantasy-backend` и поднимите контейнер заново. Скрипт в этом случае
только предупредит и продолжит.

Требования:
  pip install -r scripts/requirements-sync-admin-api.txt

Пример:
  export SOURCE_USER=admin SOURCE_PASSWORD=...
  export DEST_USER=admin DEST_PASSWORD=defaultPassword123
  python3 scripts/sync_tournaments_admin_api.py \\
    --source https://fantasy.example.com \\
    --dest http://localhost:8080 \\
    --tournament-ids 1,2,3

С теми же логином/паролем на обоих концах:
  python3 scripts/sync_tournaments_admin_api.py -S https://prod -D http://localhost:8080 \\
    -u admin -p secret

Переменные окружения (перекрывают флаги, кроме URL):
  SOURCE_USER, SOURCE_PASSWORD, DEST_USER, DEST_PASSWORD
"""

from __future__ import annotations

import argparse
import os
import re
import sys
import time
from typing import Any
from urllib.parse import urljoin

import requests

USER_AGENT = "polemica-fantasy-sync-tournaments/1.0"
MAX_IMAGE_BYTES = 5 * 1024 * 1024
# backend MultipartImageValidation: JPEG, PNG, WebP
CT_TO_NAME = {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
}
_PATCH_405_WARNED = False


def _auth_from_env(prefix: str) -> tuple[str, str] | None:
    u = os.environ.get(f"{prefix}_USER")
    p = os.environ.get(f"{prefix}_PASSWORD")
    if u is not None and p is not None:
        return (u, p)
    return None


def _session(base: str, user: str, password: str, verify: bool) -> requests.Session:
    s = requests.Session()
    s.auth = (user, password)
    s.headers["User-Agent"] = USER_AGENT
    s.headers["Accept"] = "application/json"
    s.verify = verify
    s._api_base = base.rstrip("/")  # type: ignore[attr-defined]
    return s


def _url(s: requests.Session, path: str) -> str:
    b: str = getattr(s, "_api_base")
    return urljoin(b + "/", path.lstrip("/"))


def _guess_mime_from_url(url: str) -> str | None:
    m = re.search(r"\.(jpe?g|png|webp)(?:$|[?#&])", (url or "").split("?")[0], re.I)
    if not m:
        return None
    ext = m.group(1).lower()
    return {
        "jpg": "image/jpeg",
        "jpeg": "image/jpeg",
        "jpe": "image/jpeg",
        "png": "image/png",
        "webp": "image/webp",
    }.get(ext)


def _upload_player_photo(
    dst: requests.Session,
    dest_tid: int,
    tournament_player_id: int,
    photo_url: str | None,
    delay: float,
    verify: bool,
) -> None:
    if not (photo_url or "").strip():
        return
    url = photo_url.strip()
    try:
        gr = requests.get(
            url,
            timeout=120,
            verify=verify,
            headers={"User-Agent": USER_AGENT},
        )
        if not gr.ok:
            print(
                f"  [warn] photo download HTTP {gr.status_code} ({url[:90]}…)",
                file=sys.stderr,
            )
            return
        data = gr.content
    except requests.RequestException as e:
        print(f"  [warn] photo download failed: {e}", file=sys.stderr)
        return
    if len(data) > MAX_IMAGE_BYTES:
        print("  [warn] photo > 5MB, skip upload (backend limit)", file=sys.stderr)
        return
    raw_ct = (gr.headers.get("Content-Type") or "").split(";")[0].strip().lower()
    if raw_ct in CT_TO_NAME:
        ct = raw_ct
    elif raw_ct in ("application/octet-stream", "binary/octet-stream", ""):
        ct = _guess_mime_from_url(url)
    else:
        ct = _guess_mime_from_url(url) or (raw_ct if raw_ct in CT_TO_NAME else None)
    if not ct or ct not in CT_TO_NAME:
        print(
            f"  [warn] could not map image type (url/content-type) for {url[:80]}… — skip",
            file=sys.stderr,
        )
        return
    name = f"player.{CT_TO_NAME[ct]}"
    pr = dst.post(
        _url(dst, f"/api/v1/admin/tournaments/{dest_tid}/players/{tournament_player_id}/photo"),
        files={"file": (name, data, ct)},
        timeout=120,
    )
    if not pr.ok:
        body = (pr.text or "")[:1500]
        print(
            f"  [warn] photo upload HTTP {pr.status_code} player={tournament_player_id}\n{body}",
            file=sys.stderr,
        )
        return
    time.sleep(delay)


def _patch_excluded_from_pack_pool(
    dst: requests.Session,
    dest_tid: int,
    tournament_player_id: int,
    delay: float,
) -> None:
    global _PATCH_405_WARNED
    r = dst.patch(
        _url(dst, f"/api/v1/admin/tournaments/{dest_tid}/players/{tournament_player_id}"),
        json={"excludedFromPackPool": True},
        headers={"Content-Type": "application/json"},
    )
    if r.status_code == 405:
        if not _PATCH_405_WARNED:
            print(
                "  [warn] PATCH /api/v1/admin/tournaments/{id}/players/{playerId} → 405 "
                "(Method Not Allowed). Rebuild the backend image from current sources "
                "(e.g. docker compose build fantasy-backend) so patchTournamentPlayer is present. "
                "Skipping excludedFromPackPool for this run (may repeat for each player).",
                file=sys.stderr,
            )
            _PATCH_405_WARNED = True
        return
    if not r.ok:
        print(f"HTTP {r.status_code} PATCH excludedFromPack player {tournament_player_id}\n{r.text[:2000]}", file=sys.stderr)
        r.raise_for_status()
    time.sleep(delay)


def _check(r: requests.Response, what: str) -> dict[str, Any] | list[Any]:
    if not r.ok:
        body = r.text[:2000] if r.text else ""
        print(f"HTTP {r.status_code} {what}\n{body}", file=sys.stderr)
        r.raise_for_status()
    if r.status_code == 204 or not (r.text or "").strip():
        return {}
    return r.json()


def copy_tournament(
    src: requests.Session,
    dst: requests.Session,
    source_tournament_id: int,
    delay: float,
    dry_run: bool,
    skip_photos: bool,
    verify: bool,
) -> tuple[int, int]:
    """
    Returns (source_tournament_id, dest_tournament_id).
    """
    detail = _check(
        src.get(_url(src, f"/api/v1/admin/tournaments/{source_tournament_id}")),
        f"GET source tournament {source_tournament_id}",
    )
    assert isinstance(detail, dict)

    prod_fantasy_to_polemica: dict[int, int] = {}
    for pl in detail.get("players") or []:
        prod_fantasy_to_polemica[int(pl["fantasyPlayerId"])] = int(pl["polemicaUserId"])

    create_body: dict[str, Any] = {
        "name": detail["name"],
        "status": detail["status"],
        "kind": detail.get("kind", "STANDALONE"),
    }
    if detail.get("description") is not None:
        create_body["description"] = detail["description"]
    pcid = detail.get("polemicaCompetitionId")
    if pcid is not None:
        create_body["polemicaCompetitionId"] = pcid

    print(f"  [tournament] source id={source_tournament_id!r} name={detail.get('name')!r}", flush=True)
    if dry_run:
        packs_preview = _check(
            src.get(
                _url(src, "/api/v1/admin/card-packs"),
                params={"tournamentId": source_tournament_id},
            ),
            f"GET source card-packs tournamentId={source_tournament_id}",
        )
        assert isinstance(packs_preview, list)
        n_players = len(detail.get("players") or [])
        print(
            f"  [dry-run] would create tournament + {n_players} player(s) + {len(packs_preview)} pack(s)",
            flush=True,
        )
        return (source_tournament_id, -1)

    t_created = _check(
        dst.post(
            _url(dst, "/api/v1/admin/tournaments"),
            json=create_body,
            headers={"Content-Type": "application/json"},
        ),
        "POST dest tournament",
    )
    assert isinstance(t_created, dict)
    dest_tid = int(t_created["id"])
    print(f"  -> created dest tournament id={dest_tid}", flush=True)
    time.sleep(delay)

    # polemicaUserId -> local fantasyPlayerId
    polemica_to_local_fantasy: dict[int, int] = {}
    for pl in sorted(detail.get("players") or [], key=lambda x: int(x["id"])):
        puid = int(pl["polemicaUserId"])
        nick = pl["nickname"]
        body = {"polemicaUserId": puid, "nickname": nick}
        pr = _check(
            dst.post(
                _url(dst, f"/api/v1/admin/tournaments/{dest_tid}/players"),
                json=body,
                headers={"Content-Type": "application/json"},
            ),
            f"POST dest player polemica={puid}",
        )
        assert isinstance(pr, dict)
        tid_player = int(pr["id"])
        polemica_to_local_fantasy[puid] = int(pr["fantasyPlayerId"])
        time.sleep(delay)
        if not skip_photos and pl.get("photoUrl"):
            _upload_player_photo(dst, dest_tid, tid_player, pl.get("photoUrl"), delay, verify)
        if pl.get("excludedFromPackPool") is True:
            _patch_excluded_from_pack_pool(dst, dest_tid, tid_player, delay)

    # Card packs
    packs = _check(
        src.get(
            _url(src, "/api/v1/admin/card-packs"),
            params={"tournamentId": source_tournament_id},
        ),
        f"GET source card-packs tournamentId={source_tournament_id}",
    )
    assert isinstance(packs, list)
    print(f"  [packs] {len(packs)} pack(s) on source", flush=True)

    for pack in packs:
        use_all = bool(pack.get("useAllTournamentPlayers"))
        body: dict[str, Any] = {
            "name": pack["name"],
            "tournamentId": dest_tid,
            "active": bool(pack.get("active", True)),
            "autoGenerated": bool(pack.get("autoGenerated", False)),
            "priceFantiki": int(pack.get("priceFantiki", 0)),
            "freeOpensPerUser": int(pack.get("freeOpensPerUser", 0)),
            "useAllTournamentPlayers": use_all,
            "rarityConfigs": [
                {"rarity": c["rarity"], "cardsCount": int(c["cardsCount"])}
                for c in (pack.get("rarityConfigs") or [])
            ],
        }
        if not use_all:
            local_ids: list[int] = []
            for fpid in pack.get("playerIds") or []:
                puid = prod_fantasy_to_polemica.get(int(fpid))
                if puid is None:
                    print(
                        f"  [warn] pack {pack.get('name')!r}: unknown prod fantasyPlayerId {fpid} "
                        f"(not in tournament roster on source) — skip pack",
                        file=sys.stderr,
                    )
                    local_ids = []
                    break
                lf = polemica_to_local_fantasy.get(puid)
                if lf is None:
                    print(
                        f"  [warn] pack {pack.get('name')!r}: no local fantasy id for "
                        f"polemica {puid} — skip pack",
                        file=sys.stderr,
                    )
                    local_ids = []
                    break
                local_ids.append(lf)
            if not (pack.get("playerIds") or []):
                print(
                    f"  [warn] pack {pack.get('name')!r}: useAllTournamentPlayers=false but "
                    f"no playerIds on source — skip",
                    file=sys.stderr,
                )
                continue
            if not local_ids:
                continue
            body["playerIds"] = local_ids
        if use_all and "playerIds" in body:
            del body["playerIds"]

        _check(
            dst.post(
                _url(dst, "/api/v1/admin/card-packs"),
                json=body,
                headers={"Content-Type": "application/json"},
            ),
            f"POST dest card-pack {pack.get('name')!r}",
        )
        print(f"  + pack {pack.get('name')!r} created on dest", flush=True)
        time.sleep(delay)

    return (source_tournament_id, dest_tid)


def main() -> int:
    ap = argparse.ArgumentParser(
        description="Copy tournaments + rosters + card packs from source Admin API to dest.",
    )
    ap.add_argument(
        "-S",
        "--source",
        help="Base URL of source API (e.g. https://fantasy.prod.tld). Env: FANTASY_SOURCE_URL",
    )
    ap.add_argument(
        "-D",
        "--dest",
        help="Base URL of destination API (e.g. http://localhost:8080). Env: FANTASY_DEST_URL",
    )
    ap.add_argument(
        "-u",
        "--user",
        help="Username for both source and dest (if not using separate). Env not used for -u alone.",
    )
    ap.add_argument(
        "-p",
        "--password",
        help="Password for both source and dest (if not using separate).",
    )
    ap.add_argument("--source-user", default=None, help="Override source Basic user")
    ap.add_argument("--source-password", default=None, help="Override source password")
    ap.add_argument("--dest-user", default=None, help="Override dest Basic user")
    ap.add_argument("--dest-password", default=None, help="Override dest password")
    ap.add_argument(
        "--tournament-ids",
        type=str,
        help="Comma-separated source tournament ids (default: all from GET /admin/tournaments)",
    )
    ap.add_argument(
        "--delay",
        type=float,
        default=0.2,
        help="Seconds to sleep between requests (default 0.2)",
    )
    ap.add_argument(
        "--insecure",
        action="store_true",
        help="Disable TLS certificate verification (source/dest)",
    )
    ap.add_argument(
        "--dry-run",
        action="store_true",
        help="List what would be copied, do not POST to dest",
    )
    ap.add_argument(
        "--skip-photos",
        action="store_true",
        help="Do not download photoUrl and POST …/players/{id}/photo",
    )
    args = ap.parse_args()

    source_base = args.source or os.environ.get("FANTASY_SOURCE_URL", "").strip() or None
    dest_base = args.dest or os.environ.get("FANTASY_DEST_URL", "").strip() or None
    if not source_base or not dest_base:
        ap.error("Pass --source and --dest (or FANTASY_SOURCE_URL / FANTASY_DEST_URL).")

    global _PATCH_405_WARNED
    _PATCH_405_WARNED = False

    su, sp = args.source_user, args.source_password
    if su is not None and sp is not None:
        pass
    else:
        t = _auth_from_env("SOURCE")
        if t:
            su, sp = t
        elif args.user and args.password:
            su, sp = args.user, args.password
    if su is None or sp is None:
        ap.error("Set --source-user/--source-password, or SOURCE_USER/SOURCE_PASSWORD, or -u/-p (shared).")

    du, dp = args.dest_user, args.dest_password
    if du is not None and dp is not None:
        pass
    else:
        t = _auth_from_env("DEST")
        if t:
            du, dp = t
        elif args.user and args.password:
            du, dp = args.user, args.password
    if du is None or dp is None:
        ap.error("Set --dest-user/--dest-password, or DEST_USER/DEST_PASSWORD, or -u/-p (shared).")

    verify = not args.insecure
    s_src = _session(source_base, su, sp, verify)
    s_dst = _session(dest_base, du, dp, verify)

    if args.tournament_ids:
        ids = [int(x.strip()) for x in args.tournament_ids.split(",") if x.strip()]
    else:
        raw = _check(
            s_src.get(_url(s_src, "/api/v1/admin/tournaments")),
            "GET /api/v1/admin/tournaments (source)",
        )
        assert isinstance(raw, list)
        ids = [int(t["id"]) for t in raw]
    if not ids:
        print("No tournament ids to process.", file=sys.stderr)
        return 1

    print(f"Source: {source_base!r} — {len(ids)} tournament(s): {ids}", flush=True)
    print(f"Dest:   {dest_base!r}", flush=True)
    if args.dry_run:
        print("DRY-RUN: no writes to dest.", flush=True)

    mapping: list[tuple[int, int]] = []
    for idx, tid in enumerate(ids):
        print(f"[{idx + 1}/{len(ids)}] tournament source id={tid} …", flush=True)
        m = copy_tournament(
            s_src,
            s_dst,
            tid,
            args.delay,
            args.dry_run,
            args.skip_photos,
            verify,
        )
        if m[1] >= 0 or args.dry_run:
            mapping.append(m)

    print("Done. Source tournament id -> dest tournament id:", flush=True)
    for a, b in mapping:
        print(f"  {a} -> {b}", flush=True)
    if args.dry_run and not mapping:
        print("(dry-run: dest ids are placeholders / not created)")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except requests.HTTPError as e:
        print(f"Request failed: {e}", file=sys.stderr)
        raise SystemExit(1)
    except requests.RequestException as e:
        print(f"Request error: {e}", file=sys.stderr)
        raise SystemExit(1)
