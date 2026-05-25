package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.UserLegendaryUpgradeEvent
import org.springframework.data.jpa.repository.JpaRepository

interface UserLegendaryUpgradeEventRepository : JpaRepository<UserLegendaryUpgradeEvent, Long>
