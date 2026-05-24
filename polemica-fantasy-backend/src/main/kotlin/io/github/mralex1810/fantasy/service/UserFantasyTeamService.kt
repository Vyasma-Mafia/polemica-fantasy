package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.SubmitFantasyTeamRequest
import io.github.mralex1810.fantasy.dto.user.response.AchievementInGameDto
import io.github.mralex1810.fantasy.dto.user.response.CardGameBreakdownDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamDetailSlotDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamSeriesDetailsDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamSeriesGameInfoDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamSlotDto
import io.github.mralex1810.fantasy.dto.user.response.PublicFantasyTeamDto
import io.github.mralex1810.fantasy.dto.user.response.PublicFantasyTeamSlotDto
import io.github.mralex1810.fantasy.dto.user.response.UserPublicDto
import io.github.mralex1810.fantasy.entity.FantasyTeam
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import io.github.mralex1810.fantasy.entity.FantasyTeamCardGameScore
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.OnboardingStep
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.SeriesLeague
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamCardGameScoreRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.MarketplaceListingRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesLeagueRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class UserFantasyTeamService(
    private val seriesRepository: SeriesRepository,
    private val seriesGameRepository: SeriesGameRepository,
    private val seriesPlayerRepository: SeriesPlayerRepository,
    private val userCardRepository: UserCardRepository,
    private val telegramUserRepository: TelegramUserRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val fantasyTeamCardGameScoreRepository: FantasyTeamCardGameScoreRepository,
    private val fantasyTeamRosterPruningService: FantasyTeamRosterPruningService,
    private val seriesLeagueRepository: SeriesLeagueRepository,
    private val leagueService: LeagueService,
    private val marketplaceListingRepository: MarketplaceListingRepository,
    private val entityManager: EntityManager,
    private val imageStorageService: ImageStorageService,
    private val cardValueService: CardValueService,
    private val onboardingService: OnboardingService,
) {

    @Transactional(readOnly = true)
    fun listAllTeams(user: TelegramUser): List<FantasyTeamDto> {
        val teams = fantasyTeamRepository.findAllByTelegramUser_IdWithCards(user.id!!)
        return teams.map { it.toDto() }
    }

    @Transactional
    fun getTeamForSeries(user: TelegramUser, seriesId: Long, leagueCode: String = LeagueService.MAIN_CODE): FantasyTeamDto {
        fantasyTeamRosterPruningService.pruneInvalidCardsForSeries(seriesId)
        val team = fantasyTeamRepository.findByUserAndSeriesAndLeagueCodeWithCards(user.id!!, seriesId, leagueCode)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No fantasy team for this series")
        return team.toDto()
    }

    @Transactional
    fun getTeamDetailsForSeries(
        user: TelegramUser,
        seriesId: Long,
        leagueCode: String = LeagueService.MAIN_CODE,
    ): FantasyTeamSeriesDetailsDto {
        fantasyTeamRosterPruningService.pruneInvalidCardsForSeries(seriesId)
        val team = fantasyTeamRepository.findByUserAndSeriesAndLeagueCodeWithCards(user.id!!, seriesId, leagueCode)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No fantasy team for this series")
        return buildTeamSeriesDetails(team, seriesId)
    }

    @Transactional
    fun getPublicTeamForSeries(
        seriesId: Long,
        ownerTelegramId: Long,
        leagueCode: String = LeagueService.MAIN_CODE,
    ): PublicFantasyTeamDto {
        if (!seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        fantasyTeamRosterPruningService.pruneInvalidCardsForSeries(seriesId)
        val owner = telegramUserRepository.findByTelegramId(ownerTelegramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val team = fantasyTeamRepository.findByUserAndSeriesAndLeagueCodeWithCards(owner.id!!, seriesId, leagueCode)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No fantasy team for this series")
        val templateIds = team.cards.map { it.userCard!!.cardTemplate!!.id!! }
        val templatesById = cardTemplateRepository.findAllByIdWithAchievementsLoaded(templateIds)
            .associateBy { it.id!! }
        val ownerDto = UserPublicDto(
            telegramId = owner.telegramId,
            username = owner.username,
            firstName = owner.firstName,
            displayName = owner.displayName,
        )
        val s = team.series!!
        val slots = team.cards.sortedBy { it.slot }.map { ftc ->
            val uc = ftc.userCard!!
            val tid = uc.cardTemplate!!.id!!
            val template = templatesById[tid] ?: uc.cardTemplate!!
            PublicFantasyTeamSlotDto(
                slot = ftc.slot,
                score = ftc.score,
                card = uc.toUserCardItemDto(
                    template,
                    imageStorageService,
                    cardValueService,
                ),
            )
        }
        return PublicFantasyTeamDto(
            owner = ownerDto,
            seriesId = s.id!!,
            tournamentId = s.tournament!!.id!!,
            leagueCode = team.seriesLeague!!.league!!.code,
            totalScore = team.totalScore,
            submittedAt = team.submittedAt,
            slots = slots,
        )
    }

    @Transactional
    fun getPublicTeamDetailsForSeries(
        seriesId: Long,
        ownerTelegramId: Long,
        leagueCode: String = LeagueService.MAIN_CODE,
    ): FantasyTeamSeriesDetailsDto {
        if (!seriesRepository.existsById(seriesId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        fantasyTeamRosterPruningService.pruneInvalidCardsForSeries(seriesId)
        val owner = telegramUserRepository.findByTelegramId(ownerTelegramId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "User not found")
        val team = fantasyTeamRepository.findByUserAndSeriesAndLeagueCodeWithCards(owner.id!!, seriesId, leagueCode)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No fantasy team for this series")
        return buildTeamSeriesDetails(team, seriesId)
    }

    private fun buildTeamSeriesDetails(team: FantasyTeam, seriesId: Long): FantasyTeamSeriesDetailsDto {
        val seriesGames = seriesGameRepository.findAllBySeries_Id(seriesId)
            .sortedWith(compareBy({ it.polemicaGameId }, { it.id }))
        val games = seriesGames.map { sg ->
            FantasyTeamSeriesGameInfoDto(
                seriesGameId = sg.id!!,
                polemicaGameId = sg.polemicaGameId,
                gameName = formatSeriesGameDisplayName(sg),
                scored = sg.scored,
            )
        }
        val scores = fantasyTeamCardGameScoreRepository.findAllByFantasyTeamId(team.id!!)
        val scoreByCardAndGame = scores.associateBy {
            Pair(it.fantasyTeamCard!!.id!!, it.seriesGame!!.id!!)
        }
        val columns = team.cards.sortedBy { it.slot }.map { ftc ->
            val cells = seriesGames.map { sg ->
                scoreByCardAndGame[Pair(ftc.id!!, sg.id!!)]?.toBreakdownDto()
            }
            FantasyTeamDetailSlotDto(
                slot = ftc.slot,
                userCardId = ftc.userCard!!.id!!,
                cells = cells,
            )
        }
        return FantasyTeamSeriesDetailsDto(
            seriesId = seriesId,
            games = games,
            columns = columns,
        )
    }

    private fun FantasyTeamCardGameScore.toBreakdownDto(): CardGameBreakdownDto {
        val ach = achievements.map { a ->
            AchievementInGameDto(
                achievementId = a.achievement!!.id,
                achievementName = a.achievement!!.name,
                bonusPoints = a.bonusPoints,
            )
        }
        return CardGameBreakdownDto(
            basePoints = basePoints,
            achievementBonus = achievementBonus,
            rarityModifier = rarityModifier,
            totalScore = totalScore,
            achievements = ach,
        )
    }

    @Transactional
    fun createFantasyTeam(
        user: TelegramUser,
        seriesId: Long,
        request: SubmitFantasyTeamRequest,
        leagueCode: String = LeagueService.MAIN_CODE,
    ): FantasyTeamDto {
        val seriesLeague = resolveSeriesLeague(seriesId, leagueCode)
        if (fantasyTeamRepository.findByTelegramUser_IdAndSeriesLeague_Id(user.id!!, seriesLeague.id!!) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Fantasy team already submitted for this series")
        }
        val series = seriesLeague.series!!
        assertDeadline(series.teamDeadline)
        val team = FantasyTeam(
            telegramUser = user,
            series = series,
            seriesLeague = seriesLeague,
            submittedAt = Instant.now(),
            totalScore = null,
        )
        fantasyTeamRepository.save(team)
        attachCards(team, user, seriesId, seriesLeague, request.userCardIds)
        entityManager.flush()
        entityManager.refresh(team)
        onboardingService.markStep(user.id!!, OnboardingStep.SUBMIT_FIRST_TEAM)
        return team.toDto()
    }

    @Transactional
    fun updateFantasyTeam(
        user: TelegramUser,
        seriesId: Long,
        request: SubmitFantasyTeamRequest,
        leagueCode: String = LeagueService.MAIN_CODE,
    ): FantasyTeamDto {
        val seriesLeague = resolveSeriesLeague(seriesId, leagueCode)
        val team = fantasyTeamRepository.findByTelegramUser_IdAndSeriesLeague_Id(user.id!!, seriesLeague.id!!)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No fantasy team for this series")
        val series = team.series!!
        assertDeadline(series.teamDeadline)
        val existingCards = fantasyTeamCardRepository.findAllByFantasyTeam_Id(team.id!!)
        fantasyTeamCardRepository.deleteAll(existingCards)
        fantasyTeamCardRepository.flush()
        team.cards.clear()
        team.submittedAt = Instant.now()
        team.totalScore = null
        fantasyTeamRepository.save(team)
        attachCards(team, user, seriesId, seriesLeague, request.userCardIds)
        entityManager.flush()
        entityManager.refresh(team)
        return team.toDto()
    }

    private fun assertDeadline(teamDeadline: Instant) {
        if (Instant.now().isAfter(teamDeadline)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Team submission deadline has passed")
        }
    }

    private fun attachCards(
        team: FantasyTeam,
        user: TelegramUser,
        seriesId: Long,
        seriesLeague: SeriesLeague,
        userCardIds: List<Long>,
    ) {
        val minTeamSize = seriesLeague.league!!.minTeamSize
        val maxTeamSize = seriesLeague.league!!.maxTeamSize
        if (userCardIds.size !in minTeamSize..maxTeamSize) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Team size must be between $minTeamSize and $maxTeamSize cards",
            )
        }
        val distinct = userCardIds.distinct()
        if (distinct.size != userCardIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate user cards in team are not allowed")
        }
        val cards = userCardRepository.findAllByIdInAndTelegramUser_Id(userCardIds.toSet(), user.id!!)
        if (cards.size != userCardIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or foreign user cards")
        }
        val byId = cards.associateBy { it.id!! }
        userCardIds.forEach { id ->
            if (byId[id] == null) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown user card id $id")
            }
        }
        userCardIds.forEach { id ->
            if (marketplaceListingRepository.existsByUserCard_IdAndStatus(id, MarketplaceListingStatus.ACTIVE)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot use a card that is listed on the marketplace",
                )
            }
        }
        val fantasyPlayerIds = userCardIds.map { byId[it]!!.cardTemplate!!.fantasyPlayer!!.id!! }
        if (fantasyPlayerIds.toSet().size != fantasyPlayerIds.size) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Team cannot include more than one card per player")
        }
        val legendaryCount = cards.count { it.cardTemplate!!.rarity == Rarity.LEGENDARY }
        val maxLegendary = leagueService.getEffectiveMaxLegendary(seriesLeague)
        if (maxLegendary != null && legendaryCount > maxLegendary) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Maximum $maxLegendary LEGENDARY card(s) allowed per fantasy team",
            )
        }
        val valueCap = leagueService.getEffectiveValueCap(seriesLeague)
        val totalCardValue = cards.sumOf { cardValueService.calculateValue(it.cardTemplate!!) }
        if (valueCap != null && totalCardValue > valueCap) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Team value $totalCardValue exceeds league cap $valueCap",
            )
        }
        userCardIds.forEachIndexed { index, ucId ->
            val uc = byId[ucId]!!
            if (uc.usesRemaining <= 0) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Card $ucId has no remaining uses")
            }
            val existingLeagueCount = fantasyTeamCardRepository.countLeaguesInSeriesForCard(
                userCardId = ucId,
                seriesId = seriesId,
                excludeSeriesLeagueId = seriesLeague.id!!,
            )
            if (existingLeagueCount + 1 > uc.usesRemaining) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Card $ucId has only ${uc.usesRemaining} uses and cannot be placed in another league",
                )
            }
            val fantasyPlayerId = uc.cardTemplate!!.fantasyPlayer!!.id!!
            if (!seriesPlayerRepository.existsBySeries_IdAndTournamentPlayer_FantasyPlayer_Id(seriesId, fantasyPlayerId)) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Card $ucId is for a player who is not in this series roster",
                )
            }
            fantasyTeamCardRepository.save(
                FantasyTeamCard(
                    fantasyTeam = team,
                    userCard = uc,
                    slot = index + 1,
                    score = null,
                ),
            )
        }
    }

    private fun FantasyTeam.toDto(): FantasyTeamDto {
        val s = series!!
        val slots = cards.sortedBy { it.slot }.map { fc ->
            FantasyTeamSlotDto(
                slot = fc.slot,
                userCardId = fc.userCard!!.id!!,
                score = fc.score,
            )
        }
        return FantasyTeamDto(
            seriesId = s.id!!,
            tournamentId = s.tournament!!.id!!,
            leagueCode = seriesLeague!!.league!!.code,
            totalScore = totalScore,
            submittedAt = submittedAt,
            slots = slots,
        )
    }

    private fun resolveSeriesLeague(seriesId: Long, leagueCode: String): SeriesLeague =
        seriesLeagueRepository.findBySeries_IdAndLeague_CodeAndEnabledTrue(seriesId, leagueCode)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "League $leagueCode is not available for series $seriesId",
            )
}
