package io.github.mralex1810.fantasy.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.dto.admin.request.AddFantasyPlayerAliasRequest
import io.github.mralex1810.fantasy.dto.admin.request.CreateFantasyPlayerAdminRequest
import io.github.mralex1810.fantasy.dto.admin.request.MergeFantasyPlayersRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateFantasyPlayerAdminRequest
import io.github.mralex1810.fantasy.dto.admin.response.FantasyPlayerAliasDto
import io.github.mralex1810.fantasy.dto.admin.response.FantasyPlayerAdminDto
import io.github.mralex1810.fantasy.dto.admin.response.FantasyPlayerMergeIssueDto
import io.github.mralex1810.fantasy.dto.admin.response.FantasyPlayerMergePreviewDto
import io.github.mralex1810.fantasy.dto.admin.response.FantasyPlayerMergeResultDto
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.entity.FantasyPlayerAlias
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerAliasRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import jakarta.persistence.EntityManager
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@Service
class FantasyPlayerAdminService(
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val fantasyPlayerAliasRepository: FantasyPlayerAliasRepository,
    private val tournamentPlayerRepository: TournamentPlayerRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val imageStorageService: ImageStorageService,
    private val resolverService: FantasyPlayerResolverService,
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    private val entityManager: EntityManager,
) {

    @Transactional(readOnly = true)
    fun listPlayers(query: String?): List<FantasyPlayerAdminDto> {
        val normalizedQuery = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return fantasyPlayerRepository.findAll(Sort.by(Sort.Direction.ASC, "nickname"))
            .asSequence()
            .filter { fp ->
                val aliases = fp.id?.let { fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(it) }
                    ?: emptyList()
                normalizedQuery == null ||
                    fp.nickname.lowercase().contains(normalizedQuery) ||
                    fp.polemicaUserId.toString().contains(normalizedQuery) ||
                    aliases.any { it.toString().contains(normalizedQuery) } ||
                    fp.id?.toString()?.contains(normalizedQuery) == true
            }
            .map { it.toDto() }
            .toList()
    }

    @Transactional
    fun createPlayer(request: CreateFantasyPlayerAdminRequest): FantasyPlayerAdminDto {
        val player = resolverService.createWithPrimaryAlias(request.polemicaUserId, request.nickname)
        return player.toDto()
    }

    @Transactional
    fun updatePlayer(id: Long, request: UpdateFantasyPlayerAdminRequest): FantasyPlayerAdminDto {
        val player = fantasyPlayerRepository.findById(id).orElseThrow { notFound(id) }
        player.nickname = request.nickname.trim()
        return fantasyPlayerRepository.save(player).toDto()
    }

    @Transactional
    fun uploadPhoto(id: Long, file: MultipartFile): FantasyPlayerAdminDto {
        file.validateImageUpload()
        val player = fantasyPlayerRepository.findById(id).orElseThrow { notFound(id) }
        val ext = file.imageExtension()
        val key = imageStorageService.playerPhotoKey(player.id!!, ext)
        player.photoUrl?.let { prev ->
            runCatching { imageStorageService.delete(imageStorageService.keyFromUrlOrKey(prev)) }
        }
        val url = imageStorageService.upload(key, file.bytes, file.contentType!!)
        player.photoUrl = url
        return fantasyPlayerRepository.save(player).toDto()
    }

    @Transactional
    fun addAlias(id: Long, request: AddFantasyPlayerAliasRequest): FantasyPlayerAdminDto {
        if (request.polemicaUserId <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "polemicaUserId must be positive")
        }
        val player = fantasyPlayerRepository.findById(id).orElseThrow { notFound(id) }
        val existing = resolverService.resolveByPolemicaUserId(request.polemicaUserId)
        if (existing != null && existing.id != id) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Polemica user id already belongs to fantasyPlayer ${existing.id}",
            )
        }
        val alias = fantasyPlayerAliasRepository.findByPolemicaUserId(request.polemicaUserId)
            ?: fantasyPlayerAliasRepository.save(
                FantasyPlayerAlias(
                    fantasyPlayer = player,
                    polemicaUserId = request.polemicaUserId,
                    primaryAlias = false,
                ),
            )
        if (request.primary) {
            setPrimaryAlias(player, alias)
        }
        return player.toDto()
    }

    @Transactional(readOnly = true)
    fun mergePreview(targetId: Long, sourceId: Long): FantasyPlayerMergePreviewDto =
        buildMergePreview(targetId, sourceId)

    @Transactional
    fun mergeConfirm(targetId: Long, request: MergeFantasyPlayersRequest): FantasyPlayerMergeResultDto {
        val reason = request.reason.trim()
        if (reason.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "reason is required")
        }
        val orderedIds = listOf(targetId, request.sourceFantasyPlayerId).sorted()
        val first = fantasyPlayerRepository.findByIdForUpdate(orderedIds[0]).orElseThrow { notFound(orderedIds[0]) }
        val second = fantasyPlayerRepository.findByIdForUpdate(orderedIds[1]).orElseThrow { notFound(orderedIds[1]) }
        val target = if (first.id == targetId) first else second
        val source = if (first.id == request.sourceFantasyPlayerId) first else second
        if (target.id == source.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "source and target must be different")
        }
        val preview = buildMergePreview(target.id!!, source.id!!)
        if (!preview.canMerge) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot merge fantasy players while blockers exist: ${preview.blockers.joinToString { it.code }}",
            )
        }

        val sourceId = source.id!!
        val targetPlayerId = target.id!!
        val sourceAliases = aliasesFor(sourceId)
        val targetAliasesBefore = aliasesFor(targetPlayerId)
        val auditId = insertMergeAudit(sourceId, targetPlayerId, reason, sourceAliases, targetAliasesBefore)

        transferDirectReferences(sourceId, targetPlayerId)
        transferRosterReferences(sourceId, targetPlayerId)
        jdbcTemplate.update("UPDATE fantasy_player_alias SET primary_alias = FALSE WHERE fantasy_player_id = ?", sourceId)
        jdbcTemplate.update(
            "UPDATE fantasy_player_alias SET fantasy_player_id = ? WHERE fantasy_player_id = ?",
            targetPlayerId,
            sourceId,
        )
        jdbcTemplate.update("DELETE FROM fantasy_player WHERE id = ?", sourceId)
        entityManager.clear()
        val refreshedTarget = fantasyPlayerRepository.findById(targetPlayerId).orElseThrow { notFound(targetPlayerId) }
        return FantasyPlayerMergeResultDto(auditId = auditId, target = refreshedTarget.toDto())
    }

    private fun FantasyPlayer.toDto(): FantasyPlayerAdminDto {
        val playerId = id!!
        val aliases = fantasyPlayerAliasRepository.findAllByFantasyPlayer_IdOrderByPrimaryAliasDescPolemicaUserIdAsc(playerId)
        return FantasyPlayerAdminDto(
            id = playerId,
            polemicaUserId = polemicaUserId,
            nickname = nickname,
            photoUrl = imageStorageService.publicObjectUrl(photoUrl),
            aliases = aliases.map { FantasyPlayerAliasDto(id = it.id!!, polemicaUserId = it.polemicaUserId, primary = it.primaryAlias) },
            tournamentIds = tournamentPlayerRepository.findDistinctTournamentIdsByFantasyPlayerId(playerId),
            tournamentCount = tournamentPlayerRepository.countByFantasyPlayer_Id(playerId),
            cardTemplateCount = cardTemplateRepository.countByFantasyPlayer_Id(playerId),
        )
    }

    private fun setPrimaryAlias(player: FantasyPlayer, alias: FantasyPlayerAlias) {
        fantasyPlayerAliasRepository.clearPrimaryAlias(player.id!!)
        alias.primaryAlias = true
        fantasyPlayerAliasRepository.save(alias)
        player.polemicaUserId = alias.polemicaUserId
        fantasyPlayerRepository.save(player)
    }

    private fun buildMergePreview(targetId: Long, sourceId: Long): FantasyPlayerMergePreviewDto {
        if (targetId == sourceId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "source and target must be different")
        }
        val target = fantasyPlayerRepository.findById(targetId).orElseThrow { notFound(targetId) }
        val source = fantasyPlayerRepository.findById(sourceId).orElseThrow { notFound(sourceId) }
        val sourceAliases = aliasesFor(sourceId)
        val targetAliases = aliasesFor(targetId)
        val mergedAliases = (sourceAliases + targetAliases).toSet()
        val blockers = mutableListOf<FantasyPlayerMergeIssueDto>()
        val warnings = mutableListOf<FantasyPlayerMergeIssueDto>()

        addIssue(
            blockers,
            "BLOCKER",
            "PENDING_PACK_CHOICES",
            "Pending pack choices reference source fantasy_player_id",
            pendingPackChoices(sourceId),
        )
        addIssue(
            blockers,
            "BLOCKER",
            "PENDING_ACHIEVEMENT_CHOICES",
            "Pending achievement card choices reference source fantasy_player_id",
            pendingAchievementChoices(sourceId),
        )
        addIssue(
            blockers,
            "BLOCKER",
            "EASTER_EGG_CONFIG",
            "economy_config easter_egg.developer_fantasy_player_id still points to source fantasy_player_id",
            developerEasterEggReferences(sourceId),
        )
        addIssue(
            blockers,
            "BLOCKER",
            "ACTIVE_TEAM_DUPLICATES",
            "Non-finalized fantasy teams contain both source and target player cards",
            activeTeamDuplicateConflicts(sourceId, targetId),
        )
        addIssue(
            blockers,
            "BLOCKER",
            "REPLACEMENT_ALIAS_CONFLICTS",
            "Series replacement Polemica user id matches one of the merged player's aliases",
            replacementAliasConflicts(sourceId, targetId, mergedAliases),
        )
        addIssue(
            blockers,
            "BLOCKER",
            "SERIES_REPLACEMENT_CONFLICTS",
            "Source and target are both present in the same series with different replacement Polemica user ids",
            seriesReplacementConflicts(sourceId, targetId),
        )
        addIssue(
            blockers,
            "BLOCKER",
            "TOURNAMENT_PACK_POOL_CONFLICTS",
            "Source and target are both present in the same tournament with different pack-pool exclusion flags",
            tournamentPackPoolConflicts(sourceId, targetId),
        )

        addIssue(
            warnings,
            "WARNING",
            "ROSTER_CONFLICTS",
            "Source and target are both present in the same tournament roster; duplicate roster rows will be collapsed",
            rosterConflicts(sourceId, targetId),
        )
        addIssue(
            warnings,
            "WARNING",
            "SERIES_ROSTER_CONFLICTS",
            "Source and target are both present in the same series roster; duplicate series rows will be collapsed",
            seriesRosterConflicts(sourceId, targetId),
        )
        addIssue(
            warnings,
            "WARNING",
            "MARKETPLACE_WATCHES",
            "Marketplace watches for source player will point to target player after merge",
            directCount("SELECT COUNT(*) FROM marketplace_watch_filter WHERE fantasy_player_id = ?", sourceId),
        )
        addIssue(
            warnings,
            "WARNING",
            "FAVORITE_BADGES",
            "Favorite badge customizations for source player will point to target player after merge",
            directCount("SELECT COUNT(*) FROM user_profile_customization WHERE favorite_badge_fantasy_player_id = ?", sourceId),
        )
        addIssue(
            warnings,
            "WARNING",
            "CARD_MERGE_AUDIT_ROWS",
            "Card merge audit rows for source player will point to target player after merge",
            directCount("SELECT COUNT(*) FROM user_card_merge WHERE fantasy_player_id = ?", sourceId),
        )
        addIssue(
            warnings,
            "WARNING",
            "CARD_TEMPLATES",
            "Card templates for source player will point to target player; existing user_card rows stay attached through templates",
            directCount("SELECT COUNT(*) FROM card_template WHERE fantasy_player_id = ?", sourceId),
        )

        return FantasyPlayerMergePreviewDto(
            source = source.toDto(),
            target = target.toDto(),
            sourceAliases = sourceAliases,
            targetAliases = targetAliases,
            blockers = blockers,
            warnings = warnings,
            canMerge = blockers.isEmpty(),
        )
    }

    private fun transferRosterReferences(sourceId: Long, targetId: Long) {
        jdbcTemplate.update(
            """
            UPDATE series_player sp
            SET tournament_player_id = target_tp.id
            FROM tournament_player source_tp
            JOIN tournament_player target_tp ON target_tp.tournament_id = source_tp.tournament_id
            WHERE sp.tournament_player_id = source_tp.id
              AND source_tp.fantasy_player_id = ?
              AND target_tp.fantasy_player_id = ?
              AND NOT EXISTS (
                  SELECT 1 FROM series_player existing
                  WHERE existing.series_id = sp.series_id
                    AND existing.tournament_player_id = target_tp.id
              )
            """.trimIndent(),
            sourceId,
            targetId,
        )
        jdbcTemplate.update(
            """
            DELETE FROM series_player sp
            USING tournament_player source_tp, tournament_player target_tp, series_player existing
            WHERE sp.tournament_player_id = source_tp.id
              AND source_tp.fantasy_player_id = ?
              AND target_tp.fantasy_player_id = ?
              AND target_tp.tournament_id = source_tp.tournament_id
              AND existing.series_id = sp.series_id
              AND existing.tournament_player_id = target_tp.id
            """.trimIndent(),
            sourceId,
            targetId,
        )
        jdbcTemplate.update(
            """
            DELETE FROM tournament_player source_tp
            USING tournament_player target_tp
            WHERE source_tp.fantasy_player_id = ?
              AND target_tp.fantasy_player_id = ?
              AND target_tp.tournament_id = source_tp.tournament_id
            """.trimIndent(),
            sourceId,
            targetId,
        )
        jdbcTemplate.update(
            "UPDATE tournament_player SET fantasy_player_id = ? WHERE fantasy_player_id = ?",
            targetId,
            sourceId,
        )
    }

    private fun transferDirectReferences(sourceId: Long, targetId: Long) {
        jdbcTemplate.update(
            """
            DELETE FROM card_pack_player source_cpp
            USING card_pack_player target_cpp
            WHERE source_cpp.fantasy_player_id = ?
              AND target_cpp.fantasy_player_id = ?
              AND target_cpp.card_pack_id = source_cpp.card_pack_id
            """.trimIndent(),
            sourceId,
            targetId,
        )
        jdbcTemplate.update("UPDATE card_pack_player SET fantasy_player_id = ? WHERE fantasy_player_id = ?", targetId, sourceId)
        jdbcTemplate.update(
            """
            DELETE FROM marketplace_watch_filter source_mwf
            USING marketplace_watch_filter target_mwf
            WHERE source_mwf.fantasy_player_id = ?
              AND target_mwf.fantasy_player_id = ?
              AND target_mwf.telegram_user_id = source_mwf.telegram_user_id
              AND COALESCE(target_mwf.tournament_id, -1) = COALESCE(source_mwf.tournament_id, -1)
              AND COALESCE(target_mwf.rarity, '') = COALESCE(source_mwf.rarity, '')
              AND COALESCE(target_mwf.max_price, -1) = COALESCE(source_mwf.max_price, -1)
              AND target_mwf.perk_ids_key = source_mwf.perk_ids_key
              AND COALESCE(target_mwf.min_times_renewed, -1) = COALESCE(source_mwf.min_times_renewed, -1)
              AND COALESCE(target_mwf.max_times_renewed, -1) = COALESCE(source_mwf.max_times_renewed, -1)
            """.trimIndent(),
            sourceId,
            targetId,
        )
        jdbcTemplate.update("UPDATE marketplace_watch_filter SET fantasy_player_id = ? WHERE fantasy_player_id = ?", targetId, sourceId)
        jdbcTemplate.update(
            "UPDATE user_profile_customization SET favorite_badge_fantasy_player_id = ? WHERE favorite_badge_fantasy_player_id = ?",
            targetId,
            sourceId,
        )
        jdbcTemplate.update("UPDATE user_card_merge SET fantasy_player_id = ? WHERE fantasy_player_id = ?", targetId, sourceId)
        jdbcTemplate.update("UPDATE card_template SET fantasy_player_id = ? WHERE fantasy_player_id = ?", targetId, sourceId)
    }

    private fun insertMergeAudit(
        sourceId: Long,
        targetId: Long,
        reason: String,
        sourceAliases: List<Long>,
        targetAliasesBefore: List<Long>,
    ): Long =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO fantasy_player_merge_audit (
                source_fantasy_player_id,
                target_fantasy_player_id,
                reason,
                source_polemica_user_ids,
                target_polemica_user_ids_before
            )
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb)
            RETURNING id
            """.trimIndent(),
            Long::class.java,
            sourceId,
            targetId,
            reason,
            objectMapper.writeValueAsString(sourceAliases),
            objectMapper.writeValueAsString(targetAliasesBefore),
        )!!

    private fun aliasesFor(fantasyPlayerId: Long): List<Long> =
        fantasyPlayerAliasRepository.findPolemicaUserIdsByFantasyPlayerId(fantasyPlayerId)
            .ifEmpty {
                fantasyPlayerRepository.findById(fantasyPlayerId)
                    .map { listOf(it.polemicaUserId) }
                    .orElse(emptyList())
            }

    private fun addIssue(
        target: MutableList<FantasyPlayerMergeIssueDto>,
        severity: String,
        code: String,
        message: String,
        count: Long,
    ) {
        if (count > 0) {
            target.add(FantasyPlayerMergeIssueDto(code = code, severity = severity, message = message, count = count))
        }
    }

    private fun pendingPackChoices(sourceId: Long): Long =
        directCount(
            """
            SELECT COUNT(*)
            FROM user_card_pack_choice
            WHERE selected_at IS NULL
              AND options::text ~ ?
            """.trimIndent(),
            fantasyPlayerJsonRegex(sourceId),
        )

    private fun pendingAchievementChoices(sourceId: Long): Long =
        directCount(
            """
            SELECT COUNT(*)
            FROM user_achievement_card_choice
            WHERE claimed_at IS NULL
              AND options::text ~ ?
            """.trimIndent(),
            fantasyPlayerJsonRegex(sourceId),
        )

    private fun developerEasterEggReferences(sourceId: Long): Long =
        directCount(
            """
            SELECT COUNT(*)
            FROM economy_config
            WHERE key = 'easter_egg.developer_fantasy_player_id'
              AND value = ?
            """.trimIndent(),
            sourceId.toString(),
        )

    private fun activeTeamDuplicateConflicts(sourceId: Long, targetId: Long): Long =
        directCount(
            """
            SELECT COUNT(DISTINCT ft.id)
            FROM fantasy_team ft
            JOIN series s ON s.id = ft.series_id
            JOIN fantasy_team_card source_slot ON source_slot.fantasy_team_id = ft.id
            JOIN user_card source_uc ON source_uc.id = source_slot.user_card_id
            JOIN card_template source_ct ON source_ct.id = source_uc.card_template_id
            JOIN fantasy_team_card target_slot ON target_slot.fantasy_team_id = ft.id
            JOIN user_card target_uc ON target_uc.id = target_slot.user_card_id
            JOIN card_template target_ct ON target_ct.id = target_uc.card_template_id
            WHERE s.finalized = FALSE
              AND source_ct.fantasy_player_id = ?
              AND target_ct.fantasy_player_id = ?
            """.trimIndent(),
            sourceId,
            targetId,
        )

    private fun replacementAliasConflicts(sourceId: Long, targetId: Long, mergedAliases: Set<Long>): Long {
        if (mergedAliases.isEmpty()) return 0L
        val placeholders = mergedAliases.joinToString(",") { "?" }
        return directCount(
            """
            SELECT COUNT(*)
            FROM series_player sp
            JOIN series s ON s.id = sp.series_id
            WHERE s.finalized = FALSE
              AND sp.replacement_polemica_user_id IN ($placeholders)
              AND EXISTS (
                  SELECT 1
                  FROM series_player merged_sp
                  JOIN tournament_player merged_tp ON merged_tp.id = merged_sp.tournament_player_id
                  WHERE merged_sp.series_id = sp.series_id
                    AND merged_tp.fantasy_player_id IN (?, ?)
              )
            """.trimIndent(),
            *mergedAliases.toTypedArray(),
            sourceId,
            targetId,
        )
    }

    private fun rosterConflicts(sourceId: Long, targetId: Long): Long =
        directCount(
            """
            SELECT COUNT(*)
            FROM tournament_player source_tp
            JOIN tournament_player target_tp ON target_tp.tournament_id = source_tp.tournament_id
            WHERE source_tp.fantasy_player_id = ?
              AND target_tp.fantasy_player_id = ?
            """.trimIndent(),
            sourceId,
            targetId,
        )

    private fun seriesRosterConflicts(sourceId: Long, targetId: Long): Long =
        directCount(
            """
            SELECT COUNT(*)
            FROM series_player source_sp
            JOIN tournament_player source_tp ON source_tp.id = source_sp.tournament_player_id
            JOIN series_player target_sp ON target_sp.series_id = source_sp.series_id
            JOIN tournament_player target_tp ON target_tp.id = target_sp.tournament_player_id
            WHERE source_tp.fantasy_player_id = ?
              AND target_tp.fantasy_player_id = ?
            """.trimIndent(),
            sourceId,
            targetId,
        )

    private fun seriesReplacementConflicts(sourceId: Long, targetId: Long): Long =
        directCount(
            """
            SELECT COUNT(*)
            FROM series_player source_sp
            JOIN tournament_player source_tp ON source_tp.id = source_sp.tournament_player_id
            JOIN series_player target_sp ON target_sp.series_id = source_sp.series_id
            JOIN tournament_player target_tp ON target_tp.id = target_sp.tournament_player_id
            WHERE source_tp.fantasy_player_id = ?
              AND target_tp.fantasy_player_id = ?
              AND source_sp.replacement_polemica_user_id IS DISTINCT FROM target_sp.replacement_polemica_user_id
            """.trimIndent(),
            sourceId,
            targetId,
        )

    private fun tournamentPackPoolConflicts(sourceId: Long, targetId: Long): Long =
        directCount(
            """
            SELECT COUNT(*)
            FROM tournament_player source_tp
            JOIN tournament_player target_tp ON target_tp.tournament_id = source_tp.tournament_id
            WHERE source_tp.fantasy_player_id = ?
              AND target_tp.fantasy_player_id = ?
              AND source_tp.excluded_from_pack_pool IS DISTINCT FROM target_tp.excluded_from_pack_pool
            """.trimIndent(),
            sourceId,
            targetId,
        )

    private fun directCount(sql: String, vararg args: Any): Long =
        jdbcTemplate.queryForObject(sql, Long::class.java, *args) ?: 0L

    private fun fantasyPlayerJsonRegex(sourceId: Long): String =
        """"fantasyPlayerId"\s*:\s*$sourceId([,}\]])"""

    private fun notFound(id: Long): ResponseStatusException =
        ResponseStatusException(HttpStatus.NOT_FOUND, "FantasyPlayer $id not found")
}
