package io.github.mralex1810.fantasy.dto.user.response

data class TournamentSubscriptionEntry(
    val tournamentId: Long,
    val tournamentName: String,
    val subscribed: Boolean,
)

data class TournamentSubscriptionsResponse(
    val subscriptions: List<TournamentSubscriptionEntry>,
    val availableTournaments: List<TournamentSubscriptionEntry>,
)
