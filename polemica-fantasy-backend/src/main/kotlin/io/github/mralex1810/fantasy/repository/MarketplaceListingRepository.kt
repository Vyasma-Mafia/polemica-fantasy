package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplaceListing
import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MarketplaceListingRepository : JpaRepository<MarketplaceListing, Long> {

    fun existsByUserCard_IdAndStatus(userCardId: Long, status: MarketplaceListingStatus): Boolean

    fun findBySeller_IdAndStatusOrderByCreatedAtDesc(
        sellerId: Long,
        status: MarketplaceListingStatus,
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
        AND (:rarity IS NULL OR ct.rarity = :rarity)
        AND (:minPrice IS NULL OR ml.price >= :minPrice)
        AND (:maxPrice IS NULL OR ml.price <= :maxPrice)
        """,
    )
    fun findAllActiveFiltered(
        @Param("active") active: MarketplaceListingStatus,
        @Param("fantasyPlayerId") fantasyPlayerId: Long?,
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
}
