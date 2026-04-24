package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.MarketplaceListingStatus
import io.github.mralex1810.fantasy.entity.Rarity
import io.github.mralex1810.fantasy.entity.UserCard
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserCardRepository : JpaRepository<UserCard, Long> {

    fun countByTelegramUser_IdAndSourceCardPack_Id(telegramUserId: Long, sourceCardPackId: Long): Long

    fun findByIdAndTelegramUser_Id(id: Long, telegramUserId: Long): UserCard?

    @Query(
        """
        SELECT DISTINCT uc FROM UserCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH ct.achievements ach
        LEFT JOIN FETCH ach.achievement
        WHERE uc.telegramUser.id = :telegramUserId
        AND (:tournamentId IS NULL OR EXISTS (
            SELECT 1 FROM TournamentPlayer tp
            WHERE tp.fantasyPlayer.id = fp.id AND tp.tournament.id = :tournamentId
        ))
        AND (:seriesId IS NULL OR EXISTS (
            SELECT 1 FROM SeriesPlayer sp
            WHERE sp.series.id = :seriesId AND sp.tournamentPlayer.fantasyPlayer.id = fp.id
        ))
        AND (:rarity IS NULL OR ct.rarity = :rarity)
        ORDER BY uc.acquiredAt DESC
        """,
    )
    fun findAllForUserFiltered(
        @Param("telegramUserId") telegramUserId: Long,
        @Param("tournamentId") tournamentId: Long?,
        @Param("seriesId") seriesId: Long?,
        @Param("rarity") rarity: Rarity?,
    ): List<UserCard>

    @Query(
        """
        SELECT DISTINCT uc FROM UserCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH ct.achievements ach
        LEFT JOIN FETCH ach.achievement
        WHERE uc.telegramUser.id = :telegramUserId AND uc.id IN :ids
        """,
    )
    fun findAllByIdInAndTelegramUser_Id(
        @Param("ids") ids: Collection<Long>,
        @Param("telegramUserId") telegramUserId: Long,
    ): List<UserCard>

    @Query(
        """
        SELECT DISTINCT uc FROM UserCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH uc.craftedBy
        LEFT JOIN FETCH ct.achievements ach
        LEFT JOIN FETCH ach.achievement
        WHERE uc.id = :id AND uc.telegramUser.id = :telegramUserId
        """,
    )
    fun findByIdAndTelegramUser_IdWithTemplateAchievements(
        @Param("id") id: Long,
        @Param("telegramUserId") telegramUserId: Long,
    ): UserCard?

    @Query(
        """
        SELECT DISTINCT uc FROM UserCard uc
        JOIN FETCH uc.cardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        WHERE uc.telegramUser.id = :currentOwnerId
        AND EXISTS (
            SELECT 1 FROM MarketplaceListing ml
            WHERE ml.userCard.id = uc.id
            AND ml.status = :sold
            AND ml.seller.id = :partnerId
            AND ml.buyer.id = uc.telegramUser.id
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
        LEFT JOIN FETCH ct.achievements ach
        LEFT JOIN FETCH ach.achievement
        """,
    )
    fun findAllForGlobalRating(): List<UserCard>
}
