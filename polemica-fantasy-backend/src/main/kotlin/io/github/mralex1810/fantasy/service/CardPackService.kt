package io.github.mralex1810.fantasy.service

import io.github.mralex1810.fantasy.dto.admin.request.CardPackRarityConfigDto
import io.github.mralex1810.fantasy.dto.admin.request.CreateCardPackRequest
import io.github.mralex1810.fantasy.dto.admin.request.UpdateCardPackRequest
import io.github.mralex1810.fantasy.dto.admin.response.CardPackDto
import io.github.mralex1810.fantasy.dto.admin.response.CardPackRarityConfigResponseDto
import io.github.mralex1810.fantasy.entity.CardPack
import io.github.mralex1810.fantasy.entity.CardPackRarityConfig
import io.github.mralex1810.fantasy.repository.CardPackRarityConfigRepository
import io.github.mralex1810.fantasy.repository.CardPackRepository
import io.github.mralex1810.fantasy.repository.TournamentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import kotlin.math.abs

@Service
class CardPackService(
    private val cardPackRepository: CardPackRepository,
    private val cardPackRarityConfigRepository: CardPackRarityConfigRepository,
    private val tournamentRepository: TournamentRepository,
) {

    @Transactional(readOnly = true)
    fun listPacks(tournamentId: Long?): List<CardPackDto> {
        val packs = if (tournamentId != null) {
            if (!tournamentRepository.existsById(tournamentId)) {
                throw ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament $tournamentId not found")
            }
            cardPackRepository.findAllByTournament_IdOrderByIdAsc(tournamentId)
        } else {
            cardPackRepository.findAllByOrderByIdAsc()
        }
        return packs.map { it.toDto() }
    }

    @Transactional
    fun createPack(request: CreateCardPackRequest): CardPackDto {
        validateProbabilities(request.rarityConfigs)
        val tournament = tournamentRepository.findById(request.tournamentId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Tournament ${request.tournamentId} not found")
        }
        val pack = cardPackRepository.save(
            CardPack(
                name = request.name.trim(),
                tournament = tournament,
                active = request.active,
            ),
        )
        request.rarityConfigs.forEach { dto ->
            cardPackRarityConfigRepository.save(
                CardPackRarityConfig(
                    cardPack = pack,
                    rarity = dto.rarity,
                    probability = dto.probability,
                    cardsCount = dto.cardsCount,
                ),
            )
        }
        return cardPackRepository.findById(pack.id!!).get().toDto()
    }

    @Transactional
    fun updatePack(id: Long, request: UpdateCardPackRequest): CardPackDto {
        val pack = cardPackRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Card pack $id not found")
        }
        request.name?.let { pack.name = it.trim() }
        request.active?.let { pack.active = it }
        request.rarityConfigs?.let { cfg ->
            validateProbabilities(cfg)
            cardPackRarityConfigRepository.deleteAllByCardPack_Id(id)
            cardPackRarityConfigRepository.flush()
            val newCfgs = cfg.map { dto ->
                cardPackRarityConfigRepository.save(
                    CardPackRarityConfig(
                        cardPack = pack,
                        rarity = dto.rarity,
                        probability = dto.probability,
                        cardsCount = dto.cardsCount,
                    ),
                )
            }
            pack.rarityConfigs.clear()
            pack.rarityConfigs.addAll(newCfgs)
        }
        cardPackRepository.save(pack)
        return cardPackRepository.findById(id).get().toDto()
    }

    companion object {
        fun validateProbabilities(configs: List<CardPackRarityConfigDto>) {
            val sum = configs.sumOf { it.probability }
            if (abs(sum - 1.0) > 1e-6) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rarity probabilities must sum to 1.0 (got $sum)",
                )
            }
        }
    }

    private fun CardPack.toDto(): CardPackDto {
        val cfgs = rarityConfigs.map { c ->
            CardPackRarityConfigResponseDto(
                id = c.id!!,
                rarity = c.rarity,
                probability = c.probability,
                cardsCount = c.cardsCount,
            )
        }
        return CardPackDto(
            id = id!!,
            name = name,
            tournamentId = tournament!!.id!!,
            active = active,
            rarityConfigs = cfgs,
        )
    }
}
