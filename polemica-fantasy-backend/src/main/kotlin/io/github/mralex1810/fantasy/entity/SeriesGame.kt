package io.github.mralex1810.fantasy.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "series_game")
class SeriesGame(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "series_id", nullable = false)
    var series: Series? = null,

    @Column(name = "polemica_game_id", nullable = false)
    var polemicaGameId: Long = 0L,

    @Column(name = "game_name", nullable = false)
    var gameName: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "game_data_cache", columnDefinition = "jsonb")
    var gameDataCache: JsonNode? = null,

    @Column(nullable = false)
    var scored: Boolean = false,

    @Column(name = "played_at")
    var playedAt: Instant? = null,
)
