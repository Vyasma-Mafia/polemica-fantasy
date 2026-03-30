package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.EconomyConfig
import org.springframework.data.jpa.repository.JpaRepository

interface EconomyConfigRepository : JpaRepository<EconomyConfig, String> {
    fun findByKey(key: String): EconomyConfig?
}
