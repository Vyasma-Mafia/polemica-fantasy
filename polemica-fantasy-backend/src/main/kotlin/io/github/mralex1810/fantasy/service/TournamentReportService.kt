package io.github.mralex1810.fantasy.service

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Service
class TournamentReportService(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    private val mskZone: ZoneId = ZoneId.of("Europe/Moscow")
    private val shortDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM HH:mm", Locale.US)
        .withZone(mskZone)
    private val generatedAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'MSK'", Locale.US)
        .withZone(mskZone)

    @Transactional(readOnly = true)
    fun generateHtmlReport(tournamentId: Long, requestedSeriesIds: List<Long>?): String {
        val seriesIds = requestedSeriesIds
            ?.distinct()
            ?.filter { it > 0 }
            .orEmpty()
        if (seriesIds.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "seriesIds must not be empty")
        }

        val tournament = loadTournament(tournamentId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament not found")
        val series = loadSeries(tournamentId, seriesIds)
        if (series.size != seriesIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "All selected series must belong to the tournament")
        }

        val leaders = loadLeaders(seriesIds)
        val bestTeam = loadBestTeam(seriesIds)
        val topTeams = loadTopTeams(seriesIds)
        val topCards = loadTopCards(seriesIds)
        val popularPlayers = loadPlayerStats(seriesIds, PlayerStatsMode.POPULAR)
        val effectivePlayers = loadPlayerStats(seriesIds, PlayerStatsMode.EFFECTIVE).ifEmpty {
            loadPlayerStats(seriesIds, PlayerStatsMode.EFFECTIVE_FALLBACK)
        }
        val averageTeamScore = loadAverageTeamScore(seriesIds)
        val bestRarity = loadBestRarity(seriesIds)

        val summary = ReportSummary(
            seriesCount = series.size,
            gamesCount = series.sumOf { it.gamesCount },
            managerCount = loadDistinctManagerCount(seriesIds),
            scoredTeamCount = loadScoredTeamCount(seriesIds),
            averageTeamScore = averageTeamScore,
            bestRarity = bestRarity,
        )

        return renderHtml(
            tournament = tournament,
            series = series,
            summary = summary,
            leaders = leaders,
            bestTeam = bestTeam,
            topTeams = topTeams,
            topCards = topCards,
            popularPlayers = popularPlayers,
            effectivePlayers = effectivePlayers,
        )
    }

    private fun loadTournament(tournamentId: Long): ReportTournament? =
        jdbc.query(
            """
            SELECT id, name, status, kind
            FROM tournament
            WHERE id = :id
            """.trimIndent(),
            mapOf("id" to tournamentId),
            RowMapper { rs, _ ->
                ReportTournament(
                    id = rs.getLong("id"),
                    name = rs.getString("name"),
                    status = rs.getString("status"),
                    kind = rs.getString("kind"),
                )
            },
        ).firstOrNull()

    private fun loadSeries(tournamentId: Long, seriesIds: List<Long>): List<ReportSeries> =
        jdbc.query(
            """
            SELECT
                s.id,
                s.name,
                s.status,
                s.starts_at,
                COUNT(DISTINCT sg.id) AS games_count,
                COUNT(DISTINCT ft.telegram_user_id) FILTER (WHERE ft.total_score IS NOT NULL) AS managers_count,
                COUNT(ft.id) FILTER (WHERE ft.total_score IS NOT NULL) AS scored_team_count
            FROM series s
            LEFT JOIN series_game sg ON sg.series_id = s.id
            LEFT JOIN fantasy_team ft ON ft.series_id = s.id
            WHERE s.tournament_id = :tournamentId
              AND s.id IN (:seriesIds)
            GROUP BY s.id, s.name, s.status, s.starts_at
            ORDER BY s.starts_at ASC, s.id ASC
            """.trimIndent(),
            params(seriesIds).addValue("tournamentId", tournamentId),
            RowMapper { rs, _ ->
                ReportSeries(
                    id = rs.getLong("id"),
                    name = rs.getString("name"),
                    status = rs.getString("status"),
                    startsAt = rs.getTimestamp("starts_at").toInstant(),
                    gamesCount = rs.getInt("games_count"),
                    managersCount = rs.getInt("managers_count"),
                    scoredTeamCount = rs.getInt("scored_team_count"),
                )
            },
        )

    private fun loadLeaders(seriesIds: List<Long>): List<LeagueLeaders> {
        val rows = jdbc.query(
            """
            WITH totals AS (
                SELECT
                    l.code AS league_code,
                    l.name AS league_name,
                    tu.id AS user_id,
                    COALESCE(NULLIF(tu.display_name, ''), NULLIF(tu.first_name, ''), NULLIF(tu.username, ''), tu.telegram_id::text) AS manager_name,
                    SUM(ft.total_score) AS total_score,
                    COUNT(*) AS team_count
                FROM fantasy_team ft
                JOIN telegram_user tu ON tu.id = ft.telegram_user_id
                JOIN series_league sl ON sl.id = ft.series_league_id
                JOIN league l ON l.id = sl.league_id
                WHERE ft.series_id IN (:seriesIds)
                  AND ft.total_score IS NOT NULL
                GROUP BY l.code, l.name, tu.id, manager_name
            ),
            ranked AS (
                SELECT
                    *,
                    ROW_NUMBER() OVER (PARTITION BY league_code ORDER BY total_score DESC, user_id ASC) AS rank,
                    COUNT(*) OVER (PARTITION BY league_code) AS participant_count
                FROM totals
            )
            SELECT *
            FROM ranked
            WHERE rank <= 10
            ORDER BY CASE league_code WHEN 'MAIN' THEN 0 WHEN 'BUDGET' THEN 1 ELSE 2 END, league_name, rank
            """.trimIndent(),
            params(seriesIds),
            RowMapper { rs, _ ->
                LeaderRow(
                    leagueCode = rs.getString("league_code"),
                    leagueName = rs.getString("league_name"),
                    participantCount = rs.getInt("participant_count"),
                    rank = rs.getInt("rank"),
                    managerName = rs.getString("manager_name"),
                    totalScore = rs.getDouble("total_score"),
                    teamCount = rs.getInt("team_count"),
                )
            },
        )
        return rows.groupBy { it.leagueCode }
            .values
            .map { leagueRows ->
                LeagueLeaders(
                    code = leagueRows.first().leagueCode,
                    name = leagueRows.first().leagueName,
                    participantCount = leagueRows.first().participantCount,
                    rows = leagueRows,
                )
            }
    }

    private fun loadBestTeam(seriesIds: List<Long>): BestTeam? {
        val team = jdbc.query(
            """
            SELECT
                ft.id,
                COALESCE(NULLIF(tu.display_name, ''), NULLIF(tu.first_name, ''), NULLIF(tu.username, ''), tu.telegram_id::text) AS manager_name,
                ft.total_score,
                s.name AS series_name,
                l.name AS league_name
            FROM fantasy_team ft
            JOIN telegram_user tu ON tu.id = ft.telegram_user_id
            JOIN series s ON s.id = ft.series_id
            JOIN series_league sl ON sl.id = ft.series_league_id
            JOIN league l ON l.id = sl.league_id
            WHERE ft.series_id IN (:seriesIds)
              AND ft.total_score IS NOT NULL
            ORDER BY ft.total_score DESC, ft.id ASC
            LIMIT 1
            """.trimIndent(),
            params(seriesIds),
            RowMapper { rs, _ ->
                BestTeam(
                    id = rs.getLong("id"),
                    managerName = rs.getString("manager_name"),
                    totalScore = rs.getDouble("total_score"),
                    seriesName = rs.getString("series_name"),
                    leagueName = shortLeagueName(rs.getString("league_name")),
                    cards = emptyList(),
                )
            },
        ).firstOrNull() ?: return null

        val cards = jdbc.query(
            """
            SELECT
                fp.nickname AS player_name,
                COALESCE(fp.photo_url, ct.image_url) AS photo_url,
                ct.rarity,
                ftc.score
            FROM fantasy_team_card ftc
            JOIN user_card uc ON uc.id = ftc.user_card_id
            JOIN card_template ct ON ct.id = uc.card_template_id
            JOIN fantasy_player fp ON fp.id = ct.fantasy_player_id
            WHERE ftc.fantasy_team_id = :teamId
            ORDER BY ftc.slot ASC
            """.trimIndent(),
            mapOf("teamId" to team.id),
            RowMapper { rs, _ ->
                TeamCard(
                    playerName = rs.getString("player_name"),
                    photoUrl = rs.getString("photo_url"),
                    rarity = rs.getString("rarity"),
                    score = rs.getDoubleOrNull("score"),
                )
            },
        )
        return team.copy(cards = cards)
    }

    private fun loadTopTeams(seriesIds: List<Long>): List<TopTeamRow> =
        jdbc.query(
            """
            SELECT
                ROW_NUMBER() OVER (ORDER BY ft.total_score DESC, ft.id ASC) AS rank,
                COALESCE(NULLIF(tu.display_name, ''), NULLIF(tu.first_name, ''), NULLIF(tu.username, ''), tu.telegram_id::text) AS manager_name,
                ft.total_score,
                s.name AS series_name,
                l.name AS league_name
            FROM fantasy_team ft
            JOIN telegram_user tu ON tu.id = ft.telegram_user_id
            JOIN series s ON s.id = ft.series_id
            JOIN series_league sl ON sl.id = ft.series_league_id
            JOIN league l ON l.id = sl.league_id
            WHERE ft.series_id IN (:seriesIds)
              AND ft.total_score IS NOT NULL
            ORDER BY ft.total_score DESC, ft.id ASC
            LIMIT 8
            """.trimIndent(),
            params(seriesIds),
            RowMapper { rs, _ ->
                TopTeamRow(
                    rank = rs.getInt("rank"),
                    managerName = rs.getString("manager_name"),
                    totalScore = rs.getDouble("total_score"),
                    seriesName = rs.getString("series_name"),
                    leagueName = shortLeagueName(rs.getString("league_name")),
                )
            },
        )

    private fun loadTopCards(seriesIds: List<Long>): List<TopCard> {
        val cards = jdbc.query(
            """
            WITH ranked AS (
                SELECT
                    ftc.id AS fantasy_team_card_id,
                    ct.id AS template_id,
                    fp.id AS fantasy_player_id,
                    fp.nickname AS player_name,
                    COALESCE(fp.photo_url, ct.image_url) AS photo_url,
                    ct.rarity,
                    ftc.score,
                    COALESCE(NULLIF(tu.display_name, ''), NULLIF(tu.first_name, ''), NULLIF(tu.username, ''), tu.telegram_id::text) AS manager_name,
                    s.name AS series_name,
                    l.name AS league_name,
                    ROW_NUMBER() OVER (
                        PARTITION BY fp.id
                        ORDER BY ftc.score DESC,
                            CASE ct.rarity WHEN 'LEGENDARY' THEN 4 WHEN 'EPIC' THEN 3 WHEN 'RARE' THEN 2 ELSE 1 END DESC,
                            ct.id ASC
                    ) AS player_rank
                FROM fantasy_team_card ftc
                JOIN fantasy_team ft ON ft.id = ftc.fantasy_team_id
                JOIN telegram_user tu ON tu.id = ft.telegram_user_id
                JOIN series s ON s.id = ft.series_id
                JOIN series_league sl ON sl.id = ft.series_league_id
                JOIN league l ON l.id = sl.league_id
                JOIN user_card uc ON uc.id = ftc.user_card_id
                JOIN card_template ct ON ct.id = uc.card_template_id
                JOIN fantasy_player fp ON fp.id = ct.fantasy_player_id
                WHERE ft.series_id IN (:seriesIds)
                  AND ftc.score IS NOT NULL
            ),
            chosen AS (
                SELECT *
                FROM ranked
                WHERE player_rank = 1
                ORDER BY score DESC, fantasy_player_id ASC
                LIMIT 8
            )
            SELECT
                ROW_NUMBER() OVER (ORDER BY score DESC, fantasy_player_id ASC) AS rank,
                template_id,
                fantasy_player_id,
                player_name,
                photo_url,
                rarity,
                score,
                manager_name,
                series_name,
                league_name
            FROM chosen
            ORDER BY rank ASC
            """.trimIndent(),
            params(seriesIds),
            RowMapper { rs, _ ->
                TopCard(
                    rank = rs.getInt("rank"),
                    templateId = rs.getLong("template_id"),
                    fantasyPlayerId = rs.getLong("fantasy_player_id"),
                    playerName = rs.getString("player_name"),
                    photoUrl = rs.getString("photo_url"),
                    rarity = rs.getString("rarity"),
                    score = rs.getDouble("score"),
                    managerName = rs.getString("manager_name"),
                    seriesName = rs.getString("series_name"),
                    leagueName = shortLeagueName(rs.getString("league_name")),
                    perks = emptyList(),
                )
            },
        )
        if (cards.isEmpty()) return cards

        val perksByTemplate = loadPerks(cards.map { it.templateId })
        return cards.map { it.copy(perks = perksByTemplate[it.templateId].orEmpty()) }
    }

    private fun loadPerks(templateIds: List<Long>): Map<Long, List<String>> =
        jdbc.query(
            """
            SELECT ctp.card_template_id, p.name
            FROM card_template_perk ctp
            JOIN perk p ON p.id = ctp.perk_id
            WHERE ctp.card_template_id IN (:templateIds)
            ORDER BY ctp.card_template_id ASC, ctp.id ASC
            """.trimIndent(),
            MapSqlParameterSource("templateIds", templateIds.distinct()),
            RowMapper { rs, _ -> rs.getLong("card_template_id") to rs.getString("name") },
        ).groupBy({ it.first }, { it.second })

    private fun loadPlayerStats(seriesIds: List<Long>, mode: PlayerStatsMode): List<PlayerStat> {
        val minPicks = when (mode) {
            PlayerStatsMode.POPULAR -> 1
            PlayerStatsMode.EFFECTIVE -> 20
            PlayerStatsMode.EFFECTIVE_FALLBACK -> 1
        }
        val orderBy = when (mode) {
            PlayerStatsMode.POPULAR -> "picks DESC, avg_score DESC, fp.id ASC"
            PlayerStatsMode.EFFECTIVE,
            PlayerStatsMode.EFFECTIVE_FALLBACK,
            -> "avg_score DESC, picks DESC, fp.id ASC"
        }
        return jdbc.query(
            """
            SELECT
                fp.id AS fantasy_player_id,
                fp.nickname AS player_name,
                fp.photo_url AS photo_url,
                COUNT(*) AS picks,
                COUNT(DISTINCT ft.telegram_user_id) AS managers,
                AVG(ftc.score) AS avg_score,
                MAX(ftc.score) AS max_score
            FROM fantasy_team_card ftc
            JOIN fantasy_team ft ON ft.id = ftc.fantasy_team_id
            JOIN user_card uc ON uc.id = ftc.user_card_id
            JOIN card_template ct ON ct.id = uc.card_template_id
            JOIN fantasy_player fp ON fp.id = ct.fantasy_player_id
            WHERE ft.series_id IN (:seriesIds)
              AND ftc.score IS NOT NULL
            GROUP BY fp.id, fp.nickname, fp.photo_url
            HAVING COUNT(*) >= :minPicks
            ORDER BY $orderBy
            LIMIT 8
            """.trimIndent(),
            params(seriesIds).addValue("minPicks", minPicks),
            RowMapper { rs, _ ->
                PlayerStat(
                    fantasyPlayerId = rs.getLong("fantasy_player_id"),
                    playerName = rs.getString("player_name"),
                    photoUrl = rs.getString("photo_url"),
                    picks = rs.getInt("picks"),
                    managers = rs.getInt("managers"),
                    averageScore = rs.getDouble("avg_score"),
                    maxScore = rs.getDouble("max_score"),
                )
            },
        )
    }

    private fun loadDistinctManagerCount(seriesIds: List<Long>): Int =
        jdbc.queryForObject(
            """
            SELECT COUNT(DISTINCT telegram_user_id)
            FROM fantasy_team
            WHERE series_id IN (:seriesIds)
              AND total_score IS NOT NULL
            """.trimIndent(),
            params(seriesIds),
            Int::class.java,
        ) ?: 0

    private fun loadScoredTeamCount(seriesIds: List<Long>): Int =
        jdbc.queryForObject(
            """
            SELECT COUNT(*)
            FROM fantasy_team
            WHERE series_id IN (:seriesIds)
              AND total_score IS NOT NULL
            """.trimIndent(),
            params(seriesIds),
            Int::class.java,
        ) ?: 0

    private fun loadAverageTeamScore(seriesIds: List<Long>): Double? =
        jdbc.queryForObject(
            """
            SELECT AVG(total_score)
            FROM fantasy_team
            WHERE series_id IN (:seriesIds)
              AND total_score IS NOT NULL
            """.trimIndent(),
            params(seriesIds),
            Double::class.java,
        )

    private fun loadBestRarity(seriesIds: List<Long>): RarityAverage? =
        jdbc.query(
            """
            SELECT ct.rarity, AVG(ftc.score) AS avg_score
            FROM fantasy_team_card ftc
            JOIN fantasy_team ft ON ft.id = ftc.fantasy_team_id
            JOIN user_card uc ON uc.id = ftc.user_card_id
            JOIN card_template ct ON ct.id = uc.card_template_id
            WHERE ft.series_id IN (:seriesIds)
              AND ftc.score IS NOT NULL
            GROUP BY ct.rarity
            ORDER BY avg_score DESC, ct.rarity ASC
            LIMIT 1
            """.trimIndent(),
            params(seriesIds),
            RowMapper { rs, _ -> RarityAverage(rs.getString("rarity"), rs.getDouble("avg_score")) },
        ).firstOrNull()

    private fun renderHtml(
        tournament: ReportTournament,
        series: List<ReportSeries>,
        summary: ReportSummary,
        leaders: List<LeagueLeaders>,
        bestTeam: BestTeam?,
        topTeams: List<TopTeamRow>,
        topCards: List<TopCard>,
        popularPlayers: List<PlayerStat>,
        effectivePlayers: List<PlayerStat>,
    ): String {
        val generatedAt = generatedAtFormatter.format(Instant.now())
        val heroImage = topCards.firstOrNull()?.photoUrl ?: bestTeam?.cards?.firstOrNull()?.photoUrl
        val heroStyle = if (heroImage.isNullOrBlank()) {
            ""
        } else {
            " style=\"background:linear-gradient(90deg,rgba(17,18,15,.96),rgba(17,18,15,.64)),url('${cssUrl(heroImage)}') center 24%/cover no-repeat\""
        }
        val mainLeader = leaders.firstOrNull { it.code == "MAIN" }?.rows?.firstOrNull() ?: leaders.firstOrNull()?.rows?.firstOrNull()
        val budgetLeader = leaders.firstOrNull { it.code == "BUDGET" }?.rows?.firstOrNull()
        val topCard = topCards.firstOrNull()
        val popular = popularPlayers.firstOrNull()
        val effective = effectivePlayers.firstOrNull()

        return """
            <!doctype html>
            <html lang="ru">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${html(tournament.name)} · Fantasy recap</title>
              <style>${reportCss()}</style>
            </head>
            <body>
              <main class="page">
                <div class="deck-toolbar" aria-label="Вкладки репорта">
                  <b>Слайды:</b>
                  <button class="deck-tab is-active" type="button" data-slide-target="report-cover">Обложка</button>
                  <button class="deck-tab" type="button" data-slide-target="report-leaders">Лидеры лиг</button>
                  <button class="deck-tab" type="button" data-slide-target="report-records">Рекорды</button>
                  <button class="deck-tab" type="button" data-slide-target="report-stats">Статистика</button>
                  <button class="deck-tab" type="button" data-slide-target="report-series">Серии</button>
                  <button class="deck-tab" type="button" data-slide-target="report-details">Топы карточек</button>
                  <button class="screen-mode-button" type="button">Режим скрина</button>
                </div>
                <article class="poster" id="report-full">
                  <div class="slide-panel is-active" id="report-cover">
                    <section class="hero"$heroStyle>
                      <div>
                        <p class="eyebrow">Fantasy recap · ${html(generatedAt)}</p>
                        <h1>${html(tournament.name)}</h1>
                        <p class="subtitle">Топы по лигам, рекордная карточка, лучший состав и статистика по выбранным сериям.</p>
                        <p class="hero-note">Для витрины считаем все синхронизированные игры засчитанными. Лидерборды и рекорды ранжируются по составам, у которых уже есть рассчитанные очки.</p>
                      </div>
                      <div class="hero-side">
                        ${heroTile("Основная лига", mainLeader)}
                        ${heroTile("Бюджетная лига", budgetLeader)}
                      </div>
                    </section>
                    <section class="summary-grid">
                      <div class="summary-cell"><b>${summary.seriesCount}</b><span>серий</span></div>
                      <div class="summary-cell"><b>${summary.gamesCount}/${summary.gamesCount}</b><span>игр засчитано</span></div>
                      <div class="summary-cell"><b>${summary.managerCount}</b><span>фэнтези-менеджеров</span></div>
                      <div class="summary-cell"><b>${summary.scoredTeamCount}</b><span>составов с очками</span></div>
                    </section>
                  </div>
                  <section class="section slide-panel" id="report-leaders">
                    <div class="section-title"><h2>Лидеры лиг</h2><span>сумма очков по выбранным сериям</span></div>
                    <div class="two-col">${leadersHtml(leaders)}</div>
                  </section>
                  <section class="section slide-panel" id="report-records">
                    <div class="section-title"><h2>Рекорды турнира</h2><span>лучшие single-series результаты</span></div>
                    <div class="highlight-grid">
                      ${recordCardHtml(topCard)}
                      ${bestTeamHtml(bestTeam)}
                    </div>
                  </section>
                  <section class="section slide-panel" id="report-stats">
                    <div class="section-title"><h2>Статистические приколы</h2><span>по карточкам в рассчитанных составах</span></div>
                    <div class="fun-grid">
                      <div class="fun-card"><span>Народный выбор</span><b>${html(popular?.playerName ?: "—")}</b><strong>${popular?.picks?.toString() ?: "0"} пиков</strong></div>
                      <div class="fun-card"><span>Самый эффективный пик</span><b>${html(effective?.playerName ?: "—")}</b><strong>${effective?.averageScore?.let { "${score(it)} ср." } ?: "—"}</strong></div>
                      <div class="fun-card"><span>Средний состав</span><b>по выборке</b><strong>${summary.averageTeamScore?.let { score(it) } ?: "—"}</strong></div>
                      <div class="fun-card"><span>Редкость с лучшим средним</span><b>${html(summary.bestRarity?.rarity?.let { rarityLabel(it) } ?: "—")}</b><strong>${summary.bestRarity?.averageScore?.let { score(it) } ?: "—"}</strong></div>
                    </div>
                    <div class="two-col">
                      ${playerPanelHtml("Самые популярные игроки", "пики карточек", popularPlayers, PlayerPanelMode.POPULAR)}
                      ${playerPanelHtml("Самые эффективные игроки", "мин. 20 пиков", effectivePlayers, PlayerPanelMode.EFFECTIVE)}
                    </div>
                  </section>
                  <section class="section slide-panel" id="report-series">
                    <div class="section-title"><h2>Серии</h2><span>для витрины все игры считаются scored</span></div>
                    <div class="series-strip">${series.joinToString("") { seriesCardHtml(it) }}</div>
                  </section>
                  <section class="section detail slide-panel" id="report-details">
                    <div class="two-col">
                      <div class="panel"><div class="panel__head"><b>Топ карточек</b><span>unique players</span></div>${topCards.joinToString("") { topCardRowHtml(it) }}</div>
                      <div class="panel"><div class="panel__head"><b>Топ составов</b><span>single-series</span></div>${topTeamsHtml(topTeams)}</div>
                    </div>
                  </section>
                  <footer class="footer">Polemica Fantasy · tournament id ${tournament.id} · generated from application data at ${html(generatedAt)}</footer>
                </article>
              </main>
              <script>${reportScript()}</script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun heroTile(label: String, row: LeaderRow?): String =
        """
        <div class="stat-tile">
          <span>${html(label)}</span>
          <strong>${row?.totalScore?.let { score(it) } ?: "—"}</strong>
          <small>${html(row?.managerName ?: "нет данных")}${row?.rank?.let { " · #$it" } ?: ""}</small>
        </div>
        """.trimIndent()

    private fun leadersHtml(leaders: List<LeagueLeaders>): String {
        if (leaders.isEmpty()) {
            return """<div class="panel"><div class="panel__head"><b>Лиги</b><span>0 игроков</span></div><div class="empty-state">Нет рассчитанных составов.</div></div>"""
        }
        return leaders.joinToString("") { league ->
            """
            <div class="panel">
              <div class="panel__head"><b>${html(league.name)}</b><span>${league.participantCount} игроков</span></div>
              ${league.rows.joinToString("") { leaderRowHtml(it, league.rows.first().totalScore) }}
            </div>
            """.trimIndent()
        }
    }

    private fun leaderRowHtml(row: LeaderRow, topScore: Double): String {
        val pct = percent(row.totalScore, topScore)
        val topClass = if (row.rank <= 3) " leader-row--top leader-row--${row.rank}" else ""
        return """
        <div class="leader-row$topClass">
          <div class="rank">${row.rank}</div>
          <div class="leader-main"><div class="leader-name">${html(row.managerName)}</div><div class="meter"><span style="width:${pct}%"></span></div></div>
          <div class="leader-meta"><strong>${score(row.totalScore)}</strong><span>${row.teamCount} сост.</span></div>
        </div>
        """.trimIndent()
    }

    private fun recordCardHtml(card: TopCard?): String {
        if (card == null) {
            return """<div class="record-card"><div class="muted">Карточка турнира</div><div class="record-title">Нет данных<span>—</span></div></div>"""
        }
        return """
        <div class="record-card">
          <div class="record-photo-wrap">
            ${img("record-photo", card.photoUrl, card.playerName)}
            <div>
              <div class="muted">Карточка турнира</div>
              <div class="record-title">${html(card.playerName)}<span>${score(card.score)}</span></div>
              <div class="muted">${html(rarityLabel(card.rarity))} · ${html(card.managerName)} · ${html(card.leagueName)}</div>
              ${chipsHtml(card.perks)}
            </div>
          </div>
        </div>
        """.trimIndent()
    }

    private fun bestTeamHtml(team: BestTeam?): String {
        if (team == null) {
            return """<div class="team-showcase"><div class="muted">Состав турнира</div><h2>Нет данных</h2></div>"""
        }
        return """
        <div class="team-showcase">
          <div class="team-showcase__top">
            <div><div class="muted">Состав турнира</div><h2>${html(team.managerName)}</h2><div class="muted">${html(team.seriesName)} · ${html(team.leagueName)}</div></div>
            <div class="team-score">${score(team.totalScore)}<span>очков</span></div>
          </div>
          <div class="team-cards">${team.cards.joinToString("") { teamCardHtml(it) }}</div>
        </div>
        """.trimIndent()
    }

    private fun teamCardHtml(card: TeamCard): String =
        """
        <div class="team-card-mini rarity-border--${card.rarity.lowercase()}">
          ${img("team-card-mini__photo", card.photoUrl, card.playerName)}
          <div class="team-card-mini__name">${html(card.playerName)}</div>
          <div class="team-card-mini__score">${card.score?.let { score(it) } ?: "—"}</div>
          <div class="team-card-mini__rarity">${html(rarityLabel(card.rarity))}</div>
        </div>
        """.trimIndent()

    private fun playerPanelHtml(title: String, subtitle: String, players: List<PlayerStat>, mode: PlayerPanelMode): String {
        val maxValue = players.maxOfOrNull { if (mode == PlayerPanelMode.POPULAR) it.picks.toDouble() else it.averageScore } ?: 0.0
        return """
        <div class="panel">
          <div class="panel__head"><b>${html(title)}</b><span>${html(subtitle)}</span></div>
          ${players.ifEmpty { emptyList() }.joinToString("") { playerRowHtml(it, maxValue, mode) }.ifBlank { """<div class="empty-state">Нет данных.</div>""" }}
        </div>
        """.trimIndent()
    }

    private fun playerRowHtml(player: PlayerStat, maxValue: Double, mode: PlayerPanelMode): String {
        val value = if (mode == PlayerPanelMode.POPULAR) player.picks.toDouble() else player.averageScore
        val label = if (mode == PlayerPanelMode.POPULAR) player.picks.toString() else score(player.averageScore)
        val small = if (mode == PlayerPanelMode.POPULAR) {
            "${player.managers} менеджеров · ср. ${score(player.averageScore)}"
        } else {
            "${player.picks} пиков · макс. ${score(player.maxScore)}"
        }
        return """
        <div class="player-row">
          ${img("player-row__photo", player.photoUrl, player.playerName)}
          <div><div class="player-row__top"><b>${html(player.playerName)}</b><strong>$label</strong></div><div class="meter meter--thin"><span style="width:${percent(value, maxValue)}%"></span></div><small>${html(small)}</small></div>
        </div>
        """.trimIndent()
    }

    private fun seriesCardHtml(series: ReportSeries): String =
        """
        <div class="series-card">
          <div class="series-kicker">${html(shortDateFormatter.format(series.startsAt))} · ${html(series.status.lowercase())}</div>
          <h3>${html(series.name)}</h3>
          <div class="series-grid">
            <span><b>${series.gamesCount}/${series.gamesCount}</b> игр</span>
            <span><b>${series.managersCount}</b> менеджеров</span>
            <span><b>${series.scoredTeamCount}</b> составов</span>
          </div>
        </div>
        """.trimIndent()

    private fun topCardRowHtml(card: TopCard): String =
        """
        <div class="card-row">
          <div class="rank rank--small">${card.rank}</div>
          ${img("card-row__photo", card.photoUrl, card.playerName)}
          <div>
            <div class="card-row__title">${html(card.playerName)} <span class="rarity rarity--${card.rarity.lowercase()}">${html(rarityLabel(card.rarity))}</span></div>
            <div class="muted">${html(card.managerName)} · ${html(card.seriesName)} · ${html(card.leagueName)}</div>
            ${chipsHtml(card.perks)}
          </div>
          <div class="score-pill">${score(card.score)}</div>
        </div>
        """.trimIndent()

    private fun topTeamsHtml(teams: List<TopTeamRow>): String =
        teams.joinToString("") { team ->
            """
            <div class="team-row">
              <div class="rank rank--small">${team.rank}</div>
              <div class="team-row__main"><b>${html(team.managerName)}</b><span>${html(team.seriesName)} · ${html(team.leagueName)}</span></div>
              <strong>${score(team.totalScore)}</strong>
            </div>
            """.trimIndent()
        }.ifBlank { """<div class="empty-state">Нет данных.</div>""" }


    private fun chipsHtml(perks: List<String>): String =
        if (perks.isEmpty()) "" else """<div class="chips">${perks.joinToString("") { "<span>${html(it)}</span>" }}</div>"""

    private fun img(className: String, url: String?, alt: String): String =
        if (url.isNullOrBlank()) {
            """<div class="avatar-fallback $className">${html(initials(alt))}</div>"""
        } else {
            """<img class="$className" src="${html(url)}" alt="${html(alt)}" loading="lazy" onerror="this.replaceWith(Object.assign(document.createElement('div'),{className:'avatar-fallback $className',textContent:'${html(initials(alt))}'}))">"""
        }

    private fun params(seriesIds: List<Long>): MapSqlParameterSource =
        MapSqlParameterSource("seriesIds", seriesIds)

    private fun ResultSet.getDoubleOrNull(column: String): Double? {
        val value = getDouble(column)
        return if (wasNull()) null else value
    }

    private fun score(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun percent(value: Double, maxValue: Double): Int =
        if (maxValue <= 0.0) 0 else ((value / maxValue) * 100).roundToInt().coerceIn(0, 100)

    private fun rarityLabel(rarity: String): String =
        rarity.lowercase().replaceFirstChar { it.titlecase(Locale.US) }

    private fun shortLeagueName(name: String): String =
        name.removeSuffix(" лига").removeSuffix(" Лига")

    private fun initials(name: String): String =
        name.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.first().uppercaseChar().toString() }
            .ifBlank { "?" }

    private fun html(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")

    private fun cssUrl(value: String): String =
        value.replace("\\", "\\\\").replace("'", "\\'")

    private fun reportCss(): String = """
        :root{color-scheme:dark;--panel:rgba(255,255,255,.08);--line:rgba(255,255,255,.16);--text:#f8f3e8;--muted:#bdb5a5;--gold:#f0c15f;--green:#65d49a;--cyan:#6fd3e8;--ink:#191a16}*{box-sizing:border-box}body{margin:0;font-family:Inter,ui-sans-serif,system-ui,-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif;color:var(--text);background:radial-gradient(circle at 18% 0%,rgba(240,193,95,.22),transparent 28rem),radial-gradient(circle at 90% 16%,rgba(101,212,154,.18),transparent 24rem),linear-gradient(145deg,#11120f 0%,#181712 52%,#101412 100%);line-height:1.35}.page{width:min(1120px,calc(100% - 32px));margin:0 auto;padding:28px 0 44px}.poster{border:1px solid var(--line);border-radius:8px;overflow:hidden;background:linear-gradient(135deg,rgba(255,255,255,.10),rgba(255,255,255,.035)),linear-gradient(180deg,rgba(0,0,0,.06),rgba(0,0,0,.30));box-shadow:0 24px 80px rgba(0,0,0,.38)}.hero{position:relative;min-height:360px;padding:44px;display:grid;grid-template-columns:1.35fr .65fr;gap:28px;border-bottom:1px solid var(--line);background:linear-gradient(90deg,rgba(17,18,15,.96),rgba(17,18,15,.64))}.hero:after{content:"";position:absolute;inset:0;background:linear-gradient(180deg,transparent 0%,rgba(17,18,15,.72) 100%);pointer-events:none}.hero>*{position:relative;z-index:1}.eyebrow{margin:0 0 16px;color:var(--gold);font-weight:800;text-transform:uppercase;font-size:14px}h1{margin:0;max-width:720px;font-size:58px;line-height:.98;letter-spacing:0}.subtitle{margin:18px 0 0;max-width:680px;color:#ded7c9;font-size:20px}.hero-note{margin-top:26px;color:var(--muted);font-size:14px}.hero-side{display:grid;gap:12px;align-content:end}.stat-tile,.fun-card,.series-card,.panel,.record-card,.team-showcase{border:1px solid var(--line);border-radius:8px;background:var(--panel)}.stat-tile{padding:18px;background:rgba(0,0,0,.30);backdrop-filter:blur(10px)}.stat-tile span{display:block;color:var(--muted);font-size:13px}.stat-tile strong{display:block;margin-top:4px;font-size:34px;line-height:1;color:var(--gold)}.stat-tile small{display:block;margin-top:8px;color:#ddd4c3}.summary-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:var(--line);border-bottom:1px solid var(--line)}.summary-cell{background:rgba(17,18,15,.88);padding:20px 24px}.summary-cell b{display:block;font-size:34px;line-height:1}.summary-cell span{display:block;margin-top:8px;color:var(--muted);font-size:13px}.section{padding:32px 44px}.section-title{display:flex;justify-content:space-between;align-items:end;gap:18px;margin-bottom:18px}h2{margin:0;font-size:28px;letter-spacing:0}.section-title span,.panel__head span,.muted{color:var(--muted);font-size:14px}.two-col{display:grid;grid-template-columns:1fr 1fr;gap:18px}.panel{overflow:hidden}.panel__head{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:16px 18px;border-bottom:1px solid var(--line);background:rgba(255,255,255,.055)}.panel__head b{font-size:18px}.leader-row,.card-row,.team-row,.player-row{display:grid;align-items:center;gap:12px;padding:12px 16px;border-top:1px solid rgba(255,255,255,.09)}.leader-row:first-child,.card-row:first-child,.team-row:first-child,.player-row:first-child{border-top:0}.leader-row{grid-template-columns:36px 1fr 82px}.leader-row--top{background:linear-gradient(90deg,rgba(240,193,95,.14),transparent)}.rank{width:34px;height:34px;display:grid;place-items:center;border-radius:50%;background:rgba(255,255,255,.12);font-weight:900}.rank--small{width:30px;height:30px;font-size:13px}.leader-row--1 .rank{background:var(--gold);color:var(--ink)}.leader-row--2 .rank{background:#d9dde0;color:var(--ink)}.leader-row--3 .rank{background:#c9915b;color:var(--ink)}.leader-name{font-weight:800;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.leader-meta{text-align:right}.leader-meta strong{display:block;font-size:18px;color:var(--gold)}.leader-meta span{display:block;color:var(--muted);font-size:12px}.meter{height:7px;margin-top:8px;background:rgba(255,255,255,.10);border-radius:999px;overflow:hidden}.meter span{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,var(--gold),var(--green))}.meter--thin{height:5px;margin:6px 0 4px}.highlight-grid{display:grid;grid-template-columns:.92fr 1.08fr;gap:18px}.record-card{min-height:390px;padding:22px;background:linear-gradient(145deg,rgba(240,193,95,.18),rgba(255,255,255,.06))}.record-photo-wrap{display:grid;grid-template-columns:112px 1fr;gap:16px;align-items:center}.record-photo{width:112px;height:148px;object-fit:cover;border-radius:8px;border:2px solid rgba(240,193,95,.72)}.avatar-fallback{display:grid;place-items:center;background:rgba(255,255,255,.13);color:var(--gold);font-weight:900;border-radius:8px}.record-title{font-size:30px;font-weight:900;line-height:1.05}.record-title span{display:block;margin-top:6px;color:var(--gold);font-size:48px}.chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:10px}.chips span{border:1px solid rgba(240,193,95,.35);color:#ffe5aa;background:rgba(240,193,95,.10);border-radius:999px;padding:4px 8px;font-size:12px}.team-showcase{padding:22px;background:rgba(255,255,255,.07)}.team-showcase__top{display:flex;justify-content:space-between;gap:18px;margin-bottom:18px}.team-score{text-align:right;color:var(--gold);font-weight:900;font-size:44px;line-height:1}.team-score span{display:block;color:var(--muted);font-size:12px;font-weight:700;margin-top:6px}.team-cards{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}.team-card-mini{position:relative;min-height:252px;border:1px solid var(--line);border-radius:8px;overflow:hidden;background:rgba(0,0,0,.18)}.team-card-mini__photo{width:100%;height:176px;object-fit:cover;display:block}.team-card-mini__name{padding:10px 10px 0;font-weight:900;min-height:46px}.team-card-mini__score{position:absolute;top:10px;right:10px;padding:5px 8px;border-radius:999px;color:var(--ink);background:var(--gold);font-weight:900}.team-card-mini__rarity{padding:0 10px 10px;color:var(--muted);font-size:12px;text-transform:uppercase}.rarity-border--common{border-color:rgba(220,220,220,.32)}.rarity-border--rare{border-color:rgba(111,211,232,.70)}.rarity-border--epic{border-color:rgba(101,212,154,.76)}.rarity-border--legendary{border-color:rgba(240,193,95,.92)}.card-row{grid-template-columns:30px 54px 1fr 72px}.card-row__photo,.player-row__photo{width:54px;height:66px;border-radius:8px;object-fit:cover;border:1px solid var(--line)}.card-row__title{font-weight:900}.rarity{margin-left:6px;padding:2px 6px;border-radius:999px;color:var(--ink);font-size:11px;font-weight:900;text-transform:uppercase}.rarity--common{background:#c9c9c9}.rarity--rare{background:var(--cyan)}.rarity--epic{background:var(--green)}.rarity--legendary{background:var(--gold)}.score-pill{justify-self:end;min-width:62px;padding:8px 9px;border-radius:999px;text-align:center;background:rgba(101,212,154,.14);color:var(--green);font-weight:900}.fun-grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:18px}.fun-card{min-height:134px;padding:16px}.fun-card span{color:var(--muted);font-size:12px}.fun-card b{display:block;margin-top:8px;font-size:24px;line-height:1.06}.fun-card strong{display:block;color:var(--gold);margin-top:10px;font-size:24px}.player-row{grid-template-columns:54px 1fr}.player-row__top{display:flex;justify-content:space-between;gap:12px}.player-row__top strong{color:var(--gold)}.player-row small{color:var(--muted)}.series-strip{display:grid;grid-template-columns:repeat(5,1fr);gap:10px}.series-card{min-height:112px;padding:12px}.series-kicker{color:var(--muted);font-size:11px}.series-card h3{margin:7px 0 10px;font-size:16px}.series-grid{display:grid;gap:4px;color:var(--muted);font-size:12px}.series-grid b{color:var(--text)}.team-row{grid-template-columns:30px 1fr 70px}.team-row__main b,.team-row__main span{display:block}.team-row__main span{color:var(--muted);font-size:13px;margin-top:2px}.team-row strong{justify-self:end;color:var(--gold)}.empty-state{padding:18px;color:var(--muted)}.footer{padding:18px 44px 28px;color:var(--muted);font-size:12px;border-top:1px solid var(--line)}.deck-toolbar{position:sticky;top:12px;z-index:10;display:flex;flex-wrap:wrap;gap:8px;align-items:center;margin-bottom:14px;padding:10px;border:1px solid var(--line);border-radius:8px;background:rgba(17,18,15,.90);backdrop-filter:blur(14px);box-shadow:0 16px 44px rgba(0,0,0,.28)}.deck-toolbar b{margin-right:6px;font-size:13px;color:var(--muted)}.deck-tab,.screen-mode-button{appearance:none;border:1px solid rgba(240,193,95,.34);border-radius:8px;background:rgba(240,193,95,.10);color:var(--text);padding:9px 12px;font:inherit;font-size:13px;font-weight:800;cursor:pointer}.deck-tab:hover,.screen-mode-button:hover{background:rgba(240,193,95,.18)}.deck-tab.is-active{background:var(--gold);color:var(--ink);border-color:var(--gold)}.screen-mode-button{margin-left:auto;border-color:rgba(101,212,154,.38);background:rgba(101,212,154,.12)}.slide-panel{display:none}.slide-panel.is-active{display:block}body.screen-mode .deck-toolbar{display:none}body.screen-mode .page{width:min(1120px,100%);padding-top:0;padding-bottom:0}body.screen-mode .poster{border-radius:0;border-left:0;border-right:0}@media(max-width:840px){.page{width:min(100% - 20px,1120px);padding-top:10px}.hero,.two-col,.highlight-grid,.series-strip{grid-template-columns:1fr}.hero{padding:28px 22px}h1{font-size:40px}.summary-grid,.fun-grid{grid-template-columns:repeat(2,1fr)}.section{padding:24px 18px}.team-cards{grid-template-columns:1fr}.team-card-mini__photo{height:210px}.footer{padding:18px}}
    """.trimIndent()

    private fun reportScript(): String = """
        const tabs=[...document.querySelectorAll('.deck-tab')];
        const slides=[...document.querySelectorAll('.slide-panel')];
        const screenModeButton=document.querySelector('.screen-mode-button');
        function activateSlide(id){slides.forEach(s=>s.classList.toggle('is-active',s.id===id));tabs.forEach(t=>t.classList.toggle('is-active',t.dataset.slideTarget===id));const url=new URL(window.location.href);url.hash=id;history.replaceState(null,'',url);window.scrollTo({top:0,behavior:'instant'});}
        tabs.forEach(t=>t.addEventListener('click',()=>activateSlide(t.dataset.slideTarget)));
        screenModeButton.addEventListener('click',()=>{document.body.classList.toggle('screen-mode');screenModeButton.textContent=document.body.classList.contains('screen-mode')?'Показать вкладки':'Режим скрина';window.scrollTo({top:0,behavior:'instant'});});
        const initialHash=window.location.hash.replace('#','');
        if(initialHash&&document.getElementById(initialHash)?.classList.contains('slide-panel'))activateSlide(initialHash);
    """.trimIndent()

    private data class ReportTournament(
        val id: Long,
        val name: String,
        val status: String,
        val kind: String,
    )

    private data class ReportSeries(
        val id: Long,
        val name: String,
        val status: String,
        val startsAt: Instant,
        val gamesCount: Int,
        val managersCount: Int,
        val scoredTeamCount: Int,
    )

    private data class ReportSummary(
        val seriesCount: Int,
        val gamesCount: Int,
        val managerCount: Int,
        val scoredTeamCount: Int,
        val averageTeamScore: Double?,
        val bestRarity: RarityAverage?,
    )

    private data class LeagueLeaders(
        val code: String,
        val name: String,
        val participantCount: Int,
        val rows: List<LeaderRow>,
    )

    private data class LeaderRow(
        val leagueCode: String,
        val leagueName: String,
        val participantCount: Int,
        val rank: Int,
        val managerName: String,
        val totalScore: Double,
        val teamCount: Int,
    )

    private data class BestTeam(
        val id: Long,
        val managerName: String,
        val totalScore: Double,
        val seriesName: String,
        val leagueName: String,
        val cards: List<TeamCard>,
    )

    private data class TeamCard(
        val playerName: String,
        val photoUrl: String?,
        val rarity: String,
        val score: Double?,
    )

    private data class TopTeamRow(
        val rank: Int,
        val managerName: String,
        val totalScore: Double,
        val seriesName: String,
        val leagueName: String,
    )

    private data class TopCard(
        val rank: Int,
        val templateId: Long,
        val fantasyPlayerId: Long,
        val playerName: String,
        val photoUrl: String?,
        val rarity: String,
        val score: Double,
        val managerName: String,
        val seriesName: String,
        val leagueName: String,
        val perks: List<String>,
    )

    private data class PlayerStat(
        val fantasyPlayerId: Long,
        val playerName: String,
        val photoUrl: String?,
        val picks: Int,
        val managers: Int,
        val averageScore: Double,
        val maxScore: Double,
    )

    private data class RarityAverage(
        val rarity: String,
        val averageScore: Double,
    )

    private enum class PlayerStatsMode { POPULAR, EFFECTIVE, EFFECTIVE_FALLBACK }

    private enum class PlayerPanelMode { POPULAR, EFFECTIVE }
}
