package io.github.mralex1810.fantasy.repository

import io.github.mralex1810.fantasy.entity.SeriesStreamLink
import org.springframework.data.jpa.repository.JpaRepository

interface SeriesStreamLinkRepository : JpaRepository<SeriesStreamLink, Long> {
    fun findAllBySeries_IdOrderByDisplayOrderAscIdAsc(seriesId: Long): List<SeriesStreamLink>

    fun findAllBySeries_IdInOrderBySeries_IdAscDisplayOrderAscIdAsc(
        seriesIds: Collection<Long>,
    ): List<SeriesStreamLink>

    fun deleteAllBySeries_Id(seriesId: Long)
}
