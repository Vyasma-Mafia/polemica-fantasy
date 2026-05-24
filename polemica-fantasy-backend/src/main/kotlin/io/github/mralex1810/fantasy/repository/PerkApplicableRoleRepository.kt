package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.PerkApplicableRole
import io.github.mralex1810.fantasy.entity.PerkApplicableRoleId
import org.springframework.data.jpa.repository.JpaRepository

interface PerkApplicableRoleRepository :
    JpaRepository<PerkApplicableRole, PerkApplicableRoleId> {
    fun deleteAllByPerkId(perkId: String)
}
