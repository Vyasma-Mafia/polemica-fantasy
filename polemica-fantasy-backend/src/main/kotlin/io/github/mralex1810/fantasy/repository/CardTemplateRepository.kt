package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.CardTemplate
import io.github.mralex1810.fantasy.entity.Rarity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface CardTemplateRepository : JpaRepository<CardTemplate, Long> {

    @Query(
        """
        SELECT ct FROM CardTemplate ct
        JOIN ct.fantasyPlayer fp
        WHERE (:fantasyPlayerId IS NULL OR fp.id = :fantasyPlayerId)
        AND (:rarity IS NULL OR ct.rarity = :rarity)
        AND (
            :tournamentId IS NULL OR EXISTS (
                SELECT 1 FROM TournamentPlayer tp
                WHERE tp.fantasyPlayer.id = fp.id AND tp.tournament.id = :tournamentId
            )
        )
        """,
    )
    fun findAllFiltered(
        @Param("tournamentId") tournamentId: Long?,
        @Param("fantasyPlayerId") fantasyPlayerId: Long?,
        @Param("rarity") rarity: Rarity?,
    ): List<CardTemplate>

    fun findAllByRarity(rarity: Rarity): List<CardTemplate>

    fun findAllByFantasyPlayer_IdAndRarity(fantasyPlayerId: Long, rarity: Rarity): List<CardTemplate>

    fun countByFantasyPlayer_Id(fantasyPlayerId: Long): Long

    @Query(
        """
        SELECT DISTINCT ct FROM CardTemplate ct
        JOIN FETCH ct.fantasyPlayer fp
        LEFT JOIN FETCH ct.perks perk
        LEFT JOIN FETCH perk.perk
        WHERE ct.id IN :ids
        """,
    )
    fun findAllByIdWithPerksLoaded(@Param("ids") ids: Collection<Long>): List<CardTemplate>
}
