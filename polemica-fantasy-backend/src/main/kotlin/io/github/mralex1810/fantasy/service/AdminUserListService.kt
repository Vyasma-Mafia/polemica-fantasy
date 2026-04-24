package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.response.AdminUserListItemDto
import io.github.mralex1810.fantasy.entity.TelegramUser
import io.github.mralex1810.fantasy.repository.SeriesRepository
import io.github.mralex1810.fantasy.repository.TelegramUserRepository
import io.github.mralex1810.fantasy.util.LikePattern
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class AdminUserListService(
    private val telegramUserRepository: TelegramUserRepository,
    private val seriesRepository: SeriesRepository,
) {

    @Transactional(readOnly = true)
    fun listForAdmin(tournamentId: Long?, seriesId: Long?, q: String?): List<AdminUserListItemDto> {
        val likePattern = q?.trim()?.takeIf { it.isNotEmpty() }?.let { LikePattern.forContains(it) }
        when {
            seriesId != null && tournamentId == null ->
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "tournamentId is required when seriesId is set",
                )
            seriesId == null && tournamentId != null ->
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "seriesId is required when tournamentId is set",
                )
            seriesId == null ->
                return if (likePattern == null) {
                    telegramUserRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).map { u ->
                        toListItemNoSeries(u)
                    }
                } else {
                    telegramUserRepository.findAllMatchingQOrderedById(likePattern).map { u ->
                        toListItemNoSeries(u)
                    }
                }
            else -> {
                seriesRepository.findByIdAndTournament_Id(seriesId, tournamentId!!)
                    ?: throw ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Series $seriesId not found for tournament $tournamentId",
                    )
                return if (likePattern == null) {
                    telegramUserRepository.findAllWithCardsInSeriesCount(seriesId).map { row ->
                        mapSeriesCountRow(row)
                    }
                } else {
                    telegramUserRepository
                        .findAllWithCardsInSeriesCountMatching(seriesId, likePattern)
                        .map { row -> mapSeriesCountRow(row) }
                }
            }
        }
    }

    private fun toListItemNoSeries(u: TelegramUser) = AdminUserListItemDto(
        id = u.id!!,
        telegramId = u.telegramId,
        username = u.username,
        displayName = u.displayName,
        fantiki = u.fantiki,
        cardsInSeries = null,
    )

    private fun mapSeriesCountRow(row: Array<Any>): AdminUserListItemDto {
        val id = (row[0] as Number).toLong()
        val telegramId = (row[1] as Number).toLong()
        val username = row[2] as String?
        val displayName = row[3] as String?
        val fantiki = (row[4] as Number).toLong()
        val count = (row[5] as Number).toLong()
        return AdminUserListItemDto(
            id = id,
            telegramId = telegramId,
            username = username,
            displayName = displayName,
            fantiki = fantiki,
            cardsInSeries = count,
        )
    }
}
