package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplaceListing
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MarketplaceListingRepository : JpaRepository<MarketplaceListing, Long> {

    fun deleteAllByUserCard_Id(userCardId: Long)

    fun existsByUserCard_IdAndStatus(userCardId: Long, status: MarketplaceListingStatus): Boolean

    fun findBySeller_IdAndStatusOrderByCreatedAtDesc(
        sellerId: Long,
        status: MarketplaceListingStatus,
    ): List<MarketplaceListing>

    @Query(
        """
        SELECT ml FROM MarketplaceListing ml
        JOIN ml.userCard uc
        WHERE uc.id IN :userCardIds AND ml.status = :status
        """,
    )
    fun findAllByUserCard_IdInAndStatus(
        @Param("userCardIds") userCardIds: Collection<Long>,
        @Param("status") status: MarketplaceListingStatus,
    ): List<MarketplaceListing>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT ml FROM MarketplaceListing ml
        JOIN FETCH ml.seller
        JOIN FETCH ml.userCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        WHERE ml.id = :id AND ml.status = :status
        """,
    )
    fun findByIdAndStatusForUpdate(
        @Param("id") id: Long,
        @Param("status") status: MarketplaceListingStatus,
    ): MarketplaceListing?

    @Query(
        """
        SELECT ml FROM MarketplaceListing ml
        JOIN ml.userCard uc
        JOIN uc.cardTemplate ct
        JOIN ct.fantasyPlayer fp
        WHERE ml.status = :active
        AND (:fantasyPlayerId IS NULL OR fp.id = :fantasyPlayerId)
        AND (:tournamentId IS NULL OR EXISTS (
            SELECT 1 FROM TournamentPlayer tp
            WHERE tp.fantasyPlayer.id = fp.id AND tp.tournament.id = :tournamentId
        ))
        AND (:seriesId IS NULL OR EXISTS (
            SELECT 1 FROM SeriesPlayer sp
            JOIN sp.tournamentPlayer tp2
            WHERE tp2.fantasyPlayer.id = fp.id AND sp.series.id = :seriesId
        ))
        AND (:rarity IS NULL OR ct.rarity = :rarity)
        AND (:minPrice IS NULL OR ml.price >= :minPrice)
        AND (:maxPrice IS NULL OR ml.price <= :maxPrice)
        """,
        countQuery = """
        SELECT count(ml) FROM MarketplaceListing ml
        JOIN ml.userCard uc
        JOIN uc.cardTemplate ct
        JOIN ct.fantasyPlayer fp
        WHERE ml.status = :active
        AND (:fantasyPlayerId IS NULL OR fp.id = :fantasyPlayerId)
        AND (:tournamentId IS NULL OR EXISTS (
            SELECT 1 FROM TournamentPlayer tp
            WHERE tp.fantasyPlayer.id = fp.id AND tp.tournament.id = :tournamentId
        ))
        AND (:seriesId IS NULL OR EXISTS (
            SELECT 1 FROM SeriesPlayer sp
            JOIN sp.tournamentPlayer tp2
            WHERE tp2.fantasyPlayer.id = fp.id AND sp.series.id = :seriesId
        ))
        AND (:rarity IS NULL OR ct.rarity = :rarity)
        AND (:minPrice IS NULL OR ml.price >= :minPrice)
        AND (:maxPrice IS NULL OR ml.price <= :maxPrice)
        """,
    )
    fun findAllActiveFiltered(
        @Param("active") active: MarketplaceListingStatus,
        @Param("fantasyPlayerId") fantasyPlayerId: Long?,
        @Param("tournamentId") tournamentId: Long?,
        @Param("seriesId") seriesId: Long?,
        @Param("rarity") rarity: Rarity?,
        @Param("minPrice") minPrice: Long?,
        @Param("maxPrice") maxPrice: Long?,
        pageable: Pageable,
    ): Page<MarketplaceListing>

    @Query(
        """
        SELECT ml FROM MarketplaceListing ml
        JOIN FETCH ml.seller
        JOIN FETCH ml.userCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        JOIN FETCH ml.buyer b
        WHERE ml.status = :sold AND ml.soldAt IS NOT NULL
        ORDER BY ml.soldAt DESC
        """,
    )
    fun findRecentSold(@Param("sold") sold: MarketplaceListingStatus, pageable: Pageable): List<MarketplaceListing>

    @Query(
        """
        SELECT ml FROM MarketplaceListing ml
        JOIN FETCH ml.seller
        JOIN FETCH ml.buyer
        JOIN FETCH ml.userCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer
        LEFT JOIN FETCH ml.soldCardTemplate sct
        LEFT JOIN FETCH sct.fantasyPlayer
        WHERE ml.id = :id
          AND ml.status = :sold
          AND ml.soldAt IS NOT NULL
        """,
    )
    fun findSoldByIdWithTradeDetails(
        @Param("id") id: Long,
        @Param("sold") sold: MarketplaceListingStatus,
    ): MarketplaceListing?

    @Query(
        """
        SELECT DISTINCT ml FROM MarketplaceListing ml
        JOIN FETCH ml.seller
        JOIN FETCH ml.buyer
        JOIN FETCH ml.userCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer
        LEFT JOIN FETCH ml.soldCardTemplate sct
        LEFT JOIN FETCH sct.fantasyPlayer
        WHERE ml.id IN :ids
        """,
    )
    fun findAllWithTradeDetailsByIdIn(
        @Param("ids") ids: Collection<Long>,
    ): List<MarketplaceListing>

    @Query(
        value =
            """
            SELECT ml.seller_id, ml.buyer_id,
                COUNT(*)::bigint,
                COALESCE(SUM(ml.price), 0)::bigint,
                COALESCE(SUM(ml.price - (ml.price * :pct) / 100), 0)::bigint
            FROM marketplace_listing ml
            WHERE ml.status = 'SOLD' AND ml.buyer_id IS NOT NULL
            GROUP BY ml.seller_id, ml.buyer_id
            """,
        nativeQuery = true,
    )
    fun aggregateSoldTradesBySellerBuyer(
        @Param("pct") pct: Int,
    ): List<Array<Any>>

    @Query(
        """
        SELECT ml FROM MarketplaceListing ml
        WHERE ml.status = :sold
        AND (
            (ml.seller.id = :idA AND ml.buyer.id = :idB)
            OR (ml.seller.id = :idB AND ml.buyer.id = :idA)
        )
        ORDER BY ml.soldAt ASC, ml.id ASC
        """,
    )
    fun findSoldListingsBetweenUsers(
        @Param("sold") sold: MarketplaceListingStatus,
        @Param("idA") idA: Long,
        @Param("idB") idB: Long,
    ): List<MarketplaceListing>

    @Query(
        """
        SELECT COALESCE(SUM(ml.price - (ml.price * :pct) / 100), 0)
        FROM MarketplaceListing ml
        WHERE ml.status = :sold AND ml.seller.id = :sellerId AND ml.buyer.id = :buyerId
        """,
    )
    fun sumSellerReceivedForSalesTo(
        @Param("sold") sold: MarketplaceListingStatus,
        @Param("sellerId") sellerId: Long,
        @Param("buyerId") buyerId: Long,
        @Param("pct") pct: Int,
    ): Long

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE MarketplaceListing ml
        SET ml.status = :cancelled
        WHERE ml.seller.id = :sellerId AND ml.status = :active
        """,
    )
    fun cancelAllActiveBySellerId(
        @Param("sellerId") sellerId: Long,
        @Param("active") active: MarketplaceListingStatus,
        @Param("cancelled") cancelled: MarketplaceListingStatus,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE MarketplaceListing ml
        SET ml.status = :cancelled
        WHERE ml.userCard.id = :userCardId
          AND ml.status = :active
        """,
    )
    fun cancelAllActiveByUserCardId(
        @Param("userCardId") userCardId: Long,
        @Param("active") active: MarketplaceListingStatus,
        @Param("cancelled") cancelled: MarketplaceListingStatus,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE MarketplaceListing ml
        SET ml.status = :cancelled
        WHERE ml.status = :active
          AND ml.userCard.cardTemplate.fantasyPlayer.id = :fantasyPlayerId
        """,
    )
    fun cancelAllActiveByFantasyPlayerId(
        @Param("fantasyPlayerId") fantasyPlayerId: Long,
        @Param("active") active: MarketplaceListingStatus,
        @Param("cancelled") cancelled: MarketplaceListingStatus,
    ): Int

    @Query(
        value = """
            SELECT ct.fantasy_player_id,
                   ct.rarity,
                   CAST(COUNT(*) AS bigint),
                   CAST(MIN(ml.price) AS bigint)
            FROM marketplace_listing ml
            JOIN user_card uc ON uc.id = ml.user_card_id
            JOIN card_template ct ON ct.id = uc.card_template_id
            WHERE ml.status = 'ACTIVE'
              AND ct.fantasy_player_id IN (:fantasyPlayerIds)
            GROUP BY ct.fantasy_player_id, ct.rarity
        """,
        nativeQuery = true,
    )
    fun findActiveListingSummaryByFantasyPlayerIds(
        @Param("fantasyPlayerIds") fantasyPlayerIds: Collection<Long>,
    ): List<Array<Any>>

    @Query(
        """
        SELECT COUNT(ml), MIN(ml.price), MAX(ml.price)
        FROM MarketplaceListing ml
        JOIN ml.userCard uc
        JOIN uc.cardTemplate ct
        WHERE ml.status = :active
          AND ct.fantasyPlayer.id = :fantasyPlayerId
          AND ct.rarity = :rarity
        """,
    )
    fun findActiveListingStatsForPlayerAndRarity(
        @Param("active") active: MarketplaceListingStatus,
        @Param("fantasyPlayerId") fantasyPlayerId: Long,
        @Param("rarity") rarity: Rarity,
    ): Array<Any>

    @Query(
        """
        SELECT ml FROM MarketplaceListing ml
        LEFT JOIN ml.soldCardTemplate ct
        WHERE ml.status = :sold
          AND COALESCE(ct.fantasyPlayer.id, ml.userCard.cardTemplate.fantasyPlayer.id) = :fantasyPlayerId
          AND COALESCE(ct.rarity, ml.userCard.cardTemplate.rarity) = :rarity
          AND ml.soldAt IS NOT NULL
        ORDER BY ml.soldAt DESC
        """,
    )
    fun findRecentSoldByPlayerAndRarity(
        @Param("sold") sold: MarketplaceListingStatus,
        @Param("fantasyPlayerId") fantasyPlayerId: Long,
        @Param("rarity") rarity: Rarity,
        pageable: Pageable,
    ): List<MarketplaceListing>

    fun countBySeller_IdAndStatus(sellerId: Long, status: MarketplaceListingStatus): Long

    fun countByBuyer_IdAndStatus(buyerId: Long, status: MarketplaceListingStatus): Long

    @Query(
        """
        SELECT ml FROM MarketplaceListing ml
        JOIN FETCH ml.userCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        JOIN FETCH ml.seller s
        LEFT JOIN FETCH ml.buyer b
        WHERE ml.status = :sold AND ml.soldAt IS NOT NULL
          AND (ml.seller.id = :userId OR ml.buyer.id = :userId)
        ORDER BY ml.soldAt DESC
        """,
    )
    fun findRecentTradesByUserId(
        @Param("sold") sold: MarketplaceListingStatus,
        @Param("userId") userId: Long,
        pageable: Pageable,
    ): List<MarketplaceListing>
}
