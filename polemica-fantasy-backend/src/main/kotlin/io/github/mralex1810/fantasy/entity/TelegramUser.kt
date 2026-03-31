package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "telegram_user")
class TelegramUser(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "telegram_id", nullable = false, unique = true)
    var telegramId: Long = 0L,

    @Column(name = "username")
    var username: String? = null,

    @Column(name = "first_name")
    var firstName: String? = null,

    @Column(name = "display_name")
    var displayName: String? = null,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "fantiki", nullable = false)
    var fantiki: Long = 1000L,

    @OneToMany(mappedBy = "telegramUser")
    var userCards: MutableList<UserCard> = mutableListOf(),

    @OneToMany(mappedBy = "telegramUser")
    var fantikiTransactions: MutableList<FantikiTransaction> = mutableListOf(),

    @OneToMany(mappedBy = "telegramUser")
    var fantasyTeams: MutableList<FantasyTeam> = mutableListOf(),
)
