package io.github.mralex1810.fantasy.dto.user.request

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class SubmitFantasyTeamRequest(
    @field:NotEmpty(message = "userCardIds required")
    @field:Size(min = 3, max = 3, message = "Exactly 3 user cards required")
    val userCardIds: List<Long>,
)
