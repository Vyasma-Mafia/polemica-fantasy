package io.github.mralex1810.fantasy.dto.user.response

data class UserPlayerDto(
    val fantasyPlayerId: Long,
    val polemicaUserId: Long,
    val playerNickname: String,
    val playerPhotoUrl: String?,
)
