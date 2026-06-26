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
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes

@Entity
@Table(name = "user_card_merge_input")
class UserCardMergeInput(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merge_id", nullable = false)
    var merge: UserCardMerge? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "input_user_card_id", nullable = false)
    var inputUserCard: UserCard? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "input_card_template_id", nullable = false)
    var inputCardTemplate: CardTemplate? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "input_rarity", nullable = false, length = 32)
    var inputRarity: Rarity = Rarity.COMMON,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_perk_ids", nullable = false, columnDefinition = "jsonb")
    var inputPerkIds: JsonNode? = null,

    @Column(name = "input_uses_remaining", nullable = false)
    var inputUsesRemaining: Int = 0,

    @Column(name = "input_times_renewed", nullable = false)
    var inputTimesRenewed: Int = 0,

    @Column(name = "input_skin_code", length = 64)
    var inputSkinCode: String? = null,
)
