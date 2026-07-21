package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.dto.periodicrating.CreatePeriodicRatingPeriodRequest
import io.github.mralex1810.fantasy.dto.periodicrating.FinalizePeriodicRatingRequest
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingContributionDto
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingEntryDto
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingLeaderboardDto
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingMeDto
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingPeriodDto
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingPreviewDto
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingSeriesPreviewDto
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingUserDto
import io.github.mralex1810.fantasy.dto.periodicrating.UpdatePeriodicRatingSeriesRequest
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.event.AchievementProgressEvent
import io.github.mralex1810.fantasy.event.AchievementProgressEventType
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant

@Service
class PeriodicRatingService(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val eventPublisher: ApplicationEventPublisher,
    private val cardPackService: CardPackService,
) {
    companion object {
        const val TIMEZONE = "Europe/Moscow"
        const val LEAGUE = "MAIN"
        private val MUTABLE_STATUSES = setOf("DRAFT", "OPEN", "SETTLING")
        private val VISIBLE_STATUSES = setOf("OPEN", "SETTLING", "FINALIZED")
    }

    @Transactional
    fun listPeriods(): List<PeriodicRatingPeriodDto> {
        settleExpiredOpenPeriods()
        return jdbc.query(
            "SELECT * FROM periodic_rating_period ORDER BY starts_at DESC, id DESC",
        ) { rs, _ -> mapPeriod(rs) }
    }

    @Transactional
    fun listVisiblePeriods(): List<PeriodicRatingPeriodDto> {
        settleExpiredOpenPeriods()
        return jdbc.query(
            """
            SELECT * FROM periodic_rating_period
            WHERE status IN ('OPEN','SETTLING','FINALIZED')
            ORDER BY starts_at DESC, id DESC
            """.trimIndent(),
        ) { rs, _ -> mapPeriod(rs) }.map { period ->
            val calculation = calculate(period)
            period.copy(
                seriesCount = calculation.series.count { it.included && it.finalized },
                participantCount = calculation.entries.size,
            )
        }
    }

    @Transactional
    fun createPeriod(request: CreatePeriodicRatingPeriodRequest): PeriodicRatingPeriodDto {
        val code = request.code.trim()
        val title = request.title.trim()
        if (code.isBlank() || title.isBlank()) badRequest("code and title must not be blank")
        if (!request.startsAt.isBefore(request.endsAt)) badRequest("startsAt must be before endsAt")
        jdbc.execute("SELECT pg_advisory_xact_lock(5263942)")
        val overlap = jdbc.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1 FROM periodic_rating_period
                WHERE status <> 'CANCELLED' AND starts_at < ? AND ends_at > ?
            )
            """.trimIndent(),
            Boolean::class.java,
            Timestamp.from(request.endsAt),
            Timestamp.from(request.startsAt),
        ) ?: false
        if (overlap) throw ResponseStatusException(HttpStatus.CONFLICT, "Period overlaps an existing active period")
        val id = try {
            jdbc.queryForObject(
                """
                INSERT INTO periodic_rating_period(code, title, starts_at, ends_at, timezone, league_code, rules_snapshot)
                VALUES (?, ?, ?, ?, ?, ?, jsonb_build_object('timezone', ?, 'league', ?)) RETURNING id
                """.trimIndent(),
                Long::class.java,
                code,
                title,
                Timestamp.from(request.startsAt),
                Timestamp.from(request.endsAt),
                TIMEZONE,
                LEAGUE,
                TIMEZONE,
                LEAGUE,
            )!!
        } catch (_: DuplicateKeyException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Period code already exists")
        }
        audit(id, "PERIOD_CREATED", "{}")
        return getPeriod(id)
    }

    @Transactional
    fun openPeriod(id: Long): PeriodicRatingPeriodDto {
        val updated = jdbc.update(
            "UPDATE periodic_rating_period SET status='OPEN', updated_at=now() WHERE id=? AND status='DRAFT'",
            id,
        )
        if (updated == 0) throw ResponseStatusException(HttpStatus.CONFLICT, "Only a DRAFT period can be opened")
        audit(id, "PERIOD_OPENED", "{}")
        return getPeriod(id)
    }

    @Transactional
    fun updateSeries(id: Long, seriesId: Long, request: UpdatePeriodicRatingSeriesRequest): PeriodicRatingPreviewDto {
        val period = getPeriod(id)
        if (period.status !in MUTABLE_STATUSES) throw ResponseStatusException(HttpStatus.CONFLICT, "Period is immutable")
        if (!request.included && request.reason.isNullOrBlank()) badRequest("A non-blank public reason is required for exclusion")
        val seriesExists = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM series WHERE id=?)", Boolean::class.java, seriesId) ?: false
        if (!seriesExists) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series not found")
        jdbc.update(
            """
            INSERT INTO periodic_rating_series(period_id, series_id, included, public_reason)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (period_id, series_id) DO UPDATE
            SET included=EXCLUDED.included, public_reason=EXCLUDED.public_reason, updated_at=now()
            """.trimIndent(),
            id,
            seriesId,
            request.included,
            request.reason?.trim()?.takeIf { it.isNotBlank() },
        )
        audit(id, "SERIES_OVERRIDE_UPDATED", "{\"seriesId\":$seriesId,\"included\":${request.included}}")
        return preview(id)
    }

    @Transactional
    fun preview(id: Long): PeriodicRatingPreviewDto {
        val period = getPeriod(id)
        val calculation = calculate(period)
        return PeriodicRatingPreviewDto(
            period = period.copy(
                seriesCount = calculation.series.count { it.included && it.finalized },
                participantCount = calculation.entries.size,
            ),
            sourceChecksum = calculation.checksum,
            series = calculation.series,
            blockers = calculation.series.filter { it.blocker },
            leaderboard = calculation.entries,
            rewardLiability = linkedMapOf(
                "champion" to calculation.entries.count { it.rank == 1 },
                "silver" to calculation.entries.count { it.rank == 2 },
                "bronze" to calculation.entries.count { it.rank == 3 },
                "finalist" to calculation.entries.count { it.rank in 4..10 },
                "total" to calculation.entries.count { it.rank <= 10 },
            ),
        )
    }

    @Transactional
    fun finalize(id: Long, request: FinalizePeriodicRatingRequest, actorId: String): PeriodicRatingPreviewDto {
        val finalizeReason = request.reason.trim()
        if (finalizeReason.isEmpty()) badRequest("Finalize reason must not be blank")
        val period = jdbc.query(
            "SELECT * FROM periodic_rating_period WHERE id=? FOR UPDATE",
            { rs, _ -> mapPeriod(rs) },
            id,
        ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Period not found")
        if (period.status == "FINALIZED") return preview(id)
        if (period.status != "SETTLING") {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Only a SETTLING period can be finalized")
        }

        val calculation = calculate(period)
        if (calculation.checksum != request.sourceChecksum) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Preview is stale; current sourceChecksum=${calculation.checksum}",
            )
        }
        val blockers = calculation.series.filter { it.blocker }
        if (blockers.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot finalize while series blockers exist: ${blockers.joinToString { it.seriesId.toString() }}",
            )
        }
        val rewarded = calculation.entries.filter { PeriodicRatingRules.isRewarded(it.rank) }
        val policyInputs = loadPolicyInputs(id, rewarded.isNotEmpty())

        calculation.series.forEach { series ->
            jdbc.update(
                """
                INSERT INTO periodic_rating_series(
                    period_id,series_id,included,public_reason,effective_at,score_snapshot_checksum
                ) VALUES (?,?,?,?,?,?)
                ON CONFLICT(period_id,series_id) DO UPDATE SET
                    included=EXCLUDED.included,public_reason=EXCLUDED.public_reason,
                    effective_at=EXCLUDED.effective_at,score_snapshot_checksum=EXCLUDED.score_snapshot_checksum,
                    updated_at=now()
                """.trimIndent(),
                id, series.seriesId, series.included, series.reason,
                series.effectiveAt?.let(Timestamp::from), calculation.checksum,
            )
        }

        val internalUserIds = calculation.entries.associate { entry ->
            entry.user.telegramId to jdbc.queryForObject(
                "SELECT id FROM telegram_user WHERE telegram_id=?",
                Long::class.java,
                entry.user.telegramId,
            )!!
        }
        val entryIds = linkedMapOf<Long, Long>()
        calculation.entries.forEach { entry ->
            val internalUserId = internalUserIds.getValue(entry.user.telegramId)
            val entryId = jdbc.queryForObject(
                """
                INSERT INTO periodic_rating_entry(
                    period_id,telegram_user_id,rank,total_score,series_count,average_score,best_series_score
                ) VALUES (?,?,?,?,?,?,?) RETURNING id
                """.trimIndent(),
                Long::class.java,
                id, internalUserId, entry.rank, entry.totalScore, entry.seriesCount,
                entry.averageScore, entry.bestSeriesScore,
            )!!
            entryIds[entry.user.telegramId] = entryId
            calculation.contributions[entry.user.telegramId].orEmpty().forEach { contribution ->
                jdbc.update(
                    """
                    INSERT INTO periodic_rating_contribution(
                        entry_id,series_id,score,series_rank,participants_count
                    ) VALUES (?,?,?,?,?)
                    """.trimIndent(),
                    entryId, contribution.seriesId, contribution.score,
                    contribution.seriesRank, contribution.participantsCount,
                )
            }
        }

        var bundledRewardIndex = 0
        rewarded.sortedWith(compareBy<PeriodicRatingEntryDto> { it.rank }.thenBy { it.user.telegramId })
            .forEachIndexed { index, entry ->
                val policy = rewardPolicy(entry.rank, policyInputs, bundledRewardIndex)
                if (entry.rank in 4..5) bundledRewardIndex += 1
                val serial = "${period.code}-${(index + 1).toString().padStart(3, '0')}"
                val fantiki = PeriodicRatingRules.rewardFantiki(entry.rank)
                val userId = internalUserIds.getValue(entry.user.telegramId)
                val rewardId = jdbc.queryForObject(
                    """
                    INSERT INTO periodic_rating_reward(
                        period_id,entry_id,telegram_user_id,rank,status,policy_snapshot,serial,claim_deadline,
                        fantiki_amount,fantiki_granted_at
                    ) VALUES (?, ?, ?, ?, 'AVAILABLE', ?::jsonb, ?, now() + interval '30 days', ?,
                        CASE WHEN ? > 0 THEN now() ELSE NULL END)
                    RETURNING id
                    """.trimIndent(),
                    Long::class.java,
                    id, entryIds.getValue(entry.user.telegramId), userId, entry.rank,
                    objectMapper.writeValueAsString(policy), serial, fantiki, fantiki,
                )!!
                if (fantiki > 0) {
                    jdbc.update("UPDATE telegram_user SET fantiki=fantiki+? WHERE id=?", fantiki, userId)
                    jdbc.update(
                        "INSERT INTO fantiki_transaction(telegram_user_id,amount,reason) VALUES (?,?,'PERIODIC_RATING_REWARD')",
                        userId, fantiki,
                    )
                }
                audit(id, "REWARD_CREATED", objectMapper.writeValueAsString(mapOf("rewardId" to rewardId, "serial" to serial)))
            }

        jdbc.update(
            """
            UPDATE periodic_rating_period SET status='FINALIZED', finalized_at=now(), source_checksum=?,
                rules_snapshot=?::jsonb, updated_at=now() WHERE id=?
            """.trimIndent(),
            calculation.checksum,
            objectMapper.writeValueAsString(mapOf("version" to 1, "rounding" to "HALF_UP_2", "league" to LEAGUE)),
            id,
        )
        audit(id, "PERIOD_FINALIZED", objectMapper.writeValueAsString(mapOf("reason" to finalizeReason)), actorId)
        eventPublisher.publishEvent(
            AchievementProgressEvent(
                type = AchievementProgressEventType.PERIODIC_RATING_FINALIZED,
                internalTelegramUserIds = internalUserIds.values.toSet(),
            ),
        )
        return preview(id)
    }

    @Transactional
    fun current(): PeriodicRatingPeriodDto {
        settleExpiredOpenPeriods()
        val rows = jdbc.query(
            """
            SELECT * FROM periodic_rating_period
            WHERE status IN ('OPEN','SETTLING','FINALIZED')
            ORDER BY CASE status WHEN 'OPEN' THEN 0 WHEN 'SETTLING' THEN 1 ELSE 2 END, starts_at DESC, id DESC
            LIMIT 1
            """.trimIndent(),
        ) { rs, _ -> mapPeriod(rs) }
        val period = rows.firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No visible rating period")
        val calc = calculate(period)
        return period.copy(
            seriesCount = calc.series.count { it.included && it.finalized },
            participantCount = calc.entries.size,
        )
    }

    @Transactional
    fun leaderboard(id: Long, currentUser: TelegramUser): PeriodicRatingLeaderboardDto {
        settleExpiredOpenPeriods()
        val period = requireVisible(id)
        val calc = calculate(period)
        val entries = calc.entries.take(100)
        val current = calc.entries.find { it.user.telegramId == currentUser.telegramId }
        return PeriodicRatingLeaderboardDto(
            period = period.copy(
                seriesCount = calc.series.count { it.included && it.finalized },
                participantCount = calc.entries.size,
            ),
            provisional = period.status != "FINALIZED",
            entries = entries,
            currentUser = current,
            blockersCount = calc.series.count { it.blocker },
        )
    }

    @Transactional
    fun me(id: Long, currentUser: TelegramUser): PeriodicRatingMeDto {
        val period = requireVisible(id)
        val calc = calculate(period)
        val entry = calc.entries.find { it.user.telegramId == currentUser.telegramId }
        return PeriodicRatingMeDto(
            period = period.copy(
                seriesCount = calc.series.count { it.included && it.finalized },
                participantCount = calc.entries.size,
            ),
            entry = entry,
            contributions = calc.contributions[currentUser.telegramId].orEmpty(),
        )
    }

    private fun calculate(period: PeriodicRatingPeriodDto): Calculation {
        if (period.status == "FINALIZED") return calculateSnapshot(period)
        val candidates = candidateSeries(period)
        val includedIds = candidates.filter { it.included && it.finalized && !it.blocker }.map { it.seriesId }
        if (includedIds.isEmpty()) return Calculation(candidates, emptyList(), emptyMap(), checksum(candidates, emptyList()))
        val marks = includedIds.joinToString(",") { "?" }
        val raw = jdbc.query(
            """
            SELECT ft.telegram_user_id, u.telegram_id, u.username, u.first_name, u.display_name,
                   ft.series_id, s.name series_name, t.id tournament_id, t.name tournament_name, ft.total_score
            FROM fantasy_team ft
            JOIN telegram_user u ON u.id=ft.telegram_user_id
            JOIN series s ON s.id=ft.series_id
            JOIN tournament t ON t.id=s.tournament_id
            JOIN series_league sl ON sl.id=ft.series_league_id
            JOIN league l ON l.id=sl.league_id
            WHERE ft.series_id IN ($marks) AND l.code='MAIN' AND ft.total_score IS NOT NULL
            """.trimIndent(),
            { rs, _ -> RawScore(
                userId = rs.getLong("telegram_user_id"),
                user = PeriodicRatingUserDto(
                    rs.getLong("telegram_id"), rs.getString("username"), rs.getString("first_name"),
                    rs.getString("display_name"), null,
                ),
                seriesId = rs.getLong("series_id"),
                seriesName = rs.getString("series_name"),
                tournamentId = rs.getLong("tournament_id"),
                tournamentName = rs.getString("tournament_name"),
                score = BigDecimal.valueOf(rs.getDouble("total_score")),
            ) },
            *includedIds.toTypedArray(),
        )
        val seriesStats = raw.groupBy { it.seriesId }.mapValues { (_, rows) ->
            val sorted = rows.sortedByDescending { it.score.setScale(2, RoundingMode.HALF_UP) }
            val ranks = PeriodicRatingRules.competitionRanks(sorted.map { it.score.setScale(2, RoundingMode.HALF_UP) })
            sorted.mapIndexed { index, row -> row.user.telegramId to ranks[index] }.toMap() to rows.size
        }
        val grouped = raw.groupBy { it.user.telegramId }
        val preEntries = grouped.values.map { rows ->
            val total = rows.fold(BigDecimal.ZERO) { acc, row -> acc + row.score }.roundedScore()
            PeriodicRatingEntryDto(
                user = rows.first().user,
                rank = 0,
                totalScore = total,
                seriesCount = rows.size,
                averageScore = total.divide(BigDecimal(rows.size), 2, RoundingMode.HALF_UP),
                bestSeriesScore = rows.maxOf { it.score }.roundedScore(),
            )
        }.sortedWith(compareByDescending<PeriodicRatingEntryDto> { it.totalScore }.thenBy { it.user.telegramId })
        val ranks = PeriodicRatingRules.competitionRanks(preEntries.map { it.totalScore })
        val entries = preEntries.mapIndexed { index, entry -> entry.copy(rank = ranks[index]) }
        val contributions = grouped.mapValues { (_, rows) -> rows.map { row ->
            val stats = seriesStats.getValue(row.seriesId)
            PeriodicRatingContributionDto(
                row.seriesId, row.seriesName, row.tournamentId, row.tournamentName, row.score.roundedScore(),
                stats.first.getValue(row.user.telegramId), stats.second,
            )
        }.sortedByDescending { it.score } }
        return Calculation(candidates, entries, contributions, checksum(candidates, raw))
    }

    private fun calculateSnapshot(period: PeriodicRatingPeriodDto): Calculation {
        val entries = jdbc.query(
            """
            SELECT e.*, u.telegram_id, u.username, u.first_name, u.display_name
            FROM periodic_rating_entry e JOIN telegram_user u ON u.id=e.telegram_user_id
            WHERE e.period_id=? ORDER BY e.rank, e.telegram_user_id
            """.trimIndent(),
            { rs, _ -> PeriodicRatingEntryDto(
                PeriodicRatingUserDto(rs.getLong("telegram_id"), rs.getString("username"), rs.getString("first_name"), rs.getString("display_name")),
                rs.getInt("rank"), rs.getBigDecimal("total_score"), rs.getInt("series_count"),
                rs.getBigDecimal("average_score"), rs.getBigDecimal("best_series_score"),
            ) },
            period.id,
        )
        val series = jdbc.query(
            """
            SELECT prs.series_id, s.name series_name, t.id tournament_id, t.name tournament_name,
                   prs.effective_at, prs.included, prs.public_reason, s.finalized
            FROM periodic_rating_series prs
            JOIN series s ON s.id=prs.series_id JOIN tournament t ON t.id=s.tournament_id
            WHERE prs.period_id=? ORDER BY prs.effective_at NULLS LAST, prs.series_id
            """.trimIndent(),
            { rs, _ -> PeriodicRatingSeriesPreviewDto(
                rs.getLong("series_id"), rs.getString("series_name"), rs.getLong("tournament_id"),
                rs.getString("tournament_name"), rs.getTimestamp("effective_at")?.toInstant(),
                rs.getBoolean("included"), rs.getString("public_reason"), rs.getBoolean("finalized"), false,
            ) },
            period.id,
        )
        val contributionRows = jdbc.query(
            """
            SELECT u.telegram_id, c.series_id, s.name series_name, t.id tournament_id, t.name tournament_name,
                   c.score, c.series_rank, c.participants_count
            FROM periodic_rating_contribution c
            JOIN periodic_rating_entry e ON e.id=c.entry_id
            JOIN telegram_user u ON u.id=e.telegram_user_id
            JOIN series s ON s.id=c.series_id JOIN tournament t ON t.id=s.tournament_id
            WHERE e.period_id=? ORDER BY u.telegram_id, c.score DESC
            """.trimIndent(),
            { rs, _ -> rs.getLong("telegram_id") to PeriodicRatingContributionDto(
                rs.getLong("series_id"), rs.getString("series_name"), rs.getLong("tournament_id"),
                rs.getString("tournament_name"), rs.getBigDecimal("score"), rs.getInt("series_rank"),
                rs.getInt("participants_count"),
            ) },
            period.id,
        )
        return Calculation(series, entries, contributionRows.groupBy({ it.first }, { it.second }), "snapshot")
    }

    private fun candidateSeries(period: PeriodicRatingPeriodDto): List<PeriodicRatingSeriesPreviewDto> = jdbc.query(
        """
        SELECT s.id series_id, s.name series_name, t.id tournament_id, t.name tournament_name,
               MAX(sg.played_at) effective_at, s.finalized,
               prs.included override_included, prs.public_reason
        FROM series s
        JOIN tournament t ON t.id=s.tournament_id
        LEFT JOIN series_game sg ON sg.series_id=s.id
        LEFT JOIN periodic_rating_series prs ON prs.period_id=? AND prs.series_id=s.id
        GROUP BY s.id, s.name, s.starts_at, t.id, t.name, s.finalized, prs.included, prs.public_reason
        HAVING (MAX(sg.played_at) >= ? AND MAX(sg.played_at) < ?)
            OR (MAX(sg.played_at) IS NULL AND s.starts_at >= ? AND s.starts_at < ?)
        ORDER BY MAX(sg.played_at) NULLS LAST, s.id
        """.trimIndent(),
        { rs, _ ->
            val effectiveAt = rs.getTimestamp("effective_at")?.toInstant()
            val finalized = rs.getBoolean("finalized")
            val override = rs.getObject("override_included") as? Boolean
            val blocker = PeriodicRatingRules.isBlocker(finalized, effectiveAt, override != false)
            PeriodicRatingSeriesPreviewDto(
                rs.getLong("series_id"), rs.getString("series_name"), rs.getLong("tournament_id"),
                rs.getString("tournament_name"), effectiveAt, override != false, rs.getString("public_reason"), finalized, blocker,
            )
        },
        period.id,
        Timestamp.from(period.startsAt), Timestamp.from(period.endsAt),
        Timestamp.from(period.startsAt), Timestamp.from(period.endsAt),
    )

    private fun getPeriod(id: Long): PeriodicRatingPeriodDto = jdbc.query(
        "SELECT * FROM periodic_rating_period WHERE id=?",
        { rs, _ -> mapPeriod(rs) },
        id,
    ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Period not found")

    private fun requireVisible(id: Long): PeriodicRatingPeriodDto = getPeriod(id).also {
        if (it.status !in VISIBLE_STATUSES) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Period not found")
    }

    private fun mapPeriod(rs: ResultSet) = PeriodicRatingPeriodDto(
        id = rs.getLong("id"), code = rs.getString("code"), title = rs.getString("title"),
        startsAt = rs.getTimestamp("starts_at").toInstant(), endsAt = rs.getTimestamp("ends_at").toInstant(),
        timezone = rs.getString("timezone"), league = rs.getString("league_code"), status = rs.getString("status"),
        provisional = rs.getString("status") != "FINALIZED", seriesCount = 0, participantCount = 0,
        finalizedAt = rs.getTimestamp("finalized_at")?.toInstant(),
    )

    private fun settleExpiredOpenPeriods() {
        jdbc.update("UPDATE periodic_rating_period SET status='SETTLING', updated_at=now() WHERE status='OPEN' AND ends_at <= now()")
    }

    private fun audit(periodId: Long, event: String, payload: String, actorId: String? = null) {
        jdbc.update(
            "INSERT INTO periodic_rating_audit_event(period_id,actor_type,actor_id,event_type,payload) VALUES (?,'ADMIN',?,?,?::jsonb)",
            periodId, actorId, event, payload,
        )
    }

    private fun loadPolicyInputs(periodId: Long, required: Boolean): PolicyInputs {
        val perks = jdbc.queryForList(
            "SELECT id FROM perk WHERE can_appear_on_random_cards=true ORDER BY id",
            String::class.java,
        )
        val players = cardPackService.buildActivePackFantasyPlayerPool(Rarity.RARE)
            .mapNotNull { it.id }
            .distinct()
            .sortedBy { stablePolicyOrder(periodId, it) }
        if (required && perks.size < 3) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "At least 3 eligible perks are required to freeze reward policy")
        }
        if (required && players.size < 3) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "At least 3 active-pack RARE players are required to freeze reward policy")
        }
        return PolicyInputs(perks, players)
    }

    private fun rewardPolicy(rank: Int, inputs: PolicyInputs, bundledRewardIndex: Int): Map<String, Any> {
        val tier = PeriodicRatingRules.rewardTier(rank)
        val rarity = PeriodicRatingRules.rewardRarity(rank)
        val skins = PeriodicRatingRules.rewardSkinCodes(rank)
        val base = linkedMapOf<String, Any>(
            "policyVersion" to 1,
            "rarity" to rarity,
            "editionTier" to tier,
            "skinCodes" to skins,
            "fulfillmentMode" to "AUTO_ISSUE",
        )
        when (rank) {
            1 -> base.putAll(mapOf(
                "playerSelectionMode" to "FREE_CHOICE",
                "perkSelectionMode" to "FREE_CHOICE",
                "perkSelectionCount" to 2,
                "perkPool" to inputs.perks,
            ))
            2 -> base.putAll(mapOf(
                "playerSelectionMode" to "FREE_CHOICE",
                "perkSelectionMode" to "FREE_CHOICE",
                "perkSelectionCount" to 1,
                "perkPool" to inputs.perks,
            ))
            3 -> base.putAll(mapOf(
                "playerSelectionMode" to "FREE_CHOICE",
                "perkSelectionMode" to "FIXED_PERK_OPTIONS",
                "perkSelectionCount" to 1,
                "perkPool" to inputs.perks.take(3),
            ))
            in 4..5 -> base.putAll(mapOf(
                "playerSelectionMode" to "BUNDLED_OPTIONS",
                "perkSelectionMode" to "BUNDLED_OPTIONS",
                "perkSelectionCount" to 1,
                "bundles" to circularTake(inputs.players, bundledRewardIndex * 3, 3).mapIndexed { index, playerId ->
                    val perkId = inputs.perks[(bundledRewardIndex * 3 + index) % inputs.perks.size]
                    mapOf("playerId" to playerId, "perkIds" to listOf(perkId))
                },
            ))
            else -> base.putAll(mapOf(
                "playerSelectionMode" to "FREE_CHOICE",
                "perkSelectionMode" to "NONE",
                "perkSelectionCount" to 0,
                "perkPool" to emptyList<String>(),
                "fantikiAmount" to 50,
            ))
        }
        return base
    }

    private fun stablePolicyOrder(periodId: Long, playerId: Long): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$periodId:$playerId".toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun checksum(series: List<PeriodicRatingSeriesPreviewDto>, scores: List<RawScore>): String {
        val source = buildString {
            series.sortedBy { it.seriesId }.forEach { append("S:${it.seriesId}:${it.effectiveAt}:${it.included}:${it.finalized}:${it.reason}\n") }
            scores.sortedWith(compareBy<RawScore> { it.seriesId }.thenBy { it.user.telegramId }).forEach {
                append("T:${it.seriesId}:${it.user.telegramId}:${it.score.toPlainString()}\n")
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(source.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun BigDecimal.roundedScore() = setScale(2, RoundingMode.HALF_UP)
    private fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private data class RawScore(
        val userId: Long,
        val user: PeriodicRatingUserDto,
        val seriesId: Long,
        val seriesName: String,
        val tournamentId: Long,
        val tournamentName: String,
        val score: BigDecimal,
    )

    private data class Calculation(
        val series: List<PeriodicRatingSeriesPreviewDto>,
        val entries: List<PeriodicRatingEntryDto>,
        val contributions: Map<Long, List<PeriodicRatingContributionDto>>,
        val checksum: String,
    )

    private data class PolicyInputs(val perks: List<String>, val players: List<Long>)
}

internal fun <T> circularTake(values: List<T>, offset: Int, count: Int): List<T> {
    require(values.isNotEmpty()) { "values must not be empty" }
    require(count >= 0) { "count must be non-negative" }
    return List(count) { index -> values[(offset + index) % values.size] }
}
