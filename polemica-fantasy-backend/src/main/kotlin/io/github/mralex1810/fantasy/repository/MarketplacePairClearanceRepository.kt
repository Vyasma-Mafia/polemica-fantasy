package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplacePairClearance
import io.github.mralex1810.fantasy.entity.MarketplacePairClearanceId
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MarketplacePairClearanceRepository : JpaRepository<MarketplacePairClearance, MarketplacePairClearanceId> {
    /**
     * Rows whose [userIdLow] is in the set. Caller filters by (userIdLow, userIdHigh) in the desired key set
     * (avoids a Cartesian from IN(low) x IN(high)).
     */
    fun findByUserIdLowIn(userIds: Collection<Long>): List<MarketplacePairClearance>

    @Query(
        "DELETE FROM MarketplacePairClearance c " +
            "WHERE c.userIdLow = :low AND c.userIdHigh = :high",
    )
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteByUserIdPair(
        @Param("low") low: Long,
        @Param("high") high: Long,
    ): Int
}
