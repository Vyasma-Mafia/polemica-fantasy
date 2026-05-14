package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplaceListingSanction
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MarketplaceListingSanctionRepository : JpaRepository<MarketplaceListingSanction, Long> {
    fun existsByListing_Id(listingId: Long): Boolean

    fun findByListing_Id(listingId: Long): MarketplaceListingSanction?

    fun findAllByListing_IdIn(listingIds: Collection<Long>): List<MarketplaceListingSanction>

    @Query(
        """
        SELECT s.listing.id
        FROM MarketplaceListingSanction s
        WHERE s.listing.id IN :listingIds
        """,
    )
    fun findListingIdsWithSanctions(@Param("listingIds") listingIds: Collection<Long>): List<Long>
}
