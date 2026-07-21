package io.github.mralex1810.fantasy.polemica

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.PolemicaProperties
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.HtmlUtils

data class PolemicaPublicPointsRow(
    val tablePosition: Int,
    val points: Double,
)

data class PolemicaPublicPointsResult(
    val rows: List<PolemicaPublicPointsRow>,
    val warnings: List<String>,
)

@Component
class PolemicaPublicPointsLoader(
    private val polemicaPublicRestTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: PolemicaProperties,
) {
    fun load(polemicaGameId: Long): PolemicaPublicPointsResult {
        val html = polemicaPublicRestTemplate.getForObject(
            "${properties.profileSiteBaseUrl.removeSuffix("/")}/match/{id}",
            String::class.java,
            polemicaGameId,
        ) ?: throw IllegalStateException("Polemica match $polemicaGameId returned an empty response")
        val match = GAME_DATA_ATTRIBUTE.find(html)
            ?: throw IllegalStateException("Polemica match $polemicaGameId has no :game-data attribute")
        val root = objectMapper.readTree(HtmlUtils.htmlUnescape(match.groupValues[2]))
        val players = root.get("players")
        require(players != null && players.isArray) {
            "Polemica match $polemicaGameId has no valid players array"
        }

        val rows = mutableListOf<PolemicaPublicPointsRow>()
        val warnings = mutableListOf<String>()
        players.forEachIndexed { index, player ->
            val position = player.get("tablePosition")
            val points = player.get("points")
            val positionValue = position
                ?.takeIf { it.isIntegralNumber }
                ?.longValue()
                ?.takeIf { it in 1L..10L }
                ?.toInt()
            val pointsValue = points
                ?.takeIf { it.isNumber }
                ?.doubleValue()
                ?.takeIf { it.isFinite() }
            if (positionValue == null || pointsValue == null) {
                val reasons = buildList {
                    if (positionValue == null) add("tablePosition must be an integer from 1 to 10")
                    if (pointsValue == null) add("points must be a finite number")
                }
                warnings += "Public points row $index for game $polemicaGameId is invalid: ${reasons.joinToString()}"
            } else {
                rows += PolemicaPublicPointsRow(positionValue, pointsValue)
            }
        }
        return PolemicaPublicPointsResult(rows, warnings)
    }

    private companion object {
        val GAME_DATA_ATTRIBUTE = Regex(""":game-data\s*=\s*(['\"])(.*?)\1""", setOf(RegexOption.DOT_MATCHES_ALL))
    }
}
