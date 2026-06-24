package io.github.mralex1810.fantasy.dto.admin.request

import io.github.mralex1810.fantasy.entity.TournamentKind
import io.github.mralex1810.fantasy.entity.TournamentStatus
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class CreateTournamentRequest(
    @field:NotBlank @field:Size(max = 512)
    val name: String,
    @field:Size(max = 10000)
    val description: String? = null,
    @field:NotNull
    val status: TournamentStatus = TournamentStatus.DRAFT,
    /** Defaults to STANDALONE when omitted in JSON. */
    val kind: TournamentKind? = null,
    val polemicaCompetitionId: Long? = null,
)

data class UpdateTournamentRequest(
    @field:Size(max = 512)
    val name: String? = null,
    @field:Size(max = 10000)
    val description: String? = null,
    val status: TournamentStatus? = null,
    val kind: TournamentKind? = null,
    val polemicaCompetitionId: Long? = null,
)

data class AddTournamentPlayerRequest(
    val fantasyPlayerId: Long? = null,
    val polemicaUserId: Long? = null,
    @field:Size(max = 512)
    val nickname: String? = null,
)

data class PatchTournamentPlayerRequest(
    @field:NotNull
    val excludedFromPackPool: Boolean,
)
