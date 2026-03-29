package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.user.request.SubmitFantasyTeamRequest
import io.github.mralex1810.fantasy.dto.user.response.AchievementInGameDto
import io.github.mralex1810.fantasy.dto.user.response.CardGameBreakdownDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamDetailSlotDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamSeriesDetailsDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamSeriesGameInfoDto
import io.github.mralex1810.fantasy.dto.user.response.FantasyTeamSlotDto
import io.github.mralex1810.fantasy.entity.FantasyTeam
import io.github.mralex1810.fantasy.entity.FantasyTeamCard
import io.github.mralex1810.fantasy.entity.FantasyTeamCardGameScore
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.FantasyTeamCardGameScoreRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamCardRepository
import io.github.mralex1810.fantasy.repository.FantasyTeamRepository
import io.github.mralex1810.fantasy.repository.SeriesGameRepository
import io.github.mralex1810.fantasy.repository.SeriesPlayerRepository
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.UserCardRepository
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
    private val fantasyTeamRepository: FantasyTeamRepository,
    private val fantasyTeamCardRepository: FantasyTeamCardRepository,
    private val fantasyTeamCardGameScoreRepository: FantasyTeamCardGameScoreRepository,
) {

    @Transactional(readOnly = true)
    fun listAllTeams(user: TelegramUser): List<FantasyTeamDto> {
        val teams = fantasyTeamRepository.findAllByTelegramUser_IdWithCards(user.id!!)
        return teams.map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getTeamForSeries(user: TelegramUser, seriesId: Long): FantasyTeamDto {
        val team = fantasyTeamRepository.findByUserAndSeriesWithCards(user.id!!, seriesId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No fantasy team for this series")
        return team.toDto()
    }

    @Transactional(readOnly = true)
    fun getTeamDetailsForSeries(user: TelegramUser, seriesId: Long): FantasyTeamSeriesDetailsDto {
        val team = fantasyTeamRepository.findByUserAndSeriesWithCards(user.id!!, seriesId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No fantasy team for this series")
        val seriesGames = seriesGameRepository.findAllBySeries_Id(seriesId)
            .sortedWith(compareBy({ it.polemicaGameId }, { it.id }))
        val games = seriesGames.map { sg ->
            FantasyTeamSeriesGameInfoDto(
                seriesGameId = sg.id!!,
                polemicaGameId = sg.polemicaGameId,
                gameName = sg.gameName,
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
    fun createFantasyTeam(user: TelegramUser, seriesId: Long, request: SubmitFantasyTeamRequest): FantasyTeamDto {
        if (fantasyTeamRepository.findByTelegramUser_IdAndSeries_Id(user.id!!, seriesId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Fantasy team already submitted for this series")
        }
        val series = seriesRepository.findById(seriesId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Series $seriesId not found")
        }
        assertDeadline(series.teamDeadline)
        val team = FantasyTeam(
            telegramUser = user,
            series = series,
            submittedAt = Instant.now(),
            totalScore = null,
        )
        fantasyTeamRepository.save(team)
        attachCards(team, user, seriesId, request.userCardIds)
        return fantasyTeamRepository.findByUserAndSeriesWithCards(user.id!!, seriesId)!!.toDto()
    }

    @Transactional
    fun updateFantasyTeam(user: TelegramUser, seriesId: Long, request: SubmitFantasyTeamRequest): FantasyTeamDto {
        val team = fantasyTeamRepository.findByTelegramUser_IdAndSeries_Id(user.id!!, seriesId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No fantasy team for this series")
        val series = team.series!!
        assertDeadline(series.teamDeadline)
        fantasyTeamCardRepository.deleteAllByFantasyTeam_Id(team.id!!)
        team.cards.clear()
        team.submittedAt = Instant.now()
        team.totalScore = null
        fantasyTeamRepository.save(team)
        attachCards(team, user, seriesId, request.userCardIds)
        return fantasyTeamRepository.findByUserAndSeriesWithCards(user.id!!, seriesId)!!.toDto()
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
        userCardIds: List<Long>,
    ) {
        val distinct = userCardIds.distinct()
        if (distinct.size != 3) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Three distinct user cards required")
        }
        val cards = userCardRepository.findAllByIdInAndTelegramUser_Id(userCardIds.toSet(), user.id!!)
        if (cards.size != 3) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or foreign user cards")
        }
        val byId = cards.associateBy { it.id!! }
        userCardIds.forEach { id ->
            if (byId[id] == null) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown user card id $id")
            }
        }
        userCardIds.forEachIndexed { index, ucId ->
            val uc = byId[ucId]!!
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
            totalScore = totalScore,
            submittedAt = submittedAt,
            slots = slots,
        )
    }
}
