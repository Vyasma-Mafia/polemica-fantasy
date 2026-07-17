package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingRewardDraftRequest
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingRewardDto
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingRewardPlayerDto
import io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingRewardPlayerPageDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.sql.ResultSet
import java.sql.Timestamp
import kotlin.math.ceil

@Service
class PeriodicRatingRewardService(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private const val MAX_PAGE_SIZE = 50
        private val ACTIONABLE = setOf("AVAILABLE", "DRAFT", "CHANGES_REQUESTED", "OVERDUE")
    }

    @Transactional
    fun listForUser(user: TelegramUser): List<PeriodicRatingRewardDto> {
        markOverdue(user.id!!)
        return jdbc.query(
            rewardSelect("WHERE r.telegram_user_id=? ORDER BY r.created_at DESC, r.id DESC"),
            { rs, _ -> mapReward(rs) },
            user.id!!,
        )
    }

    @Transactional
    fun getForUser(id: Long, user: TelegramUser): PeriodicRatingRewardDto {
        markOverdue(user.id!!)
        return jdbc.query(
            rewardSelect("WHERE r.id=? AND r.telegram_user_id=?"),
            { rs, _ -> mapReward(rs) },
            id,
            user.id!!,
        ).firstOrNull() ?: notFound()
    }

    @Transactional
    fun searchPlayers(
        rewardId: Long,
        user: TelegramUser,
        query: String?,
        page: Int,
        requestedSize: Int,
    ): PeriodicRatingRewardPlayerPageDto {
        if (page < 0) badRequest("page must be non-negative")
        val size = requestedSize.coerceIn(1, MAX_PAGE_SIZE)
        val reward = getForUser(rewardId, user)
        if (reward.status !in ACTIONABLE) conflict("Reward is not editable")
        val allowedPlayerIds = bundledPlayerIds(reward.policy)
        val normalized = query?.trim().orEmpty()
        val text = "%${escapeLike(normalized.lowercase())}%"
        val numericText = "%${escapeLike(normalized)}%"
        val allowed = allowedPlayerIds.take(3) + List((3 - allowedPlayerIds.size).coerceAtLeast(0)) { -1L }
        val count = jdbc.queryForObject(
            """
            SELECT count(*) FROM fantasy_player fp
            WHERE (? = '' OR lower(fp.nickname) LIKE ? ESCAPE '\' OR fp.polemica_user_id::text LIKE ?
                  )
              AND (? = 0 OR fp.id IN (?,?,?))
            """.trimIndent(),
            Long::class.java,
            normalized,
            text,
            numericText,
            allowedPlayerIds.size,
            allowed[0], allowed[1], allowed[2],
        ) ?: 0L
        val content = jdbc.query(
            """
            SELECT fp.id,fp.polemica_user_id,fp.nickname,fp.photo_url FROM fantasy_player fp
            WHERE (? = '' OR lower(fp.nickname) LIKE ? ESCAPE '\' OR fp.polemica_user_id::text LIKE ?
                  )
              AND (? = 0 OR fp.id IN (?,?,?))
            ORDER BY lower(fp.nickname),fp.polemica_user_id,fp.id LIMIT ? OFFSET ?
            """.trimIndent(),
            { rs, _ -> PeriodicRatingRewardPlayerDto(
                rs.getLong("id"), rs.getLong("polemica_user_id"), rs.getString("nickname"), rs.getString("photo_url"),
            ) },
            normalized,
            text,
            numericText,
            allowedPlayerIds.size,
            allowed[0], allowed[1], allowed[2],
            size,
            page * size,
        )
        return PeriodicRatingRewardPlayerPageDto(content, page, size, count, ceil(count.toDouble() / size).toInt())
    }

    @Transactional
    fun saveDraft(id: Long, user: TelegramUser, request: PeriodicRatingRewardDraftRequest): PeriodicRatingRewardDto {
        val current = lockReward(id, user.id!!)
        requireVersion(current, request.version)
        if (current.status !in ACTIONABLE) conflict("Reward is not editable")
        val selection = selection(request)
        validateSelection(current.policy, selection)
        val updated = jdbc.update(
            """
            UPDATE periodic_rating_reward SET selection=?::jsonb,status='DRAFT',changes_requested_reason=NULL,
                version=version+1,updated_at=now() WHERE id=? AND version=?
            """.trimIndent(),
            objectMapper.writeValueAsString(selection), id, request.version,
        )
        if (updated != 1) stale(id)
        audit(current.periodId, id, "USER", user.telegramId.toString(), "REWARD_DRAFT_SAVED", "{}")
        return getForUser(id, user)
    }

    @Transactional
    fun submit(id: Long, user: TelegramUser, version: Int): PeriodicRatingRewardDto {
        val current = lockReward(id, user.id!!)
        if (current.status == "FULFILLED") return getForUser(id, user)
        requireVersion(current, version)
        if (current.status !in setOf("DRAFT", "CHANGES_REQUESTED", "OVERDUE")) {
            conflict("Only an editable draft can be submitted")
        }
        issueLocked(
            current = current,
            expectedStatus = current.status,
            expectedVersion = version,
            actorType = "USER",
            actorId = user.telegramId.toString(),
            event = "REWARD_CONFIRMED_AND_ISSUED",
        )
        return getForUser(id, user)
    }

    @Transactional(readOnly = true)
    fun listForAdmin(periodId: Long?, status: String?): List<PeriodicRatingRewardDto> {
        val conditions = mutableListOf<String>()
        val args = mutableListOf<Any>()
        if (periodId != null) {
            conditions += "r.period_id=?"
            args += periodId
        }
        if (status != null) {
            conditions += "r.status=?"
            args += status
        }
        val where = if (conditions.isEmpty()) "" else "WHERE ${conditions.joinToString(" AND ")}"
        return jdbc.query(
            rewardSelect("$where ORDER BY p.starts_at DESC,r.rank,r.id"),
            { rs, _ -> mapReward(rs) },
            *args.toTypedArray(),
        )
    }

    @Transactional
    fun requestChanges(id: Long, reason: String, version: Int, actorId: String): PeriodicRatingRewardDto {
        val trimmed = reason.trim()
        if (trimmed.isEmpty()) badRequest("reason must not be blank")
        val current = lockReward(id, null)
        requireVersion(current, version)
        if (current.status != "REVIEW_REQUIRED") conflict("Only REVIEW_REQUIRED reward can request changes")
        val updated = jdbc.update(
            """
            UPDATE periodic_rating_reward SET status='CHANGES_REQUESTED',changes_requested_reason=?,
                version=version+1,updated_at=now() WHERE id=? AND version=?
            """.trimIndent(),
            trimmed, id, version,
        )
        if (updated != 1) stale(id)
        audit(current.periodId, id, "ADMIN", actorId, "REWARD_CHANGES_REQUESTED", objectMapper.writeValueAsString(mapOf("reason" to trimmed)))
        return getForAdmin(id)
    }

    @Transactional
    fun approveAndIssue(id: Long, version: Int, actorId: String): PeriodicRatingRewardDto {
        val current = lockReward(id, null)
        if (current.status == "FULFILLED") return getForAdmin(id)
        requireVersion(current, version)
        if (current.status != "REVIEW_REQUIRED") conflict("Only REVIEW_REQUIRED reward can be issued")
        issueLocked(
            current = current,
            expectedStatus = "REVIEW_REQUIRED",
            expectedVersion = version,
            actorType = "ADMIN",
            actorId = actorId,
            event = "REWARD_ISSUED",
        )
        return getForAdmin(id)
    }

    private fun issueLocked(
        current: LockedReward,
        expectedStatus: String,
        expectedVersion: Int,
        actorType: String,
        actorId: String?,
        event: String,
    ) {
        validateSelection(current.policy, current.selection)

        val playerId = current.selection.path("playerId").asLong()
        val perkIds = current.selection.path("perkIds").map { it.asText() }.distinct().sorted()
        val skinCode = current.selection.path("skinCode").asText()
        val rarity = current.policy.path("rarity").asText()
        jdbc.execute("SELECT pg_advisory_xact_lock($playerId)")
        val templateId = findOrCreateTemplate(playerId, rarity, perkIds)
        val skinId = jdbc.queryForObject("SELECT id FROM card_skin WHERE code=?", Long::class.java, skinCode)
            ?: conflict("Reward skin is unavailable")
        val uses = jdbc.queryForObject("SELECT value::int FROM economy_config WHERE key=?", Int::class.java, "card.uses.$rarity")
            ?: conflict("Card uses config is missing")
        val userCardId = jdbc.queryForObject(
            """
            INSERT INTO user_card(
                telegram_user_id,card_template_id,card_skin_id,acquired_at,uses_remaining,times_renewed,
                trophy_reward_id,trophy_period_id,trophy_period_title,trophy_rank,trophy_serial,
                trophy_original_owner_telegram_id
            )
            SELECT ?,?,?,now(),?,0,r.id,r.period_id,p.title,r.rank,r.serial,u.telegram_id
            FROM periodic_rating_reward r
            JOIN periodic_rating_period p ON p.id=r.period_id
            JOIN telegram_user u ON u.id=r.telegram_user_id
            WHERE r.id=?
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            current.internalUserId,
            templateId,
            skinId,
            uses,
            current.id,
        )!!
        jdbc.update(
            """
            INSERT INTO user_card_ownership_history(user_card_id,telegram_user_id,acquired_at,acquisition_type)
            VALUES (?,?,now(),'PERIODIC_RATING_REWARD')
            """.trimIndent(),
            userCardId,
            current.internalUserId,
        )
        val updated = jdbc.update(
            """
            UPDATE periodic_rating_reward SET status='FULFILLED',issued_card_template_id=?,issued_user_card_id=?,
                issued_at=now(),changes_requested_reason=NULL,version=version+1,updated_at=now()
            WHERE id=? AND status=? AND version=?
            """.trimIndent(),
            templateId,
            userCardId,
            current.id,
            expectedStatus,
            expectedVersion,
        )
        if (updated != 1) conflict("Reward was already issued or changed")
        audit(
            current.periodId,
            current.id,
            actorType,
            actorId,
            event,
            objectMapper.writeValueAsString(mapOf("userCardId" to userCardId)),
        )
    }

    @Transactional(readOnly = true)
    fun getForAdmin(id: Long): PeriodicRatingRewardDto = jdbc.query(
        rewardSelect("WHERE r.id=?"),
        { rs, _ -> mapReward(rs) },
        id,
    ).firstOrNull() ?: notFound()

    private fun findOrCreateTemplate(playerId: Long, rarity: String, perkIds: List<String>): Long {
        val candidates = jdbc.queryForList(
            "SELECT id FROM card_template WHERE fantasy_player_id=? AND rarity=? ORDER BY id",
            Long::class.java,
            playerId,
            rarity,
        )
        candidates.firstOrNull { templateId ->
            jdbc.queryForList(
                "SELECT perk_id FROM card_template_perk WHERE card_template_id=? ORDER BY perk_id",
                String::class.java,
                templateId,
            ) == perkIds
        }?.let { return it }
        val templateId = jdbc.queryForObject(
            "INSERT INTO card_template(fantasy_player_id,rarity) VALUES (?,?) RETURNING id",
            Long::class.java,
            playerId,
            rarity,
        )!!
        perkIds.forEach { perkId ->
            jdbc.update(
                "INSERT INTO card_template_perk(card_template_id,perk_id,bonus_points) VALUES (?,?,NULL)",
                templateId,
                perkId,
            )
        }
        return templateId
    }

    private fun validateSelection(policy: JsonNode, selection: JsonNode) {
        val playerId = selection.path("playerId").asLong(0)
        if (playerId <= 0 || jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fantasy_player WHERE id=?)", Boolean::class.java, playerId) != true) {
            badRequest("Selected fantasy player does not exist")
        }
        val skinCode = selection.path("skinCode").asText()
        if (policy.path("skinCodes").none { it.asText() == skinCode }) badRequest("skinCode is not allowed by reward policy")
        val perkIds = selection.path("perkIds").map { it.asText() }
        if (perkIds.distinct().size != perkIds.size) badRequest("perkIds must be distinct")
        val count = policy.path("perkSelectionCount").asInt()
        if (perkIds.size != count) badRequest("Exactly $count perkIds are required")
        val mode = policy.path("perkSelectionMode").asText()
        if (mode == "BUNDLED_OPTIONS") {
            val matches = policy.path("bundles").any { bundle ->
                bundle.path("playerId").asLong() == playerId && bundle.path("perkIds").map { it.asText() } == perkIds
            }
            if (!matches) badRequest("playerId and perkIds must match a frozen bundle")
        } else {
            val pool = policy.path("perkPool").map { it.asText() }.toSet()
            if (!pool.containsAll(perkIds)) badRequest("perkIds are not allowed by reward policy")
        }
    }

    private fun selection(request: PeriodicRatingRewardDraftRequest): JsonNode = objectMapper.valueToTree(
        linkedMapOf("playerId" to request.playerId, "perkIds" to request.perkIds, "skinCode" to request.skinCode),
    )

    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

    private fun markOverdue(internalUserId: Long) {
        jdbc.update(
            """
            UPDATE periodic_rating_reward
            SET status='OVERDUE',version=version+1,updated_at=now()
            WHERE telegram_user_id=? AND claim_deadline < now()
              AND status IN ('AVAILABLE','DRAFT','CHANGES_REQUESTED')
            """.trimIndent(),
            internalUserId,
        )
    }

    private fun bundledPlayerIds(policy: JsonNode): List<Long> =
        if (policy.path("playerSelectionMode").asText() == "BUNDLED_OPTIONS") {
            policy.path("bundles").map { it.path("playerId").asLong() }
        } else emptyList()

    private fun lockReward(id: Long, internalUserId: Long?): LockedReward {
        val ownerClause = if (internalUserId == null) "" else " AND telegram_user_id=?"
        val args = if (internalUserId == null) arrayOf(id) else arrayOf(id, internalUserId)
        return jdbc.query(
            "SELECT * FROM periodic_rating_reward WHERE id=?$ownerClause FOR UPDATE",
            { rs, _ -> LockedReward(
                id = rs.getLong("id"),
                periodId = rs.getLong("period_id"),
                internalUserId = rs.getLong("telegram_user_id"),
                status = rs.getString("status"),
                policy = objectMapper.readTree(rs.getString("policy_snapshot")),
                selection = objectMapper.readTree(rs.getString("selection")),
                version = rs.getInt("version"),
                issuedUserCardId = rs.getLong("issued_user_card_id").takeUnless { rs.wasNull() },
            ) },
            *args,
        ).firstOrNull() ?: notFound()
    }

    private fun requireVersion(current: LockedReward, supplied: Int) {
        if (current.version != supplied) stale(current.id)
    }

    private fun stale(id: Long): Nothing = conflict("Reward version is stale; reload reward $id")

    private fun rewardSelect(suffix: String) = """
        SELECT r.*,p.code period_code,p.title period_title,
               u.telegram_id owner_telegram_id,u.username owner_username,
               u.first_name owner_first_name,u.display_name owner_display_name,
               fp.id selected_player_id,fp.polemica_user_id selected_player_polemica_user_id,
               fp.nickname selected_player_nickname,fp.photo_url selected_player_photo_url
        FROM periodic_rating_reward r
        JOIN periodic_rating_period p ON p.id=r.period_id
        JOIN telegram_user u ON u.id=r.telegram_user_id
        LEFT JOIN fantasy_player fp ON fp.id=NULLIF(r.selection->>'playerId','')::bigint
        $suffix
    """.trimIndent()

    private fun mapReward(rs: ResultSet) = PeriodicRatingRewardDto(
        id = rs.getLong("id"),
        periodId = rs.getLong("period_id"),
        periodCode = rs.getString("period_code"),
        periodTitle = rs.getString("period_title"),
        user = io.github.mralex1810.fantasy.dto.periodicrating.PeriodicRatingUserDto(
            telegramId = rs.getLong("owner_telegram_id"),
            username = rs.getString("owner_username"),
            firstName = rs.getString("owner_first_name"),
            displayName = rs.getString("owner_display_name"),
        ),
        rank = rs.getInt("rank"),
        status = rs.getString("status"),
        serial = rs.getString("serial"),
        policy = objectMapper.readTree(rs.getString("policy_snapshot")),
        selection = objectMapper.readTree(rs.getString("selection")),
        selectedPlayer = rs.getLong("selected_player_id").takeUnless { rs.wasNull() }?.let { selectedId ->
            PeriodicRatingRewardPlayerDto(
                id = selectedId,
                polemicaUserId = rs.getLong("selected_player_polemica_user_id"),
                nickname = rs.getString("selected_player_nickname"),
                photoUrl = rs.getString("selected_player_photo_url"),
            )
        },
        version = rs.getInt("version"),
        claimDeadline = rs.getTimestamp("claim_deadline")?.toInstant(),
        changesRequestedReason = rs.getString("changes_requested_reason"),
        fantikiAmount = rs.getLong("fantiki_amount"),
        fantikiGrantedAt = rs.getTimestamp("fantiki_granted_at")?.toInstant(),
        issuedCardTemplateId = rs.getLong("issued_card_template_id").takeUnless { rs.wasNull() },
        issuedUserCardId = rs.getLong("issued_user_card_id").takeUnless { rs.wasNull() },
        issuedAt = rs.getTimestamp("issued_at")?.toInstant(),
        createdAt = rs.getTimestamp("created_at").toInstant(),
        updatedAt = rs.getTimestamp("updated_at").toInstant(),
    )

    private fun audit(periodId: Long, rewardId: Long, actorType: String, actorId: String?, event: String, payload: String) {
        jdbc.update(
            """
            INSERT INTO periodic_rating_audit_event(period_id,reward_id,actor_type,actor_id,event_type,payload)
            VALUES (?,?,?,?,?,?::jsonb)
            """.trimIndent(),
            periodId, rewardId, actorType, actorId, event, payload,
        )
    }

    private fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)
    private fun notFound(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Reward not found")

    private data class LockedReward(
        val id: Long,
        val periodId: Long,
        val internalUserId: Long,
        val status: String,
        val policy: JsonNode,
        val selection: JsonNode,
        val version: Int,
        val issuedUserCardId: Long?,
    )
}
