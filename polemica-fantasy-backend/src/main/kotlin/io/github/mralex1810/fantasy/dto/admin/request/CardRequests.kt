package io.github.mralex1810.fantasy.dto.admin.request

import io.github.mralex1810.fantasy.entity.Rarity
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateCardTemplateRequest(
    @field:NotNull
    val fantasyPlayerId: Long,
    @field:NotNull
    val rarity: Rarity,
    @field:Size(max = 20000)
    val description: String? = null,
)

data class UpdateCardTemplateRequest(
    val rarity: Rarity? = null,
    @field:Size(max = 20000)
    val description: String? = null,
)

data class AddCardTemplateAchievementRequest(
    @field:NotBlank
    val achievementId: String,
    val bonusPoints: Double? = null,
)

data class GiveCardsRequest(
    @field:NotNull
    val cardTemplateIds: List<Long>,
    val skinId: Long? = null,
)
