# Progress

## Hidden Codex fantasy player MVP (сентябрь 2026, не активирован)

- [x] Согласованы design и implementation plan скрытого AI-managed пользователя с обычной экономикой, marketplace, полной исторической Polemica-аналитикой, persistent memory и почасовым расписанием.
- [x] Backend V83: opaque Bearer credentials с hash/expiry/revoke/audit, internal agent marker без публичного DTO, TMA manual-login guard, Telegram recipient suppression и pack-purchase `Idempotency-Key` replay для INSTANT/CHOOSE.
- [x] Agent runtime: закрытые Fantasy/Research/Memory MCP tools, trusted immutable Research snapshots, sealed-decision ACT authorization, single-attempt durable intents/reconciliation, SQLite WAL/blob memory, prompt/version audit, flock и fail-closed runner.
- [x] Проверки: Python 3.10 runtime `100 passed`; backend focused compile/unit green; изолированный remote Testcontainers прогон Bearer + pack idempotency/concurrency на PostgreSQL 16 green с Docker API 1.44.
- [x] На `codex@51.250.97.185` установлен только disabled no-secret runtime в `/home/codex/.local/share/polemica-agent-runtime`; MCP services/timer/cron отсутствуют, production run/write не выполнялся.
- [x] Commits `c4d2533`/`4910104` отправлены в public `master`; production workflow success, VPS SHA совпадает, backend healthy, Flyway V83 физически подтверждён, credentials count = 0.
- [x] Подготовлен root-only `install-system-disabled.sh`: dedicated broker user, `/opt` runtime, root-owned empty env files, SQLite migration и activation-ready systemd units без start/enable/credentials.
- [ ] Runtime system install blocked: root SSH запрещён, `codex` sudo требует интерактивный пароль; безопасная broker/runner изоляция не заменяется user-service компромиссом.
- [ ] Activation gate: production account/credential, read-only Polemica credential, model/reasoning, expert cohort/stop rule/retention/alerts, live contract smoke и staged read-only→team→economy→marketplace enablement.

- [x] **2026-09-04 — Polemica Grand Slam 2026 card skin:** Flyway V82 seeds `polemica_grand_slam_2026`; the TMA applies its electric-blue/cyan frame, `GRAND SLAM` badge, glow/sweep, name, and perk-chip treatment on every existing card surface without changing component geometry. Reduced-motion is supported. Backend/test compilation, TMA production build, and browser CSS verification passed. Not deployed yet.
- [x] **2026-08-28 — Opt-out игрока `41582`:** production tournament `39` очищен до 19 игроков, FK-связей с историей нет. Flyway V81 сохраняет отдельный opt-out, условно удаляет orphan-профиль и вместе с backend import policy блокирует прямой Polemica ID и алиасы при добавлении в любой tournament; unit tests и Kotlin/test compilation прошли, Testcontainers scenario blocked без Docker. Изменения ещё не развёрнуты.
- [x] **2026-08-28 — RESULT identity aliases:** matcher canonicalizes `фокс/fox` and mixed-script `ОloF/olof` in sheriff and mafia evidence; focused test passed. Production backend image `2b92632099c8...` is healthy with rollback `rollback-pre-identity-aliases-20260828`; ЗЛ №28 is READY, while ЛП №41 was separately operator-confirmed and finalized by the normal queued flow after readiness recovered.
- [x] **2026-08-26 — ЗЛ alias `Doc.` -> `Doc`:** punctuation-preserving Spring map binding (`[Doc.]`) and exact resolver alias are covered by unit/binding tests; same immutable evidence refreshes only unapplied announcements after a resolver-version change. Production post `2292` now has READY roster 10/10, including `Doc.` -> `Doc` and `Монарх` -> `Воробей`; item 25 is `READY_TO_PREVIEW`, operator message `218624` contains delivered `CREATE_PREVIEW`, backend image `f0049a94fd03...` is healthy, and rollback is retained.
- [x] **2026-08-26 — Восстановление OCR анонса после Telegram edit:** targeted replay поста `2292` доставил `ROSTER_REVIEW` и безопасно оставил ЗЛ №28 в `NEEDS_REVIEW` с 9/10 распознанными игроками. Semantic duplicate с новой source version теперь повторно ставит только source-media `SUPERSEDED` OCR; same-version loop и terminal failures не переоткрываются. 63 worker tests прошли; production worker `e2656cf7df61...`, backend health и очереди проверены, rollback image сохранён.
- [x] **2026-08-24 — Default expected games на турнире:** nullable `defaultExpectedGameCount` добавлен в tournament schema/admin API/UI; новая серия наследует его при omitted/null count, допускает числовой override и сохраняет snapshot независимо от будущих изменений турнира. Tri-state update позволяет очистить default. Backend compile, DTO unit test и admin build прошли; Testcontainers integration scenario не стартовал без Docker daemon. Production DB later confirmed Flyway V80 applied at `2026-08-24 11:30:08Z`; current admin static rollout remains unverified.
- [x] **2026-08-24 — Audited Telegram RESULT correction:** Flyway V79 хранит immutable per-game mafia-line override отдельно от исходной Telegram revision; backend под series/evidence lock принимает только состав, совпадающий с текущими DON/MAFIA ролями Polemica, сохраняет admin actor/reason и audit event, а admin UI предоставляет отдельную correction modal. Production ЛП №39 (`series_id=246`) исправлена и финализирована: 193 reward transactions / 9 588₣, 568 списаний uses; backend unit tests и admin build прошли, PostgreSQL integration scenario скомпилирован, но локально skipped без Docker.
- [x] **2026-08-17 — Telegram LP/ZL creation без спама:** preview, confirm, created и active редактируют одно операторское сообщение; после создания доступны `Перевести в ACTIVE` и ссылка на admin. Activation — отдельное signed same-actor действие со строгим locked `UPCOMING -> ACTIVE`; финализация использует отдельное RESULT-сообщение и редактирует его внутри собственного flow. Flyway V77 и commit `e2f796b` развёрнуты в production lightweight image `432974d7aaf6...`; backend/worker/DB, egress guard, TMA/admin HTTP и очереди проверены. Unit/regression tests прошли; 17 Testcontainers-сценариев скомпилированы, но skipped без Docker.
- [x] **2026-08-16 — STANDALONE sync снова additive:** ранее найденные игры не удаляются при изменении префикса, даты или ростера; явное ручное удаление остается доступно. Competition sync сохраняет snapshot-семантику диапазона.

## Admin series finalization (август 2026)
- [x] Admin readiness/finalization допускает готовые `UPCOMING`/`ACTIVE`/`SCORING` серии без snapshot-hash в UI; backend повторно проверяет readiness под lock, а Telegram automation сохраняет `SCORING` + checksum fence.
- [x] Ложные timeout после committed finalization устранены: массовый achievement recompute для `SERIES_FINALIZED` выполняется best-effort async по пользователям, admin перепроверяет неоднозначный HTTP outcome, tracked Nginx fallback поднят до 300 секунд.

## Production Telegram webhook relay (август 2026)
- [x] Создан DNS `tg-hook.maftourbot.ru -> 91.108.240.119`; на VPS2 установлен Nginx и выпущен Let's Encrypt сертификат с успешным renewal dry-run.
- [x] HTTPS ingress принимает только exact `POST /api/v1/telegram/webhook` из официальных Telegram CIDR, сохраняет secret header, не логирует body/secret и возвращает upstream non-2xx/`502` без маскировки.
- [x] Внутренний тракт `VPS2 wg2 -> VPS1 wg0 -> Fantasy wg-tg` ограничен адресами `10.20.0.2 -> 10.10.0.4:18082`, а listener Fantasy Nginx доступен только через WireGuard и проксирует на loopback backend `127.0.0.1:18080`.
- [x] Webhook переключён с `drop_pending_updates=false`; реальный Telegram update получил `200`, pending queue стала `0`, `last_error` очищен. Synthetic import callback записан и обработан один раз, pending/leased inbox rows = `0`.
- [x] Rollback установлен на Fantasy VPS: `sudo /usr/local/sbin/polemica-webhook-rollback`.

## Telegram league import automation (август 2026)
- [x] Automatic writes имеют delivery barrier: pending operator notification должна быть успешно доставлена, после чего начинается новый 120-секундный hold; Bot API outage блокирует lease/write.
- [x] Global-off/mode/generation sweeps terminally отменяют jobs/actions, очищают lease token и supersede pending alerts; source edit выполняет job cancellation после commit без обратного lock order.
- [x] Sync/scoring сохраняют selector fingerprints, а completion требует совпадения текущего tournament/series/roster/alias selector с обоими checkpoints.
- [x] Admin API/UI поддерживает `expectedGameCount`, запрещает ручной выбор `FINISHED` и блокирует `SCORING` без ожидаемого числа игр.
- [x] Admin finalization выполняется через readiness preview и обязательный checksum-bound confirm; stale readiness отклоняется backend под lock.
- [x] `AUTO_CREATE_PENDING` / `AUTO_FINALIZE_PENDING` уведомляют операторский чат об ETA, mode и policy generation; финализация имеет отдельный cancel hold не менее 120 секунд.
- [x] Automatic-конфиг fail-fast требует ingest + production writes + operator notifications, а automatic finalize также result processing; stale manual actions привязаны к generation и отменяются при mode/generation change.
- [x] Production rollout manual-flow выполнен коммитом `cec1abe`: Flyway V76, backend OCR roster mapping и worker OCR включены, roster write разрешён только после same-actor preview/confirm; backend/worker healthy, старый backlog не переигрывался.
- [x] Real-corpus OCR hardening: golden `2243` подтверждает 10/10 ЛП; golden `2247` разбирает caption replacement `Монарх → Воробей`, explicit OCR aliases `Градиент → Gradient` / `Cristo I → Cristo` и атомарно создаёт итоговый состав без исходящего игрока. Неизвестные/неоднозначные формулировки блокируются.

## Что реализовано

### Discovery: Скаутская лаборатория и импорт анонса (август 2026)
- [x] Создан отдельный brief `docs/features/IDEAS-SCOUTING-LAB-AND-ANNOUNCEMENT-IMPORT.md` с продуктовым сценарием, mobile UX, MVP, API/data sketches, производительностью, метриками, рисками и этапами развития обеих идей.
- [x] Лаборатория явно не дублирует scoring replay: она до дедлайна сравнивает доступные пользователю карты и предлагает объяснимые допустимые составы по историческим данным; перед реализацией требуется coverage/selection-bias audit.
- [x] Импорт уточнён под allowlisted `@polemica_closed_league`: read-only Telethon worker под user session принимает/polls и классифицирует `ANNOUNCEMENT`/`RESULT`, Yandex Vision OCR + schema-constrained extraction извлекают факты анонса, backend разрешает tournament/aliases/kind-specific fields, а result post запускает Polemica sync/reconciliation/scoring и readiness preview. Telegram не является scoring source of truth.
- [x] Зафиксирована automation ladder: shadow ingest -> reviewed announcement apply -> result assistant с human finalize -> guarded auto-create/auto-finalize только после lifecycle hardening и shadow evidence.
- [x] Lifecycle hardening: финализация берёт pessimistic row lock `series`; `updateSeries -> FINISHED` начинает с того же lock; sync/rescore отклоняются для finalized series до внешнего HTTP и повторно проверяют состояние под lock перед записью, включая пустой sync.
- [x] Telethon auth bootstrap развёрнут и проверен на production: dedicated account/session читает allowlisted `@polemica_closed_league`, identity metadata сохранена, session directory/file permissions `0700/0600`, one-shot container завершён.
- [x] Fail-closed VPN guard установлен: отдельная external network `172.24.0.0/28`, весь её egress через table 201, независимый nftables forward hook блокирует любой выход не через `wg-tg`, systemd применяет его до Docker и timer восстанавливает правила.
- [x] Production shadow channel-ingest запущен отдельным daemon service: bounded crash-safe polling/backfill, immutable revisions, rolling/deep edit reconciliation, conservative ЗЛ/ЛП classifier, durable SQLite inbox/outbox и healthcheck. Первый backfill: 100 сообщений, 22 `RESULT`, 78 `IGNORE`; repeated canary idempotent, notifications выключены, production series/finalized counts неизменны.
- [x] Operator notifications включены в существующий приватный админский чат через Fantasy bot: explicit `notify-test` доставлен, worker healthy с `notificationsEnabled=true`, 2 накопившихся shadow-candidate события доставлены, pending/failed outbox = 0. Bot token хранится только в restricted VPS env; reuse существующего production bot принят как осознанный blast-radius компромисс.
- [x] Announcement classifier rule v2 поддерживает русские текстовые даты (`11 августа`) и numeric dates; добавлен targeted idempotent replay одного Telegram message без массовой переотправки истории. Пропущенный ЛП series 34 post `2234` успешно пере-классифицирован и доставлен как один shadow candidate.
- [ ] Следующий этап — подтвердить mapping сезонов ЗЛ/ЛП, deadline/expected-game policies, OCR/media retention и reviewed apply contract; actual CREATED/FINALIZED notifications реализовать в backend только `AFTER_COMMIT`.

### Backend: подавление недоступных onboarding-чатов (август 2026)
- [x] По production-логам подтверждена причина `onboarding_tips; error`: Telegram отвечал `400 Bad Request: chat not found` для 55 уникальных чатов, а scheduler повторял те же попытки каждый час (476 ошибок за 7 запусков, 68 за запуск).
- [x] Только точный ответ `chat not found` переведён в существующий permanent-recipient suppression flow с `bot_blocked`; новый вход пользователя в TMA по-прежнему снимает флаг и разрешает повторную доставку.
- [x] Другие Telegram `400` остаются delivery errors; в delivery-логи добавлена категория уведомления. Targeted `TelegramBotApiClientTest` и `NotificationDeliveryServiceTest` проходят.
- [x] Backend выпущен вместе с observability rollout; первый production batch пометил недоступных адресатов через `bot_blocked`, не прерывая доставку остальным. Новые cloud-логи не содержат chat/user IDs.

### Production observability: Yandex Managed Prometheus (август 2026)
- [x] Repository rule `FantasyActiveSeriesSchedulerStale` hardened against cold-start repository failures via `completion > success` with the existing 20-minute grace period; fatal-path scheduler regression added.
- [ ] Upload the updated tracked `ops/monitoring/yandex/rules/domain.yml` to MSP and confirm the rule evaluates without parser/runtime errors.
- [x] Создан отдельный MSP workspace `mon9v9q5apml8dnnchl7`; attached service account `logger-robot` получил folder role `monitoring.editor`, без замены service account и без статического ключа на VM.
- [x] На `vyasma-mafia` установлен pinned Unified Agent `26.07.11`: Actuator и status остаются loopback-only, конфигурация проходит `check-config`, systemd unit enabled/active, Remote Write работает без ошибок и потерь.
- [x] Backend экспортирует low-cardinality `fantasy_*` метрики sync/scoring/finalization/notifications/schedulers через единый facade; finalization считается синхронным `AFTER_COMMIT` listener, notification — по конечному outcome после retry.
- [x] Кардинальность ограничена exact allowlist-фильтрами без `fantasy_*` wildcard: live 36 domain + 127 application + 33 Linux series; фиксированный worst case domain contract = 135, общий hard gate = 450. Сетевые интерфейсы, HTTP client URLs и другие высококардинальные метрики не отправляются.
- [x] Загружены и проверены PromQL rules `FantasyMetricsMissing`, `FantasyBackend5xxHigh`, `FantasyDatabasePoolSaturated`; critical -> Cloud Push + email, warning -> Cloud Push. Синтетический firing/resolved тест успешно доставлен и удалён.
- [x] Загружены domain rules `FantasyActiveSeriesSchedulerStale`, `FantasySyncOrScoringRepeatedErrors`, `FantasyNotificationDeliveryErrorRateHigh`; MSP API подтверждает `OK` без evaluation error.
- [x] Через официальный DashboardService gRPC API создан `Polemica Fantasy — Production` (`polemica-fantasy-production`, id `fbec6eh0cj6qn8u95p6r`) с 8 MSP widgets; все PromQL запросы проходят parser, target-level `step: "auto"` удалён как невалидный Remote API duration, UI показывает реальные графики всех 8 widgets без alert errors.
- [x] Production backend развёрнут lightweight overlay из локально собранного JAR; старый image сохранён как `rollback-domain-metrics-20260805`, server checkout остался чистым кроме существующего `achievement-report.json`.
- [ ] Telegram notification channel — после активации Yandex Cloud notification bot получателем.

### Production observability: direct Monium Logs (август 2026)
- [x] `logger-robot` получил folder role `monium.logs.writer`; отдельный API key `ajeo1kns6trm1ga75luu` ограничен scope `yc.monium.logs.write` и хранится только в `/etc/polemica-fantasy/monium-logs.env` как `root:root 0600`.
- [x] Backend пишет Logstash JSON в `/var/log/polemica-fantasy/backend.json` с rotation 10 MB/file, 100 MB total и history 7 days; Docker console cap `10m x 3` сохранён.
- [x] Dedicated `polemica-monium-logs.service` на pinned/tested Fluent Bit `5.1.0` отправляет OTLP/HTTP напрямую в project `folder__b1gdldjru7jl4ljql7ij`, cluster `production`, service `polemica-fantasy-backend`; filesystem queue ограничена 100 MB. Legacy generic `fluent-bit.service`/`yc-logging` config не используются и остались disabled/untouched.
- [x] Cloud-log guardrails: явные Telegram/internal user IDs удалены из сообщений; Telegram transport exceptions больше не сохраняют cause с token-bearing URI; `com.github.mafia.vyasma.polemica.library` понижен до WARN, потому что INFO auth refresh раскрывал configured username.
- [x] End-to-end acceptance в Monium Logs подтверждает свежий startup и ещё 49 post-fix records; среди них 0 email patterns, 0 explicit user/chat ID patterns и 0 bot-token patterns. Backend `UP`, forwarder/Unified Agent active, auth/TLS/retry errors отсутствуют.
- [x] Backend развёрнут lightweight JAR overlay `f4e2f23c8412...`; предыдущий image сохранён как `rollback-pre-monium-20260806`, server git checkout не менялся. Targeted Telegram secret-leak test, Kotlin compilation, Spring JSON smoke, Compose validation и Fluent Bit `--dry-run` прошли.
- [!] Первый acceptance-query до WARN override успел отправить одну запись с Polemica login email; дальнейшая отправка закрыта, но уже принятая запись остаётся в Monium до истечения retention.

### Production infra: восстановление Telegram Bot API egress (июль 2026)
- [x] Подтверждено внешнее ограничение маршрута Yandex Cloud → Telegram: DNS корректен, локальный firewall не блокирует, обычный HTTPS работает, но новые TCP-соединения к Telegram массово терялись до TLS.
- [x] На production fantasy VPS настроен отдельный WireGuard `wg-tg` к существующей двухузловой цепочке с зарубежным egress `91.108.240.119`; через table `201` направляются только официальные Telegram IPv4 CIDR для Docker-сети приложения.
- [x] Конфигурация переживает restart `wg-quick@wg-tg`, включена в autostart; root Bot API probes и авторизованный `getMe` проходят, остальной backend egress остаётся прямым через `51.250.18.236`.

### Backend + Admin: сверка результатов игроков серии (июль 2026)
- [x] Добавлен admin endpoint `GET /api/v1/admin/series/{id}/results`: фактические участники берутся из `series_game.game_data_cache`, а текущие базовые очки — со строгим разбором публичной Polemica match-page по позиции за столом; fantasy-перки, rarity modifiers и неполный `fantasy_team_card_game_score` не используются.
- [x] Для `STANDALONE` колонки получают номера `1..N` в стабильном хронологическом порядке, для `POLEMICA_COMPETITION` показывают реальный `game.num`; physical duplicate games сохраняются и различаются Polemica ID/table/phase.
- [x] Partial/unfinished/missing/invalid данные не превращаются в ноль: endpoint возвращает per-game status и warnings, строки с неполным итогом помечаются, а duplicate cached player ids остаются отдельными audit rows.
- [x] Admin series page получила lazy drawer с горизонтальной таблицей, refresh/error/empty states и TSV-копированием строк `Ник\tБалл1...`; результаты инвалидируются после sync/add/delete/calculate/assign.
- [x] Проверки: focused `SeriesResultsServiceTest` / `PolemicaPublicPointsLoaderTest`, `./scripts/codex-check.sh quick`, повторный admin production build и `git diff --check` прошли; остаётся существующий Vite warning по крупному `antd-vendor`.

### Backend + TMA + Admin: периодический рейтинг и уникальные награды (июль 2026)
- [x] Исправлен discovery draft `docs/features/DESIGN-PERIODIC-RATING-AWARDS.md`: итог пользователя — сумма `FantasyTeam.totalScore` по зачётным финализированным сериям выбранной лиги; карточки не являются отдельными единицами рейтинга.
- [x] Реализован biweekly MAIN-рейтинг: серия относится к периоду по `MAX(series_game.played_at)`, учитываются только финализированные серии, границы `[start, end)` в `Europe/Moscow`, итог округляется до 2 знаков, места — competition ranking; незавершённая серия блокирует finalize.
- [x] Реализован top-10 trophy ladder: #1 EPIC, #2–5 RARE с убывающей свободой выбора, #6–10 COMMON выбранного игрока + 50₣; при ничьей награждаются все пользователи с `rank <= 10`.
- [x] Добавлен полный reward journey `/rating/rewards/{rewardId}/create`: reward hub, серверный поиск игрока, выбор перков и skin, autosave draft, атомарная auto-issue после финального подтверждения, reveal и deep-link в коллекцию; admin review/changes/issue оставлен для legacy/exception записей.
- [x] Trophy card в коллекции визуально отделена от обычной карты: tier-specific metallic frame, layered glow/sweep, medal-плашка места и периода, premium cap/chips/name; внутренний serial показывается только в detail provenance.
- [x] Добавлены 12 tier/accent skin codes, реалистичный preview с редкостью, множителем, перками, местом и serial; immutable period/rank/serial/original-owner provenance хранится на `user_card` и показывается в коллекции после последующей продажи.
- [x] Добавлены admin period create/open/preview/finalize и legacy-очередь review/issue; finalize и issue идемпотентны и выполняются под блокировкой с audit actor/reason.
- [x] Trophy-карты запрещены как входы merge; существующий in-place Legendary upgrade остаётся допустимым.
- [x] Добавлен пользовательский архив периодов: `/rating` позволяет выбрать прошлый финализированный период и показывает его immutable leaderboard и личный вклад по сериям.
- [x] Добавлена линейка из 6 достижений «Рейтинг периодов» с прогрессом по finalized snapshot, competition ties, launch cutoff и targeted recompute после finalize; награды — только фантики (50/250₣ за участие, 200/1 000₣ за top-10, 400₣ за пьедестал, 600₣ за победу; 2 500₣ за всю линейку), trophy-карты и скины не дублируются.
- [x] Flyway **V72** открывает первый период `[17 июля 00:00, 20 июля 00:00)` МСК и публикует анонс в «Что нового» с переходом на `/rating`; пересекающийся открытый период с тем же стартом нормализуется без дубля.
- [x] Исправлены finalist options для мест 4–5: каждый reward получает отдельную тройку из актуального RARE-пула активных auto-generated packs, V73 обновляет незавершённые snapshots первого периода, а TMA явно показывает фиксированные комбинации вместо глобального поиска.
- [x] Проверено на локальном PostgreSQL: Flyway V69 применён, Hibernate schema validation и backend startup успешны; clean backend compile, targeted rules/merge tests и обе frontend production builds проходят.
- [ ] Отдельный следующий этап: scheduler создания/закрытия периодов, Telegram reminders, rollout feature flag и расширенные интеграционные тесты полного reward lifecycle.

### Backend: исправление onboarding для нескольких лиг (июль 2026)
- [x] `firstOpenTeamTarget` проверяет наличие команды пользователя в серии через `exists`, поэтому MAIN + BUDGET больше не приводят к `NonUniqueResultException` и `500` в onboarding checklist.
- [x] Production health и публичная статика проверены; backend `compileKotlin compileTestKotlin` прошел.

### Backend: баланс перков (июль 2026)
- [x] Flyway **V68** меняет системные `bonus_points`: `sniper=4.0`, `firstKickedFullGuess=3.0`, `winThreeToThree=1.3`, `ninja=2.0`.
- [x] В `/whats-new` добавлена новость “Баланс перков обновлен” с пояснением, что изменения применяются к новым расчетам и не пересчитывают уже завершенные результаты автоматически.

### DX: Telegram support export (июль 2026)
- [x] Добавлен `scripts/telegram-support-export/export_support_messages.py` для выгрузки support-супергруппы через Telegram user-account MTProto на Telethon.
- [x] Скрипт умеет `--list-dialogs`, читать чат по `--chat`, по умолчанию оставлять только forwarded user messages, ограничивать период/лимит и писать JSONL/CSV.
- [x] Дополнительно генерируется первичный Markdown-отчет `support-requests.md` с эвристической раскладкой на bug/feature/question/noise; финальная продуктовая кластеризация остается ручным/LLM-этапом после выгрузки.

### Backend: уведомление при админском начислении фантиков (июль 2026)
- [x] `UserService.grantFantikiByTelegramId` после успешного `ADMIN_GRANT` публикует событие с telegram id, суммой, балансом после начисления и нормализованной причиной.
- [x] `AdminFantikiGrantNotificationListener` доставляет сообщение через `NotificationDeliveryService` в категории `ADMIN_BROADCAST` («Сообщения от администрации»), без Markdown-режима, чтобы причина отображалась буквально.

### Backend+TMA: профильная косметика из достижений (июль 2026)
- [x] Зафиксирован design brief `docs/features/DESIGN-PROFILE-COSMETICS.md`: `COSMETIC_UNLOCK` доводится от claim/unlock до выбора в витрине профиля и публичного отображения; `PROFILE_FRAME` остается отдельным рабочим flow, `BADGE_STYLE` остается через featured achievements.
- [x] Backend vertical slice: Flyway **V67** добавляет каталог `profile_cosmetic`, выбранные поля `profile_title_code` / `profile_accent_code` / `profile_background_code`, explicit seed текущих title/accent reward codes и fallback для уже выданных `COSMETIC_UNLOCK` rows.
- [x] User API `/api/v1/me/profile-customization` отдаёт unlocked cosmetics и выбранные title/accent/background, валидирует выбор по enabled catalog + kind + unlock текущего пользователя, а stale saved values читает как `null`. Public profile DTO отдаёт selected title/accent/background.
- [x] TMA `/profile-customization` получила выбор титула, акцента и зарезервированного фона; публичный профиль показывает титул под именем и allowlisted accent на showcase block. `/achievements` инвалидирует profile-customization после cosmetic claim и показывает CTA **Настроить витрину**.
- [x] Admin backend validation запрещает `COSMETIC_UNLOCK` reward code без `profile_cosmetic` catalog row. Проверки: backend `compileKotlin compileTestKotlin` и TMA `npm run build` прошли; targeted Testcontainers integration tests не стартовали локально из-за недоступного Docker provider.

### TMA: вкладки достижений по состоянию награды (июль 2026)
- [x] `/achievements` теперь использует primary-фильтры **Забрать / Не завершено / Получено** вместо просмотра только по backend-категориям.
- [x] По умолчанию открывается **Забрать**, если есть незабранные награды; иначе **Не завершено**. Pending `CARD_CHOICE_ROLL` остаётся actionable: после reload пользователь видит CTA **Выбрать**, а после выбора вариантов карточная награда продолжает прежний claim-flow.
- [x] Вкладка **Не завершено** показывает `IN_PROGRESS` перед `LOCKED`, а `LOCKED` в карточке подписан как «Не начато».
- [x] Проверки: `npm run build` (`polemica-fantasy-webapp`), Browser QA `/achievements` desktop/mobile, переключение фильтров, pending choice resume, console warn/error clean.

### Backend+admin+TMA: ссылки на трансляции серий (июнь 2026)
- [x] Flyway **V66** добавляет `tournament_stream_link` и `series_stream_link`, чтобы хранить много ссылок на уровне турнира и конкретной серии.
- [x] Admin create/edit турнира и серии редактирует `streamLinks` списком (`label` + `url`), backend валидирует только `http(s)` URL.
- [x] User API отдаёт effective ссылки серии как ссылки турнира + ссылки серии; новый `GET /api/v1/tournaments/active-series` показывает на главной `ACTIVE`/`SCORING` серии независимо от дедлайна команды.
- [x] TMA показывает трансляции в блоке активных серий на главной, в блоке открытой подачи состава и на странице серии; для `UPCOMING` трансляции скрыты.
- [x] Главная рендерит stream links как icon-only кнопки с `aria-label/title`; страница серии показывает иконку + подпись. Twitch/VK Video определяются по URL.
- [x] Проверка: `./scripts/codex-check.sh quick`, targeted backend/TMA build и локальный browser smoke успешно; Vite warning про крупный `antd-vendor` остался существующим.

### Backend: sheriff perks (июнь 2026)
- [x] Flyway **V65** добавляет перки `sheriffCheckBlack` и `voteOutSheriffDay1Or2` в каталог `perk` с applicable roles и стартовыми `bonus_points` `0.75` / `2.75`.
- [x] Скоринг получил detector `sheriffCheckBlack`: шериф получает бонус за каждого уникального черного игрока, включая дона, которого проверил в игре.
- [x] Скоринг получил detector `voteOutSheriffDay1Or2`: все черные игроки получают бонус, если шериф покинул стол голосованием на 1 или 2 день.
- [x] `ninja` выключен из random cards (`can_appear_on_random_cards = false`) по жалобам игроков; уже выданные карты не меняются.
- [x] Release note для `/whats-new` не сидится миграцией: пользователь напишет анонс вручную.
- [x] `scripts/balance_perks.py` обновлен для новых ролей. Read-only production sample: 1000 / 18170 уникальных profile matches, target excluding `ninja` = 0.0618; расчетные рекомендации были `sheriffCheckBlack=0.71`, `voteOutSheriffDay1Or2=2.82`, вручную округлено до `0.75` / `2.75`.
- [x] Проверки: `compileKotlin compileTestKotlin` + targeted `StandardPerkDetectorsTest` / `PerkDetectorRegistryTest` успешно.

### Backend+admin: aliases и merge fantasy players (июнь 2026)
- [x] Flyway **V64** добавляет `fantasy_player_alias` как source of truth для Polemica id и `fantasy_player_merge_audit`; `fantasy_player.polemica_user_id` остаётся cached primary alias.
- [x] Backend получил resolver Polemica id -> `fantasy_player`, admin add-alias и merge preview/confirm под `/api/v1/admin/fantasy-players`.
- [x] Merge переносит прямые FK на target (`tournament_player`/`series_player` с dedupe, `card_pack_player`, watches, favorite badge, card merge audit, `card_template`) и блокирует pending JSON/config references, replacement alias conflicts и active-team duplicate-player conflicts.
- [x] Исторические replacement alias references в finalized series больше не блокируют merge и сохраняются без переписывания; тот же конфликт в нефинализированной серии остаётся blocker.
- [x] Add player by Polemica id, STANDALONE sync, scoring, perk statistics и series replacement validation учитывают все aliases; scoring сначала ищет aliases основного игрока, затем replacement fallback.
- [x] Admin Players показывает aliases, добавляет alias и выполняет merge через preview modal с blockers/warnings и обязательной причиной.

### Backend+admin: аудит операций фантиков (июнь 2026)
- [x] Flyway **V63** добавляет `fantiki_transaction.admin_reason` для текстовой причины ручных операций.
- [x] Admin `give-fantiki` / `take-fantiki` требуют `adminReason`, триммят и сохраняют её только для `ADMIN_GRANT` / `ADMIN_CONFISCATE`.
- [x] Admin API отдаёт paged журнал `fantiki_transaction` через `/api/v1/admin/users/fantiki-transactions` с опциональными фильтрами `telegramUserId` и `reason`.
- [x] Админка показывает обязательное поле Reason в `User tools`; журнал по умолчанию открыт на `ADMIN_GRANT` по всем пользователям, с переключателем на списания или все транзакции.

### DX: TMA CSS modularization (июнь 2026)
- [x] `polemica-fantasy-webapp/src/index.css` сокращён до ordered import manifest.
- [x] Существующие TMA CSS-правила механически разнесены по тематическим файлам `polemica-fantasy-webapp/src/styles/*.css` без изменения классов и порядка каскада.
- [x] Проверки: `git diff --check` для CSS и `npm run build` (`polemica-fantasy-webapp`) — успешно.

### Backend+TMA: выбор игрока для бейджа «Любимый игрок» (июнь 2026)
- [x] Добавлен nullable `favorite_badge_fantasy_player_id` в `user_profile_customization` (Flyway **V62**) для явного выбора игрока бейджа.
- [x] Backend отдаёт eligible-options для бейджа по условию `SAME_PLAYER_4_RARITIES`, валидирует выбранный `fantasy_player_id` и использует выбор в заголовках достижений и публичной витрине.
- [x] TMA `/profile-customization` получила селект «Любимый игрок» с автоподбором как fallback.

### Backend+TMA: скидка на legendary upgrade за переподписание (июнь 2026)
- [x] Legendary upgrade использует existing `marketplace.contract_reissue_discount_percent`: effective cost считается от `legendary.upgrade.cost` с дисконтом за `user_card.times_renewed` (текущие `↻0/1/2`: 400/340/280₣).
- [x] `/api/v1/legendary-upgrade/info` отдаёт `contractReissueDiscountPercent` и `costTiers`; `LegendaryUpgradeService.upgrade` списывает effective cost конкретной карты.
- [x] TMA wizard показывает цену выбранной EPIC по контракту и проверяет баланс по effective cost; `/help` добавлена таблица цен legendary upgrade.
- [x] Flyway **V60** публикует release note “Ветеранам проще стать легендами” с CTA на `/cards?legendaryUpgrade=1`.

### Design: слияние карт (июнь 2026)
- [x] Подготовлен draft-дизайн `docs/features/DESIGN-CARD-MERGE.md` для механики `3 COMMON -> 1 RARE` и `3 RARE -> 1 EPIC` в рамках одного `fantasy_player`.
- [x] Зафиксированы продуктовые правила перков: `COMMON -> RARE` выбирает 1 перк из roll; `RARE -> EPIC` выбирает 2 уникальных перка из входных RARE, а при `A/A/A` сохраняет `A` и предлагает второй перк из roll без `A`.
- [x] Зафиксированы правила контрактов и истории: результат создаётся новым `user_card`, входные карты soft-delete, `times_renewed = max(inputs)`, `uses_remaining = min(baseUses(resultRarity), sum(inputUses))`, provenance хранится через `CARD_MERGE` и audit tables.
- [x] Уточнён UX: `/cards/merge`, выбор игрока/операции/материалов, полноценный preview, предупреждения про value loss, BUDGET, контракт, скины, stale states и CTA `Снять с продажи` для ACTIVE marketplace cards.
- [x] Зафиксированы анти-reroll preview rules, `/help`/`/whats-new` коммуникация и V1 achievements для merge (`CARD_MERGES`, `CARD_MERGE_EPIC_RESULTS`, `CARD_MERGE_UNIQUE_PLAYERS`).
- [x] Реализация backend/TMA/admin для слияния карт: Flyway **V61**, user API `/api/v1/cards/merge/*`, admin read-only `/api/v1/admin/card-merges`, TMA `/cards/merge`, admin page **Card merges**, release note/help and achievement progress.
- [x] Проверки: backend `compileKotlin compileTestKotlin`, targeted `CardMergeServiceTest`, TMA/admin `npm run build`, `./scripts/codex-check.sh quick` — успешно. Локальный smoke повторно выполнен на backend `28080/28081` из-за занятого `8080`: health ok, Flyway v61 validated, `merge/options` и `merge/preview` ok, TMA `/cards/merge` дошла до preview-state, admin `/card-merges` отрендерился.

### Backend+admin+TMA: публичный номер серии (июнь 2026)
- [x] Flyway **V59** добавляет `series.public_number` и бэкофиллит его из последнего числа в `series.name`; если чисел нет — `1`.
- [x] Backend вычисляет `publicNumber` при создании серии и пересчитывает при изменении названия; поле отдано в admin/user DTO, при этом `game_num_from/game_num_to` остаются техническими полями sync для `POLEMICA_COMPETITION`.
- [x] TMA показывает бейдж «Серия N» из `publicNumber`; админка показывает номер в списке серий, но форма создания/редактирования не требует ручного ввода.

### Backend+admin: глобальный каталог игроков (июнь 2026)
- [x] Добавлена admin-страница **Players** со списком всех `fantasy_player`, поиском, созданием, редактированием ника, загрузкой фото и добавлением выбранного игрока в целевой турнир.
- [x] Backend получил `FantasyPlayerAdminController` / `FantasyPlayerAdminService`: `GET/POST/PUT /api/v1/admin/fantasy-players`, `POST /api/v1/admin/fantasy-players/{id}/photo`, DTO с `tournamentIds`, `tournamentCount` и `cardTemplateCount`.
- [x] `POST /api/v1/admin/tournaments/{id}/players` теперь принимает `fantasyPlayerId` для переиспользования существующего игрока, сохраняя старый сценарий `polemicaUserId + nickname`.
- [x] Форма Add player на странице турнира получила поиск существующего игрока из каталога, чтобы не копировать Polemica id и ник вручную.
- [x] Проверки: backend `./gradlew compileKotlin compileTestKotlin` успешно; admin `npm run build` успешно.

### Backend+admin: персональные сообщения через бота (июнь 2026)
- [x] Добавлен admin endpoint `POST /api/v1/admin/notifications/direct` для отправки Telegram MarkdownV2 сообщения одному `telegram_user` по `telegramUserId`.
- [x] Персональная отправка переиспользует `NotificationDeliveryService` и категорию `ADMIN_BROADCAST`, поэтому учитывает `bot_blocked`, 429 retry и пометку пользователя при 403; ответ возвращает `sent/skippedBlocked/skippedPreference/failed`.
- [x] `AdminUserListItemDto` расширен `botBlocked`; админская страница Users показывает статус доступности бота.
- [x] Страница Broadcast в админке стала **Bot messages** с вкладками Direct message и Broadcast; direct-вкладка ищет пользователя, показывает warning для `botBlocked`, валидирует MarkdownV2 и даёт preview.
- [x] Проверки: targeted `AdminBroadcastNotificationServiceTest` успешно; `npm run build` (`polemica-fantasy-admin`) успешно.

### Backend+admin+TMA: CHOOSE-паки (июнь 2026)
- [x] В `card_pack` добавлен `openingMode` (`INSTANT`/`CHOOSE`), Flyway **V58** создаёт `user_card_pack_choice` для pending выбора из 3 вариантов с reservation/payment fields.
- [x] User Store API: `POST /store/packs/{id}/buy` возвращает `OPENED` или `PENDING_CHOICE`; `POST /store/pack-choices/{choiceId}/select` row-locks choice, материализует выбранный вариант, повтор того же option идемпотентен, другой option после выбора возвращает `409`.
- [x] Общая логика draw/materialize/finalize вынесена в `CardPackService`, чтобы INSTANT и CHOOSE совпадали по скинам, перкам, uses, ownership history, Tyulenchik и pack-open facts.
- [x] TMA Store показывает “Выбор 1 из 3 наборов”, pending resume CTA и новый `PackChoiceOverlay` с горизонтальным scroll-snap carousel; после select открывается существующий `PackOpening`.
- [x] Admin Card packs позволяет выбрать Instant/Choose, для CHOOSE принудительно включает auto-generated и фиксирует V1-ограничение “3 варианта, выбрать 1”; User tools скрывает CHOOSE в прямом open-pack, backend admin open-pack тоже rejects CHOOSE.
- [x] Проверки: backend `compileKotlin compileTestKotlin`, TMA `npm run build`, admin `npm run build`, Docker backend rebuild, локальный API smoke buy/select/idempotency/409 — успешно. Targeted Testcontainers test не стартовал локально из-за недоступного Docker provider для тестов; Browser plugin заблокировал локальный URL политикой Browser Use, поэтому визуальный in-app pass не завершён.

### Backend+admin: ручное управление играми серии (июнь 2026)
- [x] Admin API получил `GET /api/v1/admin/series/{id}/games`, `POST /api/v1/admin/series/{id}/games` и `DELETE /api/v1/admin/series/{id}/games/{gameId}` для просмотра, добавления и удаления строк `series_game`.
- [x] Add по Polemica game id загружает полный `PolemicaGame`, делает upsert по `(series_id, polemica_game_id)`, для `POLEMICA_COMPETITION` валидирует принадлежность id связанному соревнованию и оставляет ручной `Calculate scores`.
- [x] Delete запрещен для `finalized` серий, удаляет связанные `fantasy_team_card_game_score` строки и пересобирает сохраненные totals команд/карт из оставшихся per-game breakdown.
- [x] Admin `SeriesDetailPage` получил блок **Games** с таблицей id/num/table/phase/playedAt/status, формой add-by-id, refresh и delete с подтверждением; действия заблокированы у finalized серий.
- [x] Проверки: backend `compileKotlin compileTestKotlin` успешно; admin `npm run build` успешно. Targeted `AdminApiIntegrationTest.admin can list and delete series games from scoring` не выполнился локально из-за недоступного Docker/Testcontainers.

### DX: skill для создания серий по анонсам (июнь 2026)
- [x] Добавлен проектный skill `.codex/skills/polemica-create-series-from-announcement` для production workflow по анонсам `Лиги Претендентов` и `Закрытой лиги`.
- [x] В skill зафиксирован безопасный порядок: read-only найти активный `STANDALONE` tournament и ростер, создать серию через admin API в `UPCOMING`, назначить `series_player`, затем проверить результат read-only запросами.
- [x] Добавлен helper `scripts/prod-admin-series.py`: dry-run по умолчанию, `--execute` для реального вызова admin API через VPS, credentials читаются только из remote `.env`, порт API `18080`.
- [x] Проверки: helper `--help`, dry-run `create-series`, dry-run `assign-players`, Python syntax parse, ручная проверка frontmatter. `quick_validate.py` не запущен до конца из-за отсутствующего модуля `yaml` в локальном Python окружении.
- [x] 18 июля skill обобщён на любые активные `STANDALONE` / `POLEMICA_COMPETITION` турниры: добавлены kind-specific payload, batch/resume, защита от дублей, разбор ролей/замен/временных аннотаций, production-derived numbering и проверка реальных `series_game` counters; `--execute` исправлен на глобальную позицию перед subcommand. Проверены YAML/frontmatter, Python syntax, dry-run create/assign, parsing глобального `--execute` и `git diff --check`; штатный `quick_validate.py` по-прежнему недоступен без локального `PyYAML`.
- [x] 28 августа добавлен отдельный переносимый `.codex/skills/polemica-api-create-series-from-announcement` для внешнего админа с доступом только к HTTPS admin API. Включены API-only discovery/duplicate/roster verification, точный primary/alias `find-player`, добавление отсутствующего подтверждённого игрока через `fantasyPlayerId` или точный `polemicaUserId`, проверка ожидаемого backend-derived `publicNumber`, обязательный explicit `gamePhase` для competition (`2` обычно для финала), строгий resume только при полном совпадении и пустом состоянии серии, operation ledger для partial commits без автоматического rollback/retry, безопасная работа с Basic Auth env, stdlib helper для GET/add/create/assign, dry-run по умолчанию и честная граница API-verification без заявлений о DB/team-card impact. Проверены helper `--help`, add/create/assign dry-run и Python compilation; `quick_validate.py` упирается в отсутствующий локальный `PyYAML`.

### TMA: fallback для игроков без фото (июнь 2026)
- [x] Добавлен общий компонент `PlayerImage`: показывает реальное изображение, а при `null` или `onError` переключается на CSS-силуэт со стабильным цветовым тоном по `fantasyPlayerId`.
- [x] Fallback применён в TMA на участниках турнира/серии, коллекции и grouped player view, сборке команды, pack opening, achievement choice cards, marketplace/listings/feed/transaction detail, истории, лидерборде, сравнении и модалках.
- [x] Backend/API/БД-контракты не менялись; существующее правило выбора карточного изображения `playerPhotoUrl ?? imageUrl` сохранено.
- [x] Проверки: `npm run build` (`polemica-fantasy-webapp`) успешно; локально поднят стек через `./scripts/local-up.sh --generate-init-data`, добавлены тестовые `NoPhoto Alpha/Bravo/Charlie/Delta` без `photo_url`, browser-проверка подтвердила 4 fallback-аватара на `/tournaments/1/participants` и 4 fallback-карточки на `/cards?view=players`.

### Backend+admin: MAIN top-100 + BUDGET 75% (июнь 2026)
- [x] Добавлен новый tier `series.reward.top100` для 51–100 места; `series.reward.participation` теперь описывает 101+ место.
- [x] Flyway **V57** фиксирует MAIN-сетку через economy config: `250 / 200 / 150 / 100 / 75 / 50 / 40 / 30`, а BUDGET переводит на `league.reward_scale.BUDGET = 75`.
- [x] `EconomyConfigService.getSeriesReward` и порядок `economy-info.seriesRewards` синхронизированы с новым tier; admin Economy сортирует `top100` в правильном месте.
- [x] Проверки: focused `EconomyConfigServiceTest`, backend `compileKotlin compileTestKotlin`, admin `npm run build`, TMA `npm run build` — успешно.

### Backend+admin: HTML-репорт турнира (июнь 2026)
- [x] `GET /api/v1/admin/tournaments/{id}/report.html?seriesIds=...` возвращает standalone `text/html` по выбранным сериям, требует непустой список `seriesIds` и отклоняет серии не из турнира.
- [x] `TournamentReportService` собирает данные из текущей БД read-only: метаданные турнира, серии, лидерборды лиг, лучший состав, топ составов, уникальный топ карточек по `fantasy_player`, полные перки шаблона, популярность и эффективность игроков.
- [x] Витринные счетчики игр показывают `total/total`, а лидерборды и статистика строятся только по рассчитанным `total_score` / `fantasy_team_card.score`.
- [x] Admin `TournamentDetailPage` получил кнопку `HTML report`, модалку выбора серий с `Select all` / `Clear` / `Open report`; HTML загружается через Basic Auth `apiFetch` и открывается как Blob в новой вкладке.
- [x] Проверки: backend `./gradlew compileKotlin compileTestKotlin` успешно; admin `npm run build` успешно. Targeted `AdminApiIntegrationTest` не выполнился локально из-за недоступного Docker/Testcontainers на initialization.

### Backend+TMA: глобальный резерв uses карт (июнь 2026)
- [x] `UserFantasyTeamService.attachCards` теперь проверяет reserved uses карты по всем незавершённым сериям (`series.finalized = false`), а не только внутри текущей серии; выбранные `user_card` читаются под `PESSIMISTIC_WRITE`.
- [x] `SeriesFinalizationService` лочит карты перед списанием и возвращает `409 CONFLICT` при overcommit вместо silent `maxOf(0, ...)`.
- [x] `GET /api/v1/me/cards?seriesId=...` считает `canJoinMoreLeagues` по глобальному резерву, сохраняя `leaguesInSeries` как список лиг текущей серии.
- [x] TMA `TeamPage` инвалидирует cards-cache после submit/update команды, чтобы новое состояние доступности подтягивалось сразу.
- [x] Добавлены regression-тесты на межсерийный резерв и overcommit финализации; TMA `npm run build` прошёл. Backend Gradle compile не был запущен из-за read-only sandbox: sandboxed Gradle упал на lock-файле `~/.gradle`, escalated compile был отклонён reviewer.

### Backend+TMA: ускорение публичных deep link команды (июнь 2026)
- [x] Публичные endpoints просмотра чужой команды больше не вызывают `FantasyTeamRosterPruningService` при каждом read-запросе; pruning остаётся в admin roster assignment flow.
- [x] `PublicFantasyTeamDto` расширен `seriesName`, TMA `LeaderboardPlayerTeamPage` больше не блокирует первый рендер полным `/api/v1/series/{id}` и откладывает leaderboard/details до загрузки команды.
- [x] Проверки: backend `./gradlew compileKotlin compileTestKotlin`, TMA `npm run build` — успешно.

### Backend+TMA: marketplace-перезаключения контракта через `timesRenewed` (май 2026)
- [x] Flyway **V54** добавляет `marketplace.contract_reissue_discount_percent`, сбрасывает `times_renewed = 0` для карт в ACTIVE-листингах и расширяет marketplace watch-фильтры полями `min_times_renewed` / `max_times_renewed`.
- [x] Marketplace create/update/buy использует effective min price с 15% дисконтом за каждое `timesRenewed`, запрещает продажу/покупку при `timesRenewed >= renewal.max_times`, а покупка восстанавливает uses и увеличивает `timesRenewed` на 1.
- [x] User marketplace DTO и `/me/cards` отдают контрактные поля для UI; TMA показывает маркер `↻ N/max`, добавляет фильтр по контракту в marketplace/watch и использует `minListingPrice` конкретной карты при продаже и смене цены.
- [x] Flyway **V55** публикует `/whats-new` лорный анонс с CTA на `/marketplace` и сидит draft-кампанию для Product communication; release notes получили CTA-поля, admin может отправлять draft-кампании вручную.
- [x] Flyway **V56** настраивает экономику релиза: комиссия marketplace `15%`, минимумы листинга COMMON `20₣`, RARE `40₣`, EPIC `120₣`, LEGENDARY без изменений `250₣`; TMA `/help` объясняет переподписание и уход игрока на покой.
- [x] Проверки: backend `compileKotlin compileTestKotlin`, unit `MarketplaceWatchServiceTest`, TMA/admin `npm run build`, `./scripts/codex-check.sh quick` — успешно.

### Backend+admin+TMA: замены игроков в скоринге серии (май 2026)
- [x] `series_player` получил optional `replacement_polemica_user_id`; `fantasy_team_card_game_score` сохраняет фактический `scored_polemica_user_id`, имя и флаг `scored_via_replacement`.
- [x] Admin API/UI позволяют задавать raw Polemica id замены для выбранного игрока серии; валидация запрещает неположительные id, unselected keys, совпадение с основными игроками и дубли замен.
- [x] Скоринг выбирает основного игрока приоритетно, иначе замену; перки и редкость остаются от исходной карты. TMA показывает replacement marker в детализации очков.
- [x] Проверки: backend compile, `DefaultScoringServiceTest`, admin build, TMA build прошли; targeted `AdminApiIntegrationTest` не был локально выполнен из-за недоступного Docker/Testcontainers.

### Admin: production blank screen fix (май 2026)
- [x] Диагностирован production blank screen на `https://admin.fantasy.maftourbot.ru/`: браузерный console error `Cannot read properties of undefined (reading 'Modal')` в `antd-vendor-DSgSAWTQ.js`
- [x] Причина: небезопасное `maxSize`-дробление Ant Design в Vite 8 / Rolldown создало circular dependency между `antd-vendor-*` chunks
- [x] Исправление: admin `vite.config.ts` держит `antd` в одном `antd-vendor` chunk, а `@rc-component` / `rc-*` зависимости вынесены отдельно
- [x] Проверки: `npm run build` (`polemica-fantasy-admin`) успешно; local `vite preview` рендерит `/login` без нового runtime error

### DX: Vite chunk-size warnings (май 2026)
- [x] TMA `vite.config.ts`: добавлены Vite 8 / Rolldown `codeSplitting.groups` для React, TanStack Query, Telegram SDK и прочего vendor-кода; крупнейшие JS chunks теперь ниже стандартного Vite warning limit 500 kB
- [x] Admin `vite.config.ts`: добавлены отдельные группы для React, TanStack Query, Ant Design/icons и vendor-кода; Ant Design оставлен единым chunk после production runtime regression с circular dependency
- [x] Проверки: `npm run build` для `polemica-fantasy-webapp` и `polemica-fantasy-admin` — успешно; для admin warning про >500 kB chunk допустим до более безопасного lazy-route splitting

### Design: система пользовательских достижений (май 2026)
- [x] Достижение `same_player_3_rarities` переведено на all-4-rarities условие через Flyway **V52** (`SAME_PLAYER_4_RARITIES`), reward metadata обновлена на RARE 2 из 5, а title/бейдж динамически показывают `Любимый игрок: {ник игрока}` в каталоге и profile showcase/customization
- [x] V1 styled achievement card choice: TMA `/achievements` показывает pending `CARD_CHOICE_ROLL` options как компактные pack-summary cards с rarity/skin visuals, selected marker и exact-count submit; Flyway **V51** добавил 5 achievement card skins and attached `metadata.skinCode` to supported rewards (`top_quarter_10` temporarily previews `common_challenge_edition` until common-only condition exists). Targeted seed/lifecycle tests and TMA build passed.
- [x] Backend foundation для achievement card rewards: DTO расширены `metadata` и card result structures, admin validation поддерживает `RANDOM_CARD` / `CARD_CHOICE_ROLL`, `CARD_SKIN_UNLOCK` исключен из backend cosmetic reward types, Flyway **V49** добавляет pending choice table и ownership acquisition type `ACHIEVEMENT_REWARD`, card reward options генерируются из eligible players активных auto-generated паков без `LEGENDARY` from packs
- [x] Все ранее dormant/future достижения включены и подключены к реальному прогрессу: marketplace buy/sell/watch/unique counterparties, social share/profile/compare/view public profile, legendary upgrade 1/3
- [x] Backend future achievements: Flyway **V48** добавил `user_legendary_upgrade_event` и включает 12 definitions для существующих DB; V46 seed для fresh DB теперь включает все 42 definitions; evaluators учитывают только post-launch факты, SOLD без sanctions, product events и timestamped legendary upgrade facts
- [x] TMA future achievements: share profile/team/place, compare views и открытие публичного профиля отправляют product events, после которых инвалидируется achievements cache
- [x] Профильные рамки достижений расширены на публичные списки: `user.profileFrameCode` добавлен в user snippets глобального рейтинга и лидербордов, TMA рисует компактную обводку имени в rating/leaderboard rows и pinned rows
- [x] Реализован Stage 3 Admin для пользовательских достижений: admin list/edit metadata/rewards, aggregate stats, dry-run UI, инвариант `tracking_started_at` при первом включении dormant rows, без новых marketplace/social/legendary evaluators
- [x] Реализован Stage 1 backend+TMA slice пользовательских достижений: Flyway **V46** schema/seed на 42 определения (30 enabled visible + 12 dormant disabled), catalog/claim API, admin dry-run с `instantCompleted=0`/`instantFantikiLiability=0`, post-commit progress events, lock-safe claim, `ACHIEVEMENT_REWARD`, pack-open fact table, `fantasy_team.created_at`, `series.finalized_at`, минимальная TMA `/achievements`
- [x] Stage 1 evaluators включают participation, BUDGET, results, collection, packs; dormant marketplace/social/legendary-upgrade definitions seeded but hidden/disabled
- [x] Проверки Stage 1: targeted `AchievementStage1IntegrationTest` — успешно; `cd polemica-fantasy-backend && ./gradlew compileKotlin compileTestKotlin` — успешно; `cd polemica-fantasy-webapp && npm run build` — успешно; `cd polemica-fantasy-admin && npm run build` — успешно; `./scripts/codex-check.sh quick` — успешно
- [x] Добавлен `docs/features/DESIGN-ACHIEVEMENTS.md` с V1-рамкой: достижения как витрина публичного профиля, автоматический прогресс, claim-based награды и launch baseline без выдачи за старые действия
- [x] Термины разведены: `perk` остаётся механикой карты/скоринга, `achievement` используется для пользовательского мета-прогресса
- [x] Продуктовые ограничения зафиксированы: награды только экономика/косметика, без бонусов к скорингу, uses, силе карт или правилам лиг
- [x] В дизайн включен явный seed-каталог V1: 42 основных достижения + 2 optional secret по категориям участие, бюджетная лига, результаты, коллекция, marketplace, паки/апгрейды, социальность и особые достижения
- [x] Зафиксирована политика истории для V1 seed: все достижения запускаются как `FROM_ACHIEVEMENTS_LAUNCH`; `CURRENT_STATE` и `RETROACTIVE_CUMULATIVE` оставлены только как future-only технические policy после отдельной оценки экономики
- [x] По production DB snapshot на 2026-05-25 посчитан rejected instant completion: retroactive/current-state вариант дал бы potential launch liability **343 695₣**, поэтому V1 seed требует `0₣` instant payout на dry-run
- [x] Зафиксированы осторожные вилки fantiki-наград: малые 10-25, средние 30-75, длинные цепочки 100-200, редкие исключения до 250

### Backend+TMA+admin: переименование достижений в перки (май 2026)
- [x] Backend-код переименован на `Perk*`: JPA entities, repositories, DTO, services, controllers, scoring detectors/registry и тесты
- [x] API-контракты переведены на `/api/v1/perks`, `/api/v1/admin/perks`, `/api/v1/admin/perk-statistics`, `/card-templates/{id}/perks`, `perkId`, `perkIds`, `perks`, `perkBonus`
- [x] Flyway **V45** переименовывает таблицы/колонки БД с `achievement*` на `perk*`, включая score breakdown, pack pools, marketplace watch filters и economy key `card.value.perk_bonus`
- [x] TMA/admin фронтенды переведены на перки: API clients, TS-типы, страницы, компоненты, видимые тексты и chip CSS-классы
- [x] Проверки: backend `./gradlew compileKotlin compileTestKotlin`, `npm run build` для `polemica-fantasy-webapp` и `polemica-fantasy-admin` — успешно

### Backend+TMA: фильтр по перкам в коллекции, маркетплейсе и отслеживании (май 2026)
- [x] Backend `GET /api/v1/me/cards` и `GET /api/v1/marketplace/listings` получили повторяемый query-параметр `perkIds`; фильтр работает как OR внутри выбранных перков и AND с остальными фильтрами
- [x] Flyway **V44** добавил `marketplace_watch_filter_perk`, `perk_ids_key` и нормализованный unique index для marketplace watch-фильтров
- [x] `POST /api/v1/settings/marketplace-watches` принимает `perkIds`, валидирует неизвестные id, нормализует порядок и возвращает выбранные перки в `MarketplaceWatchDto`
- [x] Watch-уведомления при создании листинга учитывают пересечение перков карты и watch-фильтра
- [x] TMA: мультивыбор перков добавлен в коллекцию `/cards`, маркетплейс `/marketplace` и страницу отслеживания `/notifications/marketplace-watches`
- [x] Проверки: `./gradlew compileKotlin compileTestKotlin`, targeted backend tests для filters/watch, `npm run build` (`polemica-fantasy-webapp`) — успешно

### DX: production DB readonly skill (май 2026)
- [x] Добавлен проектный skill `.codex/skills/polemica-prod-db-readonly` для безопасного чтения production PostgreSQL через VPS и контейнер `fantasy-db`
- [x] В skill описаны read-only правила, штатный SSH/Docker Compose путь, fallback-команда и SQL-интроспекция таблиц/колонок/Flyway
- [x] Добавлен helper `scripts/prod-db-readonly.sh`: оборачивает SQL в read-only транзакцию с `statement_timeout`/`lock_timeout`, отключает pager и блокирует очевидные write/maintenance SQL и psql meta-команды
- [x] Проверки: `bash -n` helper-скрипта, `--help`, блокировка `delete from telegram_user`; `quick_validate.py` не запущен из-за отсутствующего локального Python-модуля `yaml`

### TMA: победы в профиле игрока (май 2026)
- [x] Backend `PlayerProfileDto` расширен блоком `seriesWins`: общее число побед и разбивка по лигам
- [x] `FantasyTeamRepository` считает победы как первое место в завершённой серии/лиге (`series.status = FINISHED`, leaderboard order `total_score DESC NULLS LAST, id ASC`)
- [x] TMA `PlayerProfilePage` показывает блок «Победы в сериях» с total, основной и бюджетной лигой
- [x] Ник текущего пользователя в верхней панели TMA ведёт на `/players/{telegramId}`
- [x] Проверки: `./gradlew compileKotlin` (`polemica-fantasy-backend`) и `npm run build` (`polemica-fantasy-webapp`) — успешно

### TMA: social share and compare P1 (май 2026)
- [x] Добавлен frontend helper Telegram share links: короткие `startapp` payload для `team`/`place`/`card`/`profile`/`compareS`/`compareT`, direct Mini App link через `VITE_TMA_BOT_USERNAME` или fallback `VITE_TELEGRAM_BOT_USERNAME`, production runtime default `polemica_fantasy_bot` без short name, fallback на `t.me/share/url`
- [x] `App.tsx` обрабатывает `start_param` / `tgWebAppStartParam` и переводит пользователя на существующие TMA routes: команда/место/карточка, профиль, series/tournament compare
- [x] При открытии share-route в обычном браузере без Telegram initData приложение редиректит в TMA deep link, а не показывает ошибку отсутствующего initData
- [x] Кнопки share добавлены в TMA: команда на `TeamPage` и `LeaderboardPlayerTeamPage`, место на `LeaderboardPage`/team detail, карточка только в контексте команды/серии, профиль на `PlayerProfilePage` и в глобальном рейтинге
- [x] Добавлены compare views `/series/:seriesId/compare/:telegramId?league=...` и `/tournaments/:tournamentId/compare/:telegramId?league=...` на существующих authenticated/public API без backend-миграций и без новых privacy-моделей
- [x] Проверка: `npm run build` (`polemica-fantasy-webapp`) — успешно; `npm run lint` всё ещё падает на ранее существующих lint-ошибках вне этой фичи

### Memory bank: glossary and operational insights (май 2026)
- [x] Добавлен `memory-bank/glossary.md` как канонический словарь: пользователь vs игрок, турнир vs соревнование Polemica, серия vs игра, `card_template` vs `user_card`, sync/scoring/finalize и остальные основные доменные сущности
- [x] Добавлен `memory-bank/operationalInsights.md` с same-day insight по сериям: серии часто создаются в день проведения игр, поэтому админский workflow, TMA CTA, уведомления и sync не должны требовать долгого периода `UPCOMING`
- [x] `AGENTS.md` обновлен: для broad/product work нужно читать `glossary.md` и `operationalInsights.md` вместе с остальным `memory-bank`

### Anti-churn onboarding and product communication V1 (май 2026)
- [x] **Backend data/API:** Flyway **V43** добавил `onboarding_progress`, `release_note`, `release_note_view`, `product_campaign`, `product_event`; user API — `/api/v1/onboarding`, `/api/v1/release-notes`, `/api/v1/product-events`; admin API — campaigns, release notes, analytics under `/api/v1/admin/notifications`
- [x] **Bot `/start` 2.0:** короткое state-based меню для неизвестных/новичков/action-no-team/open-deadline пользователей; inline-кнопки `Открыть игру`, `Как начать`, `Собрать команду`, `Написать в поддержку`; support callback отвечает подсказкой
- [x] **Contextual nudges:** hourly scheduler для no-action, action-no-team, open-deadline и after-first-team; фильтрует `bot_blocked`, preference `ONBOARDING_TIPS`, throttling max 1/day и 3 lifetime
- [x] **TMA onboarding:** чеклист на главной, primary CTA на сбор команды для `action_no_team`, `/whats-new` с badge/read-state, auto-complete шагов через магазин/коллекцию/команду/уведомления/результаты, campaign event tracking через `product_event`
- [x] **Admin Product comms:** страница `/product-comms` с tabs Campaigns / Release notes / Analytics; dry-run counts, preview/send, delivery report table, release-note creation/publish toggle, funnel summary
- [x] **Проверки:** `./gradlew compileKotlin compileTestKotlin`, targeted `TelegramSupportUpdateServiceTest` + `TelegramStartMenuServiceTest`, `npm run build` для `polemica-fantasy-webapp` и `polemica-fantasy-admin` — успешно

### Backend: ban-pair без удаления карт и повторных списаний (май 2026)
- [x] `MarketplaceAdminService.banPair` больше не удаляет и не soft-delete'ит карты при санкции пары; `user_card.deleted_at` остаётся неизменным, SOLD-листинг сохраняет статус `SOLD`
- [x] Учтённые при бане пары SOLD-листинги маркируются через `MarketplaceListingSanction(reason = "Pair ban", adminUsername = "pair-ban:<low>:<high>")`
- [x] `MarketplaceListingRepository.sumSellerReceivedForSalesTo`, `findSoldListingsBetweenUsers`, `findSoldFromPartnerToBuyer` и preview-кандидаты в `UserCardRepository.findUserCardsBoughtOnMarketplaceFromPartner` исключают уже санкционированные листинги, поэтому повторный бан не списывает штраф повторно за старые транзакции
- [x] Новые сделки той же пары после первого бана остаются санкционируемыми, потому что фильтр идёт по `marketplace_listing_sanction`, а не по факту прошлой записи в `marketplace_pair_sanction_history`
- [x] Регрессии в `MarketplacePairBanFantikiIntegrationTest`: карты/листинги сохраняются, повторный бан старой транзакции даёт 0, повторный бан после новой сделки списывает только новую сделку, перепроданная третьему пользователю карта не удаляется, но листинг помечается санкцией
- [x] Проверки: `./gradlew compileTestKotlin` — успешно; targeted Testcontainers-тест `MarketplacePairBanFantikiIntegrationTest` не стартовал из-за недоступного Docker (`ContainerFetchException`)

### Marketplace moderation: pair trades createdAt и complaintsCount (май 2026)
- [x] **Backend DTO:** `PairTradeDto` получил поля `createdAt` (время создания листинга из `MarketplaceListing.createdAt`) и `complaintsCount` (количество жалоб на операцию)
- [x] **MarketplaceAdminService:** в `getPairTrades` добавлен batch-запрос `MarketplaceComplaintRepository.countGroupedByListingIds` для всех sold listings между юзерами; маппинг complaint count в `PairTradeDto`
- [x] **Admin API types:** `polemica-fantasy-admin/src/api/types.ts` добавлены `createdAt` и `complaintsCount` в `PairTradeDto`
- [x] **Admin UI (`MarketplaceModerationPage`):** в таблице pair trades добавлены колонки «Created at» (время листинга, width=180, формат `YYYY-MM-DD HH:mm`) и «Жалобы» (число жалоб, красный тег если ≥3, иначе обычный текст)
- [x] Проверки: `./gradlew compileKotlin` (`polemica-fantasy-backend`) и `npm run build` (`polemica-fantasy-admin`) — успешно

### Marketplace moderation: контекст рынка в жалобах (май 2026)
- [x] `MarketplaceAdminDtos`: `ComplainedTransactionDto.createdAt`, новые `ConcurrentListingDto` и `TransactionMarketContextDto`, `TransactionComplaintsListDto.marketContext`
- [x] **Flyway V42:** `CREATE INDEX idx_marketplace_listing_user_card ON marketplace_listing(user_card_id)`
- [x] `MarketplaceListingRepository`: `findConcurrentListingsForContext` (same fantasy-player + rarity, окно активности на момент `soldAt`, исключение текущего листинга, fallback `soldCardTemplate -> userCard.cardTemplate`)
- [x] `MarketplaceAdminService`: в `getComplainedTransactions` проброшен `createdAt`; в `getTransactionComplaints` построен `marketContext` с разделением concurrent-листингов на `sameTemplate` и `samePlayerRarity`, сортировка по цене
- [x] Admin API types (`polemica-fantasy-admin/src/api/types.ts`): добавлены `ConcurrentListingDto`, `TransactionMarketContextDto`, `marketContext` в `TransactionComplaintsListDto`, `createdAt` в `ComplainedTransactionDto`
- [x] `MarketplaceModerationPage`: в таблице жалоб колонка «Создан»; в модалке санкции поля «Создан» и «Время на рынке»; секция «Рынок на момент выкупа» (2 таблицы + success-alert если пусто + цветовая маркировка цены)
- [x] Проверки: `./gradlew compileKotlin` (`polemica-fantasy-backend`) и `npm run build` (`polemica-fantasy-admin`) — успешно

### STANDALONE: фильтр по started-дню серии (май 2026)
- [x] **Flyway V41:** добавлена колонка `series.game_started_on` (`DATE NULL`) для day-based фильтра Polemica `game.started`
- [x] **Backend контракты:** `Series.gameStartedOn` + проброс в `SeriesDto`, `CreateSeriesRequest`, `UpdateSeriesRequest`
- [x] **Update semantics:** в `UpdateSeriesRequest` добавлен флаг `gameStartedOnSpecified` для явного сброса `gameStartedOn = null`
- [x] **SeriesService:** для `STANDALONE` фильтр сохраняется/обновляется; для `POLEMICA_COMPETITION` non-null `gameStartedOn` отклоняется с 400 и в сущности удерживается `null`
- [x] **Sync логика:** `DefaultGameSyncService.fetchStandalonePrepared` дополняет `name_prefix`-фильтр проверкой `game.started.toLocalDate() == series.gameStartedOn` (если фильтр задан)
- [x] **Админка:** `SeriesFormModal` и `SeriesDetailPage` получили DatePicker (без времени, optional, clear/reset), `TournamentDetailPage` показывает дату фильтра рядом с `Prefix`
- [x] **Trace script:** `scripts/trace_series_game_sync.py` читает `gameStartedOn`, фильтрует кандидаты по started-дню и выводит отдельную статистику отсечения
- [x] **Проверки:** `./gradlew compileKotlin compileTestKotlin`, `npm run build` (`polemica-fantasy-admin`), `python3 -m py_compile scripts/trace_series_game_sync.py`; запуск целевого Testcontainers-теста упёрся в отсутствие Docker в окружении

### TMA: устранение мерцания у legendary-crafted и tournament_gold карт (май 2026)
- [x] В `polemica-fantasy-webapp/src/index.css` из keyframes `pf-legendary-crafted-glow` и `pf-skin-gold-glow` убран `filter` (анимация оставлена на `box-shadow`)
- [x] Добавлены GPU/compositing hints (`will-change`) для glow/shimmer/sweep анимаций: `box-shadow`, `background-position`, `transform`
- [x] Для `tournament_gold` sweep-псевдоэлемента уменьшен экстент (`inset: -60% -> -20%`) и подправлены gradient stop'ы, чтобы снизить repaint-glitches на `overflow: hidden` контейнерах
- [x] Проверка: `npm run build` (`polemica-fantasy-webapp`) — успешно; lints для `src/index.css` — без ошибок

### Special card skins `tournament_gold` (май 2026)
- [x] **Flyway V40:** `card_skin`, `user_card.card_skin_id`, `card_pack.card_skin_id`, `marketplace_listing.sold_skin_code`; seed первого скина `tournament_gold`
- [x] **Backend модель/сервисы:** `CardSkin` + `CardSkinRepository`; `CardPackService.openPack` назначает скин из пака на `user_card`; `MarketplaceService.buyCard` сохраняет snapshot `soldSkinCode`; `MarketplaceTransactionService` и feed/listings используют skin snapshot для sold-кейсов
- [x] **DTO контракты:** `skinCode` добавлен в `UserCardItemDto`, `MarketplaceListingCardDto`, `TransactionCardDto`; в admin `CardPackDto` добавлены `skinId`/`skinCode`; в create/update card-pack request добавлен `skinId`
- [x] **Admin API/UI:** `GET /api/v1/admin/card-skins`; в `CardPacksPage` добавлен выбор skin (create/edit) и отображение skin в таблице паков
- [x] **TMA рендеринг:** `skinCode` добавлен в TS-типы; helper `skinClass`; skin-модификаторы подключены для collection/team/modal/mini cards, marketplace listings/feed/my-listings, leaderboard/history cards, pack opening reveal/summary, transaction detail
- [x] **CSS для `tournament_gold`:** отдельные модификаторы с золотым conic-border, glow/shimmer/sweep-анимациями, бейджем `GOLD`, и tint для perk chips
- [x] **Визуальная отстройка от legendary:** палитра `tournament_gold` смещена в «шампань + изумруд», ослаблена янтарная доминанта в border/glow/sweep/text/chips — скин читается как отдельная косметика, а не вариация `legendary-crafted`
- [x] **Интеграционные тесты:** `UserApiIntegrationTest` — (1) opening skinned pack возвращает `skinCode` в `cards`/`openingCards`/`me/cards`; (2) marketplace сохраняет `skinCode` через listing/feed/transaction/buyer cards
- [x] **Проверки:** `./gradlew test --tests "...store buy from skinned pack assigns skin..." --tests "...marketplace preserves skin code..."`; `npm run build` в `polemica-fantasy-admin` и `polemica-fantasy-webapp` — успешно

### DX: Codex workflow и быстрые проверки (май 2026)
- [x] Добавлен multi-agent workflow для крупных фич: `docs/codex/MULTI_AGENT_WORKFLOW.md` описывает spec-first процесс с product/design/technical/QA ролями, шаблоны промптов, ownership worker'ов, integration rules и verification defaults
- [x] Добавлен проектный skill `.codex/skills/polemica-feature-discovery`, который включает product/design discovery перед разработкой и указывает использовать workflow-документ
- [x] Добавлен проектный skill `.codex/skills/polemica-feature-delivery` для полного delivery-loop крупных фич: plan writer, plan reviewer gate, pre-code QA/risk review, vertical-slice first, ownership fan-out after stable contract, code reviewer gate, local tester, UX review before/after implementation, final integration owner
- [x] `AGENTS.md` дополнен разделом `Codex Workflow`: что читать перед задачами, как держать DTO/API контракты синхронными, когда обновлять `memory-bank/`, какие проверки предпочитать
- [x] Актуализированы агентские версии и ориентиры: `polemica-library:1.8.8`, Flyway `V1` … `V42+`, key backend tests без устаревшего `CardPackServiceProbabilityTest`
- [x] Добавлен `scripts/codex-check.sh` с целями `quick`, `backend`, `backend-test`, `webapp`, `admin`, `frontend`, `lint`
- [x] README получил раздел «Разработка через Codex» с быстрым smoke/check циклом
- [x] `memory-bank/techContext.md` синхронизирован с текущими версиями и новым проверочным entrypoint
- [x] Добавлен `scripts/generate-tma-init-data.py` для свежего `VITE_DEV_INIT_DATA` (Telegram initData HMAC, token из env или `.env`)
- [x] `scripts/local-up.sh` получил `--generate-init-data` для запуска TMA без ручного копирования initData
- [x] Добавлен проектный Codex skill `.codex/skills/polemica-local-testing` для локального UI-тестирования webapp/admin
- [x] Workflow проверен на реальном локальном запуске: TMA открылся с dev initData; кнопка «Обзор серии» на `TeamPage` исправлена и подтверждена browser-проверкой

### DX: единый локальный старт стека (май 2026)
- [x] Добавлен исполняемый скрипт `scripts/local-up.sh` для запуска backend + admin + TMA одной командой
- [x] Скрипт поддерживает `--init-data`, `--host`, `--admin-port`, `--tma-port` и ENV-аналоги (`VITE_DEV_INIT_DATA`, `DEV_HOST`, `ADMIN_PORT`, `TMA_PORT`)
- [x] Добавлены preflight-проверки зависимостей (`docker`, `npm`, `curl`) и ожидание health backend (`http://localhost:8081/actuator/health`)
- [x] Логи frontend dev-серверов выводятся в `.local-dev-logs/admin.log` и `.local-dev-logs/tma.log`
- [x] README дополнен разделом «Быстрый старт всего стека (backend + admin + TMA)»
- [x] Проверка: `bash -n scripts/local-up.sh` и `./scripts/local-up.sh --help` — успешно

### Backend: marketplace complaints/moderation (май 2026)
- [x] **Flyway V39:** добавлены `marketplace_complaint`, `marketplace_listing_sanction`, колонка `telegram_user.marketplace_banned_until`, сид `economy_config.marketplace.daily_complaint_limit`
- [x] **Новые сущности/репозитории:** `MarketplaceComplaint`, `MarketplaceListingSanction`, `MarketplaceComplaintRepository`, `MarketplaceListingSanctionRepository`, расширения `MarketplaceListingRepository` для trade-details
- [x] **User API:** `MarketplaceTransactionService` + `MarketplaceComplaintService`, DTO transaction-detail/complaints, endpoint'ы детализации сделки и подачи жалобы
- [x] **Admin API:** список жалоб по сделкам, деталь жалоб по сделке, применение санкции, список пользователей по жалобам, бан пользователя на срок (`marketplace_banned_until`) и обновлённый unban
- [x] **Санкции и экономика:** `MarketplaceSanctionService` (штраф продавцу/покупателю, награда жалобщикам, опциональный временный бан), новые `FantikiTransactionReason` (`MARKETPLACE_SANCTION_FINE`, `MARKETPLACE_COMPLAINT_REWARD`)
- [x] **Уведомления:** `MarketplaceSanctionAppliedEvent` + `MarketplaceSanctionNotificationListener`, новые категории `MARKETPLACE_SANCTION_APPLIED` и `MARKETPLACE_COMPLAINT_RESOLVED`, кнопка перехода к сделке в `NotificationButtonFactory`
- [x] **Публичная витрина:** в `/marketplace/listings` скрываются seller и `card.value`; в `my-listings` поля сохраняются
- [x] **Покрытие тестами:** `MarketplaceComplaintServiceTest`, новые интеграционные сценарии в `UserApiIntegrationTest` и `AdminApiIntegrationTest`; целевой прогон `./gradlew test --tests "io.github.mralex1810.fantasy.service.MarketplaceComplaintServiceTest" --tests "io.github.mralex1810.fantasy.AdminApiIntegrationTest" --tests "io.github.mralex1810.fantasy.UserApiIntegrationTest.GET marketplace listings hides seller and card value but my-listings keeps them" --tests "io.github.mralex1810.fantasy.UserApiIntegrationTest.marketplace transaction detail and complain endpoint work for sold listing"` — успешно

### TMA: marketplace complaints UI (май 2026)
- [x] `src/pages/TransactionDetailPage.tsx`: новый экран `/marketplace/transactions/:listingId` с карточкой сделки, участниками, экономикой (цена/комиссия/net), счётчиком жалоб и блоком санкции
- [x] `src/api/types.ts`: добавлены `MarketplaceTransactionDetail`/`ComplainResult`, расширены `MarketplaceFeedItem` и `PlayerMarketplaceTrade` (`listingId`, `sanctioned`), а также nullable-контракт для `MarketplaceListingEntry.seller` и `MarketplaceListingCard.value`
- [x] `src/api/marketplace.ts`: добавлены `fetchMarketplaceTransactionDetail` и `complainMarketplaceTransaction` (`GET/POST /api/v1/marketplace/transactions/{id}`)
- [x] `src/pages/MarketplacePage.tsx`: лента сделок стала кликабельной (переход на деталку), санкционированные сделки помечаются бейджем/зачёркнутой ценой; в каталоге скрываются seller/value при `null`
- [x] `src/pages/PlayerProfilePage.tsx`: блок «Последние сделки» стал кликабельным, добавлена санкционная метка в строках сделок
- [x] `src/App.tsx`: подключён маршрут `/marketplace/transactions/:listingId`
- [x] `src/index.css`: добавлены стили страницы сделки, кликабельных feed-чипов и санкционных бейджей для ленты/профиля
- [x] `src/pages/TransactionDetailPage.tsx`: перед `POST /marketplace/transactions/{id}/complain` добавлена модалка подтверждения с пояснениями про дневной лимит жалоб, возможную награду жалобщикам и то, что администрация рассматривает жалобы на транзакции маркетплейса; отправка жалобы выполняется только из confirm-модалки
- [x] UX-полировка (`2026-05-14`): на странице сделки карточка сделана компактнее (ограничение ширины, уменьшенные отступы/типографика в overlay, более плотные инфо-блоки); правки локализованы в `TransactionDetailPage.tsx` и `.pf-transaction-detail*` стилях
- [x] Проверка: `npm run build` (`polemica-fantasy-webapp`) — успешно

### Admin frontend: marketplace complaints UI (май 2026)
- [x] `polemica-fantasy-admin/src/api/types.ts`: добавлены DTO/запросы для complained transactions, transaction complaints, sanction result, users-by-complaints и ban user request
- [x] `polemica-fantasy-admin/src/api/marketplaceAdmin.ts`: добавлены методы `getComplainedTransactions`, `getTransactionComplaints`, `sanctionTransaction`, `getUsersByComplaints`, `banMarketplaceUser`
- [x] `polemica-fantasy-admin/src/pages/MarketplaceModerationPage.tsx`: добавлены табы **«Жалобы»** и **«Игроки по жалобам»** с пагинацией, фильтром `minComplaints`, статусами и действиями
- [x] Реализована модалка санкции сделки: сводка сделки, список жалобщиков, рекомендуемые штрафы/награды по комиссии `marketplace.commission_percent`, предупреждение о превышении комиссии, подтверждение через `Popconfirm`, показ результата `SanctionTransactionResultDto`
- [x] Реализована модалка бана пользователя из таблицы жалоб: пресеты 3/7/30 дней, custom days, перманент, а также разбан через `POST /api/v1/admin/marketplace/unban/{telegramId}`
- [x] Проверка: `npm run build` (`polemica-fantasy-admin`) — успешно

### TMA: снятие листингов игрока с TeamPage (май 2026)
- [x] `polemica-fantasy-webapp/src/pages/TeamPage.tsx`: после выбора `Игрок серии` добавлен блок Marketplace с количеством активных листингов по этому `fantasyPlayerId`
- [x] Добавлено действие **«Снять игрока с листинга»**: пакетное снятие всех листингов выбранного игрока через последовательность `DELETE /api/v1/marketplace/listings/{id}` (`cancelMarketplaceListing`, `Promise.allSettled`)
- [x] После операции инвалидация кэшей `cards`, `my-marketplace-listings`, `marketplace-listings`, чтобы карточки сразу разблокировались на экране сборки
- [x] UX-фикс: блок управления листингами теперь показывается автоматически при наличии карт в продаже, с отдельным селектом `Игрок в листинге` (только игроки с активными лотами), без зависимости от фильтра `Игрок серии`
- [x] UX-коррекция по фидбеку: панель массового управления удалена; на карточках в продаже на `TeamPage` добавлена кнопка **«Снять с продажи»** для точечного снятия листинга прямо из сетки
- [x] `polemica-fantasy-webapp/src/index.css`: стили TeamPage обновлены под сценарий снятия листинга напрямую на карточке (`pf-team-grid__item`, `pf-team-card__unlist-btn`)
- [x] Проверка: `npm run build` (`polemica-fantasy-webapp`) — успешно

### Backend: soft-delete при распылении карты (май 2026)
- [x] **Flyway V38:** `user_card.deleted_at` + комментарий поля (`soft-delete` timestamp)
- [x] `CardLifecycleService.recycleCard`: вместо физического удаления (`user_card`, `user_card_ownership_history`, `marketplace_listing`) теперь ставится `deletedAt = now`
- [x] ACTIVE-листинги при recycle больше не удаляются: переводятся в `CANCELLED` через `MarketplaceListingRepository.cancelAllActiveByUserCardId`
- [x] `UserCardRepository`: активные выборки и операции (`findById...`, `/me/cards`, attach team, legendary upgrade, global rating aggregates, pair confiscation candidates) исключают `deletedAt != null`
- [x] `TelegramUserRepository`: админские запросы `findAllWithCardsInSeriesCount*` не считают soft-deleted `user_card`
- [x] Тесты обновлены: `CardLifecycleServiceTest` и `UserApiIntegrationTest#POST recycle succeeds for card listed on marketplace` (добавлена проверка, что `ownership-history` доступна после recycle)
- [x] Проверка: `./gradlew test --tests "io.github.mralex1810.fantasy.service.CardLifecycleServiceTest" --tests "io.github.mralex1810.fantasy.UserApiIntegrationTest.POST recycle succeeds for card listed on marketplace"` — успешно

### Admin: снятие игрока серии с маркетплейса (май 2026)
- [x] Backend: `POST /api/v1/admin/series/{seriesId}/players/{tournamentPlayerId}/unlist-marketplace` в `SeriesAdminController` + `SeriesService.unlistMarketplaceListingsForTournamentPlayer`
- [x] `MarketplaceListingRepository`: добавлен bulk-update `cancelAllActiveByFantasyPlayerId` (`ACTIVE -> CANCELLED`) для выбранного `fantasy_player`
- [x] Новый DTO ответа `SeriesPlayerMarketplaceUnlistResultDto` (какой игрок обработан и сколько лотов снято)
- [x] Админка `SeriesDetailPage` (блок `Assign players`): добавлен UI «Marketplace» с выбором игрока, confirm и кнопкой `Remove player listings`
- [x] Интеграционный тест backend: `AdminApiIntegrationTest#series admin can remove selected player listings from marketplace` (создание ACTIVE-листинга и проверка перехода в `CANCELLED`)
- [x] Проверки: `./gradlew test --tests "io.github.mralex1810.fantasy.AdminApiIntegrationTest.series admin can remove selected player listings from marketplace"` и `npm run build` (`polemica-fantasy-admin`) — успешно

### UX: TeamPage — действия под выбранным составом (май 2026)
- [x] `polemica-fantasy-webapp/src/pages/TeamPage.tsx`: кнопки действий `Отправить/Обновить` и `Обзор серии` перенесены из нижней части страницы (после сетки карт) в блок сразу под `pf-picked-slots`
- [x] `Обзор серии` переведён на маршрут состава серии текущей лиги: `/series/:id/team?league=...` (вместо перехода на обзор/лидерборд серии)
- [x] `polemica-fantasy-webapp/src/index.css`: добавлены стили `pf-team-picked-actions` для корректных отступов нового расположения блока действий
- [x] Проверка: `npm run build` (`polemica-fantasy-webapp`) — успешно

### TMA: турнирный лидерборд — бюджетная лига (май 2026)
- [x] `polemica-fantasy-webapp/src/pages/TournamentLeaderboardPage.tsx`: добавлен fallback для списка лиг (`MAIN` + `BUDGET`) на случай, когда в `GET /api/v1/tournaments/{id}` не приходит `series.leagues`
- [x] Переключатель лиг на странице турнирного лидерборда теперь рендерится и для fallback-набора; запросы leaderboard продолжают идти в per-league endpoint (`fetchLeagueLeaderboard`)
- [x] Проверка: `npm run build` (`polemica-fantasy-webapp`) — успешно

### Backend bugfix: marketplace analytics detail (май 2026)
- [x] `MarketplaceService.getAnalyticsDetail`: устранён `ClassCastException` при разборе агрегатов `COUNT/MIN/MAX` из `MarketplaceListingRepository.findActiveListingStatsForPlayerAndRarity`
- [x] Добавлен безопасный разбор ответа агрегатного запроса: поддержаны оба формата результата (`[count,min,max]` и `[[count,min,max]]`), для `activeCount` добавлен fallback `0`
- [x] Добавлен интеграционный регрессионный тест `UserApiIntegrationTest` на `GET /api/v1/marketplace/analytics/detail` (два активных листинга, проверка `activeCount`, `activeMinPrice`, `activeMaxPrice`, `recentSales`, `avgSalePrice`)
- [x] Проверка: `./gradlew test --tests "io.github.mralex1810.fantasy.UserApiIntegrationTest.GET marketplace analytics detail returns active stats without server error"` — успешно

### UX: коллекция и меню продажи (май 2026)
- [x] `polemica-fantasy-webapp/src/pages/CardsPage.tsx`: убрана оверлейная плашка рыночной сводки (`N шт. от X₣`) с карточек в сетке коллекции, чтобы не перекрывать чипы перков
- [x] Сводка рынка перенесена в модалку карты и показывается рядом с действиями продажи (`Продать` / `Управлять листингом`) через новый inline-бейдж
- [x] В модалке «Выставить на маркетплейс» восстановлена наблюдаемость детальной аналитики: добавлен явный `isError`-state с сообщением об ошибке загрузки и включён `refetchOnMount: 'always'` для запроса `/marketplace/analytics/detail`
- [x] Проверка: `npm run build` (`polemica-fantasy-webapp`) — успешно

### Визуальный Тюленчик при открытии пака (апрель 2026)
- [x] **Backend контракт магазина:** `BuyPackResponseDto` расширен полем `openingCards` (union `USER_CARD`/`COMPANION`) для display-последовательности открытия; поле `cards` сохранено как инвентарь (только реальные карты)
- [x] **Логика формирования display-карт:** `UserStoreService` после каждой developer-карты (`economy_config.easter_egg.developer_fantasy_player_id`) вставляет визуальный companion «Тюленчик» с теми же `rarity` и `value`, что у developer-карты; image URL берётся из `EasterEggProperties.tyulenchikImageUrl`
- [x] **Экономика пасхалки:** companion не создаёт `user_card`, но начисляет фантики в размере своей ценности (`+value` соответствующей developer-карты) через `EASTER_EGG_BONUS`; итоговый баланс возвращается в `BuyPackResponseDto.fantiki`
- [x] **Frontend TMA:** `types.ts` расширен `PackOpeningCard`; `StorePage` передаёт в `PackOpening` `openingCards` (fallback на legacy `cards`), `PackOpening` показывает companion в reveal и summary, включая вклад companion в `Суммарная ценность`
- [x] **Текст бонуса на companion-карте:** в `PackOpening` для `COMPANION` в блоке перков показывается чип `+N фантики` (в reveal и summary)
- [x] **Покрытие:** `UserApiIntegrationTest` — сценарии single/multi developer (порядок `USER_CARD -> COMPANION`, `relatedUserCardId`, `value` и `rarity` совпадают с исходной картой, баланс увеличивается на `Σ companion.value`)
- [x] **Проверки:** `./gradlew test --tests \"...store buy adds visual tyulenchik...\"` и `npm run build` (`polemica-fantasy-webapp`) — успешно

### Пасхалка для легендарного апгрейда (апрель 2026)
- [x] **Flyway V37:** добавлены ключи `economy_config` — `easter_egg.developer_fantasy_player_id` (таргет `fantasy_player`) и `easter_egg.developer_bonus_fantiki` (бонус пасхалки, default 500)
- [x] **Backend конфиг:** добавлен `easter-egg.tyulenchik-image-url` (`EASTER_EGG_TYULENCHIK_IMAGE_URL`) + `EasterEggProperties`
- [x] **API апгрейда:** `POST /api/v1/legendary-upgrade` теперь возвращает `LegendaryUpgradeResponseDto` (`card` + опциональный `easterEgg`)
- [x] **Логика бонуса:** `LegendaryUpgradeService` при апгрейде карты игрока из `easter_egg.developer_fantasy_player_id` начисляет бонус через `UserService.addBalance(..., EASTER_EGG_BONUS)` и формирует текст пасхалки + payload «Тюленьчик»
- [x] **Frontend TMA:** `LegendaryUpgradeWizard` получил result-step (2 карточки, текст пасхалки, `+N фантиков`, кнопка `OK`) вместо мгновенного закрытия в easter-egg сценарии
- [x] **Проверки:** `npm run build` (`polemica-fantasy-webapp`) — успешно; backend-таргет `./gradlew test --tests \"*upgrades EPIC to LEGENDARY*\" --tests \"*marketplace feed shows EPIC after purchased card upgraded to LEGENDARY*\" --tests \"*legendary-upgrade rejects duplicate perk on card*\"` — успешно

### Backend bugfix: финализация серии и uses в лигах (апрель 2026)
- [x] Подтверждён дефект: при финализации серии карта в `MAIN` + `BUDGET` давала `cardsDecremented = 2`, но `user_card.uses_remaining` в БД не менялся (интеграционный сценарий в `UserApiIntegrationTest`)
- [x] `SeriesFinalizationService`: после декремента uses добавлен явный `userCardRepository.saveAll(cardById.values)`, чтобы изменения карточек гарантированно сохранялись после финализации
- [x] Добавлен регрессионный тест `finalize series decrements uses by leagues count for same card` (`UserApiIntegrationTest`): создаёт команды в двух лигах одной картой, финализирует серию, проверяет уменьшение `usesRemaining` на 2
- [x] Проверка: `./gradlew test --tests "*finalize series decrements uses by leagues count for same card*"` и `./gradlew test --tests "io.github.mralex1810.fantasy.service.SeriesFinalizationServiceTest"` — успешно

### UX: главная и коллекция (апрель 2026)
- [x] **HomePage (`/`):** блок «Состав на серию» больше не исчезает после заполнения команд во всех лигах — показываются все активные серии с открытым дедлайном; CTA учитывает состояние (`Собрать` / `Изменить` / `Открыть`)
- [x] **CardsPage (`/cards`):** для карты в `activeMarketplaceListing` добавлено единое действие **«Управлять листингом»** (модалка: смена цены + снятие с продажи)
- [x] Исправлена валидация минимальной цены при выставлении карты из коллекции: нижняя граница теперь берётся из `marketplaceMinPrices`, а не из `renewalCosts`

### TMA bugfix: сохранение выбора на сборке команды
- [x] `polemica-fantasy-webapp/src/pages/TeamPage.tsx`: устранён сброс локально выбранных карт после позднего ответа/рефетча `fantasy-team` для лиги
- [x] Добавлен флаг `teamSelectionHydrated`: синхронизация `selected` из API выполняется один раз на связку `series + league`, а при `teamQ.data == null` не перетирает уже выбранные пользователем карты
- [x] Исправлен пользовательский эффект «кнопка отправки не активируется» при редактировании состава (в т.ч. в бюджетной лиге)
- [x] Проверка: `npm run build` (`polemica-fantasy-webapp`) — успешно

### TMA bugfix: лидерборд турнира по лигам + бейджи карты в сборке
- [x] `polemica-fantasy-webapp/src/pages/TournamentLeaderboardPage.tsx`: переход на per-league leaderboard для вкладок серий (`fetchLeagueLeaderboard`), добавлены `LeagueTabs` и синхронизация `?league=` в URL; ссылки на `/series/:id/leaderboard/player/:telegramId` теперь сохраняют `league`
- [x] Исправлен кейс «из турнира не видно бюджетную лигу в лидерборде по сериям»: страница турнирного лидерборда больше не привязана к legacy MAIN endpoint
- [x] `polemica-fantasy-webapp/src/pages/TeamPage.tsx` + `src/index.css`: устранено наложение ценности карты и бейджа «уже в другой лиге»; `pf-card-league-badge` смещён ниже value badge, добавлен вариант `pf-card-league-badge--team-dead` для карт со статусом «Истекла»
- [x] Проверка: `npm run build` (webapp) — успешно

### Справка `/help`: бюджетная лига (контент для игроков)
- [x] `polemica-fantasy-webapp/src/pages/HelpPage.tsx`: раздел **«Лиги»** расширен игроко-ориентированными правилами бюджетной лиги (лимит суммы `card_value`, поведение uses при игре в нескольких лигах, суммирование наград по лигам, связь с турнирным рейтингом)
- [x] Процент награды бюджетной лиги в тексте справки берётся из `economy-info` (`leagues.BUDGET.rewardScale` из `economy_config`), без хардкода
- [x] `docs/features/DESIGN-CARD-VALUE-AND-LEAGUES.md` (п. 8.4) синхронизирован: добавлено явное требование подтягивать `reward_scale` из `economy_config` и полный список информации, которую нужно показать игроку

### Фильтр фаз для POLEMICA_COMPETITION серий
- [x] **Flyway V36:** `series.game_phase` (`INT NULL`), CHECK-ограничение `0..2` или `NULL`, бэкфилл `game_phase = 0` для существующих серий турниров `POLEMICA_COMPETITION`
- [x] **Backend API и модель:** в `Series`/`SeriesDto`/`CreateSeriesRequest`/`UpdateSeriesRequest` добавлено поле `gamePhase`; create default для competition = `0`, update поддерживает явное `null` через флаг `gamePhaseSpecified`; для `STANDALONE` значение принудительно `null`
- [x] **Sync игр:** `DefaultGameSyncService.fetchCompetitionPrepared` фильтрует игры по phase (0/1/2), `null` в серии = учитывать все фазы
- [x] **Админка:** в `SeriesFormModal` и `SeriesDetailPage` добавлен выбор phase (`0`, `1`, `2`, `All phases/null`), обновлены TS-типы `SeriesDto` и запросы create/update серии

### Лиги (frontend, Plan 06)
- [x] **Типы и контракты TMA:** `SeriesLeagueInfo`/`SeriesLeagueBrief`, `FantasyTeamDto`/`PublicFantasyTeam.leagueCode`, `UserCardItem.leaguesInSeries/canJoinMoreLeagues`, `EconomyInfo.leagues`, `UserSeriesDetail`/`UserSeriesSummary.leagues`
- [x] **API-клиент лиг:** `src/api/leagues.ts` (`fetchSeriesLeagues`, `fetchLeagueLeaderboard`, `submitLeagueTeam`, `updateLeagueTeam`) на новых endpoint'ах `/api/v1/series/{id}/leagues/{code}/*`
- [x] **UI-компоненты:** `LeagueTabs` (вкладки с checkmark и value cap) и `BudgetProgressBar` (цветовой прогресс бюджета по `valueCap`)
- [x] **Страница серии и лидерборд:** `SeriesPage` и `LeaderboardPage` поддерживают `?league=` (fallback MAIN), показывают per-league таблицы и навигацию с сохранением `leagueCode`
- [x] **Сборка команды по лигам:** `TeamPage` переведена на per-league CRUD, вкладки лиг, бюджетный cap-blocking, disabled-карты при нехватке uses для доп. лиги и бейджи «уже в другой лиге»
- [x] **Главная/турнир/справка:** `HomePage` и `TournamentPage` показывают per-league статусы (`Основная ✓ / Бюджетная ✗`); `HelpPage` дополнена разделом «Лиги» (ограничения и reward scale из `economy-info`)
- [x] **Сопутствующая совместимость:** `FantasyHistoryPage` и `LeaderboardPlayerTeamPage` учитывают `leagueCode` в запросах/ключах (без конфликтов команд разных лиг одной серии)
- [x] **Проверка:** `npm run build` для `polemica-fantasy-webapp` — успешно

### Лиги (backend, Plan 05)
- [x] **Flyway V35:** `league`, `series_league`, bootstrap MAIN/BUDGET для существующих серий, `fantasy_team.series_league_id` + миграция существующих команд в MAIN, UNIQUE `(telegram_user_id, series_id, series_league_id)`, economy-ключи `league.reward_scale.*` и `league.budget.value_cap`, deprecated-маркер для `legendary.team.max_per_series`
- [x] **Доменная модель:** `League`, `SeriesLeague`, `LeagueType`, `LeagueVisibility`; репозитории `LeagueRepository`, `SeriesLeagueRepository`; `SeriesService.createSeries` автоматически создаёт `series_league` для всех SYSTEM-лиг
- [x] **Правила лиги в сборке состава:** `LeagueService` + `UserFantasyTeamService` (value cap, max legendary per league, min/max team size, uses across leagues через `FantasyTeamCardRepository.countLeaguesInSeriesForCard`)
- [x] **User API лиг:** `LeagueController` (`GET /series/{id}/leagues`, per-league leaderboard, create/update team, public team/details); legacy endpoints `/series/{id}/leaderboard`, `/series/{id}/fantasy-team*`, `/me/fantasy-teams/{seriesId}*` поддерживают `leagueCode` (default `MAIN`)
- [x] **DTO/контракты:** `LeagueDtos`, `FantasyTeamDto/PublicFantasyTeamDto.leagueCode`, `UserSeriesDetailDto`/`UserSeriesSummaryDto` + `SeriesLeagueBriefDto`, `UserCardItemDto.leaguesInSeries/canJoinMoreLeagues`, `EconomyInfoDto.leagues`
- [x] **Admin API лиг:** `LeagueAdminController` + `LeagueAdminService` (`GET/PUT /api/v1/admin/leagues`, `POST/DELETE /api/v1/admin/series/{id}/leagues*`, запрет деактивации лиги серии при наличии команд)
- [x] **Финализация и уведомления per-league:** `SeriesFinalizationService` начисляет награды по каждой лиге с `reward_scale`, списывает `uses_remaining` по числу лиг участия карты; `SeriesFinalizedNotificationEvent` и `buildSeriesFinalizedTelegramMessage` переведены на `leagueResults + totalReward`
- [x] **Тесты:** обновлены unit-тесты `SeriesFinalizationServiceTest` и `SeriesFinalizationTelegramMessageTest` под per-league модель

### Инфраструктура уведомлений (Plan notify/01)
- [x] **Flyway V34:** `notification_preference`, `tournament_subscription`, `telegram_user.bot_blocked` (default `false`), `deadline_reminder`, `marketplace_watch_filter`, `marketplace_watch_pending`
- [x] **Сущности/репозитории:** `NotificationCategory`, `NotificationPreference`, `TournamentSubscription`, `NotificationPreferenceRepository`, `TournamentSubscriptionRepository`
- [x] **Telegram infra:** `InlineKeyboardMarkup`/`InlineKeyboardButton`, `TelegramSendResult`, `TelegramBotApiClient.sendMessageSafe` + поддержка `replyMarkup` в `sendMessage`
- [x] **Delivery layer:** `NotificationDeliveryService` (`deliver`/`deliverToMany`/`broadcast`) с проверкой `telegram.bot.notifications.enabled` + token, фильтрацией `bot_blocked`, учётом user preferences, retry на 429, mark `bot_blocked=true` на 403, delay 50ms в batch
- [x] **Миграция listeners:** `SeriesFinalizedNotificationListener`, `MarketplaceSaleNotificationListener`, `PairBanNotificationListener`, `SeriesRosterReplacementNotificationListener` переведены на `NotificationDeliveryService`; добавлен `NotificationButtonFactory` (кнопки в Mini App)
- [x] **Broadcast:** `TelegramBroadcastAsyncSender` использует `NotificationDeliveryService.broadcast`; `AdminBroadcastNotificationService` отправляет только текст
- [x] **Series finalization:** `SeriesFinalizedNotificationEvent` расширен `seriesId` (для кнопки leaderboard)
- [x] **Сброс bot-block:** `UserService.getOrCreateAndUpdateProfile` сбрасывает `botBlocked=false` при входе пользователя в TMA

### Пользовательские настройки и старт серии (Plan notify/02)
- [x] **User settings API:** `NotificationSettingsController` под `/api/v1/settings` с эндпоинтами `GET/PUT /notifications` и `GET/PUT /tournament-subscriptions`
- [x] **DTO + сервисы настроек:** `NotificationSettingsDtos`, `TournamentSubscriptionDtos`, запросы update; сервисы `NotificationSettingsService`, `TournamentSubscriptionService`
- [x] **Категории уведомлений:** `NotificationCategory` получил `description` для пользовательского API настроек
- [x] **Batch start API:** `POST /api/v1/admin/series/batch-start`, DTO `BatchStartSeriesRequest` и `BatchStartSeriesResponse`
- [x] **Событие старта:** `SeriesBatchStartedEvent` + `StartedSeriesInfo`; `SeriesService.updateSeries` публикует event при переходе `UPCOMING -> ACTIVE`, `SeriesService.batchStartSeries` публикует общий event на батч
- [x] **Уведомление о старте серии:** `SeriesStartNotificationListener` (агрегация по пользователю, фильтрация по `SERIES_START`/`bot_blocked`/подпискам на турниры, fallback «без подписок = все турниры», inline-кнопка подачи команды для одиночного старта)

### Дедлайн-напоминания по командам (Plan notify/03)
- [x] **Сущность и репозиторий:** `DeadlineReminder`, `DeadlineReminderRepository` (`findBySeries_Id`, выбор pending по `remind_at` и `sent=false`)
- [x] **Upsert при изменении серии:** `SeriesService.createSeries` / `updateSeries` обновляют `deadline_reminder` (`remind_at = teamDeadline - 1h`); при переносе дедлайна в будущее после отправки — сбрасываются `sent`, `sentAt`, `recipientCount`
- [x] **Выборка получателей:** `TelegramUserRepository.findDeadlineReminderRecipients` (native SQL: `bot_blocked=false`, preference `TEAM_DEADLINE_REMINDER` не отключён, нет команды в серии, фильтр подписок турниров с fallback «нет подписок = все турниры»)
- [x] **Сервис отправки:** `DeadlineReminderService` (skip для `FINISHED`/`SCORING`, mark sent при пустом списке, сообщение с дедлайном в МСК + кнопка `submitTeamButton`, сохранение фактически доставленного `recipientCount`)
- [x] **Планировщик:** `DeadlineReminderScheduler` (`@Scheduled(fixedRate=60000)`, обработка pending reminders, логирование ошибки по reminder id без падения цикла)
- [x] **Тесты:** `DeadlineReminderServiceTest` и интеграционный сценарий в `AdminApiIntegrationTest` (create/update upsert + reset sent-state)

### Отслеживание карт на маркетплейсе (Plan notify/04)
- [x] **Сущности/репозитории:** `MarketplaceWatchFilter`, `MarketplaceWatchPending`, `MarketplaceWatchFilterRepository`, `MarketplaceWatchPendingRepository`
- [x] **Публикация события:** `MarketplaceService.createListing` публикует `MarketplaceListingCreatedEvent` после сохранения листинга; турниры игрока берутся через `TournamentPlayerRepository.findDistinctTournamentIdsByFantasyPlayerId`
- [x] **Matching + pending:** `MarketplaceWatchNotificationListener` (`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`) матчинг по `player/tournament/rarity/maxPrice`, исключение продавца, фильтры `bot_blocked` + preference `MARKETPLACE_WATCH`, запись в pending
- [x] **Батч-рассылка:** `MarketplaceWatchScheduler` (`@Scheduled(fixedRate=300000)`) агрегирует pending в одно сообщение на пользователя, добавляет кнопку открытия маркетплейса, очищает pending после попытки отправки
- [x] **User API CRUD:** `GET/POST/DELETE /api/v1/settings/marketplace-watches` в `NotificationSettingsController` через `MarketplaceWatchService` (валидация: ≥1 критерий из `fantasyPlayerId/tournamentId/rarity`, `maxPrice > 0`, лимит 10 фильтров, удаление только своих, duplicate → 409)
- [x] **Тесты:** unit `MarketplaceWatchServiceTest`

### TMA UI уведомлений (Plan notify/05)
- [x] **Типы + API-хуки:** `src/types/notifications.ts`, `src/api/notifications.ts` (`useNotificationSettings`, `useUpdateNotificationSettings`, `useTournamentSubscriptions`, `useUpdateTournamentSubscriptions`, `useMarketplaceWatches`, `useCreateMarketplaceWatch`, `useDeleteMarketplaceWatch`) с optimistic update для настроек категорий и подписок
- [x] **Top bar + роутинг:** в `src/App.tsx` добавлен 🔔-линк на `/notifications`; подключены маршруты `/notifications`, `/notifications/tournaments`, `/notifications/marketplace-watches`
- [x] **Страница категорий:** `NotificationSettingsPage` — 8 категорий по группам (Турниры/Маркетплейс/Системные), toggleable/non-toggleable состояния, переходы на подписки турниров и watch-фильтры
- [x] **Страница подписок:** `TournamentSubscriptionsPage` — чекбоксы активных турниров и сохранение через `PUT /settings/tournament-subscriptions` (пустой выбор = уведомления по всем турнирам)
- [x] **Страница watch-фильтров:** `MarketplaceWatchesPage` — список фильтров, удаление, счётчик `N из max`, форма добавления (игрок/турнир/редкость/maxPrice) и клиентская валидация
- [x] **Контекстная кнопка на маркетплейсе:** в `MarketplacePage` добавлено создание watch из текущих фильтров (без max-only сценария), UX-обработка `409` («Уже отслеживается») и `400` лимита («Достигнут лимит фильтров»)
- [x] **Стилизация:** расширен `index.css` для экрана уведомлений, switch/checkbox-элементов, списка watch-фильтров и CTA блока на маркетплейсе

### Ценность карты (card value, backend)
- [x] **Flyway V33:** ключи `economy_config` `card.value.{RARITY}` и `card.value.perk_bonus` (сид по плану)
- [x] **`CardValueService`:** `base + perkCount * bonus` по шаблону (уникальные перки), без хранения в БД
- [x] **`UserCardItemDto.value`**, `MarketplaceListingCardDto.value`; `EconomyInfoDto.cardValues` (`CardValueInfoDto`: `baseValues`, `perkBonus`); `GET /api/v1/card-value/info` = `buildCardValueInfo()`

### Паки турниров: лимит открытий, пул перков, новые перки
- [x] **Flyway V30:** `card_pack.max_opens_per_user` (0 = без лимита), `card_pack_perk` (пул перков на пак), перки `ninja`, `crowned`, `lastHeroGuess` + роли
- [x] **Scoring:** `ScoringContext(basePoints)` в `PerkDetector`, `DefaultScoringService` передаёт базовые баллы; детекторы Ninja / Crowned / Last Hero
- [x] **Паки:** `CardPackPerk` + репозиторий, `openPack` берёт пул из `card_pack_perk` или глобальный; админ/магазин DTO, лимит покупок в `UserStoreService`
- [x] **Админка / TMA:** поля max opens + multi-select пула; магазин — «осталось X/Y», отключение «Купить» при лимите

### Маркетплейс карт (backend M1–M6)
- [x] **Flyway V22:** `marketplace_listing`, `user_card_ownership_history`, индексы (в т.ч. лента продаж), ключ `economy_config.marketplace.commission_percent`, бэкфилл провенанса из `user_card` как `PACK_OPENING`
- [x] **Flyway V23:** потолок цены листинга `marketplace.max_price.{RARITY}`, порог покупок `marketplace.min_pack_opens_before_purchase` (3); `telegram_user.pack_opens_count` (инкремент в `CardPackService.openPack`); бэкфилл: всем пользователям `pack_opens_count = 3`, активные листинги с ценой выше потолка урезаны до max по редкости
- [x] **Flyway V25:** минимальная цена листинга `marketplace.min_price.{RARITY}` (дефолты = `renewal.cost.*`); `EconomyConfigService.getMinListingPrice`, `EconomyInfoDto.marketplaceMinPrices`; валидация листинга и справка TMA используют отдельные минимумы
- [x] **Flyway V26:** `marketplace_listing.sold_card_template_id` → `card_template(id)` — снимок шаблона на момент продажи; `MarketplaceService.buyCard` выставляет снимок при `SOLD`; `getFeed` строит редкость и превью карты по снимку (иначе после EPIC→LEGENDARY апгрейда in-place лента показывала бы текущую легенду). Старые строки без снимка: fallback на текущий `user_card.card_template` (как раньше)
- [x] **Сущности и репозитории:** `MarketplaceListing`, `UserCardOwnershipHistory`, `MarketplaceListingRepository` (фильтры активных листингов, `PESSIMISTIC_WRITE` на покупку, лента SOLD), `UserCardOwnershipHistoryRepository.existsBy…`; причины фантиков `MARKETPLACE_PURCHASE` / `MARKETPLACE_SALE`; `EconomyConfigService.getMarketplaceCommissionPercent()`
- [x] **`UserCardOwnershipService`:** запись при выдаче карт из **CardPackService.openPack** (`PACK_OPENING`) и **CardService.giveCards** (`ADMIN_GRANT`); при покупке на маркетплейсе — `MARKETPLACE_PURCHASE`
- [x] **`MarketplaceService`:** листинг / снятие / покупка (комиссия `⌊price×pct/100⌋`, сброс контракта покупателю), каталог с `canBuy`/`canBuyReason`, мои листинги, лента; событие **после** успешной покупки
- [x] **Блокировки:** активный листинг — нельзя в команду (`UserFantasyTeamService.attachCards`), recycle/renew (`CardLifecycleService`), legendary upgrade (`LegendaryUpgradeService`)
- [x] **Recycle + провенанс:** при переработке карты `CardLifecycleService.recycleCard` вызывает `UserCardOwnershipHistoryRepository.deleteAllByUserCard_Id` до удаления `user_card` (иначе FK `user_card_ownership_history_user_card_id_fkey` после V22)
- [x] **API:** `MarketplaceController` под `/api/v1/marketplace` — `GET listings|my-listings|feed`, `POST listings`, `DELETE listings/{id}`, `POST listings/{id}/buy`
- [x] **Telegram:** `MarketplaceSaleNotificationListener` — `AFTER_COMMIT` + `@Async`, plain-text сообщение продавцу (`TelegramBotApiClient`), баланс после коммита через `UserService.getBalance`; `telegram.bot.notifications.enabled` / токен как у финализации серии
- [x] **Тесты:** расширен `CardLifecycleServiceTest` (mock `MarketplaceListingRepository`, сценарий «карта в листинге»); полный `test` с Testcontainers в CI/локально при доступном Docker; `UserApiIntegrationTest` — лента маркетплейса после покупки EPIC и апгрейда до LEGENDARY остаётся EPIC в `GET /api/v1/marketplace/feed`

### Маркетплейс: админ-антимод (перелив фантиков/карт между парами)
- [x] **Flyway V28:** `marketplace_pair_clearance` — пометка пары «проверена» (модерация, не санкция)
- [x] **Flyway V29:** `marketplace_pair_sanction_history` — одна строка на событие `ban-pair` (каноническая пара `user_id_low` < `user_id_high`, причина, изъято фантиков и число снятых карт на low/high); `MarketplacePairSanctionHistory` + `MarketplacePairSanctionHistoryRepository`
- [x] **API (история + превью):** `GET /api/v1/admin/marketplace/ban-pair/preview?userA&userB` (`BanPairPreviewDto`), `GET /api/v1/admin/marketplace/ban-pair/history` (`PagedPairSanctionHistoryDto`, `Pageable`, по умолчанию 20 на страницу)
- [x] **`MarketplaceAdminService`:** `loadPairUsers`, `getBanPairPreview`, `getBanPairHistory`, сохранение истории в `banPair` (в той же `@Transactional`); `getPairTrades` использует `loadPairUsers`
- [x] **Админка:** `getBanPairPreview` / `getBanPairHistory` в `api/marketplaceAdmin.ts`, типы в `types.ts`; `MarketplaceModerationPage` — таб **История санкций**, модалка санкции с превью (кнопка подтверждения после загрузки превью)
- [x] **Flyway V27:** `telegram_user.marketplace_banned` (по умолчанию `false`); JPA-поле `TelegramUser.marketplaceBanned`
- [x] **`UserService` / `TelegramUserRepository`:** `forceDeductBalance` / `forceDeductFantiki` — списание без требования `fantiki >= amount` (допускается отрицательный баланс при санкциях)
- [x] **`MarketplaceService`:** при `marketplace_banned` — 403 и выставление листинга, и покупка
- [x] **Причины `fantiki_transaction`:** `ADMIN_PAIR_BAN`, `ADMIN_CARD_CONFISCATE` (нулевые строки на зафиксированную карту; привязка `user_card_id` в леджер не ведётся)
- [x] **`MarketplaceAdminService` + `MarketplaceAdminController`:** `GET /api/v1/admin/marketplace/pair-analysis`, `GET /pair-trades?userA&userB`, `POST /ban-pair`, `POST /unban/{telegramId}`; репозитории: `aggregateSoldTradesBySellerBuyer`, `findSoldListingsBetweenUsers`, `sumSellerReceivedForSalesTo`, `UserCardRepository.findUserCardsBoughtOnMarketplaceFromPartner` (в JPQL-`EXISTS` — `ml.buyer.id = uc.telegramUser.id`, чтобы не забирать карты, уже перепроданные с парного лота третьим лицам; **фантики** с продавца взыскиваются за **все** такие SOLD, независимо от перепродажи). `cancelAllActiveBySellerId` в репозитории **есть**, но **не** вызывается из `ban-pair`: санкция за перелив **не** отменяет ACTIVE-листинги и **не** выставляет `marketplace_banned`; в DTO `listingsCancelled` всегда 0. Разбан — для снятия `marketplace_banned`, если флаг был выставлен иначе (наследие/ручной сценарий).
- [x] **Telegram:** `PairBanNotificationEvent` + `PairBanNotificationListener` (`AFTER_COMMIT`, `@Async`) — текст о санкции (без бана маркетплейса: доступ и текущие лоты не режутся), причина, фантики, карты, баланс; как у продажи/финализации по доставке
- [x] **Админка:** `MarketplaceModerationPage`, `api/marketplaceAdmin.ts`, маршрут и меню **Marketplace**; в сделках пары — `PairTradeDto.buyerStillOwnsCard` и колонка **Seize card at ban**; модалка pair sanctions: фантики по всем взаимным продажам, изъятие карт — только у исходного покупателя, если карта ещё у него; **без** бана маркетплейса и **без** mass-cancel витрины

### Маркетплейс TMA (M7–M11) и чтение провенанса
- [x] **TMA:** `MarketplacePage` (`/marketplace`) — лента сделок, фильтры (редкость, сортировка, цена, **игрок из справочника** `GET /api/v1/fantasy-players`, **турнир / серия** без обязательного выбора игрока — query `tournamentId`/`seriesId` в `GET /marketplace/listings`), пагинация, покупка; `MyListingsPage` (`/marketplace/my`); `api/marketplace.ts` + типы в `api/types.ts`; навигация в `App.tsx`
- [x] **Коллекция:** кнопка «Продать» (тулбар и модалка карты), модалка цены с превью комиссии; условия: `uses_remaining > 0`, нет активного листинга (`GET my-listings`), карта не в команде серии со статусом ≠ `FINISHED` (загрузка статусов серий по `fantasy-teams`)
- [x] **Провенанс в UI:** `CardOwnershipHistoryBlock` + `GET /api/v1/user-cards/{userCardId}/ownership-history` (`UserCardOwnershipController`, `UserCardOwnershipService.listOwnershipHistory`, репозиторий `findAllByUserCard_IdOrderByAcquiredAtAsc`); подписи `acquisitionLabel` на русском
- [x] **Economy info:** в `EconomyInfoDto` поля `marketplaceCommissionPercent`, `marketplaceMinPrices`, `marketplaceMaxPrices`, `minPackOpensBeforeMarketplacePurchase`; в `UserProfileDto` — `packOpensCount`; интеграционный тест `UserApiIntegrationTest` проверяет комиссию и поля economy-info маркетплейса

### Награды лидерборда серии: топ-25 и топ-50
- [x] **Flyway V21:** ключи `economy_config` `series.reward.top25`, `series.reward.top50`; подпись участия — «51+ место»
- [x] **`EconomyConfigService`:** `getSeriesReward` — диапазоны 11–25 и 26–50; `seriesRewardKeysInOrder` — 7 тиров
- [x] **Админка:** порядок строк наград серии в таблице Economy
- [x] **TMA Справка:** текст про диапазоны мест + список сумм из `economy-info`
- [x] **Тест:** `UserApiIntegrationTest` ожидает `seriesRewards.length() == 7`

### Детекторы перков (polemica-fantasy-backend)
- [x] **`votingOnlyForBlack`:** учитывается только если у мирного есть хотя бы одно финальное голосование (`mine.isNotEmpty()`), иначе пустой список давал бы «все голоса за чёрных» по `Collection.all`.
- [x] **`sniper`:** дополнительно требуется смерть шерифа в **ночь 1** (`getKilled` + `night == 1`), а не только «реальный ком-убийца» по первой жертве в целом.

### STANDALONE: подбор игр серии по профилю (min(8, N))
- [x] **`DefaultGameSyncService.fetchStandalonePrepared`:** частоты match id по страницам профиля; порог **≥ min(8, N)** игроков ростера, затем `name_prefix`; константа `STANDALONE_MIN_PLAYERS_IN_PROFILE_OVERLAP`. С мая 2026 дополнительно поддерживается optional day-filter `series.game_started_on` (`gameStartedOn`): матч учитывается только если `game.started.toLocalDate()` совпадает с днём фильтра.
- [x] **`scripts/trace_series_game_sync.py`:** трассировка той же логики (`--min-overlap` + started-day filter из серии с отдельной статистикой отсечения).

### Документация в репозитории
- [x] **Структура `docs/`:** [`architecture/DESIGN.md`](../docs/architecture/DESIGN.md) (SDD), [`plans/archive/`](../docs/plans/archive/) (V2/V3 планы, CHANGES-V2), [`features/DESIGN-LEGENDARY-CARDS.md`](../docs/features/DESIGN-LEGENDARY-CARDS.md); в корне — [`README.md`](../README.md), указатель [`docs/README.md`](../docs/README.md)
- [x] **Актуализация SDD:** команды 1–3 карты и награды; базовые очки через `GamePointsService`; V11 `can_appear_on_random_cards`; сущности и API (display name, legendary upgrade, free pack opens, GET `/perks`, порядок серий, автофинализация, Phase 4+)

### Поддержка через Telegram Forum + webhook
- [x] **Flyway V18:** `telegram_support_topic` (`telegram_user_id`, `forum_message_thread_id`)
- [x] **Backend:** `telegram.support.*` (`TELEGRAM_SUPPORT_ENABLED`, `TELEGRAM_SUPPORT_FORUM_CHAT_ID`, `TELEGRAM_SUPPORT_WEBHOOK_SECRET`); расширен `TelegramBotApiClient` (`getMe`, `createForumTopic`, `sendMessage` с `message_thread_id`, `forwardMessage`, `copyMessage`); `TelegramSupportRelayService`, `TelegramSupportUpdateService`, `TelegramSupportBotIdentity`; `POST /api/v1/telegram/webhook` + `TelegramWebhookController`; `TelegramUserRepository.findByTelegramIdForUpdate` (PESSIMISTIC_WRITE)
- [x] **Тесты:** `TelegramSupportUpdateServiceTest`, `TelegramWebhookControllerTest`; `mockito-kotlin` в test scope
- [x] Текст `/start`: мини-приложение + как писать в поддержку; перед `copyMessage` ответа админа в личку — отдельное сообщение «Ответ от поддержки:» (`TelegramSupportRelayService.SUPPORT_REPLY_HEADER`); `TelegramSupportRelayServiceTest`

### Порядок серий турнира (новые сверху)
- [x] **Backend:** `findAllByTournament_IdOrderByIdDesc` в `SeriesRepository`; `UserTournamentService.getTournament`, `SeriesService.listSeriesByTournament`
- [x] **TMA:** `SeriesPickerPage` — бейдж номера серии не зависит от порядка в списке; актуальный источник — `publicNumber`, который backend выводит из названия серии.

### Очистка «призрачных» карт при смене ростера серии
- [x] **`FantasyTeamRosterPruningService.pruneInvalidCardsForSeries`:** удаляет `fantasy_team_card`, если `card_template.fantasy_player_id` больше не в `series_player` для этой серии; только пока `now <= team_deadline`; уплотняет слоты 1..n; при отсутствии карт удаляет `fantasy_team`; возвращает **`FantasyTeamRosterPruneResult`** (снятые карты по пользователям для уведомлений)
- [x] Вызовы: после **`SeriesService.assignPlayers`**; в начале **`UserFantasyTeamService`** — `getTeamForSeries`, `getTeamDetailsForSeries`, `getPublicTeamForSeries`, `getPublicTeamDetailsForSeries` (методы переведены с `readOnly` на обычную `@Transactional` из-за prune)
- [x] **`FantasyTeamRepository.findAllBySeries_IdWithCards`** (+ `JOIN FETCH telegram_user` для prune)
- [x] **Flyway V17** — одноразовая data-migration для **`series_id = 5`** (тот же алгоритм, только пока дедлайн не прошёл)
- [x] **Тесты:** `FantasyTeamRosterPruningServiceTest`, `SeriesRosterReplacementTelegramMessageTest`
- [x] **Telegram:** при **`assignPlayers`**, если у пользователей срезаны карты — **`SeriesRosterReplacementNotificationEvent`** + **`SeriesRosterReplacementNotificationListener`** (как финализация серии); текст с именами убранного и нового игрока при zip пар `tournament_player` (см. `systemPatterns.md`)

### Рассылка в Telegram из админки
- [x] **Backend:** `POST /api/v1/admin/notifications/broadcast`, `AdminBroadcastNotificationService`, `TelegramBroadcastAsyncSender`, `TelegramUserRepository.findAllTelegramIds`; тесты `AdminBroadcastNotificationServiceTest`, `AdminBroadcastApiIntegrationTest`; рассылка с `parse_mode` **MarkdownV2** в `TelegramBroadcastAsyncSender`
- [x] **Админка:** маршрут `/broadcast`, меню Broadcast, `api/notifications.ts`; подсказка по MarkdownV2 и блок preview (`telegramMarkdownV2Preview.tsx`)

### Бесплатные открытия паков (per user, per pack)
- [x] **Flyway V16:** `card_pack.free_opens_per_user`; таблица `user_card_pack_free_usage` (счётчик использованных бесплатных открытий на пару user+pack)
- [x] **Backend:** `UserStoreService` — для платного пака и положительного лимита: `INSERT … ON CONFLICT DO NOTHING` + атомарный `UPDATE … WHERE free_opens_used < limit`; иначе полное списание; для цены 0 квота не расходуется; `GET /store/packs` с `@AuthenticationPrincipal`, в ответе `freeOpensRemaining`
- [x] **Admin:** поле в `CreateCardPackRequest` / `UpdateCardPackRequest` и `CardPackDto`; формы Card packs
- [x] **TMA:** `StorePackItem.freeOpensRemaining`, подсказки и доступность кнопки, инвалидация списка паков после покупки
- [x] **Тесты:** `UserApiIntegrationTest` — сценарий с двумя бесплатными и третьей платной покупкой

### Отображаемое имя и конкурентное создание пользователя
- [x] **Flyway V14:** `telegram_user.display_name`
- [x] **Backend:** `UserProfileDto` / `UserPublicDto` + `displayName`; `PATCH /api/v1/me` (`UpdateProfileRequest`); `TelegramUserBootstrapService` (`REQUIRES_NEW`) для устойчивости к параллельному первому входу (`23505`); маппинг в лидерборде и публичной команде
- [x] **Тесты:** `UserApiIntegrationTest` (PATCH, сброс, лидерборд/owner), `UserServiceFantikiIntegrationTest` (конкурентные вызовы)
- [x] **TMA:** `formatUserDisplayName`, форма на странице магазина, `PATCH` в `api/client`

### V3 — Экономика (контракты + финализация серии) — [`PLAN-V3.md`](../docs/plans/archive/PLAN-V3.md)
- [x] **Flyway V13:** `user_card.uses_remaining`, `times_renewed`; `series.finalized`; `economy_config` + сиды; бэкофилл uses по редкости
- [x] **Backend:** `EconomyConfigService` (кэш + инвалидация из админки), `CardLifecycleService` (recycle/renew), `SeriesFinalizationService` (декремент uses + награды по лидерборду); причины `SERIES_REWARD`, `CARD_RECYCLE`, `CARD_RENEWAL`; выдача карт с uses из конфига; проверка uses при сборке команды; API user `/me/cards/{id}/recycle|renew`, `/me/economy-info`; admin `POST /series/{id}/finalize`, `GET/PUT /economy-config`
- [x] **Тесты:** `CardLifecycleServiceTest`, `SeriesFinalizationServiceTest`, интеграция admin economy config в `AdminApiIntegrationTest`
- [x] **Админка:** страница Economy, колонка Finalized в списке серий турнира, финализация на деталке серии
- [x] **TMA:** типы и API экономики, коллекция и TeamPage, страница **«Справка»** `/help` (очки, перки из `GET /api/v1/perks`, экономика из `GET /me/economy-info`; подписи наград за лидерборд серии — из `economy_config.description`, числа — из `value`)
- [x] **Коллекция — модалка карты:** как в лидерборде/истории фэнтези — полный список перков, блок «Очки в сериях» из `GET /me/fantasy-teams`, детализация «По играм серии» через `GET /me/fantasy-teams/{seriesId}/details` (селектор серии, если карта участвовала в нескольких); переработка/продление в модалке и на сетке; общий компонент разбивки очков — `ScoreBreakdownBlock` (`LeaderboardPlayerTeamPage`, `FantasyHistoryPage`, `CardsPage`)

### Статистика для баланса перков (этап 1)
- [x] **`PerkStatisticsService`** + **POST** `/api/v1/admin/perk-statistics/collect` — выборка игр через публичный профиль (100 игр на игрока) и `getMatch`, дедуп по матчу, агрегаты по детекторам; **2026-05-24:** добавлены EV-аномалии `fantasy_player × perk` (`globalOccurrencesPerGame`, `playerOccurrencesPerGame`, smoothing через `priorGames`, `lift`, `excessBonusPerGame`) и опциональные параметры запроса `minPlayerGames` / `minApplicableSlots` / `priorGames` / `maxAnomalies`
- [x] Тест `PerkStatisticsServiceTest`; исправление **`CardPackFindOrCreateTemplateIntegrationTest`** — вызов приватного метода на `AopTestUtils.getUltimateTargetObject` (иначе CGLIB-прокси с null полями)

### Отладка
- [x] **`scripts/trace_series_game_sync.py`** — пошаговая трассировка `DefaultGameSyncService` (STANDALONE: профиль + пересечение + опционально `getMatch`/префикс; POLEMICA_COMPETITION: список игр турнира + диапазон `num` + полная загрузка). Требует `ADMIN_*`; для полного прогона STANDALONE с фильтром имени — `POLEMICA_USERNAME`/`POLEMICA_PASSWORD` (как на бэкенде для sync).

### Импорт ростеров
- [x] **`scripts/import_closed_league_from_html.py`** — HTML Закрытой лиги → турнир Fantasy + фото с Полемики
- [x] **`scripts/import_tournament_from_mafoverlay.py`** — страница [MafOverlay](https://mafoverlay.ru) `…/admin/photos/tournaments/POLEMICA/{id}` → парсинг `#polemicaId`, ника, `data-photo-url` на MAIN → Admin API (`create` / `update` с `--refresh-photos`), опц. `--remove-bg` (rembg). Зависимости: `scripts/requirements-import-mafoverlay.txt`. Разметка соответствует проекту `overlay` (sibling `mafia/overlay`).

### Неполная фэнтези-команда (1–2 карты)
- [x] **Один игрок — одна карта в составе:** в `attachCards` проверяется уникальность `fantasy_player_id` (нельзя две разные `user_card` одного игрока, например COMMON+RARE); TMA `TeamPage` — нельзя выбрать вторую карту того же игрока (disabled + подсказка). Интеграционные тесты в `UserApiIntegrationTest`
- [x] **Ответ POST/PUT fantasy-team:** после `attachCards` вызываются `entityManager.flush()` и `refresh(team)` перед `team.toDto()` — иначе в JSON уходил пустой `slots` из-за кэша Hibernate по коллекции `FantasyTeam.cards`
- [x] **API / валидация:** `SubmitFantasyTeamRequest` и `UserFantasyTeamService.attachCards` — 1–3 различных карты (дубликаты `user_card` id запрещены)
- [x] **Финализация:** `SeriesFinalizationService.scaleSeriesRewardByRosterSize` — при 1 карте ⌈R/3⌉, при 2 картах ⌈2R/3⌉, при 3 — полная награда R; 0 карт или R≤0 → 0
- [x] **Лидерборд:** `findLeaderboardForSeries` — `JOIN FETCH ft.cards` для подсчёта слотов при начислении
- [x] **TMA TeamPage:** отправка при 1–3 выбранных картах; подсказка про пониженную награду
- [x] **Тесты:** `SeriesFinalizationServiceTest` — масштабирование и verify `addBalance` по неполному составу

### Скоринг и названия игр (март 2026)
- [x] **Только завершённые игры:** в `DefaultScoringService.calculateScores` учитываются строки `series_game` с кэшем, у которых в `PolemicaGame` задан `result` (победа красных/чёрных); live/незавершённые с `result == null` не попадают в сумму и не получают `scored = true`
- [x] **Синтетическое имя:** при пустом `name` из API в `DefaultGameSyncService` в БД пишется `Игра {num}` или `Игра #{id}`; общее отображение — `formatSeriesGameDisplayName` (`SeriesGameDisplayName.kt`), используется в `UserSeriesService` и `UserFantasyTeamService`

### Исправления (после релиза V2)
- [x] **Recycle карты в ACTIVE-листинге:** `CardLifecycleService.recycleCard` больше не валидирует `existsByUserCard_IdAndStatus(..., ACTIVE)` как ошибку; распыление разрешено и удаляет связанные листинги (`deleteAllByUserCard_Id`) вместе с картой. Обновлены тесты: unit `CardLifecycleServiceTest` + интеграционный сценарий в `UserApiIntegrationTest`.
- [x] **Обновление фэнтези-команды (`PUT .../series/{id}/fantasy-team`):** вместо bulk `deleteAllByFantasyTeam_Id` — `findAllByFantasyTeam_Id` + `deleteAll`, затем **`fantasyTeamCardRepository.flush()`** до `team.cards.clear()`. Иначе lazy-инициализация коллекции после отложенного DELETE снова поднимала старые строки из БД, а INSERT новых слотов давал `23505` на `fantasy_team_card_fantasy_team_id_slot_key`
- [x] **Повторный расчёт скоринга серии (`POST .../calculate-scores`):** после `card.gameScores.clear()` вызывается `fantasyTeamRepository.flush()`, чтобы DELETE сирот ушёл в БД до INSERT новых строк с тем же `(fantasy_team_card_id, series_game_id)` — иначе Hibernate мог выполнять INSERT раньше DELETE и ловить `23505` на `fantasy_team_card_game_score_*_key`
- [x] **Дубликаты перков на автокартах:** `CardPackService.findOrCreateCardTemplate` сравнивает набор `perk_id` через запрос к БД (не через in-memory `ct.perks`); после сохранения `CardTemplatePerk` строка добавляется в `saved.perks`; в `UserCardItemMapping` дедуп по `perkId` для выдачи; Flyway `V12` — удаление дублей в `card_template_perk` + уникальный индекс `(card_template_id, perk_id)`; админка `addPerk` — отказ с `409 CONFLICT` при повторной привязке

### Планировщик sync + скоринга (активные серии)
- [x] **`ActiveSeriesSyncScheduler`** — каждые 10 минут для серий `ACTIVE`/`SCORING`, не `finalized`; в тестах отключение `spring.task.scheduling.enabled: false`

### Инфраструктура
- [x] **VPS:** `fantasy.maftourbot.ru` — TMA; `admin.fantasy.maftourbot.ru` — админ SPA; Docker Compose prod (`docker-compose.prod.yml`), nginx + Let’s Encrypt; см. [`deploy/nginx-fantasy.maftourbot.ru.conf`](../deploy/nginx-fantasy.maftourbot.ru.conf), [`deploy/nginx-admin.fantasy.maftourbot.ru.conf`](../deploy/nginx-admin.fantasy.maftourbot.ru.conf)
- [x] **Prod Compose health:** `fantasy-db` и `fantasy-backend` используют `restart: unless-stopped`; backend healthcheck проверяет actuator `http://localhost:8081/actuator/health`; runtime image содержит `curl` для проверки.
- [x] **Prod DB backups:** `scripts/prod-db-backup.sh` creates custom-format PostgreSQL dumps from the `fantasy-db` container into `$HOME/polemica-fantasy-backups/postgres` and removes dumps older than `DB_BACKUP_RETENTION_DAYS` (default 14).
- [x] **VPS — репозиторий:** `~/polemica-fantasy` на `mafia@51.250.18.236` — **git** (ветка `master`, remote по SSH), не «голая» копия без `.git`; обновление кода прежде всего **`git pull`**, не только rsync
- [x] Gradle 9.0.0 + Kotlin 2.3.0 + JDK 21 — скелет проекта
- [x] Дизайн-документ [`docs/architecture/DESIGN.md`](../docs/architecture/DESIGN.md) — SDD (глобальные игроки, карточки, API, V3+, легендарки)
- [x] Memory Bank инициализирован
- [x] Docker Compose (PostgreSQL 16 + MinIO + backend), корневой `docker-compose.yml`, `.env.example`
- [x] Multi-stage `polemica-fantasy-backend/Dockerfile`, `.dockerignore`
- [x] CI: `.github/workflows/docker-publish.yml` (ветка **`master`**, GHCR, cosign keyless с `continue-on-error`, ручной deploy по SSH)
- [x] CI: `.github/workflows/deploy-vps.yml` — push в **`master`** или **workflow_dispatch** → SSH на VPS: `git pull`, сборка webapp/admin, `docker compose -f docker-compose.prod.yml up -d --build`, `sudo rsync` в `/var/www/fantasy.maftourbot.ru` и `/var/www/admin.fantasy.maftourbot.ru` (секреты `SSH_HOST`, `SSH_USER`, `SSH_KEY`, опционально `DEPLOY_PATH`)

### Backend (Agent A1 — Foundation)
- [x] Spring Boot 3.4.2, Spring Data JPA, Flyway, Security, Actuator + Prometheus (management порт 8081)
- [x] AWS SDK v2 S3, `S3Config` (path-style, MinIO), `ImageStorageService` (upload/delete, ключи players/cards), создание bucket при старте (`S3BucketInitializer`, отключается в профиле `test`)
- [x] Flyway `V1__initial_schema.sql` — все таблицы из DESIGN §4
- [x] Flyway `V3__fantasy_player_global_cards.sql` — таблица `fantasy_player`, карточки на глобального игрока, `tournament_player` только связь с турниром
- [x] JPA entities + enum-классы (`Rarity` с `scoreModifier`, `TournamentStatus`, `SeriesStatus`, …); **V2 (B1):** enum `PerkType` заменён справочником `Perk` + `card_template_perk.perk_id` FK
- [x] `application.yml` по DESIGN §12.5 + профиль `dev`, опция `s3.ensure-bucket-on-startup`
- [x] Три `SecurityFilterChain` @Order(1–3): admin Basic Auth; user `/api/v1/**` без `/api/v1/admin/**` — `TelegramAuthFilter` + authenticated; остальное — `permitAll`
- [x] Зависимость `polemica-library:1.8.2` (Maven Central + `mavenLocal()`); `ProfileGameRow.mmr` — десериализация числа или вложенного объекта (публичный профиль)
- [x] Интеграционный тест контекста: Testcontainers PostgreSQL 16

### Backend (Agent A3 — Admin API)
- [x] **GET `/api/v1/admin/users`:** список пользователей; с `tournamentId` + `seriesId` — поле `cardsInSeries` (подсчёт `user_card` по ростеру серии); `AdminUserListService`, `TelegramUserRepository.findAllWithCardsInSeriesCount`; тесты в `AdminApiIntegrationTest`
- [x] Репозитории JPA для admin-сущностей; фильтр списка шаблонов карточек (опционально `tournamentId` через участие игрока в турнире / `fantasyPlayerId` / rarity)
- [x] Сервисы: `TournamentService`, `SeriesService`, `CardService`, `CardPackService`; валидация multipart изображений; выдача карт и открытие паков
- [x] Контроллеры: `TournamentAdminController`, `SeriesAdminController`, `CardAdminController`; DTO + Jakarta Validation; `GlobalExceptionHandler`
- [x] Тесты: валидация конфигов паков (без `probability`), интеграционные сценарии (Basic Auth, give-cards)
- [x] Read API для админки (A6): `GET .../tournaments/{tournamentId}/series`, `GET .../series/{id}`, `GET .../card-packs` (+ опциональный `tournamentId`); `CardPackRepository` list methods; интеграционный тест в `AdminApiIntegrationTest`
- [x] `SeriesDto` включает `tournamentPlayerIds` (состав серии для админки); `assignPlayers`: `flush()` после bulk-delete, иначе UNIQUE `(series_id, tournament_player_id)` при сохранении

### Backend (Agent A4 — Scoring + Game Sync)
- [x] `PolemicaProperties`, `PolemicaConfig` — bean `PolemicaClient` (`PolemicaClientImpl` + `Jackson2ObjectMapperBuilder`)
- [x] `PolemicaIntegrationService` — пагинация `getProfileGames`, `getMatch`, JSON в `JsonNode`
- [x] `DefaultGameSyncService` — игроки серии → профильные match id → `getMatch` → фильтр по `namePrefix` → upsert `SeriesGame`, без кредов Polemica → HTTP 400
- [x] `PerkDetector` + 8 компонентов (логика как в polemica-perk-service: `sniper`, `winThreeToThree`, …) + `PerkDetectorRegistry` (**V2:** идентификаторы — строки `perk.id`, не enum)
- [x] `DefaultScoringService` — очки по формуле DESIGN §5.4, `FantasyTeamRepository.findAllWithCardsForScoring`; базовые очки из `GamePointsService` (polemica-library), префетч по `polemica_game_id` при расчёте серии
- [x] Flyway `V2__series_game_unique.sql`
- [x] Flyway `V4__tournament_kind_competition.sql` — `tournament.kind`, `tournament.polemica_competition_id`, `series.game_num_from` / `game_num_to`, `series.name_prefix` nullable
- [x] `TournamentKind`, ветвление `DefaultGameSyncService` (STANDALONE vs POLEMICA_COMPETITION); `PolemicaIntegrationService` — competitions + `getGamesFromCompetition` / `getGameFromCompetition`; `PolemicaAdminController` — read-only список/деталь Competition
- [x] Тесты: `PerkDetectorRegistryTest`; admin integration — sync без кредов → 400

### Backend (Agent A5 — User API + Telegram)
- [x] `TelegramProperties`, `TelegramInitDataValidator` (HMAC по доке Telegram Web Apps), `TelegramAuthentication` / principal `TelegramUser`
- [x] `UserService` (профиль + `getOrCreateAndUpdateProfile`), рефактор `CardService` на `UserService`
- [x] User endpoints по DESIGN §6.1: турниры (ACTIVE), турнир + серии, серия (игроки, игры), лидерборд, `/me`, `/me/cards`, fantasy team CRUD; дополнительно `GET /tournaments/{id}/participants` (ростер турнира для TMA)
- [x] Репозитории: `UserCardRepository` фильтры, `FantasyTeamRepository` + leaderboard, `FantasyTeamCardRepository`, `SeriesRepository` / `SeriesPlayerRepository` доп. запросы
- [x] Тесты: `TelegramInitDataValidatorTest`, `UserApiIntegrationTest` (401 без заголовка, 200 `/me` с подписанным initData)

### Frontend (TMA)
- [x] Проект `polemica-fantasy-webapp/` (Vite + React 19 + TS)
- [x] `@telegram-apps/sdk` + `InitDataProvider` (`retrieveRawInitData`, опционально `VITE_DEV_INIT_DATA`)
- [x] Страницы: турниры, хаб турнира, выбор серии, турнирный лидерборд (Общий + по сериям), правила, история фэнтези, участники, серия, сборка команды, лидерборд серии, коллекция (`?tournamentId`, фильтр турнира — select по активным турнирам API)
- [x] **TeamPage league race fix (2026-07-03):** выбор карт привязан к `seriesId:leagueCode`; stale selection с другой вкладки лиги не отображается и не отправляется, submit/grid блокируются до гидрации текущей лиги. Проверка: `polemica-fantasy-webapp npm run build`.
- [x] UI: тёмная тема, градиентные CTA, карточки по редкости (фото игрока с fallback на арт шаблона, цветная рамка по редкости в коллекции/команде/истории фэнтези), `PageHeader` / бейджи статусов
- [x] TanStack Query, React Router, proxy `/api` в `vite.config.ts`
- [x] **Agent B7:** анимация открытия пака — `components/PackOpening.tsx` + стили `pf-pack-open-*` в `index.css`; интеграция в `StorePage` (имя пака из кэша query), кнопка «В коллекцию» → `/cards`
- [x] **Achievements Stage 2 profile showcase (2026-05-25):** публичный профиль показывает `achievementSummary`, selected `profileFrame`, ordered `featuredAchievements`, `nextAchievement`; `/profile-customization` позволяет выбрать unlocked `PROFILE_FRAME` и до 5 claimed public badges с reorder. Hooks: `useProfileCustomization`, `useUpdateProfileCustomization`.

### Backend (Achievements Stage 2 — Profile Showcase)
- [x] Flyway `V47__profile_showcase.sql`: `user_profile_customization`, `user_profile_featured_achievement`, unique `(telegram_user_id, display_order)`.
- [x] `ProfileCustomizationService` + `ProfileCustomizationController`: read/update profile frame and featured badges, validates `PROFILE_FRAME` unlocks, claimed public achievements, duplicates/max count, and does delete + `flush()` before reorder inserts.
- [x] `PlayerProfileService` composes public achievement showcase and enforces hidden/private exclusion, stale frame nulling, disabled public featured visibility, and deterministic next-achievement ranking.
- [x] Integration test: `AchievementStage2ProfileShowcaseIntegrationTest` covers customization read/write validation, public rendering, next achievement, hidden rejection, stale frame null, and reorder collision safety.
- [x] Verification: `./gradlew test --rerun-tasks --tests "io.github.mralex1810.fantasy.AchievementStage2ProfileShowcaseIntegrationTest"`, `./gradlew compileKotlin compileTestKotlin --rerun-tasks`, `polemica-fantasy-webapp npm run build`, and `./scripts/codex-check.sh quick`.

### Frontend (Admin)
- [x] Проект `polemica-fantasy-admin/` (Vite + React 19 + TS + Ant Design 6 + TanStack Query + React Router 7)
- [x] **Users overview:** маршрут `/users` — таблица пользователей, выбор турнира и серии, колонки актуального баланса фантиков и числа карт по серии; `GET /api/v1/admin/users` (`AdminUserListItemDto.fantiki`, в native-запросе с фильтром серии — `tu.fantiki`); `api/usersList.ts`
- [x] Турниры: список, create/edit, деталь — игроки (add/remove/photo), серии (список + create)
- [x] **Batch start upcoming series:** на `TournamentsPage` (список турниров) кнопка `Start all UPCOMING` вызывает `POST /api/v1/admin/series/batch-start` с id всех `UPCOMING` серий **из всех турниров** (один батч-ивент уведомлений), добавлены frontend-контракты `BatchStartSeriesRequest` / `BatchStartSeriesResponseDto` в `api/seriesRequests.ts`, `api/types.ts`, `api/series.ts`
- [x] Серия: редактирование полей, assign players, sync games, calculate scores
- [x] Шаблоны карт и паки: CRUD-операции, фильтры, загрузка изображения карты, добавление perk
- [x] User tools: give cards, open pack по `telegramUserId`
- [x] **Agent B5:** страница `/perks` (каталог + редактирование через модалку); паки V2 в UI (auto, фантики, пул игроков турнира, подсказка auto); User Tools — начисление фантиков; шаблоны — выбор перки из `GET /admin/perks`, без bonus в форме; API `perks.ts`, `users.ts`, `getCardPack` / `updateCardPackPlayers` в `packs.ts`

### Backend (Agent B3 — Фантики + Store API)
- [x] `TelegramUserRepository.addFantiki` / `deductFantikiIfSufficient` (`@Modifying` queries)
- [x] `UserService`: баланс + аудит `fantiki_transaction`; `grantFantikiByTelegramId`; INITIAL при регистрации
- [x] `UserProfileDto.fantiki`; `GiveFantikiRequest`; `UserAdminController`; `StoreController` + `UserStoreService`; DTO `StorePackItemDto`, `BuyPackResponseDto`
- [x] `CardPackRepository.findAllByActiveTrueAndPriceFantikiGreaterThanEqualOrderByIdAsc`
- [x] Тесты: `UserServiceFantikiIntegrationTest`, расширены `AdminApiIntegrationTest` / `UserApiIntegrationTest`

### Backend (Agent B1 — Foundation V2: schema + entities)
- [x] Flyway `V5__fantiki.sql` — `telegram_user.fantiki`, `fantiki_transaction`
- [x] Flyway `V6__perk_system.sql` — `perk`, `perk_applicable_role`, seed 9 перков, `card_template_perk` → FK `perk_id`, `bonus_points` nullable
- [x] Flyway `V7__auto_packs.sql` — колонки `card_pack` (auto_generated, price_fantiki, use_all_tournament_players), `card_pack_player`, drop `card_pack_rarity_config.probability`
- [x] Flyway `V8__game_score_details.sql` — `fantasy_team_card_game_score`, `fantasy_team_card_game_perk`
- [x] Flyway `V10__replace_perk_catalog.sql` — замена справочника перков (8 шт., `voteForBlack` = `MULTIPLE_PER_GAME`; очистка `fantasy_team_card_game_perk` и `card_template_perk` перед вставкой)
- [x] Flyway `V11__perk_all_random_cards.sql` — `UPDATE perk SET can_appear_on_random_cards = TRUE` для всего каталога
- [x] JPA: `Perk`, `PerkApplicableRole`, `FantikiTransaction`, `CardPackPlayer`, `FantasyTeamCardGameScore`, `FantasyTeamCardGamePerk`; обновлены `TelegramUser`, `CardTemplatePerk`, `CardPack`, `CardPackRarityConfig`, `FantasyTeamCard`; репозитории для новых сущностей
- [x] Admin/TMA типы и UI под `perkId` / `perkName` и паки без `probability`

### polemica-library (опционально, не блокер)
- [ ] Отдельный `getPlayerGames` / фильтрация на сервере — сейчас используется пагинация `getProfileGames` из артефакта 1.8.2

## Известные проблемы
- **2026-08-14 — Telegram OCR roster production canary:** worker schema
  v3 и backend V76 добавляют evidence-bound Yandex OCR и exact tournament-roster
  matching. Happy path создаёт серию сразу с полным составом после same-actor
  preview/confirm; incomplete/ambiguous/album/provider failure остаётся
  `NEEDS_REVIEW` без кнопки записи; media create доступен только в `MANUAL`, не
  в `AUTOMATIC`. Scoped Yandex Vision key проверен реальным HTTPS smoke через
  VPN worker; V76 и OCR/roster flags включены в production при `MANUAL` mode.
  Реальные `2243`/`2247` проверены Yandex Vision и закреплены golden fixtures;
  `2247` покрывает подпись с заменой и production roster mappings. Следующий
  свежий single-photo announcement остаётся operational canary полного
  Telegram delivery → preview → human confirm пути.
- **2026-08-11 — Telegram guarded create/result slice (local, not deployed):**
  добавлены HMAC ingest и durable review workflow в admin chat с кнопками
  `Проверить создание` → `Создать без состава`; exact chat + exact message +
  same-actor confirm, атомарные source/audit/outcome записи и ссылка на ручное
  назначение roster. Worker остаётся SHADOW/VPN-only, BACKEND mode не хранит Bot
  API credentials и не доставляет silent bootstrap. RESULT post теперь exact
  links к ANNOUNCEMENT series и проходит durable sync/score/readiness pipeline;
  manual/automatic create/finalize выбираются per-policy, но global write/result
  gates и все modes default-off. OCR/media, roster automation, non-STANDALONE и
  correction/reopen остаются TODO.
- Детекторы перков зависят от полноты модели `PolemicaGame` (голосования, кики, лучший ход); при расхождениях с Полемикой уточнять по реальным логам API
- В `UserApiIntegrationTest` по-прежнему падает legacy-сценарий `POST fantasy team rejects more than one LEGENDARY per team()` (`expected 400, actual 200`) — не относится к complaints-функционалу и требует отдельного решения по актуальному правилу лимита legendary в лигах

## Технический долг
- TMA SDK: npm предупреждает о deprecated пакетах `@telegram-apps/*` в пользу `@tma.js/*` — миграция по желанию
- GitHub Actions: cosign может требовать доп. настройку OIDC — шаг помечен `continue-on-error`
- Docker Compose: переменная `POSTGRES_HOST_PORT` (по умолчанию 5433), если порт занят
