package io.github.mralex1810.fantasy.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.io.Serializable

data class AchievementApplicableRoleId(
    var achievementId: String = "",
    var role: String = "",
) : Serializable

@Entity
@IdClass(AchievementApplicableRoleId::class)
@Table(name = "achievement_applicable_role")
class AchievementApplicableRole(
    @Id
    @Column(name = "achievement_id", nullable = false, length = 64)
    var achievementId: String = "",

    @Id
    @Column(name = "role", nullable = false, length = 32)
    var role: String = "",

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "achievement_id", nullable = false, insertable = false, updatable = false)
    var achievement: Achievement? = null,
)
