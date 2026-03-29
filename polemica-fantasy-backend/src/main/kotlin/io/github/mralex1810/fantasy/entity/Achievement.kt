package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.BatchSize

@Entity
@Table(name = "achievement")
class Achievement(
    @Id
    @Column(length = 64)
    var id: String = "",

    @Column(nullable = false)
    var name: String = "",

    @Column(columnDefinition = "text")
    var description: String? = null,

    @Column(name = "bonus_points", nullable = false)
    var bonusPoints: Double = 1.0,

    @Enumerated(EnumType.STRING)
    @Column(name = "occurrence_type", nullable = false, length = 32)
    var occurrenceType: OccurrenceType = OccurrenceType.ONCE_PER_GAME,

    @Column(name = "can_appear_on_random_cards", nullable = false)
    var canAppearOnRandomCards: Boolean = false,

    @OneToMany(mappedBy = "achievement", fetch = FetchType.LAZY)
    @BatchSize(size = 32)
    var applicableRoles: MutableSet<AchievementApplicableRole> = mutableSetOf(),
)
