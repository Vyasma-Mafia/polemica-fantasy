package io.github.mralex1810.fantasy.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "user_card_merge")
class UserCardMerge(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "telegram_user_id", nullable = false)
    var telegramUser: TelegramUser? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preview_id")
    var preview: UserCardMergePreview? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "result_user_card_id", nullable = false)
    var resultUserCard: UserCard? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var operation: CardMergeOperation = CardMergeOperation.COMMON_TO_RARE,

    @Enumerated(EnumType.STRING)
    @Column(name = "source_rarity", nullable = false, length = 32)
    var sourceRarity: Rarity = Rarity.COMMON,

    @Enumerated(EnumType.STRING)
    @Column(name = "result_rarity", nullable = false, length = 32)
    var resultRarity: Rarity = Rarity.RARE,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fantasy_player_id", nullable = false)
    var fantasyPlayer: FantasyPlayer? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "selected_perk_ids", nullable = false, columnDefinition = "jsonb")
    var selectedPerkIds: JsonNode? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "fixed_perk_ids", nullable = false, columnDefinition = "jsonb")
    var fixedPerkIds: JsonNode? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "offered_perk_ids", nullable = false, columnDefinition = "jsonb")
    var offeredPerkIds: JsonNode? = null,

    @Column(name = "selected_skin_source_user_card_id")
    var selectedSkinSourceUserCardId: Long? = null,

    @Column(name = "result_skin_code", length = 64)
    var resultSkinCode: String? = null,

    @Column(name = "cost_fantiki", nullable = false)
    var costFantiki: Long = 0L,

    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now(),

    @OneToMany(mappedBy = "merge")
    var inputs: MutableList<UserCardMergeInput> = mutableListOf(),
)
