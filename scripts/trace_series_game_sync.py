#!/usr/bin/env python3
"""
Пошаговая трассировка логики sync игр серии (как DefaultGameSyncService).

Источники:
- polemica-fantasy-backend/.../DefaultGameSyncService.kt
- polemica-library/.../PolemicaClientImpl.kt

Переменные окружения (или флаги):
  FANTASY_API_BASE   — база API (например https://51.250.18.236)
  ADMIN_USERNAME, ADMIN_PASSWORD — Basic Auth к /api/v1/admin/*
  POLEMICA_USERNAME, POLEMICA_PASSWORD — для GET /v1/matches/{id} и /v1/competitions/... (необязательно:
                        без них можно увидеть только шаги «профиль + пересечение» для STANDALONE)
  POLEMICA_API_BASE  — по умолчанию https://app.polemicagame.com (как polemica.api.base-url в application.yml)
  PROFILE_SITE_BASE  — по умолчанию https://polemicagame.com (публичный get-games)

Пример:
  export ADMIN_USERNAME=admin ADMIN_PASSWORD='...'
  export POLEMICA_USERNAME='...' POLEMICA_PASSWORD='...'
  python3 scripts/trace_series_game_sync.py --series-id 1 --insecure

Коды «потери» игр в бэкенде:
- STANDALONE: матч не в пересечении профильных id всех игроков; имя не начинается с name_prefix;
  лимит 500 последних игр на игрока (PROFILE_SYNC_PAGE_SIZE) — если у кого-то в истории >500 игр,
  общие матчи старее «хвоста» не попадут в пересечение.
- POLEMICA_COMPETITION: num вне [game_num_from, game_num_to]; в upsertSeriesGame при game.id == null запись не создаётся.
"""

from __future__ import annotations

import argparse
import base64
import json
import ssl
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Set, Tuple


def http_json(
    method: str,
    url: str,
    *,
    headers: Optional[Dict[str, str]] = None,
    body: Optional[bytes] = None,
    insecure: bool = False,
) -> Tuple[int, Any]:
    h = dict(headers or {})
    ctx = None
    if insecure:
        ctx = ssl._create_unverified_context()
    req = urllib.request.Request(url, data=body, method=method, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=120, context=ctx) as resp:
            raw = resp.read().decode("utf-8")
            code = resp.getcode()
            if not raw:
                return code, None
            return code, json.loads(raw)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return e.code, parsed


def basic_auth_header(user: str, password: str) -> str:
    token = base64.b64encode(f"{user}:{password}".encode()).decode()
    return f"Basic {token}"


@dataclass
class PolemicaAuth:
    api_base: str
    username: str
    password: str
    insecure: bool
    _token: Optional[str] = None

    def login(self) -> str:
        url = f"{self.api_base.rstrip('/')}/v1/auth/login"
        body = json.dumps({"username": self.username, "password": self.password}).encode()
        code, data = http_json(
            "POST",
            url,
            headers={"Content-Type": "application/json"},
            body=body,
            insecure=self.insecure,
        )
        if code != 200 or not isinstance(data, dict):
            raise RuntimeError(f"Polemica login failed: HTTP {code} {data}")
        tok = data.get("access_token")
        if not tok:
            raise RuntimeError(f"No access_token in login response: {data}")
        self._token = tok
        return tok

    def bearer(self) -> str:
        if not self._token:
            self.login()
        assert self._token is not None
        return self._token

    def get_json(self, path: str) -> Any:
        url = f"{self.api_base.rstrip('/')}{path}"
        code, data = http_json(
            "GET",
            url,
            headers={"Authorization": f"Bearer {self.bearer()}"},
            insecure=self.insecure,
        )
        if code != 200:
            raise RuntimeError(f"GET {path} failed: HTTP {code} {data}")
        return data


def fetch_profile_games(profile_base: str, user_id: int, page: int, limit: int, insecure: bool) -> Dict[str, Any]:
    base = profile_base.rstrip("/")
    url = f"{base}/profile/default/get-games?userId={user_id}&page={page}&limit={limit}"
    code, data = http_json("GET", url, insecure=insecure)
    if code != 200:
        raise RuntimeError(f"Profile games userId={user_id} HTTP {code}: {data}")
    return data


def trace_standalone(
    series: Dict[str, Any],
    tournament_players_by_id: Dict[int, Dict[str, Any]],
    profile_base: str,
    profile_page_limit: int,
    polemica: Optional[PolemicaAuth],
    insecure: bool,
    polemica_login_failed: bool = False,
) -> None:
    prefix = (series.get("namePrefix") or "").strip()
    print("\n=== STANDALONE sync (как syncStandalone) ===")
    if not prefix:
        print("ERROR: name_prefix пуст — бэкенд вернёт 400.")
        return

    tp_ids: List[int] = series.get("tournamentPlayerIds") or []
    if not tp_ids:
        print("Нет игроков в серии — sync выходит сразу (0 игр).")
        return

    print(f"name_prefix: {prefix!r}")
    print(f"Лимит строк профиля на игрока (как в бэкенде): {profile_page_limit}")

    id_sets: List[Set[int]] = []
    for tp_id in tp_ids:
        tp = tournament_players_by_id.get(tp_id)
        if not tp:
            print(f"  WARNING: tournament_player id={tp_id} не найден в турнире")
            continue
        uid = int(tp["polemicaUserId"])
        data = fetch_profile_games(profile_base, uid, 1, profile_page_limit, insecure)
        rows = data.get("rows") or []
        ids = {int(r["id"]) for r in rows if r.get("id") is not None}
        id_sets.append(ids)
        saturated = " (ровно лимит — возможна обрезка истории!)" if len(ids) >= profile_page_limit else ""
        print(
            f"  Игрок tp={tp_id} polemicaUserId={uid} ({tp.get('nickname')}): {len(ids)} match id в первой странице профиля{saturated}"
        )

    if not id_sets:
        return

    intersection: Set[int] = set(id_sets[0])
    for s in id_sets[1:]:
        intersection &= s
    print(f"\nПересечение match id по всем игрокам: {len(intersection)} шт.")
    if len(intersection) > 30:
        sample = sorted(intersection)[:15]
        print(f"  (первые 15 id: {sample} …)")
    else:
        print(f"  ids: {sorted(intersection)}")

    if not polemica:
        if polemica_login_failed:
            print(
                "\nШаг getMatch пропущен: логин Polemica API не удался (детали — в stderr выше).\n"
                "Частая причина: неверный POLEMICA_API_BASE. В application.yml указан "
                "https://app.polemicagame.com — на polemicagame.com без «app.» /v1/auth/login отдаёт HTML, не JSON."
            )
        else:
            print(
                "\nДальше нужны POLEMICA_USERNAME / POLEMICA_PASSWORD (или флаги --polemica-user / --polemica-password): "
                "getMatch по каждому id и фильтр startswith(prefix)."
            )
        return

    print("\nЗагрузка матчей (GET /v1/matches/{id}) и фильтр по префиксу имени:")
    kept: List[int] = []
    dropped_prefix: List[Tuple[int, str]] = []
    dropped_null_id: List[int] = []
    for mid in sorted(intersection):
        try:
            game = polemica.get_json(f"/v1/matches/{mid}")
        except RuntimeError as e:
            print(f"  id={mid}: ОШИБКА загрузки: {e}")
            continue
        gid = game.get("id")
        name = (game.get("name") or "").strip()
        if gid is None:
            dropped_null_id.append(mid)
            print(f"  id={mid}: game.id is NULL — upsertSeriesGame пропустит (как в Kotlin)")
            continue
        if not name.startswith(prefix):
            dropped_prefix.append((mid, name[:80]))
            continue
        kept.append(mid)

    print(f"\nИтог: сохранилось бы {len(kept)} игр (после префикса).")
    if dropped_prefix:
        print(f"Отброшено по префиксу (не startswith {prefix!r}): {len(dropped_prefix)}")
        for mid, nm in dropped_prefix[:20]:
            print(f"  match {mid}: name={nm!r}")
        if len(dropped_prefix) > 20:
            print(f"  … ещё {len(dropped_prefix) - 20}")
    if dropped_null_id:
        print(f"Отброшено из-за null game.id: {dropped_null_id}")


def trace_competition(
    series: Dict[str, Any],
    competition_id: int,
    polemica: PolemicaAuth,
) -> None:
    from_n = series.get("gameNumFrom")
    to_n = series.get("gameNumTo")
    print("\n=== POLEMICA_COMPETITION sync (как syncCompetition) ===")
    if from_n is None or to_n is None:
        print("game_num_from / game_num_to не заданы — бэкенд вернёт 400.")
        return
    from_n, to_n = int(from_n), int(to_n)
    if from_n > to_n:
        print("game_num_from > game_num_to — бэкенд вернёт 400.")
        return

    refs = polemica.get_json(f"/v1/competitions/{competition_id}/games")
    if not isinstance(refs, list):
        print(f"Unexpected response: {refs}")
        return
    print(f"Всего ссылок на игры турнира: {len(refs)}")

    in_range = [r for r in refs if from_n <= int(r.get("num", -1)) <= to_n]
    out_range = [r for r in refs if not (from_n <= int(r.get("num", -1)) <= to_n)]
    print(f"В диапазоне num [{from_n}, {to_n}]: {len(in_range)}")
    print(f"Вне диапазона (пропускаются): {len(out_range)}")

    nums_in_range = [int(r["num"]) for r in in_range]
    dup_nums = [n for n in nums_in_range if nums_in_range.count(n) > 1]
    if dup_nums:
        print(f"WARNING: дублирующиеся num в ответе API: {sorted(set(dup_nums))}")

    print("\nПолная загрузка (GET .../competitions/{cid}/games/{gameId}):")
    null_ids = 0
    errors = 0
    for r in sorted(in_range, key=lambda x: (int(x.get("num", 0)), int(x.get("id", 0)))):
        gid = r.get("id")
        ver = r.get("version")
        ver_q = f"?version={ver}" if ver is not None else ""
        path = f"/v1/competitions/{competition_id}/games/{gid}{ver_q}"
        try:
            game = polemica.get_json(path)
        except RuntimeError as e:
            errors += 1
            print(f"  num={r.get('num')} ref_id={gid}: ERROR {e}")
            continue
        if game.get("id") is None:
            null_ids += 1
            print(f"  num={r.get('num')} ref_id={gid}: game.id is NULL — не сохранится в БД")
    print(f"\nИтог: загрузок с ошибкой: {errors}, с null game.id: {null_ids}")


def main() -> None:
    ap = argparse.ArgumentParser(description="Трассировка sync игр серии (Fantasy + Polemica API)")
    ap.add_argument("--fantasy-base", default=None, help="База URL API fantasy (env FANTASY_API_BASE)")
    ap.add_argument("--series-id", type=int, required=True)
    ap.add_argument("--profile-base", default=None, help="Публичный сайт для get-games (env PROFILE_SITE_BASE)")
    ap.add_argument(
        "--polemica-base",
        default=None,
        help="REST API Полемики (env POLEMICA_API_BASE; по умолчанию https://app.polemicagame.com)",
    )
    ap.add_argument("--polemica-user", default=None, help="Логин Polemica API (иначе env POLEMICA_USERNAME)")
    ap.add_argument("--polemica-password", default=None, help="Пароль Polemica API (иначе env POLEMICA_PASSWORD)")
    ap.add_argument("--profile-limit", type=int, default=500, help="Как PROFILE_SYNC_PAGE_SIZE в бэкенде")
    ap.add_argument("--insecure", action="store_true", help="Не проверять TLS (для IP с самоподписанным сертификатом)")
    args = ap.parse_args()

    import os

    fantasy_base = (args.fantasy_base or os.environ.get("FANTASY_API_BASE") or "").rstrip("/")
    if not fantasy_base:
        print("Задайте --fantasy-base или FANTASY_API_BASE", file=sys.stderr)
        sys.exit(1)

    admin_u = os.environ.get("ADMIN_USERNAME", "admin")
    admin_p = os.environ.get("ADMIN_PASSWORD", "")
    if not admin_p:
        print("Нужен ADMIN_PASSWORD в окружении", file=sys.stderr)
        sys.exit(1)

    profile_base = (args.profile_base or os.environ.get("PROFILE_SITE_BASE") or "https://polemicagame.com").rstrip(
        "/"
    )
    polemica_base = (
        args.polemica_base or os.environ.get("POLEMICA_API_BASE") or "https://app.polemicagame.com"
    ).rstrip("/")
    p_user = (args.polemica_user or os.environ.get("POLEMICA_USERNAME") or "").strip()
    p_pass = args.polemica_password or os.environ.get("POLEMICA_PASSWORD") or ""

    auth_header = {"Authorization": basic_auth_header(admin_u, admin_p)}

    code, series = http_json("GET", f"{fantasy_base}/api/v1/admin/series/{args.series_id}", headers=auth_header, insecure=args.insecure)
    if code != 200:
        print(f"GET series failed: {code} {series}", file=sys.stderr)
        sys.exit(1)

    tid = int(series["tournamentId"])
    code, tournament = http_json("GET", f"{fantasy_base}/api/v1/admin/tournaments/{tid}", headers=auth_header, insecure=args.insecure)
    if code != 200:
        print(f"GET tournament failed: {code} {tournament}", file=sys.stderr)
        sys.exit(1)

    players_by_tp: Dict[int, Dict[str, Any]] = {int(p["id"]): p for p in tournament.get("players") or []}

    print("=== Конфигурация (Fantasy API) ===")
    print(json.dumps({"series": series, "tournament_kind": tournament.get("kind"), "polemica_competition_id": tournament.get("polemicaCompetitionId")}, ensure_ascii=False, indent=2))

    polemica: Optional[PolemicaAuth] = None
    polemica_login_failed = False
    if p_user and p_pass:
        polemica = PolemicaAuth(polemica_base, p_user, p_pass, args.insecure)
        try:
            polemica.login()
            print(f"\nPolemica API: login OK (base={polemica_base})")
        except Exception as e:
            polemica_login_failed = True
            print(f"\nPolemica login failed: {e}", file=sys.stderr)
            polemica = None
    else:
        print("\nPOLEMICA_USERNAME/PASSWORD не заданы — шаги с getMatch/competition-games будут пропущены или частичны.")

    kind = tournament.get("kind")
    if kind == "STANDALONE":
        trace_standalone(
            series,
            players_by_tp,
            profile_base,
            args.profile_limit,
            polemica,
            args.insecure,
            polemica_login_failed=polemica_login_failed,
        )
    elif kind == "POLEMICA_COMPETITION":
        cid = tournament.get("polemicaCompetitionId")
        if cid is None:
            print("polemicaCompetitionId отсутствует")
            sys.exit(1)
        if not polemica:
            print("Для режима турнира нужны учётные данные Polemica API.")
            sys.exit(1)
        trace_competition(series, int(cid), polemica)
    else:
        print(f"Неизвестный kind: {kind}")


if __name__ == "__main__":
    main()
