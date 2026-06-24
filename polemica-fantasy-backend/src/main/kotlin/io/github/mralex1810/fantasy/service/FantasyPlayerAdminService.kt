package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.CreateFantasyPlayerAdminRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateFantasyPlayerAdminRequest
import io.github.mralex1810.fantasy.dto.admin.response.FantasyPlayerAdminDto
import io.github.mralex1810.fantasy.entity.FantasyPlayer
import io.github.mralex1810.fantasy.repository.CardTemplateRepository
import io.github.mralex1810.fantasy.repository.FantasyPlayerRepository
import io.github.mralex1810.fantasy.repository.TournamentPlayerRepository
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@Service
class FantasyPlayerAdminService(
    private val fantasyPlayerRepository: FantasyPlayerRepository,
    private val tournamentPlayerRepository: TournamentPlayerRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val imageStorageService: ImageStorageService,
) {

    @Transactional(readOnly = true)
    fun listPlayers(query: String?): List<FantasyPlayerAdminDto> {
        val normalizedQuery = query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        return fantasyPlayerRepository.findAll(Sort.by(Sort.Direction.ASC, "nickname"))
            .asSequence()
            .filter { fp ->
                normalizedQuery == null ||
                    fp.nickname.lowercase().contains(normalizedQuery) ||
                    fp.polemicaUserId.toString().contains(normalizedQuery) ||
                    fp.id?.toString()?.contains(normalizedQuery) == true
            }
            .map { it.toDto() }
            .toList()
    }

    @Transactional
    fun createPlayer(request: CreateFantasyPlayerAdminRequest): FantasyPlayerAdminDto {
        if (request.polemicaUserId <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "polemicaUserId must be positive")
        }
        if (fantasyPlayerRepository.findByPolemicaUserId(request.polemicaUserId) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Player with this polemicaUserId already exists")
        }
        val player = fantasyPlayerRepository.save(
            FantasyPlayer(
                polemicaUserId = request.polemicaUserId,
                nickname = request.nickname.trim(),
            ),
        )
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

    private fun FantasyPlayer.toDto(): FantasyPlayerAdminDto {
        val playerId = id!!
        return FantasyPlayerAdminDto(
            id = playerId,
            polemicaUserId = polemicaUserId,
            nickname = nickname,
            photoUrl = imageStorageService.publicObjectUrl(photoUrl),
            tournamentIds = tournamentPlayerRepository.findDistinctTournamentIdsByFantasyPlayerId(playerId),
            tournamentCount = tournamentPlayerRepository.countByFantasyPlayer_Id(playerId),
            cardTemplateCount = cardTemplateRepository.countByFantasyPlayer_Id(playerId),
        )
    }

    private fun notFound(id: Long): ResponseStatusException =
        ResponseStatusException(HttpStatus.NOT_FOUND, "FantasyPlayer $id not found")
}
