package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.ProfileCosmetic
import org.springframework.data.jpa.repository.JpaRepository

interface ProfileCosmeticRepository : JpaRepository<ProfileCosmetic, String> {
    fun findAllByCodeInAndEnabledTrue(codes: Collection<String>): List<ProfileCosmetic>

    fun findByCodeAndEnabledTrue(code: String): ProfileCosmetic?
}
