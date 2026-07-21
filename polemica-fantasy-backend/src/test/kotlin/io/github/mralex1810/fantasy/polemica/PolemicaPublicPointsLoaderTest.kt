package io.github.mralex1810.fantasy.polemica

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.mralex1810.fantasy.config.PolemicaProperties
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PolemicaPublicPointsLoaderTest {
    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.bindTo(restTemplate).build()
    private val loader = PolemicaPublicPointsLoader(
        restTemplate,
        ObjectMapper(),
        PolemicaProperties(profileSiteBaseUrl = "https://public.example"),
    )

    @Test
    fun `keeps zero negative and fractional points`() {
        respond("""{"players":[{"tablePosition":1,"points":0},{"tablePosition":2,"points":-1.5},{"tablePosition":3,"points":2.25}]}""")

        val result = loader.load(42)

        assertEquals(listOf(0.0, -1.5, 2.25), result.rows.map { it.points })
        assertEquals(emptyList(), result.warnings)
        server.verify()
    }

    @Test
    fun `missing and non numeric points are reported and never converted to zero`() {
        respond("""{"players":[{"tablePosition":1},{"tablePosition":2,"points":"7"},{"tablePosition":3,"points":4}]}""")

        val result = loader.load(42)

        assertEquals(listOf(PolemicaPublicPointsRow(3, 4.0)), result.rows)
        assertEquals(2, result.warnings.size)
        server.verify()
    }

    @Test
    fun `players must be an array`() {
        respond("""{"players":null}""")

        assertFailsWith<IllegalArgumentException> { loader.load(42) }
        server.verify()
    }

    private fun respond(gameData: String) {
        val escaped = gameData.replace("\"", "&quot;")
        server.expect(requestTo("https://public.example/match/42"))
            .andRespond(withSuccess("<div :game-data='$escaped'></div>", MediaType.TEXT_HTML))
    }
}
