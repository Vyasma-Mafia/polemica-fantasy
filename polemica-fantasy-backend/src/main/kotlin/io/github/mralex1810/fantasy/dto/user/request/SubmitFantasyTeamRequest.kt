package io.github.mralex1810.fantasy.dto.user.request

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class SubmitFantasyTeamRequest(
    @field:NotEmpty(message = "userCardIds required")
    @field:Size(min = 1, max = 3, message = "Between 1 and 3 user cards required")
    val userCardIds: List<Long>,
)
