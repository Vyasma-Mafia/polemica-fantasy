package io.github.mralex1810.fantasy.dto.admin.request

import io.github.mralex1810.fantasy.entity.Rarity
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

data class CardPackRarityConfigDto(
    @field:NotNull
    val rarity: Rarity,
    @field:NotNull
    val probability: Double,
    @field:NotNull @field:Positive
    val cardsCount: Int,
)

data class CreateCardPackRequest(
    @field:NotBlank @field:Size(max = 512)
    val name: String,
    @field:NotNull
    val tournamentId: Long,
    val active: Boolean = true,
    @field:Valid
    @field:NotNull @field:Size(min = 1)
    val rarityConfigs: List<CardPackRarityConfigDto>,
)

data class UpdateCardPackRequest(
    @field:Size(max = 512)
    val name: String? = null,
    val active: Boolean? = null,
    @field:Valid
    val rarityConfigs: List<CardPackRarityConfigDto>? = null,
)
