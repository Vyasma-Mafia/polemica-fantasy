package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplaceComplaint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface MarketplaceComplaintRepository : JpaRepository<MarketplaceComplaint, Long> {
    fun existsByListing_IdAndTelegramUser_Id(listingId: Long, telegramUserId: Long): Boolean

    fun countByListing_Id(listingId: Long): Int

    @Query(
        """
        SELECT COUNT(c) FROM MarketplaceComplaint c
        WHERE c.telegramUser.id = :userId
          AND c.createdAt >= :since
        """,
    )
    fun countByTelegramUserIdSince(
        @Param("userId") userId: Long,
        @Param("since") since: Instant,
    ): Int

    fun findAllByListing_Id(listingId: Long): List<MarketplaceComplaint>

    fun findAllByListing_IdOrderByCreatedAtAsc(listingId: Long): List<MarketplaceComplaint>

    @Query(
        """
        SELECT c.listing.id, COUNT(c.id)
        FROM MarketplaceComplaint c
        WHERE c.listing.id IN :listingIds
        GROUP BY c.listing.id
        """,
    )
    fun countGroupedByListingIds(@Param("listingIds") listingIds: Collection<Long>): List<Array<Any>>

    @Query(
        """
        SELECT c.listing.id, COUNT(c.id)
        FROM MarketplaceComplaint c
        GROUP BY c.listing.id
        HAVING COUNT(c.id) >= :minComplaints
        """,
    )
    fun findListingComplaintCounts(@Param("minComplaints") minComplaints: Long): List<Array<Any>>
}
