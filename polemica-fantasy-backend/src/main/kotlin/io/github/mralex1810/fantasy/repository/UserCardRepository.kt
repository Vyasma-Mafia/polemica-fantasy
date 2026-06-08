package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.UserCard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import jakarta.persistence.LockModeType

interface UserCardRepository : JpaRepository<UserCard, Long> {

    fun countByTelegramUser_IdAndSourceCardPack_Id(telegramUserId: Long, sourceCardPackId: Long): Long

    fun countByTelegramUser_IdAndDeletedAtIsNull(telegramUserId: Long): Long

    @Query(
        """
        SELECT uc FROM UserCard uc
        WHERE uc.id = :id
          AND uc.telegramUser.id = :telegramUserId
          AND uc.deletedAt IS NULL
        """,
    )
    fun findByIdAndTelegramUser_Id(
        @Param("id") id: Long,
        @Param("telegramUserId") telegramUserId: Long,
    ): UserCard?

    @Query(
        """
        SELECT DISTINCT uc FROM UserCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH uc.cardSkin
        LEFT JOIN FETCH ct.perks perk
        LEFT JOIN FETCH perk.perk
        WHERE uc.telegramUser.id = :telegramUserId
        AND uc.deletedAt IS NULL
        AND (:tournamentId IS NULL OR EXISTS (
            SELECT 1 FROM TournamentPlayer tp
            WHERE tp.fantasyPlayer.id = fp.id AND tp.tournament.id = :tournamentId
        ))
        AND (:seriesId IS NULL OR EXISTS (
            SELECT 1 FROM SeriesPlayer sp
            WHERE sp.series.id = :seriesId AND sp.tournamentPlayer.fantasyPlayer.id = fp.id
        ))
        AND (:rarity IS NULL OR ct.rarity = :rarity)
        AND (:perkIdsEmpty = TRUE OR EXISTS (
            SELECT 1 FROM CardTemplatePerk cta
            WHERE cta.cardTemplate.id = ct.id AND cta.perk.id IN :perkIds
        ))
        ORDER BY uc.acquiredAt DESC
        """,
    )
    fun findAllForUserFiltered(
        @Param("telegramUserId") telegramUserId: Long,
        @Param("tournamentId") tournamentId: Long?,
        @Param("seriesId") seriesId: Long?,
        @Param("rarity") rarity: Rarity?,
        @Param("perkIdsEmpty") perkIdsEmpty: Boolean,
        @Param("perkIds") perkIds: Collection<String>,
    ): List<UserCard>

    @Query(
        """
        SELECT DISTINCT uc FROM UserCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH uc.cardSkin
        LEFT JOIN FETCH ct.perks perk
        LEFT JOIN FETCH perk.perk
        WHERE uc.telegramUser.id = :telegramUserId
          AND uc.id IN :ids
          AND uc.deletedAt IS NULL
        """,
    )
    fun findAllByIdInAndTelegramUser_Id(
        @Param("ids") ids: Collection<Long>,
        @Param("telegramUserId") telegramUserId: Long,
    ): List<UserCard>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT uc FROM UserCard uc
        WHERE uc.telegramUser.id = :telegramUserId
          AND uc.id IN :ids
          AND uc.deletedAt IS NULL
        ORDER BY uc.id ASC
        """,
    )
    fun findAllByIdInAndTelegramUser_IdForUpdate(
        @Param("ids") ids: Collection<Long>,
        @Param("telegramUserId") telegramUserId: Long,
    ): List<UserCard>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT uc FROM UserCard uc
        WHERE uc.id IN :ids
        ORDER BY uc.id ASC
        """,
    )
    fun findAllByIdInForUpdate(@Param("ids") ids: Collection<Long>): List<UserCard>

    @Query(
        """
        SELECT DISTINCT uc FROM UserCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH uc.cardSkin
        LEFT JOIN FETCH uc.craftedBy
        LEFT JOIN FETCH ct.perks perk
        LEFT JOIN FETCH perk.perk
        WHERE uc.id = :id
          AND uc.telegramUser.id = :telegramUserId
          AND uc.deletedAt IS NULL
        """,
    )
    fun findByIdAndTelegramUser_IdWithTemplatePerks(
        @Param("id") id: Long,
        @Param("telegramUserId") telegramUserId: Long,
    ): UserCard?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        SELECT uc FROM UserCard uc
        WHERE uc.id = :id
          AND uc.telegramUser.id = :telegramUserId
          AND uc.deletedAt IS NULL
        """,
    )
    fun findByIdAndTelegramUser_IdForUpdate(
        @Param("id") id: Long,
        @Param("telegramUserId") telegramUserId: Long,
    ): UserCard?

    @Query(
        """
        SELECT DISTINCT uc FROM UserCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH uc.cardSkin
        WHERE uc.telegramUser.id = :currentOwnerId
        AND uc.deletedAt IS NULL
        AND EXISTS (
            SELECT 1 FROM MarketplaceListing ml
            WHERE ml.userCard.id = uc.id
            AND ml.status = :sold
            AND ml.seller.id = :partnerId
            AND ml.buyer.id = uc.telegramUser.id
            AND NOT EXISTS (
                SELECT 1 FROM MarketplaceListingSanction s WHERE s.listing.id = ml.id
            )
        )
        """,
    )
    fun findUserCardsBoughtOnMarketplaceFromPartner(
        @Param("currentOwnerId") currentOwnerId: Long,
        @Param("partnerId") partnerId: Long,
        @Param("sold") sold: MarketplaceListingStatus,
    ): List<UserCard>

    /**
     * All user cards (including [io.github.mralex1810.fantasy.entity.UserCard.usesRemaining] = 0 and active listings).
     * For global rating portfolio value.
     */
    @Query(
        """
        SELECT DISTINCT uc FROM UserCard uc
        JOIN FETCH uc.telegramUser tu
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH uc.cardSkin
        LEFT JOIN FETCH ct.perks perk
        LEFT JOIN FETCH perk.perk
        WHERE uc.deletedAt IS NULL
        """,
    )
    fun findAllForGlobalRating(): List<UserCard>

    @Query(
        """
        SELECT ct.rarity, COUNT(uc)
        FROM UserCard uc JOIN uc.cardTemplate ct
        WHERE uc.telegramUser.id = :userId
          AND uc.deletedAt IS NULL
        GROUP BY ct.rarity
        """,
    )
    fun countByUserGroupedByRarity(@Param("userId") userId: Long): List<Array<Any>>
}
